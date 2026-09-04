package com.linkshare.app.tunnel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ProviderUdpPacketForwarderTest {

    @Test
    fun forwardReturnsImmediatelyAndDrainsAsyncResponse() {
        val server = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        val forwarder = ProviderUdpPacketForwarder()
        try {
            val serverThread = Thread {
                val requestBuffer = ByteArray(1024)
                val request = DatagramPacket(requestBuffer, requestBuffer.size)
                server.receive(request)
                Thread.sleep(350)
                val replyPayload = "LINKO-UDP-RESPONSE".toByteArray()
                server.send(DatagramPacket(replyPayload, replyPayload.size, request.address, request.port))
            }
            serverThread.start()

            val packet = buildIpv4UdpPacket(
                source = byteArrayOf(10, 48, 0, 2),
                sourcePort = 40123,
                destination = byteArrayOf(127, 0, 0, 1),
                destinationPort = server.localPort,
                payload = "LINKO-UDP-REQUEST".toByteArray(),
            )

            val started = System.nanoTime()
            forwarder.forward(packet)
            val elapsedMs = (System.nanoTime() - started) / 1_000_000L
            assertTrue("forward() must not wait for the remote UDP response", elapsedMs < 200L)

            var response: ByteArray? = null
            for (attempt in 0 until 30) {
                response = forwarder.drainResponses(8).firstOrNull()
                if (response != null) break
                Thread.sleep(50)
            }

            assertTrue("provider should eventually expose the UDP response", response != null)
            val decoded = response!!
            val ihl = (decoded[0].toInt() and 0x0F) * 4
            val udpPayloadOffset = ihl + 8
            assertEquals("LINKO-UDP-RESPONSE", String(decoded, udpPayloadOffset, decoded.size - udpPayloadOffset))
            serverThread.join(1_000)
        } finally {
            forwarder.close()
            server.close()
        }
    }

    private fun buildIpv4UdpPacket(
        source: ByteArray,
        sourcePort: Int,
        destination: ByteArray,
        destinationPort: Int,
        payload: ByteArray,
    ): ByteArray {
        val totalLength = 20 + 8 + payload.size
        val packet = ByteArray(totalLength)
        val buffer = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)
        buffer.put(0x45.toByte())
        buffer.put(0)
        buffer.putShort(totalLength.toShort())
        buffer.putShort(0)
        buffer.putShort(0)
        buffer.put(64.toByte())
        buffer.put(17.toByte())
        buffer.putShort(0)
        buffer.put(source)
        buffer.put(destination)
        buffer.putShort(sourcePort.toShort())
        buffer.putShort(destinationPort.toShort())
        buffer.putShort((8 + payload.size).toShort())
        buffer.putShort(0)
        buffer.put(payload)
        return packet
    }
}
