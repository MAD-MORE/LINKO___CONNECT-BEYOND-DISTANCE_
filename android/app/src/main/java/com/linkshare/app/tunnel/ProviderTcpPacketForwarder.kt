package com.linkshare.app.tunnel

import java.io.Closeable
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Provider-side IPv4 TCP proxy with bounded per-flow state. */
class ProviderTcpPacketForwarder : Closeable {
    private data class FlowKey(val src: String, val srcPort: Int, val dst: String, val dstPort: Int)
    private data class Flow(
        val key: FlowKey,
        val socket: Socket,
        var serverSeq: Long,
        var clientSeq: Long,
        var lastActiveAt: Long = System.currentTimeMillis()
    )

    private val random = SecureRandom()
    private val flows = ConcurrentHashMap<FlowKey, Flow>()
    private val executor = Executors.newCachedThreadPool()
    private val cleaner = Executors.newSingleThreadScheduledExecutor()
    private val responseQueue = ArrayDeque<ByteArray>()

    init {
        // Periodically sweep and close idle or broken flows (every 30 seconds)
        cleaner.scheduleWithFixedDelay({
            val now = System.currentTimeMillis()
            flows.forEach { (key, flow) ->
                if (now - flow.lastActiveAt > IDLE_FLOW_TIMEOUT_MS || flow.socket.isClosed) {
                    closeFlow(key)
                }
            }
        }, 30, 30, java.util.concurrent.TimeUnit.SECONDS)
    }

    fun forward(packet: ByteArray, timeoutMs: Int = 5_000): List<ByteArray> {
        val tcp = TcpCodec.parse(packet) ?: return emptyList()
        if (tcp.payload.size > MAX_PAYLOAD) return emptyList()
        val key = FlowKey(tcp.srcHost, tcp.srcPort, tcp.dstHost, tcp.dstPort)

        if (tcp.rst) {
            closeFlow(key)
            return emptyList()
        }

        if (tcp.syn && !tcp.ack) {
            if (flows.containsKey(key)) return emptyList()
            return try {
                val socket = Socket()
                socket.soTimeout = 15_000
                socket.connect(InetSocketAddress(tcp.dstHost, tcp.dstPort), timeoutMs)
                val flow = Flow(key, socket, random.nextInt().toLong() and 0xffffffffL, (tcp.seq + 1) and 0xffffffffL)
                flows[key] = flow
                executor.execute { readResponses(flow) }
                listOf(TcpCodec.segment(tcp.dstHost, tcp.dstPort, tcp.srcHost, tcp.srcPort, flow.serverSeq, flow.clientSeq, syn = true, ackFlag = true))
            } catch (_: Exception) {
                listOf(TcpCodec.segment(tcp.dstHost, tcp.dstPort, tcp.srcHost, tcp.srcPort, 0, (tcp.seq + 1) and 0xffffffffL, rst = true, ackFlag = true))
            }
        }

        val flow = flows[key] ?: return emptyList()
        flow.lastActiveAt = System.currentTimeMillis()

        if (tcp.fin) {
            closeFlow(key)
            return listOf(TcpCodec.segment(tcp.dstHost, tcp.dstPort, tcp.srcHost, tcp.srcPort, flow.serverSeq, (tcp.seq + 1) and 0xffffffffL, fin = true, ackFlag = true))
        }
        if (tcp.payload.isNotEmpty()) {
            return try {
                flow.socket.getOutputStream().write(tcp.payload)
                flow.socket.getOutputStream().flush()
                flow.clientSeq = (tcp.seq + tcp.payload.size) and 0xffffffffL
                listOf(TcpCodec.segment(tcp.dstHost, tcp.dstPort, tcp.srcHost, tcp.srcPort, flow.serverSeq, flow.clientSeq, ackFlag = true))
            } catch (_: Exception) {
                closeFlow(key)
                listOf(TcpCodec.segment(tcp.dstHost, tcp.dstPort, tcp.srcHost, tcp.srcPort, flow.serverSeq, (tcp.seq + tcp.payload.size) and 0xffffffffL, rst = true, ackFlag = true))
            }
        }
        return emptyList()
    }

    private fun readResponses(flow: Flow) {
        val buffer = ByteArray(MAX_PAYLOAD)
        try {
            while (!flow.socket.isClosed) {
                val count = flow.socket.getInputStream().read(buffer)
                if (count < 0) break
                if (count == 0) continue
                flow.lastActiveAt = System.currentTimeMillis()
                synchronized(responseQueue) {
                    responseQueue.add(TcpCodec.segment(flow.key.dst, flow.key.dstPort, flow.key.src, flow.key.srcPort, flow.serverSeq, flow.clientSeq, payload = buffer.copyOf(count), push = true, ackFlag = true))
                }
                flow.serverSeq = (flow.serverSeq + count) and 0xffffffffL
            }
        } catch (_: Exception) {
            // Closed flows disappear cleanly
        } finally {
            closeFlow(flow.key)
        }
    }

    fun drainResponses(maxPackets: Int = 32): List<ByteArray> = synchronized(responseQueue) {
        buildList { repeat(minOf(maxPackets, responseQueue.size)) { add(responseQueue.removeFirst()) } }
    }

    private fun closeFlow(key: FlowKey) {
        flows.remove(key)?.let { runCatching { it.socket.close() } }
    }

