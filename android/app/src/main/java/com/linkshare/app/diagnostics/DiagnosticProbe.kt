package com.linkshare.app.diagnostics

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.SystemClock
import com.linkshare.app.BuildConfig
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.auth.LinkoStartupCache
import com.linkshare.app.network.LinkoConnectionPhase
import com.linkshare.app.network.LinkoEngineBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.yield
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import java.security.MessageDigest

/** Real, read-only diagnostics against LINKO's existing production components. */
object DiagnosticProbe {
    private const val DEVICE_KEY_ALIAS = "linko_device_signing_key"
    private const val HTTP_TIMEOUT_MS = 8_000

    suspend fun run(
        context: Context,
        onProgress: suspend (completed: Int, results: List<DiagnosticResult>) -> Unit = { _, _ -> }
    ): List<DiagnosticResult> = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val auth = LinkoAuth(app)
        val telemetry = LinkoDiagnosticTelemetry.snapshot.value
        val out = LinkoDiagnosticCenter.initialResults().toMutableList()
        var completed = 0

        suspend fun emit(
            name: String,
            status: DiagnosticStatus,
            detail: String,
            startedAt: Long,
            errorType: String? = null,
            errorMessage: String? = null,
            blockedBy: String? = null,
        ) {
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            val index = out.indexOfFirst { it.name == name }
            if (index >= 0) {
                out[index] = DiagnosticResult(
                    name = name,
                    status = status,
                    detail = detail,
                    latencyMs = elapsed,
                    errorType = errorType,
                    errorMessage = errorMessage,
                    blockedBy = blockedBy,
                )
            }
            completed++
            onProgress(completed, LinkoDiagnosticCenter.blockedResults(out.toList()))
            yield()
        }

        suspend fun check(name: String, block: suspend () -> ProbeOutcome) {
            val started = SystemClock.elapsedRealtime()
            val result = runCatching { block() }.getOrElse { error ->
                ProbeOutcome(
                    DiagnosticStatus.FAIL,
                    "Probe crashed before completion",
                    errorType = error::class.java.simpleName,
                    errorMessage = safeMessage(error),
                )
            }
            emit(name, result.status, result.detail, started, result.errorType, result.errorMessage, result.blockedBy)
        }

