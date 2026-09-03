package com.linkshare.app.network

import com.linkshare.app.tunnel.EncryptedDatagramTunnel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DirectP2pNegotiatorRegressionTest {
    @Test
    fun authenticatedPingGetsPongDuringNegotiation() = runBlocking {
        val providerSocket = DatagramSocket(0)
        val receiverSocket = DatagramSocket(0)
        val provider = InetSocketAddress("127.0.0.1", providerSocket.localPort)
        val receiver = InetSocketAddress("127.0.0.1", receiverSocket.localPort)
        val key = ByteArray(32) { it.toByte() }
        val sessionId = UUID.randomUUID().toString()

        val receiverTunnel = EncryptedDatagramTunnel(
            receiverSocket,
            provider,
            sessionId,
            EncryptedDatagramTunnel.Role.RECEIVER,
            key,
        )

        val executor = Executors.newSingleThreadExecutor()
        val future = executor.submit {
            val providerTunnel = EncryptedDatagramTunnel(
                providerSocket,
                receiver,
                sessionId,
                EncryptedDatagramTunnel.Role.PROVIDER,
                key,
            )
            val inbound = providerTunnel.receiveAny(2_000)
            assertNotNull(inbound)
            assertEquals(EncryptedDatagramTunnel.PacketType.PING, inbound!!.type)
            EncryptedDatagramTunnel(
                providerSocket,
                inbound.source,
                sessionId,
                EncryptedDatagramTunnel.Role.PROVIDER,
                key,
            ).sendPong(System.currentTimeMillis())
        }

        receiverTunnel.sendPing()
        val pong = receiverTunnel.receive(2_000)
        future.get(3, TimeUnit.SECONDS)

        assertNotNull(pong)
        assertEquals(EncryptedDatagramTunnel.PacketType.PONG, pong!!.type)

        receiverSocket.close()
        providerSocket.close()
        executor.shutdownNow()
    }
}
