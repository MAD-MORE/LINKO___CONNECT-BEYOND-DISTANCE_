package com.linkshare.app.tunnel

import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Provider-side IPv4/UDP forwarder.
 *
 * Each virtual UDP flow owns a persistent provider socket and a dedicated receive worker.
 * The tunnel receive loop therefore never blocks waiting for an Internet response.
 */
class ProviderUdpPacketForwarder : Closeable {
    private data class FlowKey(
        val source: String,
        val sourcePort: Int,
        val destination: String,
        val destinationPort: Int,
    )

    private data class Flow(
        val key: FlowKey,
        val socket: DatagramSocket,
        val closed: AtomicBoolean = AtomicBoolean(false),
        val queryGeneration: AtomicLong = AtomicLong(0L),
        var lastQuery: ByteArray? = null,
        var lastQueryAt: Long = 0L,
        var lastResponseAt: Long = 0L,
        var lastDnsFallbackGeneration: Long = 0L,
        var lastSeenAt: Long = System.currentTimeMillis(),
    )

    private val flows = ConcurrentHashMap<FlowKey, Flow>()
    private val responses = ConcurrentLinkedQueue<ByteArray>()
    private val executor = Executors.newCachedThreadPool()
    private val cleaner = Executors.newSingleThreadScheduledExecutor()

    init {
        cleaner.scheduleWithFixedDelay({
            val now = System.currentTimeMillis()
            flows.values.forEach { flow ->
                if (flow.closed.get() || now - flow.lastSeenAt > FLOW_IDLE_TIMEOUT_MS) {
                    closeFlow(flow.key)
                }
            }
        }, FLOW_CLEAN_INTERVAL_MS, FLOW_CLEAN_INTERVAL_MS, TimeUnit.MILLISECONDS)
    }

    /**
     * Enqueues the datagram for Internet delivery and returns immediately.
     * Responses are collected by [drainResponses].
     */
    fun forward(packet: ByteArray, timeoutMs: Int = SOCKET_TIMEOUT_MS): ByteArray? {
        val parsed = parseIpv4Udp(packet) ?: return null
        val key = FlowKey(
            source = parsed.source.hostAddress ?: parsed.source.toString(),
            sourcePort = parsed.sourcePort,
            destination = parsed.destination.hostAddress ?: parsed.destination.toString(),
            destinationPort = parsed.destinationPort,
        )

        val flow = flows.computeIfAbsent(key) {
            val socket = DatagramSocket().apply {
                reuseAddress = true
                soTimeout = SOCKET_TIMEOUT_MS
            }
            val created = Flow(key, socket)
            executor.execute { readFlow(created) }
            created
        }

        if (flow.closed.get()) return null
        flow.lastSeenAt = System.currentTimeMillis()
        if (parsed.destinationPort == 53) {
            synchronized(flow) {
                flow.lastQuery = parsed.payload.copyOf()
                flow.lastQueryAt = System.currentTimeMillis()
                flow.queryGeneration.incrementAndGet()
            }
        }

        return runCatching {
            flow.socket.send(
                DatagramPacket(
                    parsed.payload,
                    parsed.payload.size,
                    parsed.destination,
                    parsed.destinationPort,
                )
            )
            null
        }.getOrElse {
            closeFlow(key)
            null
        }
    }

    fun drainResponses(maxCount: Int = 32): List<ByteArray> = buildList {
        repeat(maxCount.coerceIn(1, 256)) {
            responses.poll()?.let(::add) ?: return@repeat
        }
    }

    override fun close() {
        flows.keys.toList().forEach(::closeFlow)
        cleaner.shutdownNow()
        executor.shutdownNow()
        responses.clear()
    }

