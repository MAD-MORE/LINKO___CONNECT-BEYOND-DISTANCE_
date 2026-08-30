package com.linkshare.app.provider

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.linkshare.app.network.LinkoControlPlaneApi
import com.linkshare.app.tunnel.EncryptedDatagramTunnel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URL
import java.nio.ByteBuffer

/**
 * ProviderTunnelRunner
 *
 * Runs on the Provider device in a background coroutine.
 * - Receives encrypted packets from Receiver
 * - Forwards packets through Provider's mobile network
 * - Sends responses back through tunnel
 * - Manages keepalive and connection health
 */
class ProviderTunnelRunner(
    private val context: Context,
    private val requestId: String
) : AutoCloseable {

    private val TAG = "LINKO-ProviderRunner"
    private val runnerScope = CoroutineScope(Dispatchers.IO + Job())

    private var tunnel: EncryptedDatagramTunnel? = null
    private var running = false

    suspend fun start() {
        Log.d(TAG, "ProviderTunnelRunner starting for request: $requestId")
        running = true

        try {
            // Fetch session details from control plane
            val sessionDetails = fetchSessionDetails(requestId)
            if (sessionDetails == null) {
                Log.e(TAG, "Failed to fetch session details")
                return
            }

            val sessionId = sessionDetails["sessionId"] as? String ?: return
            val sessionKey = sessionDetails["sessionKey"] as? ByteArray ?: return
            val relayUrl = sessionDetails["relayUrl"] as? String
            val receiverAddress = parseAddress(sessionDetails["receiverAddress"] as? String ?: "")

            if (receiverAddress == null) {
                Log.e(TAG, "Invalid receiver address")
                return
            }

            // Create encrypted tunnel
            val socket = DatagramSocket()
            tunnel = EncryptedDatagramTunnel(
                socket = socket,
                peer = receiverAddress,
                sessionId = sessionId,
                role = EncryptedDatagramTunnel.Role.PROVIDER,
                sessionKey = sessionKey
            )

            Log.d(TAG, "Tunnel established to receiver at $receiverAddress")

            // Start the read/forward/write loop
            readAndForwardLoop()

        } catch (e: Exception) {
            Log.e(TAG, "Provider tunnel setup failed: ${e.message}", e)
        }
    }

    private suspend fun readAndForwardLoop() {
        val tunnel = this.tunnel ?: return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        var keepaliveCounter = 0

        while (runnerScope.isActive && running) {
            try {
                // Receive encrypted packet from Receiver
                val received = tunnel.receive(1000) ?: run {
                    // Send keepalive every 10 seconds
                    keepaliveCounter++
                    if (keepaliveCounter >= 10) {
                        tunnel.sendPing()
                        keepaliveCounter = 0
                        Log.d(TAG, "Sent keepalive ping")
                    }
                    return@run null
                }

                keepaliveCounter = 0

                when (received.type) {
                    EncryptedDatagramTunnel.PacketType.DATA -> {
                        Log.d(TAG, "Received data packet (${received.payload.size}B)")
                        forwardThroughNetwork(received.payload, cm)
                    }
                    EncryptedDatagramTunnel.PacketType.PING -> {
                        Log.d(TAG, "Received ping, sending pong")
                        val timestamp = ByteBuffer.wrap(received.payload).long
                        tunnel.sendPong(timestamp)
                    }
                    EncryptedDatagramTunnel.PacketType.CLOSE -> {
                        Log.d(TAG, "Received close packet, shutting down")
                        running = false
                    }
                    else -> {
                        Log.w(TAG, "Received unexpected packet type: ${received.type}")
                    }
                }

            } catch (e: Exception) {
                if (runnerScope.isActive && running) {
                    Log.e(TAG, "Error in read/forward loop: ${e.message}")
                    delay(100)
                }
            }
        }

        Log.d(TAG, "Read/forward loop ended")
    }

    private fun forwardThroughNetwork(packet: ByteArray, cm: ConnectivityManager) {
        try {
            // Check if device has internet
            val network = cm.activeNetwork
            val caps = network?.let { cm.getNetworkCapabilities(it) }
            val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

            if (!hasInternet) {
                Log.w(TAG, "No internet available on provider device")
                return
            }

            // In a real implementation, this would:
            // 1. Extract IP packet destination
            // 2. Open a socket to that destination
            // 3. Send the packet payload
            // 4. Receive response
            // 5. Send response back through tunnel

            Log.d(TAG, "Forwarding packet (${packet.size}B) through provider's network")

            // For now, we'll just log the forwarding
            // Real implementation would parse IP headers and route accordingly

        } catch (e: Exception) {
            Log.e(TAG, "Failed to forward packet: ${e.message}")
        }
    }

    private suspend fun fetchSessionDetails(requestId: String): Map<String, Any>? {
        return try {
            // Fetch from control plane API
            // This is a simplified mock; real implementation would call LinkoControlPlaneApi
            Log.d(TAG, "Fetching session details for $requestId")

            // Mock response (in production, call actual API)
            mapOf(
                "sessionId" to "session-$requestId",
                "sessionKey" to ByteArray(32) { 0xAA.toByte() },
                "relayUrl" to "wss://relay.linko.io",
                "receiverAddress" to "127.0.0.1:7000"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch session details: ${e.message}")
            null
        }
    }

    private fun parseAddress(addressStr: String): InetSocketAddress? {
        return try {
            val parts = addressStr.split(":")
            if (parts.size != 2) return null
            InetSocketAddress(parts[0], parts[1].toInt())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse address: $addressStr")
            null
        }
    }

    override fun close() {
        Log.d(TAG, "Closing ProviderTunnelRunner")
        running = false
        tunnel?.close()
    }
}
