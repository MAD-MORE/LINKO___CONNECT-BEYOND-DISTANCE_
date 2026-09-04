package com.linkshare.app.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InternetPathProbeTest {
    @Test
    fun acceptsMatchingCloudflareSynAck() {
        val request = InternetPathProbe.create()
        val response = request.packet.copyOf()

        // Swap IPv4 addresses.
        System.arraycopy(request.packet, 12, response, 16, 4)
        System.arraycopy(request.packet, 16, response, 12, 4)
        // Swap TCP ports.
        val sourcePort = ((request.packet[20].toInt() and 0xff) shl 8) or (request.packet[21].toInt() and 0xff)
        response[20] = 0
        response[21] = 443.toByte()
        response[22] = (sourcePort ushr 8).toByte()
        response[23] = sourcePort.toByte()
        // Remote sequence may be arbitrary; ACK must be the client sequence + 1.
        response[24] = 0
        response[25] = 0
        response[26] = 0
        response[27] = 7
        val ack = request.expectation.sequence + 1L
        response[28] = (ack ushr 24).toByte()
        response[29] = (ack ushr 16).toByte()
        response[30] = (ack ushr 8).toByte()
        response[31] = ack.toByte()
        // SYN + ACK, data offset 6.
        response[32] = 0x60
        response[33] = 0x12

        assertTrue(InternetPathProbe.isSuccessfulResponse(response, request.expectation))
    }

    @Test
    fun rejectsWrongAcknowledgement() {
        val request = InternetPathProbe.create()
        val response = request.packet.copyOf()
        System.arraycopy(request.packet, 12, response, 16, 4)
        System.arraycopy(request.packet, 16, response, 12, 4)
        val sourcePort = ((request.packet[20].toInt() and 0xff) shl 8) or (request.packet[21].toInt() and 0xff)
        response[20] = 0
        response[21] = 443.toByte()
        response[22] = (sourcePort ushr 8).toByte()
        response[23] = sourcePort.toByte()
        response[28] = 0
        response[29] = 0
        response[30] = 0
        response[31] = 1
        response[32] = 0x60
        response[33] = 0x12

        assertFalse(InternetPathProbe.isSuccessfulResponse(response, request.expectation))
    }
}