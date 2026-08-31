package com.linkshare.app.tunnel

import java.io.Closeable
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Loopback-only SOCKS5 server used by the provider-side tun2socks engine.
 * Supports CONNECT and UDP ASSOCIATE; BIND is rejected.
 */
class ProviderSocks5Server : Closeable {
    private val running = AtomicBoolean(false)
    private val workers: ExecutorService = Executors.newCachedThreadPool()
    private var tcpServer: ServerSocket? = null
    private var udpRelay: DatagramSocket? = null

    val port: Int get() = tcpServer?.localPort ?: error("provider_socks5_not_started")

    fun start(): Int {
        check(running.compareAndSet(false, true)) { "provider_socks5_already_started" }
        tcpServer = ServerSocket(0, 32, InetAddress.getLoopbackAddress())
        workers.execute {
            while (running.get()) {
                try {
                    val accepted = tcpServer!!.accept()
                    workers.execute { handleClient(accepted) }
                } catch (_: Exception) {
                    // Closing the server socket is the normal termination path.
                }
            }
        }
        return tcpServer!!.localPort
    }

    private fun handleClient(client: Socket) {
        client.use { socket ->
            socket.soTimeout = 15_000
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            try {
                socksHandshake(input, output)
                when (readRequestCommand(input)) {
                    CMD_CONNECT -> handleConnect(input, output, socket)
                    CMD_UDP_ASSOCIATE -> handleUdpAssociate(output, socket)
                    CMD_BIND -> reply(output, REP_COMMAND_NOT_SUPPORTED)
                    else -> reply(output, REP_COMMAND_NOT_SUPPORTED)
                }
            } catch (_: Exception) {
                runCatching { reply(output, REP_GENERAL_FAILURE) }
            }
        }
    }

    private fun socksHandshake(input: InputStream, output: OutputStream) {
        require(input.read() == SOCKS_VERSION)
        val methodCount = input.read()
        require(methodCount in 0..16)
        val methods = ByteArray(methodCount)
        readFully(input, methods)
        require(methods.any { (it.toInt() and 0xff) == METHOD_NO_AUTH })
        output.write(byteArrayOf(SOCKS_VERSION.toByte(), METHOD_NO_AUTH.toByte()))
        output.flush()
    }

    private fun readRequestCommand(input: InputStream): Int {
        require(input.read() == SOCKS_VERSION)
        val command = input.read()
        require(input.read() == 0)
        return command
    }

    private fun handleConnect(input: InputStream, output: OutputStream, client: Socket) {
        val target = readTargetAddress(input)
        val upstream = Socket()
        try {
            upstream.connect(target, CONNECT_TIMEOUT_MS)
            reply(output, REP_SUCCEEDED, upstream.localAddress, upstream.localPort)
            client.soTimeout = 0
            upstream.soTimeout = 0
            proxyBidirectional(client, upstream)
        } catch (_: Exception) {
            upstream.close()
            reply(output, REP_GENERAL_FAILURE)
        }
    }

    private fun handleUdpAssociate(output: OutputStream, client: Socket) {
        val relay = DatagramSocket(0, InetAddress.getLoopbackAddress())
        udpRelay = relay
        reply(output, REP_SUCCEEDED, relay.localAddress, relay.localPort)
        client.soTimeout = 0
        val clientAddress = client.inetAddress
        val buffer = ByteArray(MAX_UDP_PACKET)
        try {
            while (running.get() && !client.isClosed) {
                val packet = DatagramPacket(buffer, buffer.size)
                relay.receive(packet)
                // The endpoint is loopback-only, so source-port changes made by the
                // SOCKS5 client are safe and expected for UDP ASSOCIATE.
                if (!packet.address.isLoopbackAddress || packet.address != clientAddress) continue
                if (packet.length < UDP_HEADER_MIN) continue
                val target = decodeUdpRequest(packet) ?: continue
                DatagramSocket().use { responseSocket ->
                    responseSocket.soTimeout = UDP_TIMEOUT_MS
                    responseSocket.send(DatagramPacket(target.payload, target.payload.size, target.address, target.port))
                    val responseBuffer = ByteArray(MAX_UDP_PACKET)
                    val response = DatagramPacket(responseBuffer, responseBuffer.size)
                    responseSocket.receive(response)
                    val encoded = encodeUdpResponse(response.address, response.port, response.data.copyOf(response.length))
                    relay.send(DatagramPacket(encoded, encoded.size, clientAddress, packet.port))
                }
            }
        } finally {
            relay.close()
            if (udpRelay === relay) udpRelay = null
        }
    }

