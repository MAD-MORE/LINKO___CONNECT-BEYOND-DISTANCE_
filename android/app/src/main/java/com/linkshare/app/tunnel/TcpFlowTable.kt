package com.linkshare.app.tunnel

import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks TCP 4-tuples observed on the TUN interface.
 * This is deliberately a flow-state layer, not a fake TCP implementation.
 * A real TCP stack/translator consumes these states and owns sequence numbers,
 * retransmission, FIN/RST and congestion control.
 */
class TcpFlowTable {
    data class Key(
        val sourceAddress: String,
        val sourcePort: Int,
        val destinationAddress: String,
        val destinationPort: Int
    )

    enum class State { SYN_SENT, ESTABLISHED, FIN_WAIT, CLOSED, RESET }

    data class Flow(
        val key: Key,
        val remote: InetSocketAddress,
        @Volatile var state: State = State.SYN_SENT,
        @Volatile var lastActivityMs: Long = System.currentTimeMillis()
    )

    private val flows = ConcurrentHashMap<Key, Flow>()

    fun getOrCreate(key: Key, remote: InetSocketAddress): Flow =
        flows.compute(key) { _, current ->
            (current ?: Flow(key, remote)).also { it.lastActivityMs = System.currentTimeMillis() }
        }!!

    fun update(key: Key, state: State) {
        flows[key]?.apply {
            this.state = state
            lastActivityMs = System.currentTimeMillis()
        }
    }

    fun remove(key: Key) { flows.remove(key) }

    fun expire(nowMs: Long = System.currentTimeMillis(), idleMs: Long = 120_000): Int {
        var removed = 0
        flows.entries.removeIf { (_, flow) ->
            if (nowMs - flow.lastActivityMs >= idleMs) { removed++; true } else false
        }
        return removed
    }

    fun clear() = flows.clear()
    fun size(): Int = flows.size
}
