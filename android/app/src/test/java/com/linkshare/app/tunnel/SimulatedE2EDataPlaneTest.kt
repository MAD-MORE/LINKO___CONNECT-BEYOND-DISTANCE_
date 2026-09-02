package com.linkshare.app.tunnel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.util.UUID

/**
 * End-to-End simulated Provider + Client Data Plane integration test.
 * Verifies live packet encryption, transmission, provider forwarding, and response delivery
 * without requiring two physical Android devices.
 */
class SimulatedE2EDataPlaneTest {

    private val random = SecureRandom()

    @Test
    fun testProviderAndClientSimulatedDataPlaneLoop() {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val sessionKey = ByteArray(32).also(random::nextBytes)
        val sessionId = UUID.randomUUID().toString()

        val providerSocket = DatagramSocket()
        val clientSocket = DatagramSocket()

        val providerPort = providerSocket.localPort
        val clientPort = clientSocket.localPort

        val providerEndpoint = InetSocketAddress("127.0.0.1", providerPort)
        val clientEndpoint = InetSocketAddress("127.0.0.1", clientPort)

        // 1. Initialize Provider Tunnel Runner
        val providerRunner = ProviderTunnelRunner(
            socket = providerSocket,
            endpoint = clientEndpoint,
            sessionId = sessionId,
            sessionKey = sessionKey,
            scope = testScope,
            adapter = FullIpProviderTransportAdapter()
        )
        providerRunner.start()
        Thread.sleep(100)

        // 2. Initialize Client Tunnel
        val clientTunnel = EncryptedDatagramTunnel(
            socket = clientSocket,
            peer = providerEndpoint,
            sessionId = sessionId,
            role = EncryptedDatagramTunnel.Role.RECEIVER,
            sessionKey = sessionKey
        )

        // 3. Client transmits synthesized IPv4 ICMP Echo Request to 8.8.8.8
        val icmpPingPacket = buildIpv4IcmpEchoRequest(
            srcIp = byteArrayOf(10, 48, 0, 2),
            dstIp = byteArrayOf(8, 8, 8, 8),
            identifier = 0x1234,
            seq = 1,
            payload = "LINKO Ping Payload Data".toByteArray(Charsets.UTF_8)
        )

        clientTunnel.send(icmpPingPacket, EncryptedDatagramTunnel.PacketType.DATA)

        // 4. Client waits for response from Provider
        var receivedEchoReply: ByteArray? = null
        for (i in 0 until 30) {
            val rx = clientTunnel.receive(300)
            if (rx != null && rx.type == EncryptedDatagramTunnel.PacketType.DATA) {
                receivedEchoReply = rx.payload
                break
            }
        }

        assertNotNull("Client should receive ICMP Echo Reply from Provider", receivedEchoReply)
        val reply = receivedEchoReply!!
        assertEquals("IP version must be 4", 4, (reply[0].toInt() ushr 4) and 0x0f)
        assertEquals("Protocol must be ICMP (1)", 1, reply[9].toInt() and 0xff)

        // Verify Source IP is 8.8.8.8 and Destination IP is 10.48.0.2
        assertEquals(8.toByte(), reply[12])
        assertEquals(8.toByte(), reply[13])
        assertEquals(8.toByte(), reply[14])
        assertEquals(8.toByte(), reply[15])

        assertEquals(10.toByte(), reply[16])
        assertEquals(48.toByte(), reply[17])
        assertEquals(0.toByte(), reply[18])
        assertEquals(2.toByte(), reply[19])

        // Verify ICMP Type is 0 (Echo Reply)
        val ihl = (reply[0].toInt() and 0x0f) * 4
        assertEquals("ICMP type must be 0 (Echo Reply)", 0, reply[ihl].toInt())

        // 5. Test Keepalive Ping -> Pong handling
        val pingTime = System.currentTimeMillis()
        clientTunnel.sendPing(pingTime)

        var receivedPong = false
        for (i in 0 until 10) {
            val rx = clientTunnel.receive(200)
            if (rx != null && rx.type == EncryptedDatagramTunnel.PacketType.PONG) {
                val echoed = ByteBuffer.wrap(rx.payload).order(ByteOrder.BIG_ENDIAN).long
                assertEquals(pingTime, echoed)
                receivedPong = true
                break
            }
        }
        assertTrue("Client must receive Pong response from Provider", receivedPong)

        // 6. Test DNS Query Forwarding & Local Synthesis Fallback
        val dnsQueryPacket = buildIpv4UdpDnsQuery(
            srcIp = byteArrayOf(10, 48, 0, 2),
            dstIp = byteArrayOf(1, 1, 1, 1),
            srcPort = 54321,
            domain = "google.com"
        )
        clientTunnel.send(dnsQueryPacket, EncryptedDatagramTunnel.PacketType.DATA)

        var receivedDnsReply: ByteArray? = null
        for (i in 0 until 30) {
            val rx = clientTunnel.receive(300)
            if (rx != null && rx.type == EncryptedDatagramTunnel.PacketType.DATA) {
                receivedDnsReply = rx.payload
                break
            }
        }
        assertNotNull("Client should receive DNS response from Provider", receivedDnsReply)
        val dnsReply = receivedDnsReply!!
        assertEquals("DNS reply protocol must be UDP (17)", 17, dnsReply[9].toInt() and 0xff)

        // Teardown
        clientTunnel.close()
        providerRunner.stop()
        testScope.cancel()
    }

