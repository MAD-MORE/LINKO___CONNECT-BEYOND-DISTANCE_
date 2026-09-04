package com.linkshare.app.tunnel

import java.io.Closeable
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Provider-side IPv4 TCP proxy with asynchronous connection establishment and per-flow state.
 * Packet processing never blocks on a remote TCP connect or response read.
 */
class ProviderTcpPacketForwarder : Closeable {
    private enum class State { CONNECTING, ESTABLISHED, CLOSED }

    private data class FlowKey(val src: String, val srcPort: Int, val dst: String, val dstPort: Int)

    private data class Flow(
        val key: FlowKey,
        val socket: Socket,
        val serverSeq: AtomicLong,
        val clientSeq: AtomicLong,
        val state: AtomicReference<State> = AtomicReference(State.CONNECTING),
        val closed: AtomicBoolean = AtomicBoolean(false),
        @Volatile var lastActiveAt: Long = System.currentTimeMillis(),
    )

    private val random = SecureRandom()
    private val flows = ConcurrentHashMap<FlowKey, Flow>()
    private val executor = Executors.newCachedThreadPool()
    private val cleaner: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val responseQueue = ConcurrentLinkedQueue<ByteArray>()

    init {
        cleaner.scheduleWithFixedDelay({
            val now = System.currentTimeMillis()
            flows.forEach { (key, flow) ->
                if (flow.closed.get() || now - flow.lastActiveAt > IDLE_FLOW_TIMEOUT_MS || flow.socket.isClosed) {
                    closeFlow(key)
                }
            }
        }, CLEAN_INTERVAL_MS, CLEAN_INTERVAL_MS, TimeUnit.MILLISECONDS)
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
            val existing = flows[key]
            if (existing != null && !existing.closed.get()) return emptyList()
            if (flows.size >= MAX_FLOWS) return listOf(rstFor(tcp, 0L))

            val socket = Socket().apply {
                keepAlive = true
                tcpNoDelay = true
                // The response reader is deliberately blocking in its worker thread; the packet path is not.
                soTimeout = 0
            }
            val flow = Flow(
                key = key,
                socket = socket,
                serverSeq = AtomicLong(random.nextInt().toLong() and 0xffffffffL),
                clientSeq = AtomicLong((tcp.seq + 1L) and 0xffffffffL),
            )
            if (flows.putIfAbsent(key, flow) != null) {
                runCatching { socket.close() }
                return emptyList()
            }

            executor.execute {
                establishRemote(flow, tcp, timeoutMs.coerceIn(MIN_CONNECT_TIMEOUT_MS, MAX_CONNECT_TIMEOUT_MS))
            }
            return emptyList()
        }

        val flow = flows[key] ?: return emptyList()
        flow.lastActiveAt = System.currentTimeMillis()

        when (flow.state.get()) {
            State.CONNECTING -> return emptyList()
            State.CLOSED -> return emptyList()
            State.ESTABLISHED -> Unit
        }

        val ack = (tcp.seq + tcp.payload.size + if (tcp.fin) 1 else 0) and 0xffffffffL
        if (tcp.fin) {
            val response = TcpCodec.segment(
                srcHost = tcp.dstHost,
                srcPort = tcp.dstPort,
                dstHost = tcp.srcHost,
                dstPort = tcp.srcPort,
                seq = flow.serverSeq.get(),
                ackNumber = ack,
                fin = true,
                ackFlag = true,
            )
            closeFlow(key)
            return listOf(response)
        }

        if (tcp.payload.isEmpty()) return emptyList()

