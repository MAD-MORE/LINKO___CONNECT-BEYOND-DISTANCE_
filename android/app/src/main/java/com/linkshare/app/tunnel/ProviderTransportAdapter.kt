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
    override fun drainPending(maxCount: Int): List<ByteArray> = delegate.drainTcpResponses(maxCount)
    override fun close() = delegate.close()
}

class UdpOnlyProviderTransportAdapter : ProviderTransportAdapter {
    private val delegate = ProviderUdpPacketForwarder()
    override fun forward(packet: ByteArray): List<ByteArray> = listOfNotNull(delegate.forward(packet))
    override fun drainPending(maxCount: Int): List<ByteArray> = emptyList()
    override fun close() = delegate.close()
}

object ProviderSocketFactory {
    fun openDatagramSocket(context: Context): DatagramSocket = LinkoNetworkTransport(context).openDatagramSocket()
    fun openDatagramSocket(): DatagramSocket = DatagramSocket()
}
