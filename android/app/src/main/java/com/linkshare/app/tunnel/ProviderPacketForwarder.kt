package com.linkshare.app.tunnel

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Provider-side forwarding boundary.
 *
 * This implementation forwards UDP payloads only. TCP is intentionally rejected
 * until a real userspace TCP/IP stack is integrated; treating TCP bytes as UDP
 * would produce a non-functional and unsafe data plane.
 */
class ProviderPacketForwarder(
    private val socket: DatagramSocket,
    private val dnsServer: InetAddress = InetAddress.getByName("1.1.1.1")
) {
    private val flows = ConcurrentHashMap<String, Flow>()

    data class Flow(val destination: InetAddress, val port: Int)

    fun forward(packet: IpPacketRouter.Packet): ByteArray? {
        if (packet.version != 4 || packet.protocol != IpPacketRouter.Protocol.UDP) return null
        val ip = packet.payload
        val ihl = (ip[0].toInt() and 0x0f) * 4
        if (ip.size < ihl + 8) return null

        val destination = InetAddress.getByAddress(ip.copyOfRange(16, 20))
        val destinationPort = ((ip[ihl + 2].toInt() and 0xff) shl 8) or (ip[ihl + 3].toInt() and 0xff)
        val sourcePort = ((ip[ihl].toInt() and 0xff) shl 8) or (ip[ihl + 1].toInt() and 0xff)
        val payload = ip.copyOfRange(ihl + 8, ip.size)

        // DNS is explicitly allowed; other UDP destinations use normal provider routing.
        if (destination.hostAddress != dnsServer.hostAddress && destinationPort == 53) return null
        val key = "${destination.hostAddress}:$destinationPort:$sourcePort"
        flows[key] = Flow(destination, destinationPort)

        val request = DatagramPacket(payload, payload.size, destination, destinationPort)
        socket.send(request)
        return null
    }

    fun close() {
        flows.clear()
    }
}
