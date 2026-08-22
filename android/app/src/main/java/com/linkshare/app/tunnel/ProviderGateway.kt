package com.linkshare.app.tunnel

import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Userspace IPv4 provider gateway. Unsupported protocols are rejected so
 * traffic can never silently bypass the LINKO tunnel boundary.
 */
class ProviderGateway(
    private val emitPacket: (ByteArray) -> Unit,
) : Closeable {
    data class FlowKey(val src: String, val srcPort: Int, val dst: String, val dstPort: Int)
    private data class TcpFlow(
        val key: FlowKey,
        val socket: Socket,
        var sendSeq: Long,
        var recvAck: Long,
    )

    private val workers = Executors.newCachedThreadPool()
    private val tcpFlows = ConcurrentHashMap<FlowKey, TcpFlow>()
    private val random = SecureRandom()

    fun forwardIpv4(packet: ByteArray): GatewayResult {
        val ip = Ipv4Packet.parse(packet) ?: return GatewayResult.Drop("invalid-ipv4")
        return when (ip.protocol) {
            17 -> forwardUdp(ip)
            6 -> forwardTcp(ip)
            else -> GatewayResult.Drop("unsupported-ip-protocol-${ip.protocol}")
        }
    }

    private fun forwardUdp(ip: Ipv4Packet): GatewayResult {
        val udp = UdpSegment.parse(ip.payload) ?: return GatewayResult.Drop("invalid-udp")
        val socket = DatagramSocket().apply { soTimeout = 3_000 }
        return try {
            socket.send(DatagramPacket(udp.payload, udp.payload.size, InetSocketAddress(ip.destination, udp.destinationPort)))
            workers.submit {
                runCatching {
                    val buffer = ByteArray(65_535)
                    val response = DatagramPacket(buffer, buffer.size)
                    socket.receive(response)
                    emitPacket(buildUdpIpv4(ip.destination, ip.source, udp.destinationPort, udp.sourcePort, response.data.copyOf(response.length)))
                }
                socket.close()
            }
            GatewayResult.UdpForwarded
        } catch (_: Exception) {
            socket.close()
            GatewayResult.Drop("udp-forward-failed")
        }
    }

    private fun forwardTcp(ip: Ipv4Packet): GatewayResult {
        val tcp = TcpSegment.parse(ip.payload) ?: return GatewayResult.Drop("invalid-tcp")
        val key = FlowKey(ip.source, tcp.sourcePort, ip.destination, tcp.destinationPort)

        if (tcp.syn && !tcp.ack) {
            if (tcpFlows.containsKey(key)) return GatewayResult.TcpAccepted(key)
            val socket = Socket()
            return try {
                socket.connect(InetSocketAddress(ip.destination, tcp.destinationPort), 5_000)
                val providerSeq = random.nextInt().toLong() and 0xffffffffL
                val flow = TcpFlow(key, socket, providerSeq + 1, (tcp.sequence + 1) and 0xffffffffL)
                tcpFlows[key] = flow
                emitPacket(buildTcpIpv4(ip.destination, ip.source, tcp.destinationPort, tcp.sourcePort, providerSeq, flow.recvAck, 0x12, ByteArray(0)))
                workers.submit { readTcp(flow) }
                GatewayResult.TcpConnected(key)
            } catch (_: Exception) {
                socket.close()
                GatewayResult.Drop("tcp-connect-failed")
            }
        }

        val flow = tcpFlows[key] ?: return GatewayResult.Drop("unknown-tcp-flow")
        if (tcp.ack) flow.recvAck = tcp.acknowledgement
        if (tcp.payload.isNotEmpty()) {
            runCatching {
                flow.socket.getOutputStream().write(tcp.payload)
                flow.socket.getOutputStream().flush()
                flow.recvAck = (tcp.sequence + tcp.payload.size) and 0xffffffffL
                emitPacket(buildTcpIpv4(ip.destination, ip.source, tcp.destinationPort, tcp.sourcePort, flow.sendSeq, flow.recvAck, 0x10, ByteArray(0)))
            }.onFailure { closeFlow(key) }
        }
        if (tcp.fin) {
            flow.recvAck = (tcp.sequence + tcp.payload.size + 1) and 0xffffffffL
            emitPacket(buildTcpIpv4(ip.destination, ip.source, tcp.destinationPort, tcp.sourcePort, flow.sendSeq, flow.recvAck, 0x11, ByteArray(0)))
            closeFlow(key)
        } else if (tcp.rst) {
            closeFlow(key)
        }
        return GatewayResult.TcpAccepted(key)
    }

    private fun readTcp(flow: TcpFlow) {
        val buffer = ByteArray(16 * 1024)
        try {
            val input = flow.socket.getInputStream()
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                val payload = buffer.copyOf(read)
                emitPacket(buildTcpIpv4(flow.key.dst, flow.key.src, flow.key.dstPort, flow.key.srcPort, flow.sendSeq, flow.recvAck, 0x18, payload))
                flow.sendSeq = (flow.sendSeq + read) and 0xffffffffL
            }
            emitPacket(buildTcpIpv4(flow.key.dst, flow.key.src, flow.key.dstPort, flow.key.srcPort, flow.sendSeq, flow.recvAck, 0x11, ByteArray(0)))
        } catch (_: Exception) {
            emitPacket(buildTcpIpv4(flow.key.dst, flow.key.src, flow.key.dstPort, flow.key.srcPort, flow.sendSeq, flow.recvAck, 0x14, ByteArray(0)))
        } finally {
            closeFlow(flow.key)
        }
    }

    private fun closeFlow(key: FlowKey) {
        tcpFlows.remove(key)?.socket?.let { runCatching { it.close() } }
    }

    override fun close() {
        tcpFlows.keys.toList().forEach(::closeFlow)
        workers.shutdownNow()
    }

    sealed interface GatewayResult {
        data object UdpForwarded : GatewayResult
        data class TcpConnected(val key: FlowKey) : GatewayResult
        data class TcpAccepted(val key: FlowKey) : GatewayResult
        data class Drop(val reason: String) : GatewayResult
    }

    data class Ipv4Packet(val source: String, val destination: String, val protocol: Int, val payload: ByteArray) {
        companion object {
            fun parse(packet: ByteArray): Ipv4Packet? {
                if (packet.size < 20 || ((packet[0].toInt() ushr 4) and 0x0f) != 4) return null
                val header = (packet[0].toInt() and 0x0f) * 4
                val total = u16(packet, 2)
                if (header < 20 || total < header || total > packet.size) return null
                return Ipv4Packet(ipv4(packet, 12), ipv4(packet, 16), packet[9].toInt() and 0xff, packet.copyOfRange(header, total))
            }
            private fun ipv4(b: ByteArray, o: Int): String = "${b[o].toInt() and 255}.${b[o + 1].toInt() and 255}.${b[o + 2].toInt() and 255}.${b[o + 3].toInt() and 255}"
        }
    }

    data class UdpSegment(val sourcePort: Int, val destinationPort: Int, val payload: ByteArray) {
        companion object {
            fun parse(b: ByteArray): UdpSegment? {
                if (b.size < 8) return null
                val len = u16(b, 4)
                if (len < 8 || len > b.size) return null
                return UdpSegment(u16(b, 0), u16(b, 2), b.copyOfRange(8, len))
            }
        }
    }

    data class TcpSegment(val sourcePort: Int, val destinationPort: Int, val sequence: Long, val acknowledgement: Long, val syn: Boolean, val ack: Boolean, val fin: Boolean, val rst: Boolean, val payload: ByteArray) {
        companion object {
            fun parse(b: ByteArray): TcpSegment? {
                if (b.size < 20) return null
                val header = ((b[12].toInt() ushr 4) and 0x0f) * 4
                if (header < 20 || header > b.size) return null
                val flags = b[13].toInt() and 255
                return TcpSegment(u16(b, 0), u16(b, 2), u32(b, 4), u32(b, 8), flags and 2 != 0, flags and 16 != 0, flags and 1 != 0, flags and 4 != 0, b.copyOfRange(header, b.size))
            }
        }
    }

    private fun buildUdpIpv4(src: String, dst: String, srcPort: Int, dstPort: Int, payload: ByteArray): ByteArray {
        val udp = ByteBuffer.allocate(8 + payload.size)
        udp.putShort(srcPort.toShort()).putShort(dstPort.toShort()).putShort((8 + payload.size).toShort()).putShort(0).put(payload)
        return buildIpv4(src, dst, 17, udp.array())
    }

    private fun buildTcpIpv4(src: String, dst: String, srcPort: Int, dstPort: Int, sequence: Long, acknowledgement: Long, flags: Int, payload: ByteArray): ByteArray {
        val tcp = ByteBuffer.allocate(20 + payload.size)
        tcp.putShort(srcPort.toShort()).putShort(dstPort.toShort()).putInt(sequence.toInt()).putInt(acknowledgement.toInt()).put(0x50).put(flags.toByte()).putShort(65535.toShort()).putShort(0).putShort(0).put(payload)
        return buildIpv4(src, dst, 6, tcp.array())
    }

    private fun buildIpv4(src: String, dst: String, protocol: Int, payload: ByteArray): ByteArray {
        val packet = ByteBuffer.allocate(20 + payload.size)
        packet.put(0x45).put(0).putShort((20 + payload.size).toShort()).putShort(0).putShort(0x4000.toShort()).put(64).put(protocol.toByte()).putShort(0).put(ip(src)).put(ip(dst)).put(payload)
        val data = packet.array()
        val checksum = checksum(data, 0, 20)
        data[10] = (checksum ushr 8).toByte(); data[11] = checksum.toByte()
        return data
    }

    private fun ip(address: String): ByteArray = address.split('.').map(String::toInt).map(Int::toByte).toByteArray()

    private fun checksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        while (i < offset + length) {
            sum += ((data[i].toInt() and 255) shl 8) or if (i + 1 < offset + length) (data[i + 1].toInt() and 255) else 0
            while (sum ushr 16 != 0) sum = (sum and 65535) + (sum ushr 16)
            i += 2
        }
        return sum.inv() and 65535
    }

    companion object {
        private fun u16(b: ByteArray, o: Int) = ((b[o].toInt() and 255) shl 8) or (b[o + 1].toInt() and 255)
        private fun u32(b: ByteArray, o: Int) = (((b[o].toLong() and 255) shl 24) or ((b[o + 1].toLong() and 255) shl 16) or ((b[o + 2].toLong() and 255) shl 8) or (b[o + 3].toLong() and 255)) and 0xffffffffL
    }
}