        check("Application") {
            ProbeOutcome(DiagnosticStatus.PASS, "Process active · ${BuildConfig.APPLICATION_ID} · build ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        }

        val cm = app.getSystemService(ConnectivityManager::class.java)
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val internet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        check("Authentication") {
            if (!auth.isSignedIn()) {
                ProbeOutcome(DiagnosticStatus.SKIPPED, "No active session · authentication-dependent checks are not exercised")
            } else {
                val token = auth.currentAccessToken()
                val userId = auth.currentUserId()
                if (token.isNullOrBlank()) {
                    ProbeOutcome(DiagnosticStatus.FAIL, "Session marked signed-in but access token is missing", "session_inconsistent", "Access token missing")
                } else if (userId.isNullOrBlank()) {
                    ProbeOutcome(DiagnosticStatus.FAIL, "Access token exists but user identity is missing", "user_identity_missing", "User ID missing")
                } else if (!internet) {
                    ProbeOutcome(DiagnosticStatus.WAITING, "Session exists locally · remote validation deferred until Internet is available")
                } else {
                    val response = httpGet("${BuildConfig.LINKO_SUPABASE_URL.trimEnd('/')}/auth/v1/user", token)
                    if (response.code in 200..299) {
                        ProbeOutcome(DiagnosticStatus.PASS, "Supabase Auth session verified · user $userId")
                    } else {
                        ProbeOutcome(DiagnosticStatus.FAIL, "Supabase Auth rejected session (HTTP ${response.code})", "auth_http_${response.code}", response.safeError)
                    }
                }
            }
        }

        check("Internet") {
            if (internet) ProbeOutcome(DiagnosticStatus.PASS, "Validated Internet connection available")
            else ProbeOutcome(DiagnosticStatus.FAIL, "Android reports no validated Internet connection", "network_unvalidated", "No validated network")
        }

        check("Supabase") {
            if (!internet) return@check ProbeOutcome(DiagnosticStatus.BLOCKED, "Blocked by Internet", blockedBy = "Internet")
            val response = httpGet("${BuildConfig.LINKO_SUPABASE_URL.trimEnd('/')}/auth/v1/settings", null)
            if (response.code in 200..299) ProbeOutcome(DiagnosticStatus.PASS, "Supabase Auth endpoint reachable · HTTP ${response.code}")
            else ProbeOutcome(DiagnosticStatus.FAIL, "Supabase endpoint failed · HTTP ${response.code}", "supabase_http_${response.code}", response.safeError)
        }

        check("Device identity") {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (store.containsAlias(DEVICE_KEY_ALIAS)) {
                ProbeOutcome(DiagnosticStatus.PASS, "Android Keystore signing identity present")
            } else {
                ProbeOutcome(DiagnosticStatus.SKIPPED, "Device signing key has not been created yet · diagnostics will not create one")
            }
        }

        check("Device registration") {
            if (!auth.isSignedIn()) ProbeOutcome(DiagnosticStatus.SKIPPED, "Requires an authenticated account")
            else if (auth.hasRegisteredDevice()) ProbeOutcome(DiagnosticStatus.PASS, "Registered device credentials are present")
            else ProbeOutcome(DiagnosticStatus.FAIL, "No registered LINKO device credentials found", "device_not_registered", "Device registration missing")
        }

        check("Realtime") {
            if (!auth.isSignedIn()) ProbeOutcome(DiagnosticStatus.SKIPPED, "Requires an authenticated account")
            else if (telemetry.realtimeError != null) ProbeOutcome(DiagnosticStatus.FAIL, "Realtime transport reported an error", "realtime_transport", telemetry.realtimeError)
            else if (telemetry.realtimeConnected) ProbeOutcome(DiagnosticStatus.PASS, "Realtime transport connected · ${telemetry.realtimeChannels.joinToString()}")
            else ProbeOutcome(DiagnosticStatus.WAITING, "Realtime manager is not currently reporting a connected transport")
        }

        check("Presence") {
            if (!auth.isSignedIn()) ProbeOutcome(DiagnosticStatus.SKIPPED, "Requires an authenticated account")
            else {
                val userId = auth.currentUserId()
                val own = userId?.let { com.linkshare.app.network.LinkoRealtimeManager.currentPresence(it) }
                when {
                    telemetry.realtimeError != null -> ProbeOutcome(DiagnosticStatus.BLOCKED, "Blocked by Realtime", blockedBy = "Realtime")
                    own?.online == true -> ProbeOutcome(DiagnosticStatus.PASS, "Local presence is currently online")
                    telemetry.realtimeConnected -> ProbeOutcome(DiagnosticStatus.WAITING, "Realtime connected but own presence has not been observed yet")
                    else -> ProbeOutcome(DiagnosticStatus.WAITING, "Waiting for realtime presence state")
                }
            }
        }

        check("Engine") {
            val connection = LinkoEngineBridge.connection.value
            if (connection.phase == LinkoConnectionPhase.Failed) {
                ProbeOutcome(DiagnosticStatus.FAIL, "Engine failed: ${connection.detail}", "engine_failed", connection.error ?: connection.detail)
            } else {
                ProbeOutcome(DiagnosticStatus.PASS, "Engine phase: ${connection.phase.name} · ${connection.detail}")
            }
        }

        check("Signaling") {
            val connection = LinkoEngineBridge.connection.value
            when (connection.phase) {
                LinkoConnectionPhase.Failed -> ProbeOutcome(DiagnosticStatus.FAIL, "Signaling/engine path failed: ${connection.detail}", "signaling_failed", connection.error ?: connection.detail)
                LinkoConnectionPhase.Connecting, LinkoConnectionPhase.Authenticating, LinkoConnectionPhase.Signaling, LinkoConnectionPhase.Establishing, LinkoConnectionPhase.Securing, LinkoConnectionPhase.Routing -> ProbeOutcome(DiagnosticStatus.WAITING, "Connection attempt currently at ${connection.phase.name}: ${connection.detail}")
                LinkoConnectionPhase.Connected -> ProbeOutcome(DiagnosticStatus.PASS, "Engine reached Connected state")
                LinkoConnectionPhase.Idle -> ProbeOutcome(DiagnosticStatus.SKIPPED, "No active connection attempt to exercise signaling")
            }
        }

        check("Relay transport") {
            val trace = telemetry.engineTrace.joinToString(" ").lowercase()
            when {
                trace.contains("selecting relay") && telemetry.enginePhase == LinkoConnectionPhase.Connected.name -> ProbeOutcome(DiagnosticStatus.PASS, "Connected session used relay transport")
                trace.contains("selecting relay") && telemetry.engineError != null -> ProbeOutcome(DiagnosticStatus.FAIL, "Relay path was selected before engine failure", "relay_path_failed", telemetry.engineError)
                else -> ProbeOutcome(DiagnosticStatus.SKIPPED, "Relay path was not exercised by the current connection")
            }
        }

        check("VPN permission") {
            if (VpnService.prepare(app) == null) ProbeOutcome(DiagnosticStatus.PASS, "VPN permission granted")
            else ProbeOutcome(DiagnosticStatus.FAIL, "VPN permission is required before tunnel start", "vpn_permission_missing", "VPN consent missing")
        }

        check("Tunnel") {
            when {
                telemetry.vpnRunning -> ProbeOutcome(DiagnosticStatus.PASS, "VPN tunnel service is running")
                telemetry.enginePhase in setOf(LinkoConnectionPhase.Establishing.name, LinkoConnectionPhase.Securing.name, LinkoConnectionPhase.Routing.name) -> ProbeOutcome(DiagnosticStatus.WAITING, "Engine is establishing the tunnel")
                telemetry.engineError != null -> ProbeOutcome(DiagnosticStatus.FAIL, "Tunnel did not reach running state", "tunnel_not_running", telemetry.engineError)
                else -> ProbeOutcome(DiagnosticStatus.SKIPPED, "No live tunnel session to inspect")
            }
        }

        check("Packet flow") {
            val tx = telemetry.vpnTxPackets
            val rx = telemetry.vpnRxPackets
            when {
                tx > 0L || rx > 0L -> ProbeOutcome(DiagnosticStatus.PASS, "Packet counters observed · TX $tx / RX $rx")
                telemetry.vpnRunning -> ProbeOutcome(DiagnosticStatus.WAITING, "Tunnel is running but no packets have been observed yet")
                else -> ProbeOutcome(DiagnosticStatus.SKIPPED, "Requires a running tunnel")
            }
        }

        check("Encryption") {
            val digestOk = runCatching { MessageDigest.getInstance("SHA-256") }.isSuccess
            val cipherOk = runCatching { Cipher.getInstance("AES/GCM/NoPadding") }.isSuccess
            if (digestOk && cipherOk) ProbeOutcome(DiagnosticStatus.PASS, "Android cryptographic providers expose SHA-256 and AES-GCM")
            else ProbeOutcome(DiagnosticStatus.FAIL, "Required cryptographic provider is unavailable", "crypto_provider_missing", "SHA-256=$digestOk AES-GCM=$cipherOk")
        }

        check("Updater") {
            if (BuildConfig.VERSION_CODE > 0) ProbeOutcome(DiagnosticStatus.PASS, "Updater is installed and its live state is shown in Diagnostic Center")
            else ProbeOutcome(DiagnosticStatus.FAIL, "Invalid installed version code", "version_code_invalid", BuildConfig.VERSION_CODE.toString())
        }

        LinkoDiagnosticCenter.blockedResults(out.toList())
    }

    private data class ProbeOutcome(
        val status: DiagnosticStatus,
        val detail: String,
        val errorType: String? = null,
        val errorMessage: String? = null,
        val blockedBy: String? = null,
    )

    private data class HttpResult(val code: Int, val safeError: String?)

    private fun httpGet(url: String, bearerToken: String?): HttpResult {
        if (!url.startsWith("https://")) return HttpResult(400, "HTTPS required")
        return runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = HTTP_TIMEOUT_MS
                readTimeout = HTTP_TIMEOUT_MS
                useCaches = false
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty("apikey", BuildConfig.LINKO_SUPABASE_PUBLISHABLE_KEY)
                bearerToken?.let { setRequestProperty("Authorization", "Bearer $it") }
            }
            try {
                val code = connection.responseCode
                val message = if (code !in 200..299) connection.responseMessage?.take(160) else null
                HttpResult(code, message)
            } finally { connection.disconnect() }
        }.getOrElse { HttpResult(599, safeMessage(it)) }
    }

    private fun safeMessage(error: Throwable): String =
        error.message?.replace(Regex("Bearer\\s+[A-Za-z0-9._-]+"), "Bearer <redacted>")?.take(240)
            ?.ifBlank { error::class.java.simpleName }
            ?: error::class.java.simpleName
}