    override fun close() {
        cleaner.shutdownNow()
        flows.keys.toList().forEach(::closeFlow)
        executor.shutdownNow()
    }

    companion object {
        private const val MAX_PAYLOAD = 16 * 1024
        private const val IDLE_FLOW_TIMEOUT_MS = 60_000L
    }
}

private data class TcpSegment(
    val srcHost: String, val srcPort: Int, val dstHost: String, val dstPort: Int,
    val seq: Long, val ackNumber: Long, val syn: Boolean, val ack: Boolean,
    val fin: Boolean, val rst: Boolean, val payload: ByteArray
)

private object TcpCodec {
    fun parse(packet: ByteArray): TcpSegment? {
        if (packet.size < 40 || (packet[0].toInt() ushr 4) != 4 || (packet[9].toInt() and 0xff) != 6) return null
        val ihl = (packet[0].toInt() and 0x0f) * 4
        if (ihl < 20 || packet.size < ihl + 20) return null
        val total = u16(packet, 2)
        if (total < ihl + 20 || total > packet.size) return null
        val offset = ihl
        val dataOffset = ((packet[offset + 12].toInt() ushr 4) and 0x0f) * 4
        if (dataOffset < 20 || offset + dataOffset > total) return null
        val flags = u16(packet, offset + 12) and 0x01ff
        return TcpSegment(
            java.net.InetAddress.getByAddress(packet.copyOfRange(12, 16)).hostAddress, u16(packet, offset),
            java.net.InetAddress.getByAddress(packet.copyOfRange(16, 20)).hostAddress, u16(packet, offset + 2),
            u32(packet, offset + 4), u32(packet, offset + 8),
            flags and 2 != 0, flags and 16 != 0, flags and 1 != 0, flags and 4 != 0,
            packet.copyOfRange(offset + dataOffset, total)
        )
    }

    fun segment(
        srcHost: String,
        srcPort: Int,
        dstHost: String,
        dstPort: Int,
        seq: Long,
        ackNumber: Long,
        syn: Boolean = false,
        ackFlag: Boolean = false,
        fin: Boolean = false,
        rst: Boolean = false,
        push: Boolean = false,
        payload: ByteArray = ByteArray(0)
    ): ByteArray {
        // When SYN is sent, include MSS Option (Kind=2, Len=4, MSS=1320) to prevent carrier MTU fragmentation
        val tcpHeaderLen = if (syn) 24 else 20
        val total = 20 + tcpHeaderLen + payload.size
        val out = ByteArray(total)

        // IPv4 Header (20 bytes)
        out[0] = 0x45
        write16(out, 2, total); out[8] = 64; out[9] = 6
        System.arraycopy(java.net.InetAddress.getByName(srcHost).address, 0, out, 12, 4)
        System.arraycopy(java.net.InetAddress.getByName(dstHost).address, 0, out, 16, 4)

        // TCP Header
        write16(out, 20, srcPort); write16(out, 22, dstPort)
        write32(out, 24, seq); write32(out, 28, ackNumber)

        val dataOffsetWords = tcpHeaderLen / 4
        val flags = (dataOffsetWords shl 12) or
            (if (fin) 1 else 0) or
            (if (syn) 2 else 0) or
            (if (rst) 4 else 0) or
            (if (push) 8 else 0) or
            (if (ackFlag) 16 else 0)
        write16(out, 32, flags)
        write16(out, 34, 65535) // Window size (64KB)

        if (syn) {
            // TCP MSS Option (Kind 2, Length 4, MSS 1320 bytes = 0x0528)
            out[40] = 2.toByte()
            out[41] = 4.toByte()
            out[42] = 0x05.toByte()
            out[43] = 0x28.toByte()
            System.arraycopy(payload, 0, out, 44, payload.size)
        } else {
            System.arraycopy(payload, 0, out, 40, payload.size)
        }

        // Checksums
        write16(out, 10, checksum(out, 0, 20))
        val pseudo = ByteArray(12 + tcpHeaderLen + payload.size)
        System.arraycopy(out, 12, pseudo, 0, 8)
        pseudo[9] = 6
        write16(pseudo, 10, tcpHeaderLen + payload.size)
        System.arraycopy(out, 20, pseudo, 12, tcpHeaderLen + payload.size)
        write16(out, 36, checksum(pseudo, 0, pseudo.size))

        return out
    }

    private fun u16(b: ByteArray, o: Int) = ((b[o].toInt() and 255) shl 8) or (b[o + 1].toInt() and 255)
    private fun u32(b: ByteArray, o: Int) = ((b[o].toLong() and 255) shl 24) or ((b[o + 1].toLong() and 255) shl 16) or ((b[o + 2].toLong() and 255) shl 8) or (b[o + 3].toLong() and 255)
    private fun write16(b: ByteArray, o: Int, v: Int) { b[o] = (v ushr 8).toByte(); b[o + 1] = v.toByte() }
    private fun write32(b: ByteArray, o: Int, v: Long) { write16(b, o, (v ushr 16).toInt()); write16(b, o + 2, v.toInt()) }
    private fun checksum(b: ByteArray, o: Int, n: Int): Int { var s = 0L; var i = o; val e = o + n; while (i + 1 < e) { s += u16(b, i); i += 2 }; if (i < e) s += (b[i].toInt() and 255) shl 8; while ((s ushr 16) != 0L) s = (s and 65535) + (s ushr 16); return s.inv().toInt() and 65535 }
}
