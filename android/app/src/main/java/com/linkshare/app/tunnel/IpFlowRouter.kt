package com.linkshare.app.tunnel

/**
 * Routes captured IP packets to the appropriate data-plane handler.
 * The handlers are intentionally injected so transport correctness cannot be
 * confused with merely parsing a packet.
 */
class IpFlowRouter(
    private val onTcp: (ByteArray, IpPacketClassifier.PacketInfo) -> Unit,
    private val onUdp: (ByteArray, IpPacketClassifier.PacketInfo) -> Unit,
    private val onControl: (ByteArray, IpPacketClassifier.PacketInfo) -> Unit
) {
    fun route(packet: ByteArray, length: Int = packet.size) {
        val info = IpPacketClassifier.inspect(packet, length)
        when (info.protocol) {
            IpPacketClassifier.Protocol.TCP -> onTcp(packet.copyOf(length), info)
            IpPacketClassifier.Protocol.UDP -> onUdp(packet.copyOf(length), info)
            IpPacketClassifier.Protocol.ICMP -> onControl(packet.copyOf(length), info)
            IpPacketClassifier.Protocol.OTHER -> onControl(packet.copyOf(length), info)
        }
    }
}