    private fun readFlow(flow: Flow) {
        val buffer = ByteArray(MAX_UDP_PAYLOAD)
        try {
            while (!flow.closed.get()) {
                try {
                    val incoming = DatagramPacket(buffer, buffer.size)
                    flow.socket.receive(incoming)
                    if (incoming.length <= 0) continue
                    flow.lastSeenAt = System.currentTimeMillis()
                    synchronized(flow) {
                        flow.lastResponseAt = flow.lastSeenAt
                        if (flow.key.destinationPort == 53) {
                            flow.lastDnsFallbackGeneration = flow.queryGeneration.get()
                        }
                    }
                    val virtual = buildIpv4UdpResponse(
                        source = incoming.address,
                        sourcePort = incoming.port,
                        destination = InetAddress.getByName(flow.key.source),
                        destinationPort = flow.key.sourcePort,
                        payload = incoming.data.copyOf(incoming.length),
                    )
                    responses.add(virtual)
                } catch (_: java.net.SocketTimeoutException) {
                    maybeSynthesizeDns(flow)
                }
            }
        } catch (_: Exception) {
            // The flow is closed by the owning cleanup path.
        } finally {
            flow.closed.set(true)
        }
    }

    private fun maybeSynthesizeDns(flow: Flow) {
        if (flow.key.destinationPort != 53 || flow.closed.get()) return

        val snapshot = synchronized(flow) {
            val generation = flow.queryGeneration.get()
            val query = flow.lastQuery?.copyOf()
            val queryAt = flow.lastQueryAt
            val responseAt = flow.lastResponseAt
            val fallbackGeneration = flow.lastDnsFallbackGeneration
            if (query == null || queryAt <= 0L || generation == fallbackGeneration || queryAt <= responseAt) {
                null
            } else {
                Triple(generation, query, queryAt)
            }
        } ?: return

        if (System.currentTimeMillis() - snapshot.third < DNS_FALLBACK_DELAY_MS) return
        val synthesized = synthesizeLocalDnsResponse(snapshot.second) ?: return
        synchronized(flow) { flow.lastDnsFallbackGeneration = snapshot.first }
        responses.add(
            buildIpv4UdpResponse(
                source = InetAddress.getByName(flow.key.destination),
                sourcePort = 53,
                destination = InetAddress.getByName(flow.key.source),
                destinationPort = flow.key.sourcePort,
                payload = synthesized,
            )
        )
    }

    private fun closeFlow(key: FlowKey) {
        flows.remove(key)?.let { flow ->
            if (flow.closed.compareAndSet(false, true)) {
                runCatching { flow.socket.close() }
            }
        }
    }

    /** Local DNS fallback is used only after a real DNS socket has not responded. */
    private fun synthesizeLocalDnsResponse(dnsQuery: ByteArray): ByteArray? {
        if (dnsQuery.size < 12) return null
        val txId = ((dnsQuery[0].toInt() and 0xFF) shl 8) or (dnsQuery[1].toInt() and 0xFF)
        val qdCount = ((dnsQuery[4].toInt() and 0xFF) shl 8) or (dnsQuery[5].toInt() and 0xFF)
        if (qdCount < 1) return null

        val domainBuilder = StringBuilder()
        var offset = 12
        while (offset < dnsQuery.size) {
            val len = dnsQuery[offset++].toInt() and 0xFF
            if (len == 0) break
            if (len > 63 || offset + len > dnsQuery.size) return null
            if (domainBuilder.isNotEmpty()) domainBuilder.append('.')
            domainBuilder.append(String(dnsQuery, offset, len, Charsets.US_ASCII))
            offset += len
        }
        if (offset + 4 > dnsQuery.size) return null

        val qType = u16(dnsQuery, offset)
        offset += 4
        val question = dnsQuery.copyOfRange(12, offset)
        val domain = domainBuilder.toString().trim().trimEnd('.')
        if (domain.isBlank() || qType != 1) return null

        val resolved = runCatching {
            InetAddress.getAllByName(domain).filterIsInstance<java.net.Inet4Address>()
        }.getOrDefault(emptyList())
        if (resolved.isEmpty()) return null

        val out = java.io.ByteArrayOutputStream()
        out.write((txId ushr 8) and 0xFF); out.write(txId and 0xFF)
        out.write(0x81); out.write(0x80)
        out.write(0x00); out.write(0x01)
        out.write((resolved.size ushr 8) and 0xFF); out.write(resolved.size and 0xFF)
        out.write(0); out.write(0); out.write(0); out.write(0)
        out.write(question)
        resolved.forEach { ip ->
            out.write(0xC0); out.write(0x0C)
            out.write(0); out.write(1)
            out.write(0); out.write(1)
            out.write(0); out.write(0); out.write(1); out.write(0x2C)
            out.write(0); out.write(4)
            out.write(ip.address)
        }
        return out.toByteArray()
    }