        return runCatching {
            synchronized(flow) {
                flow.socket.getOutputStream().write(tcp.payload)
                flow.socket.getOutputStream().flush()
                flow.clientSeq.set(ack)
            }
            listOf(
                TcpCodec.segment(
                    srcHost = tcp.dstHost,
                    srcPort = tcp.dstPort,
                    dstHost = tcp.srcHost,
                    dstPort = tcp.srcPort,
                    seq = flow.serverSeq.get(),
                    ackNumber = ack,
                    ackFlag = true,
                )
            )
        }.getOrElse {
            val response = rstFor(tcp, flow.serverSeq.get())
            closeFlow(key)
            listOf(response)
        }
    }

    private fun establishRemote(flow: Flow, clientSyn: TcpSegment, timeoutMs: Int) {
        try {
            flow.socket.connect(InetSocketAddress(clientSyn.dstHost, clientSyn.dstPort), timeoutMs)
            if (flow.closed.get()) return
            flow.state.set(State.ESTABLISHED)
            flow.lastActiveAt = System.currentTimeMillis()
            responseQueue.add(
                TcpCodec.segment(
                    srcHost = flow.key.dst,
                    srcPort = flow.key.dstPort,
                    dstHost = flow.key.src,
                    dstPort = flow.key.srcPort,
                    seq = flow.serverSeq.get(),
                    ackNumber = flow.clientSeq.get(),
                    syn = true,
                    ackFlag = true,
                )
            )
            executor.execute { readResponses(flow) }
        } catch (_: Exception) {
            if (flow.closed.compareAndSet(false, true)) {
                flow.state.set(State.CLOSED)
                responseQueue.add(rstFor(clientSyn, flow.serverSeq.get()))
                runCatching { flow.socket.close() }
                flows.remove(flow.key, flow)
            }
        }
    }

    private fun readResponses(flow: Flow) {
        val buffer = ByteArray(MAX_PAYLOAD)
        try {
            while (!flow.closed.get() && flow.state.get() == State.ESTABLISHED) {
                val count = flow.socket.getInputStream().read(buffer)
                if (count < 0) {
                    responseQueue.add(
                        TcpCodec.segment(
                            srcHost = flow.key.dst,
                            srcPort = flow.key.dstPort,
                            dstHost = flow.key.src,
                            dstPort = flow.key.srcPort,
                            seq = flow.serverSeq.get(),
                            ackNumber = flow.clientSeq.get(),
                            fin = true,
                            ackFlag = true,
                        )
                    )
                    break
                }
                if (count == 0) continue
                flow.lastActiveAt = System.currentTimeMillis()
                val seq = flow.serverSeq.getAndAdd(count.toLong())
                responseQueue.add(
                    TcpCodec.segment(
                        srcHost = flow.key.dst,
                        srcPort = flow.key.dstPort,
                        dstHost = flow.key.src,
                        dstPort = flow.key.srcPort,
                        seq = seq,
                        ackNumber = flow.clientSeq.get(),
                        payload = buffer.copyOf(count),
                        push = true,
                        ackFlag = true,
                    )
                )
            }
        } catch (_: Exception) {
            if (!flow.closed.get()) {
                responseQueue.add(
                    TcpCodec.segment(
                        srcHost = flow.key.dst,
                        srcPort = flow.key.dstPort,
                        dstHost = flow.key.src,
                        dstPort = flow.key.srcPort,
                        seq = flow.serverSeq.get(),
                        ackNumber = flow.clientSeq.get(),
                        fin = true,
                        ackFlag = true,
                    )
                )
            }
        } finally {
            closeFlow(flow.key)
        }
    }

    fun drainResponses(maxPackets: Int = 32): List<ByteArray> = buildList {
        repeat(maxPackets.coerceIn(1, 64)) {
            responseQueue.poll()?.let(::add) ?: return@repeat
        }
    }

    private fun rstFor(tcp: TcpSegment, seq: Long): ByteArray = TcpCodec.segment(
        srcHost = tcp.dstHost,
        srcPort = tcp.dstPort,
        dstHost = tcp.srcHost,
        dstPort = tcp.srcPort,
        seq = seq,
        ackNumber = (tcp.seq + tcp.payload.size + if (tcp.syn || tcp.fin) 1 else 0) and 0xffffffffL,
        rst = true,
        ackFlag = true,
    )

    private fun closeFlow(key: FlowKey) {
        flows.remove(key)?.let { flow ->
            flow.state.set(State.CLOSED)
            flow.closed.set(true)
            runCatching { flow.socket.close() }
        }
    }

    override fun close() {
        cleaner.shutdownNow()
        flows.keys.toList().forEach(::closeFlow)
        responseQueue.clear()
        executor.shutdownNow()
    }

    companion object {
        private const val MAX_PAYLOAD = 16 * 1024
        private const val MAX_FLOWS = 64
        private const val IDLE_FLOW_TIMEOUT_MS = 60_000L
        private const val CLEAN_INTERVAL_MS = 30_000L
        private const val MIN_CONNECT_TIMEOUT_MS = 500
        private const val MAX_CONNECT_TIMEOUT_MS = 5_000
    }
}

private data class TcpSegment(
    val srcHost: String, val srcPort: Int, val dstHost: String, val dstPort: Int,
    val seq: Long, val ackNumber: Long, val syn: Boolean, val ack: Boolean,
    val fin: Boolean, val rst: Boolean, val payload: ByteArray,
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
            java.net.InetAddress.getByAddress(packet.copyOfRange(12, 16)).hostAddress,
            u16(packet, offset),
            java.net.InetAddress.getByAddress(packet.copyOfRange(16, 20)).hostAddress,
            u16(packet, offset + 2),
            u32(packet, offset + 4),
            u32(packet, offset + 8),
            flags and 2 != 0,
            flags and 16 != 0,
            flags and 1 != 0,
            flags and 4 != 0,
            packet.copyOfRange(offset + dataOffset, total),
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
        payload: ByteArray = ByteArray(0),
    ): ByteArray {
        val tcpHeaderLen = if (syn) 24 else 20
        val total = 20 + tcpHeaderLen + payload.size
        val out = ByteArray(total)
        out[0] = 0x45
        out[8] = 64
        out[9] = 6
        write16(out, 2, total)
        System.arraycopy(java.net.InetAddress.getByName(srcHost).address, 0, out, 12, 4)
        System.arraycopy(java.net.InetAddress.getByName(dstHost).address, 0, out, 16, 4)
        write16(out, 20, srcPort)
        write16(out, 22, dstPort)
        write32(out, 24, seq)
        write32(out, 28, ackNumber)
        val dataOffsetWords = tcpHeaderLen / 4
        val flags = (dataOffsetWords shl 12) or
            (if (fin) 1 else 0) or
            (if (syn) 2 else 0) or
            (if (rst) 4 else 0) or
            (if (push) 8 else 0) or
            (if (ackFlag) 16 else 0)
        write16(out, 32, flags)
        write16(out, 34, 65535)
        if (syn) {
            out[40] = 2
            out[41] = 4
            out[42] = 0x05
            out[43] = 0x28
            System.arraycopy(payload, 0, out, 44, payload.size)
        } else {
            System.arraycopy(payload, 0, out, 40, payload.size)
        }
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