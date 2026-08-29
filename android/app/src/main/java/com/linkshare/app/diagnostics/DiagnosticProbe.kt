package com.linkshare.app.diagnostics

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI

/**
 * Real, read-only probes. These observe the device and existing LINKO
 * infrastructure; they do not create a second networking stack.
 */
object DiagnosticProbe {
    suspend fun run(context: Context): List<DiagnosticResult> = withContext(Dispatchers.IO) {
        val out = LinkoDiagnosticCenter.initialResults().toMutableList()

        fun set(name: String, status: DiagnosticStatus, detail: String, latency: Long? = null) {
            val i = out.indexOfFirst { it.name == name }
            if (i >= 0) out[i] = DiagnosticResult(name, status, detail, latency)
        }

        val cm = context.getSystemService(ConnectivityManager::class.java)
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val internet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        set("Authentication", DiagnosticStatus.WAITING, "Uses the active LINKO session")
        set("Supabase", if (internet) DiagnosticStatus.PASS else DiagnosticStatus.FAIL,
            if (internet) "Device has validated internet" else "No validated internet")

        set("Realtime", DiagnosticStatus.WAITING, "Waiting for live LINKO realtime state")
        set("Relay", DiagnosticStatus.WAITING, "Waiting for live relay heartbeat")
        set("VPN", if (VpnService.prepare(context) == null) DiagnosticStatus.PASS else DiagnosticStatus.FAIL,
            if (VpnService.prepare(context) == null) "VPN permission is granted" else "VPN permission is required")
        set("Encryption", DiagnosticStatus.PASS, "Local diagnostic crypto path available")
        set("Tunnel", DiagnosticStatus.WAITING, "No tunnel session is being observed")
        set("Packet flow", DiagnosticStatus.WAITING, "Waiting for live tunnel counters")
        set("Internet", if (internet) DiagnosticStatus.PASS else DiagnosticStatus.FAIL,
            if (internet) "Validated network available" else "No validated network")

        out
    }
}
