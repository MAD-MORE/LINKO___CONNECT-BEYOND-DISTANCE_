package com.linkshare.app.tunnel

import android.net.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * TunnelCoordinator
 *
 * Coordinates between the Android VPN Service and the tunnel engine.
 * - Manages VPN interface lifecycle
 * - Orchestrates packet flow
 * - Handles tunnel setup and teardown
 */
class TunnelCoordinator(
    private val vpnInterface: ParcelFileDescriptor
) : AutoCloseable {

    private val TAG = "LINKO-Coordinator"
    private val coordinatorScope = CoroutineScope(Dispatchers.Default + Job())

    private var tunnelEngine: FullIpTunnelEngine? = null
    private var running = false

    fun start() {
        Log.d(TAG, "TunnelCoordinator starting")
        running = true
        // Tunnel engine will be started separately after receiving session keys
    }

    fun startTunnelEngine(
        providerAddress: java.net.InetSocketAddress,
        sessionId: String,
        sessionKey: ByteArray,
        relayUrl: String? = null
    ) {
        Log.d(TAG, "Starting tunnel engine to ${providerAddress.hostName}:${providerAddress.port}")

        coordinatorScope.launch {
            try {
                val engine = FullIpTunnelEngine(
                    vpnInterface = vpnInterface,
                    providerAddress = providerAddress,
                    sessionId = sessionId,
                    sessionKey = sessionKey,
                    relayUrl = relayUrl
                )
                this@TunnelCoordinator.tunnelEngine = engine
                engine.start()
                engine.receiveAndForwardResponse()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start tunnel engine: ${e.message}", e)
            }
        }
    }

    override fun close() {
        Log.d(TAG, "TunnelCoordinator closing")
        running = false
        tunnelEngine?.close()
        tunnelEngine = null
    }
}