    private fun buildIpv4UdpDnsQuery(srcIp: ByteArray, dstIp: ByteArray, srcPort: Int, domain: String): ByteArray {
        val outDns = java.io.ByteArrayOutputStream()
        outDns.write(0x12); outDns.write(0x34) // Transaction ID
        outDns.write(0x01); outDns.write(0x00) // Flags: Standard query
        outDns.write(0x00); outDns.write(0x01) // QDCOUNT = 1
        outDns.write(0x00); outDns.write(0x00) // ANCOUNT = 0
        outDns.write(0x00); outDns.write(0x00) // NSCOUNT = 0
        outDns.write(0x00); outDns.write(0x00) // ARCOUNT = 0

        // Domain labels
        for (part in domain.split(".")) {
            val bytes = part.toByteArray(Charsets.US_ASCII)
            outDns.write(bytes.size)
            outDns.write(bytes)
        }
        outDns.write(0x00) // Root label
        outDns.write(0x00); outDns.write(0x01) // QTYPE = A (1)
        outDns.write(0x00); outDns.write(0x01) // QCLASS = IN (1)
        val dnsPayload = outDns.toByteArray()

        val udpLen = 8 + dnsPayload.size
        val totalLength = 20 + udpLen
        val packet = ByteArray(totalLength)
        packet[0] = 0x45
        packet[2] = (totalLength ushr 8).toByte(); packet[3] = totalLength.toByte()
        packet[8] = 64; packet[9] = 17 // UDP
        System.arraycopy(srcIp, 0, packet, 12, 4)
        System.arraycopy(dstIp, 0, packet, 16, 4)

        // UDP Header
        packet[20] = (srcPort ushr 8).toByte(); packet[21] = srcPort.toByte()
        packet[22] = 0; packet[23] = 53 // Dst Port 53
        packet[24] = (udpLen ushr 8).toByte(); packet[25] = udpLen.toByte()
        System.arraycopy(dnsPayload, 0, packet, 28, dnsPayload.size)

        val ipChecksum = checksum(packet, 0, 20)
        packet[10] = (ipChecksum ushr 8).toByte()
        packet[11] = ipChecksum.toByte()
        return packet
    }

    private fun buildIpv4IcmpEchoRequest(srcIp: ByteArray, dstIp: ByteArray, identifier: Int, seq: Int, payload: ByteArray): ByteArray {
        val totalLength = 20 + 8 + payload.size
        val packet = ByteArray(totalLength)

        // IP Header (20 bytes)
        packet[0] = 0x45 // Version 4, IHL 5
        packet[1] = 0
        packet[2] = (totalLength ushr 8).toByte()
        packet[3] = totalLength.toByte()
        packet[4] = 0x55
        packet[5] = 0x66
        packet[6] = 0
        packet[7] = 0
        packet[8] = 64 // TTL
        packet[9] = 1  // ICMP
        packet[10] = 0
        packet[11] = 0

        System.arraycopy(srcIp, 0, packet, 12, 4)
        System.arraycopy(dstIp, 0, packet, 16, 4)

        // Calculate IP Checksum
        val ipChecksum = checksum(packet, 0, 20)
        packet[10] = (ipChecksum ushr 8).toByte()
        packet[11] = ipChecksum.toByte()

        // ICMP Header (8 bytes)
        packet[20] = 8 // Echo Request
        packet[21] = 0 // Code 0
        packet[22] = 0 // Checksum placeholder
        packet[23] = 0
        packet[24] = (identifier ushr 8).toByte()
        packet[25] = identifier.toByte()
        packet[26] = (seq ushr 8).toByte()
        packet[27] = seq.toByte()

        System.arraycopy(payload, 0, packet, 28, payload.size)

        // Calculate ICMP Checksum
        val icmpChecksum = checksum(packet, 20, 8 + payload.size)
        packet[22] = (icmpChecksum ushr 8).toByte()
        packet[23] = icmpChecksum.toByte()

        return packet
    }

    private fun checksum(bytes: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            sum += (((bytes[i].toInt() and 0xFF) shl 8) or (bytes[i + 1].toInt() and 0xFF)).toLong()
            i += 2
        }
        if (i < end) sum += ((bytes[i].toInt() and 0xFF) shl 8).toLong()
        while ((sum ushr 16) != 0L) sum = (sum and 0xFFFF) + (sum ushr 16)
        return sum.inv().toInt() and 0xFFFF
    }
}
