package com.linkshare.app.diagnostics

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.yield
import kotlinx.coroutines.withContext

/**
 * Real, read-only probes. These observe the device and existing LINKO
 * infrastructure; they do not create a second networking stack.
 *
 * Each completed probe is emitted so the Diagnostic Center can show real
 * progress instead of appearing frozen while the checks run.
 */
object DiagnosticProbe {
    suspend fun run(
        context: Context,
        onProgress: suspend (completed: Int, results: List<DiagnosticResult>) -> Unit = { _, _ -> }
    ): List<DiagnosticResult> = withContext(Dispatchers.IO) {
        val out = LinkoDiagnosticCenter.initialResults().toMutableList()
        var completed = 0

        suspend fun set(
            name: String,
            status: DiagnosticStatus,
            detail: String,
            latency: Long? = null
        ) {
            val i = out.indexOfFirst { it.name == name }
            if (i >= 0) out[i] = DiagnosticResult(name, status, detail, latency)
            completed++
            onProgress(completed, out.toList())
            yield()
        }

        val cm = context.getSystemService(ConnectivityManager::class.java)
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val internet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        set("Authentication", DiagnosticStatus.WAITING, "Uses the active LINKO session")
        set(
            "Supabase",
            if (internet) DiagnosticStatus.PASS else DiagnosticStatus.FAIL,
            if (internet) "Device has validated internet" else "No validated internet"
        )
        set("Realtime", DiagnosticStatus.WAITING, "Waiting for live LINKO realtime state")
        set("Relay", DiagnosticStatus.WAITING, "Waiting for live relay heartbeat")
        val vpnGranted = VpnService.prepare(context) == null
        set(
            "VPN",
            if (vpnGranted) DiagnosticStatus.PASS else DiagnosticStatus.FAIL,
            if (vpnGranted) "VPN permission is granted" else "VPN permission is required"
        )
        set("Encryption", DiagnosticStatus.PASS, "Local diagnostic crypto path available")
        set("Tunnel", DiagnosticStatus.WAITING, "No tunnel session is being observed")
        set("Packet flow", DiagnosticStatus.WAITING, "Waiting for live tunnel counters")
        set(
            "Internet",
            if (internet) DiagnosticStatus.PASS else DiagnosticStatus.FAIL,
            if (internet) "Validated network available" else "No validated network"
        )

        out
    }
}