    private fun readTargetAddress(input: InputStream): InetSocketAddress {
        val atyp = input.read()
        val host = when (atyp) {
            ATYP_IPV4 -> InetAddress.getByAddress(readExact(input, 4)).hostAddress
            ATYP_DOMAIN -> {
                val length = input.read()
                require(length in 1..255)
                String(readExact(input, length), Charsets.US_ASCII)
            }
            ATYP_IPV6 -> InetAddress.getByAddress(readExact(input, 16)).hostAddress
            else -> throw IllegalArgumentException("unsupported_socks5_address_type")
        }
        val high = input.read()
        val low = input.read()
        require(high >= 0 && low >= 0)
        val port = ((high and 0xff) shl 8) or (low and 0xff)
        require(port in 1..65535)
        return InetSocketAddress(host, port)
    }

    private fun decodeUdpRequest(packet: DatagramPacket): UdpTarget? {
        val bytes = packet.data
        var offset = packet.offset
        val end = packet.offset + packet.length
        if (end - offset < 4) return null
        offset += 2
        val frag = bytes[offset++].toInt() and 0xff
        if (frag != 0) return null
        val atyp = bytes[offset++].toInt() and 0xff
        val address: InetAddress
        when (atyp) {
            ATYP_IPV4 -> { if (end - offset < 4) return null; address = InetAddress.getByAddress(bytes.copyOfRange(offset, offset + 4)); offset += 4 }
            ATYP_DOMAIN -> { if (end - offset < 1) return null; val len = bytes[offset++].toInt() and 0xff; if (end - offset < len) return null; address = InetAddress.getByName(String(bytes, offset, len, Charsets.US_ASCII)); offset += len }
            ATYP_IPV6 -> { if (end - offset < 16) return null; address = InetAddress.getByAddress(bytes.copyOfRange(offset, offset + 16)); offset += 16 }
            else -> return null
        }
        if (end - offset < 2) return null
        val high = bytes[offset++].toInt() and 0xff
        val low = bytes[offset++].toInt() and 0xff
        val port = (high shl 8) or low
        if (port !in 1..65535 || offset > end) return null
        return UdpTarget(address, port, bytes.copyOfRange(offset, end))
    }

    private fun encodeUdpResponse(address: InetAddress, port: Int, payload: ByteArray): ByteArray {
        val addr = address.address
        val atyp = if (addr.size == 16) ATYP_IPV6 else ATYP_IPV4
        val out = java.io.ByteArrayOutputStream()
        out.write(0); out.write(0); out.write(0); out.write(atyp)
        out.write(addr)
        out.write((port ushr 8) and 0xff); out.write(port and 0xff)
        out.write(payload)
        return out.toByteArray()
    }

    private fun proxyBidirectional(left: Socket, right: Socket) {
        val a = workers.submit { copy(left.getInputStream(), right.getOutputStream()) }
        val b = workers.submit { copy(right.getInputStream(), left.getOutputStream()) }
        runCatching { a.get() }
        runCatching { b.get() }
        runCatching { right.close() }
    }

    private fun copy(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(16 * 1024)
        while (running.get()) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            output.write(buffer, 0, count)
            output.flush()
        }
    }

    private fun reply(output: OutputStream, code: Int, address: InetAddress = InetAddress.getLoopbackAddress(), port: Int = 0) {
        val addr = address.address
        output.write(byteArrayOf(SOCKS_VERSION.toByte(), code.toByte(), 0, if (addr.size == 16) ATYP_IPV6.toByte() else ATYP_IPV4.toByte()))
        output.write(addr)
        output.write((port ushr 8) and 0xff)
        output.write(port and 0xff)
        output.flush()
    }

    override fun close() {
        if (!running.compareAndSet(true, false)) return
        runCatching { tcpServer?.close() }
        runCatching { udpRelay?.close() }
        tcpServer = null
        udpRelay = null
        workers.shutdownNow()
    }

    private fun readExact(input: InputStream, length: Int): ByteArray {
        val result = ByteArray(length)
        readFully(input, result)
        return result
    }

    private fun readFully(input: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val count = input.read(buffer, offset, buffer.size - offset)
            if (count < 0) throw EOFException()
            offset += count
        }
    }

    private data class UdpTarget(val address: InetAddress, val port: Int, val payload: ByteArray)

    companion object {
        private const val SOCKS_VERSION = 5
        private const val METHOD_NO_AUTH = 0
        private const val CMD_CONNECT = 1
        private const val CMD_BIND = 2
        private const val CMD_UDP_ASSOCIATE = 3
        private const val ATYP_IPV4 = 1
        private const val ATYP_DOMAIN = 3
        private const val ATYP_IPV6 = 4
        private const val REP_SUCCEEDED = 0
        private const val REP_GENERAL_FAILURE = 1
        private const val REP_COMMAND_NOT_SUPPORTED = 7
        private const val UDP_HEADER_MIN = 4
        private const val MAX_UDP_PACKET = 65_507
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val UDP_TIMEOUT_MS = 5_000
    }
}
