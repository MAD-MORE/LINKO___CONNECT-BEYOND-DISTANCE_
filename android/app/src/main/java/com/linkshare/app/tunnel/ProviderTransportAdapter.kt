package com.linkshare.app.tunnel

import android.content.Context
import java.net.DatagramSocket

interface ProviderTransportAdapter : java.io.Closeable {
    fun forward(packet: ByteArray): List<ByteArray>
    fun drainPending(maxCount: Int = 32): List<ByteArray>
}

class FullIpProviderTransportAdapter : ProviderTransportAdapter {
    private val delegate = ProviderIpPacketForwarder()
    override fun forward(packet: ByteArray): List<ByteArray> = delegate.forward(packet)

    override fun drainPending(maxCount: Int): List<ByteArray> {
        val limit = maxCount.coerceIn(1, 256)
        val responses = ArrayList<ByteArray>(limit)
        if (responses.size < limit) {
            responses += delegate.drainUdpResponses(limit - responses.size)
        }
        if (responses.size < limit) {
            responses += delegate.drainTcpResponses(limit - responses.size)
        }
        return responses
    }

    override fun close() = delegate.close()
}

class UdpOnlyProviderTransportAdapter : ProviderTransportAdapter {
    private val delegate = ProviderUdpPacketForwarder()
    override fun forward(packet: ByteArray): List<ByteArray> = listOfNotNull(delegate.forward(packet))
    override fun drainPending(maxCount: Int): List<ByteArray> = delegate.drainResponses(maxCount)
    override fun close() = delegate.close()
}

object ProviderSocketFactory {
    fun openDatagramSocket(context: Context): DatagramSocket = LinkoNetworkTransport(context).openDatagramSocket()
    fun openDatagramSocket(): DatagramSocket = DatagramSocket()
}