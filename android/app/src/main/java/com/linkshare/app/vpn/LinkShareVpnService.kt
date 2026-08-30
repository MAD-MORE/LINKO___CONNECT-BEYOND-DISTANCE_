package com.linkshare.app.vpn

import android.app.Service
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.linkshare.app.tunnel.TunnelCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * LINKO Receiver-side VPN Service.
 *
 * Creates a VPN interface that captures all device traffic and routes it through
 * the encrypted tunnel to the Provider's network.
 *
 * Lifecycle:
 * 1. User taps "Connect" → LinkShareViewModel → LinkoEngineBridge.connect()
 * 2. LinkoEngineBridge starts LinkShareVpnService
 * 3. Service requests VPN permission (if not already granted)
 * 4. Service creates VPN builder and prepares interface
 * 5. FullIpTunnelEngine reads packets and forwards through tunnel
 * 6. User taps "Disconnect" → service.stopVpn()
 */
class LinkShareVpnService : VpnService() {

    private val TAG = "LINKO-VPN"
    private val binder = VpnServiceBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    private var tunnelCoordinator: TunnelCoordinator? = null
    private var vpnJob: Job? = null

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "LinkShareVpnService starting")

        vpnJob?.cancel()
        vpnJob = serviceScope.launch {
            try {
                startVpn()
            } catch (e: Exception) {
                Log.e(TAG, "VPN startup failed: ${e.message}", e)
                stopSelf()
            }
        }

        return START_STICKY
    }

    private suspend fun startVpn() {
        Log.d(TAG, "Configuring VPN interface")

        val builder = Builder().apply {
            setSession("LINKO-Receiver")
            // Route all IPv4 traffic through the VPN
            addRoute("0.0.0.0", 0)
            // Route all IPv6 traffic through the VPN
            addRoute("::", 0)
            // Set DNS to a non-routable address; we'll handle DNS internally
            addDnsServer("8.8.8.8")
            addDnsServer("8.8.4.4")
            // Exclude LINKO control plane from VPN to avoid loops
            addDisallowedApplication(packageName)
        }

        val vpnInterface = try {
            builder.establish() ?: throw Exception("VPN interface failed to establish")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to establish VPN interface: ${e.message}")
            throw e
        }

        Log.d(TAG, "VPN interface established")

        val coordinator = TunnelCoordinator(vpnInterface)
        this.tunnelCoordinator = coordinator
        binder.setVpnService(this)

        // Start the tunnel read/write loop
        coordinator.start()
    }

    fun stopVpn() {
        Log.d(TAG, "Stopping VPN service")
        vpnJob?.cancel()
        tunnelCoordinator?.close()
        stopSelf()
    }

    override fun onDestroy() {
        Log.d(TAG, "LinkShareVpnService destroyed")
        vpnJob?.cancel()
        tunnelCoordinator?.close()
        super.onDestroy()
    }

    inner class VpnServiceBinder : Binder() {
        private var vpnService: LinkShareVpnService? = null

        fun setVpnService(service: LinkShareVpnService) {
            vpnService = service
        }

        fun getVpnService(): LinkShareVpnService? = vpnService
    }

    companion object {
        const val ACTION_START_VPN = "com.linkshare.app.vpn.START_VPN"
        const val ACTION_STOP_VPN = "com.linkshare.app.vpn.STOP_VPN"
    }
}
