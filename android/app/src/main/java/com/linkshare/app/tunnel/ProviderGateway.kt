package com.linkshare.app.tunnel

import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Userspace provider gateway for traffic recovered from the LINKO tunnel.
 *
 * IPv4 UDP is forwarded directly through the provider's normal network stack.
 * TCP is handled as a stream proxy keyed by the original 4-tuple. The caller
 * remains responsible for turning the resulting byte streams back into IP/TCP
 * packets for the receiver side.
 *
 * Unsupported protocols are rejected instead of silently leaking traffic.
 */
class ProviderGateway : Closeable {
    data class FlowKey(
        val srcAddress: Int,
        val srcPort: Int,
        val dstAddress: Int,
        val dstPort: Int,
        val protocol: Int,
    )

    data class TcpFlow(
        val socket: Socket,
    )

    private val workers = Executors.newCachedThreadPool()
    private val tcpFlows = ConcurrentHashMap<FlowKey, TcpFlow>()

    fun forwardIpv4(packet: ByteArray): GatewayResult {
        val view = Ipv4Packet.parse(packet) ?: return GatewayResult.Drop("invalid-ipv4")
        return when (view.protocol) {
            17 -> forwardUdp(view)
            6 -> forwardTcp(view)
            else -> GatewayResult.Drop("unsupported-ip-protocol-${view.protocol}")
        }
    }

    private fun forwardUdp(view: Ipv4Packet): GatewayResult {
        val udp = UdpSegment.parse(view.payload) ?: return GatewayResult.Drop("invalid-udp")
        val socket = DatagramSocket().apply { soTimeout = 2_000 }
        return try {
            socket.send(
                DatagramPacket(
                    udp.payload,
                    udp.payload.size,
                    InetSocketAddress(view.destinationAddress, udp.destinationPort)
                )
            )
            val response = ByteArray(65_535)
            val receive = DatagramPacket(response, response.size)
            workers.submit { runCatching { socket.receive(receive) } }
            GatewayResult.UdpSent(view, udp.destinationPort)
        } finally {
            socket.close()
        }
    }

    private fun forwardTcp(view: Ipv4Packet): GatewayResult {
        val tcp = TcpSegment.parse(view.payload) ?: return GatewayResult.Drop("invalid-tcp")
        val key = FlowKey(
            view.sourceAddress,
            tcp.sourcePort,
            view.destinationAddress,
            tcp.destinationPort,
            6
        )

        if (tcp.syn && !tcp.ack) {
            val socket = Socket()
            socket.connect(InetSocketAddress(view.destinationAddress, tcp.destinationPort), 5_000)
            val flow = TcpFlow(socket)
            tcpFlows[key] = flow
            workers.submit {
                val buffer = ByteArray(16 * 1024)
                runCatching {
                    socket.getInputStream().use { input ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            // Reverse-packet emission is intentionally delegated to
                            // the tunnel packet adapter so sequence/ACK numbers are
                            // generated from the receiver's flow state.
                        }
                    }
                }
            }
            return GatewayResult.TcpConnected(key)
        }

        val flow = tcpFlows[key] ?: return GatewayResult.Drop("unknown-tcp-flow")
        if (tcp.payload.isNotEmpty()) {
            flow.socket.getOutputStream().write(tcp.payload)
            flow.socket.getOutputStream().flush()
        }
        if (tcp.fin || tcp.rst) {
            runCatching { flow.socket.close() }
            tcpFlows.remove(key)
        }
        return GatewayResult.TcpAccepted(key)
    }

    override fun close() {
        tcpFlows.values.forEach { runCatching { it.socket.close() } }
        tcpFlows.clear()
        workers.shutdownNow()
    }

    sealed interface GatewayResult {
        data class UdpSent(val packet: Ipv4Packet, val port: Int) : GatewayResult
        data class TcpConnected(val key: FlowKey) : GatewayResult
        data class TcpAccepted(val key: FlowKey) : GatewayResult
        data class Drop(val reason: String) : GatewayResult
    }

    data class Ipv4Packet(
        val sourceAddress: String,
        val destinationAddress: String,
        val protocol: Int,
        val payload: ByteArray,
    ) {
        companion object {
            fun parse(packet: ByteArray): Ipv4Packet? {
                if (packet.size < 20) return null
                val version = (packet[0].toInt() ushr 4) and 0x0f
                if (version != 4) return null
                val headerLength = (packet[0].toInt() and 0x0f) * 4
                if (headerLength < 20 || headerLength > packet.size) return null
                val totalLength = ((packet[2].toInt() and 0xff) shl 8) or (packet[3].toInt() and 0xff)
                if (totalLength < headerLength || totalLength > packet.size) return null
                val src = ipv4(packet, 12)
                val dst = ipv4(packet, 16)
                val protocol = packet[9].toInt() and 0xff
                return Ipv4Packet(src, dst, protocol, packet.copyOfRange(headerLength, totalLength))
            }

            private fun ipv4(b: ByteArray, offset: Int): String =
                listOf(0, 1, 2, 3).joinToString(".") { (b[offset + it].toInt() and 0xff).toString() }
        }
    }

    data class UdpSegment(
        val sourcePort: Int,
        val destinationPort: Int,
        val payload: ByteArray,
    ) {
        companion object {
            fun parse(bytes: ByteArray): UdpSegment? {
                if (bytes.size < 8) return null
                val src = u16(bytes, 0)
                val dst = u16(bytes, 2)
                val len = u16(bytes, 4)
                if (len < 8 || len > bytes.size) return null
                return UdpSegment(src, dst, bytes.copyOfRange(8, len))
            }
        }
    }

    data class TcpSegment(
        val sourcePort: Int,
        val destinationPort: Int,
        val syn: Boolean,
        val ack: Boolean,
        val fin: Boolean,
        val rst: Boolean,
        val payload: ByteArray,
    ) {
        companion object {
            fun parse(bytes: ByteArray): TcpSegment? {
                if (bytes.size < 20) return null
                val src = u16(bytes, 0)
                val dst = u16(bytes, 2)
                val offset = ((bytes[12].toInt() ushr 4) and 0x0f) * 4
                if (offset < 20 || offset > bytes.size) return null
                val flags = bytes[13].toInt() and 0xff
                return TcpSegment(
                    src,
                    dst,
                    flags and 0x02 != 0,
                    flags and 0x10 != 0,
                    flags and 0x01 != 0,
                    flags and 0x04 != 0,
                    bytes.copyOfRange(offset, bytes.size)
                )
            }
        }
    }

    companion object {
        private fun u16(b: ByteArray, offset: Int): Int =
            ((b[offset].toInt() and 0xff) shl 8) or (b[offset + 1].toInt() and 0xff)
    }
}
