package com.linkshare.app.diagnostics

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import com.linkshare.app.BuildConfig
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.network.LinkoDeviceControlApi
import com.linkshare.app.network.LinkoRealtimeManager
import com.linkshare.app.vpn.LinkShareVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Real, read-only runtime diagnostics. No synthetic success states. */
object DiagnosticProbe {
    suspend fun run(
        context: Context,
        onProgress: suspend (completed: Int, results: List<DiagnosticResult>) -> Unit = { _, _ -> }
    ): List<DiagnosticResult> = withContext(Dispatchers.IO) {
        val out = LinkoDiagnosticCenter.initialResults().toMutableList()
        var completed = 0
        suspend fun set(name: String, status: DiagnosticStatus, detail: String, latency: Long? = null) {
            val i = out.indexOfFirst { it.name == name }
            if (i >= 0) out[i] = DiagnosticResult(name, status, detail, latency)
            completed++
            onProgress(completed, out.toList())
            delay(35L)
        }

        val auth = LinkoAuth(context)
        val token = auth.currentAccessToken().orEmpty()
        val registered = auth.hasRegisteredDevice()
        if (token.isBlank()) set("Authentication", DiagnosticStatus.FAIL, "No active Supabase access token")
        else {
            val started = System.currentTimeMillis()
            val result = runCatching { auth.refreshAccountIdentity() }.getOrNull()
            if (result?.success == true) set("Authentication", DiagnosticStatus.PASS, "Authenticated user verified${result.userId?.let { ": $it" } ?: ""}", System.currentTimeMillis() - started)
            else set("Authentication", DiagnosticStatus.FAIL, result?.message ?: "Authenticated session could not be refreshed", System.currentTimeMillis() - started)
        }

        val cm = context.getSystemService(ConnectivityManager::class.java)
        val network = cm?.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val internet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true && validated
        val transports = buildList {
            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) add("Wi‑Fi")
            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true) add("Cellular")
            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true) add("Ethernet")
            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) add("VPN")
        }.joinToString(", ")
        val startedSupabase = System.currentTimeMillis()
        val supabaseOk = runCatching {
            val c = URL(BuildConfig.LINKO_SUPABASE_URL.trimEnd('/') + "/rest/v1/").openConnection() as HttpURLConnection
            c.connectTimeout = 5000; c.readTimeout = 5000; c.requestMethod = "HEAD"
            c.setRequestProperty("apikey", BuildConfig.LINKO_SUPABASE_PUBLISHABLE_KEY)
            c.responseCode in 200..499
        }.getOrDefault(false)
        set("Supabase", if (supabaseOk) DiagnosticStatus.PASS else DiagnosticStatus.FAIL, if (supabaseOk) "Supabase endpoint reachable${if (transports.isNotBlank()) " over $transports" else ""}" else "Supabase endpoint is not reachable", System.currentTimeMillis() - startedSupabase)

        set("Realtime", when {
            !registered -> DiagnosticStatus.SKIPPED
            LinkoRealtimeManager.isConnected() -> DiagnosticStatus.PASS
            else -> DiagnosticStatus.FAIL
        }, when {
            !registered -> "Device is not registered; realtime runtime cannot be evaluated"
            LinkoRealtimeManager.isConnected() -> "Realtime transport connected with LINKO channels active"
            LinkoRealtimeManager.isStarted() -> "Realtime manager started but transport is not connected"
            else -> "Realtime manager is not running"
        })

        val api = LinkoDeviceControlApi(context, auth)
        val liveRegistration = runCatching { api.ensureRegistered() }.getOrNull()
        val hasActiveSession = LinkShareVpnService.isRunning() && !LinkShareVpnService.sessionId().isNullOrBlank()
        set("Relay", when {
            !internet -> DiagnosticStatus.SKIPPED
            liveRegistration == null -> DiagnosticStatus.FAIL
            hasActiveSession -> {
                val endpoint = LinkShareVpnService.peerEndpoint().orEmpty()
                val reachable = endpoint.isNotBlank() && runCatching {
                    val parts = endpoint.substringAfterLast(' ').substringBeforeLast(':') to endpoint.substringAfterLast(':').toInt()
                    val host = parts.first; val port = parts.second
                    java.net.DatagramSocket().use { socket -> socket.connect(java.net.InetSocketAddress(host, port)); socket.isConnected }
                }.getOrDefault(false)
                if (reachable) DiagnosticStatus.PASS else DiagnosticStatus.FAIL
            }
            else -> DiagnosticStatus.SKIPPED
        }, when {
            !internet -> "No validated network available for relay probe"
            liveRegistration == null -> "Authenticated control-plane registration failed"
            hasActiveSession -> "Active tunnel peer endpoint ${LinkShareVpnService.peerEndpoint().orEmpty()} is addressable from this device"
            else -> "No active tunnel; relay path is not testable without a session"
        })

        val vpnGranted = VpnService.prepare(context) == null
        set("VPN", if (vpnGranted) DiagnosticStatus.PASS else DiagnosticStatus.FAIL, if (vpnGranted) "Android VPN permission granted" else "Android VPN permission is not granted")

        val sessionId = LinkShareVpnService.sessionId()
        val config = sessionId?.let { runCatching { api.tunnelConfig(it) }.getOrNull() }
        set("Encryption", when {
            config == null -> DiagnosticStatus.SKIPPED
            config.key.size == 32 -> DiagnosticStatus.PASS
            else -> DiagnosticStatus.FAIL
        }, when {
            config == null -> "No active tunnel credentials to inspect"
            config.key.size == 32 -> "Active tunnel uses a valid 256-bit session key"
            else -> "Active tunnel key length is invalid"
        })

        val tunnelRunning = LinkShareVpnService.isRunning()
        set("Tunnel", if (tunnelRunning) DiagnosticStatus.PASS else DiagnosticStatus.FAIL, if (tunnelRunning) "Android LINKO VPN service is running for session ${sessionId ?: "unknown"}" else "LINKO VPN tunnel is not running")

        val up = LinkShareVpnService.bytesUp(); val down = LinkShareVpnService.bytesDown(); val rtt = LinkShareVpnService.rttMs()
        set("Packet flow", when {
            !tunnelRunning -> DiagnosticStatus.SKIPPED
            up > 0L || down > 0L || rtt >= 0L -> DiagnosticStatus.PASS
            else -> DiagnosticStatus.FAIL
        }, when {
            !tunnelRunning -> "No running tunnel to measure"
            up > 0L || down > 0L -> "Live tunnel traffic: $up B up / $down B down${if (rtt >= 0) "; RTT ${rtt} ms" else ""}"
            rtt >= 0L -> "Tunnel keepalive response observed; RTT ${rtt} ms"
            else -> "Tunnel is running but no packet flow or peer keepalive response observed"
        })

        if (!internet) set("Internet", DiagnosticStatus.FAIL, "Android reports no validated Internet connection")
        else {
            val started = System.currentTimeMillis()
            val ok = runCatching {
                val c = URL("https://connectivitycheck.gstatic.com/generate_204").openConnection() as HttpURLConnection
                c.connectTimeout = 5000; c.readTimeout = 5000; c.instanceFollowRedirects = false; c.responseCode == 204
            }.getOrDefault(false)
            set("Internet", if (ok) DiagnosticStatus.PASS else DiagnosticStatus.FAIL, if (ok) "HTTPS Internet probe returned 204" else "HTTPS Internet probe failed", System.currentTimeMillis() - started)
        }
        out
    }
}
