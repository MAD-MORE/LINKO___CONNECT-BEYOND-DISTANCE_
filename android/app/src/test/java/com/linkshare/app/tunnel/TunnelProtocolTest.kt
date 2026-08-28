package com.linkshare.app.tunnel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.util.UUID

class TunnelProtocolTest {

    private val random = SecureRandom()

    @Test
    fun testEncryptionAndDecryptionRoundtrip() {
        val sessionKey = ByteArray(32).also(random::nextBytes)
        val sessionId = UUID.randomUUID().toString()

        val providerSocket = DatagramSocket()
        val clientSocket = DatagramSocket()

        val providerAddress = InetSocketAddress("127.0.0.1", providerSocket.localPort)
        val clientAddress = InetSocketAddress("127.0.0.1", clientSocket.localPort)

        val providerTunnel = EncryptedDatagramTunnel(
            socket = providerSocket,
            peer = clientAddress,
            sessionId = sessionId,
            role = EncryptedDatagramTunnel.Role.PROVIDER,
            sessionKey = sessionKey
        )

        val clientTunnel = EncryptedDatagramTunnel(
            socket = clientSocket,
            peer = providerAddress,
            sessionId = sessionId,
            role = EncryptedDatagramTunnel.Role.RECEIVER,
            sessionKey = sessionKey
        )

        val message = "LINKO Real Internet Data Packet Payload".toByteArray(Charsets.UTF_8)

        // Client sends DATA to Provider
        clientTunnel.send(message, EncryptedDatagramTunnel.PacketType.DATA)

        val received = providerTunnel.receive(1000)
        assertNotNull("Provider should receive the packet", received)
        assertEquals(EncryptedDatagramTunnel.PacketType.DATA, received?.type)
        assertEquals(1L, received?.sequenceNumber)
        assertTrue(message.contentEquals(received!!.payload))

        // Provider sends response DATA back to Client
        val response = "LINKO Provider Internet Response Data".toByteArray(Charsets.UTF_8)
        providerTunnel.send(response, EncryptedDatagramTunnel.PacketType.DATA)

        val clientReceived = clientTunnel.receive(1000)
        assertNotNull("Client should receive the response", clientReceived)
        assertEquals(EncryptedDatagramTunnel.PacketType.DATA, clientReceived?.type)
        assertTrue(response.contentEquals(clientReceived!!.payload))

        providerTunnel.close()
        clientTunnel.close()
    }

    @Test
    fun testTamperedHeaderFailsMacVerification() {
        val sessionKey = ByteArray(32).also(random::nextBytes)
        val sessionId = UUID.randomUUID().toString()

        val providerSocket = DatagramSocket()
        val clientSocket = DatagramSocket()

        val providerAddress = InetSocketAddress("127.0.0.1", providerSocket.localPort)
        val clientAddress = InetSocketAddress("127.0.0.1", clientSocket.localPort)

        val providerTunnel = EncryptedDatagramTunnel(
            socket = providerSocket,
            peer = clientAddress,
            sessionId = sessionId,
            role = EncryptedDatagramTunnel.Role.PROVIDER,
            sessionKey = sessionKey
        )

        val clientTunnel = EncryptedDatagramTunnel(
            socket = clientSocket,
            peer = providerAddress,
            sessionId = sessionId,
            role = EncryptedDatagramTunnel.Role.RECEIVER,
            sessionKey = sessionKey
        )

        // Send packet
        clientTunnel.send("Sensitive IP Payload".toByteArray(Charsets.UTF_8))

        // Intercept raw datagram and tamper with the sequence number in AAD header
        val rawBuffer = ByteArray(1024)
        val rawPacket = java.net.DatagramPacket(rawBuffer, rawBuffer.size)
        providerSocket.soTimeout = 1000
        providerSocket.receive(rawPacket)

        // Tamper with sequence number at byte 75 (part of AAD)
        rawBuffer[75] = (rawBuffer[75].toInt() xor 0xFF).toByte()

        // Pass tampered packet to another socket to test receiver rejection
        val testSocket = DatagramSocket()
        val testTunnel = EncryptedDatagramTunnel(
            socket = testSocket,
            peer = clientAddress,
            sessionId = sessionId,
            role = EncryptedDatagramTunnel.Role.PROVIDER,
            sessionKey = sessionKey
        )

        testSocket.send(java.net.DatagramPacket(rawBuffer, rawPacket.length, InetSocketAddress("127.0.0.1", testSocket.localPort)))
        val tamperedReceived = testTunnel.receive(500)

        assertNull("Tampered packet must be rejected by GCM MAC authentication", tamperedReceived)

        providerTunnel.close()
        clientTunnel.close()
        testTunnel.close()
    }

    @Test
    fun testPingPongKeepalive() {
        val sessionKey = ByteArray(32).also(random::nextBytes)
        val sessionId = UUID.randomUUID().toString()

        val providerSocket = DatagramSocket()
        val clientSocket = DatagramSocket()

        val providerAddress = InetSocketAddress("127.0.0.1", providerSocket.localPort)
        val clientAddress = InetSocketAddress("127.0.0.1", clientSocket.localPort)

        val providerTunnel = EncryptedDatagramTunnel(
            socket = providerSocket,
            peer = clientAddress,
            sessionId = sessionId,
            role = EncryptedDatagramTunnel.Role.PROVIDER,
            sessionKey = sessionKey
        )

        val clientTunnel = EncryptedDatagramTunnel(
            socket = clientSocket,
            peer = providerAddress,
            sessionId = sessionId,
            role = EncryptedDatagramTunnel.Role.RECEIVER,
            sessionKey = sessionKey
        )

        val now = System.currentTimeMillis()
        clientTunnel.sendPing(now)

        val pingRx = providerTunnel.receive(1000)
        assertNotNull(pingRx)
        assertEquals(EncryptedDatagramTunnel.PacketType.PING, pingRx?.type)

        val echoedTime = ByteBuffer.wrap(pingRx!!.payload).order(ByteOrder.BIG_ENDIAN).long
        assertEquals(now, echoedTime)

        providerTunnel.sendPong(echoedTime)

        val pongRx = clientTunnel.receive(1000)
        assertNotNull(pongRx)
        assertEquals(EncryptedDatagramTunnel.PacketType.PONG, pongRx?.type)

        val clientEchoed = ByteBuffer.wrap(pongRx!!.payload).order(ByteOrder.BIG_ENDIAN).long
        assertEquals(now, clientEchoed)

        providerTunnel.close()
        clientTunnel.close()
    }
}
