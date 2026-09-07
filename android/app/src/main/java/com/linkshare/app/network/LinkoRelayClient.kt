package com.linkshare.app.network

import com.linkshare.app.tunnel.EncryptedDatagramTunnel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.Base64

/**
 * Optional LINKO relay bootstrap.
 *
 * The relay is never used as the primary path. A caller explicitly supplies an endpoint
 * only after direct P2P negotiation has failed. User tunnel frames remain AES-GCM encrypted
 * by EncryptedDatagramTunnel and are forwarded byte-for-byte by the relay.
 */
object LinkoRelayClient {
    data class Endpoint(val host: String, val port: Int = DEFAULT_PORT)

    suspend fun prepare(
        socket: DatagramSocket,
        endpoint: Endpoint,
        sessionId: String,
        sessionKey: ByteArray,
        role: EncryptedDatagramTunnel.Role,
        timeoutMs: Int = 2_000,
    ): InetSocketAddress = withContext(Dispatchers.IO) {
        require(sessionKey.size == 32) { "sessionKey must be exactly 32 bytes" }
        require(endpoint.host.isNotBlank()) { "relay host is required" }
        require(endpoint.port in 1..65535) { "relay port is invalid" }

        val relay = InetSocketAddress(endpoint.host, endpoint.port)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(sessionKey)
        val roleName = if (role == EncryptedDatagramTunnel.Role.PROVIDER) "provider" else "receiver"
        val hello = "HELLO|$sessionId|$token|$roleName".toByteArray(Charsets.US_ASCII)

        socket.soTimeout = timeoutMs
        repeat(2) {
            socket.send(DatagramPacket(hello, hello.size, relay))
            val responseBuffer = ByteArray(256)
            val response = DatagramPacket(responseBuffer, responseBuffer.size)
            try {
                socket.receive(response)
            } catch (_: SocketTimeoutException) {
                return@repeat
            }
            if (response.address.hostAddress == relay.address?.hostAddress && response.port == relay.port) {
                val text = response.data.copyOf(response.length).toString(Charsets.US_ASCII)
                if (text == "OK|$sessionId|") return@withContext relay
            }
        }
        throw LinkoNetworkException("RELAY_HANDSHAKE_FAILED")
    }

    fun isConfigured(endpoint: Endpoint?): Boolean = endpoint != null && endpoint.host.isNotBlank() && endpoint.port in 1..65535

    const val DEFAULT_PORT = 3479
}