    private data class UdpDatagram(
        val source: InetAddress,
        val sourcePort: Int,
        val destination: InetAddress,
        val destinationPort: Int,
        val payload: ByteArray,
    )

    private fun parseIpv4Udp(packet: ByteArray): UdpDatagram? {
        if (packet.size < 28) return null
        val version = (packet[0].toInt() ushr 4) and 0xF
        val ihl = (packet[0].toInt() and 0xF) * 4
        if (version != 4 || ihl < 20 || packet.size < ihl + 8) return null
        if ((packet[9].toInt() and 0xFF) != 17) return null
        val totalLength = u16(packet, 2)
        if (totalLength < ihl + 8 || totalLength > packet.size) return null
        val src = InetAddress.getByAddress(packet.copyOfRange(12, 16))
        val dst = InetAddress.getByAddress(packet.copyOfRange(16, 20))
        val sourcePort = u16(packet, ihl)
        val destinationPort = u16(packet, ihl + 2)
        val udpLength = u16(packet, ihl + 4)
        if (udpLength < 8 || ihl + udpLength > totalLength) return null
        return UdpDatagram(
            source = src,
            sourcePort = sourcePort,
            destination = dst,
            destinationPort = destinationPort,
            payload = packet.copyOfRange(ihl + 8, ihl + udpLength),
        )
    }

    private fun buildIpv4UdpResponse(
        source: InetAddress,
        sourcePort: Int,
        destination: InetAddress,
        destinationPort: Int,
        payload: ByteArray,
    ): ByteArray {
        require(source.address.size == 4 && destination.address.size == 4) { "IPv4 only" }
        val ipLength = 20
        val udpLength = 8 + payload.size
        val total = ipLength + udpLength
        val out = ByteArray(total)
        val buffer = ByteBuffer.wrap(out).order(ByteOrder.BIG_ENDIAN)
        buffer.put(0x45.toByte()); buffer.put(0)
        buffer.putShort(total.toShort()); buffer.putShort(0); buffer.putShort(0)
        buffer.put(64.toByte()); buffer.put(17.toByte()); buffer.putShort(0)
        buffer.put(source.address); buffer.put(destination.address)
        buffer.putShort(sourcePort.toShort()); buffer.putShort(destinationPort.toShort())
        buffer.putShort(udpLength.toShort()); buffer.putShort(0); buffer.put(payload)

        val ipChecksum = checksum(out, 0, ipLength)
        out[10] = (ipChecksum ushr 8).toByte(); out[11] = ipChecksum.toByte()

        val pseudo = ByteArray(12 + udpLength)
        System.arraycopy(source.address, 0, pseudo, 0, 4)
        System.arraycopy(destination.address, 0, pseudo, 4, 4)
        pseudo[9] = 17
        writeU16(pseudo, 10, udpLength)
        System.arraycopy(out, ipLength, pseudo, 12, udpLength)
        val udpChecksum = checksum(pseudo, 0, pseudo.size).let { if (it == 0) 0xFFFF else it }
        out[ipLength + 6] = (udpChecksum ushr 8).toByte(); out[ipLength + 7] = udpChecksum.toByte()
        return out
    }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 8).toByte()
        bytes[offset + 1] = value.toByte()
    }

    private fun checksum(bytes: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            sum += u16(bytes, i).toLong()
            i += 2
        }
        if (i < end) sum += ((bytes[i].toInt() and 0xFF) shl 8).toLong()
        while ((sum ushr 16) != 0L) sum = (sum and 0xFFFF) + (sum ushr 16)
        return sum.inv().toInt() and 0xFFFF
    }

    companion object {
        private const val SOCKET_TIMEOUT_MS = 500
        private const val DNS_FALLBACK_DELAY_MS = 1_500L
        private const val FLOW_IDLE_TIMEOUT_MS = 90_000L
        private const val FLOW_CLEAN_INTERVAL_MS = 30_000L
        private const val MAX_UDP_PAYLOAD = 65_507
    }
}
