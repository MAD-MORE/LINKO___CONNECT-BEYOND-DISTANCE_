package com.linkshare.app.network

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.max

/**
 * Privacy-first, device-local session history. It observes the same engine state used by
 * the connection UI, so history reflects real LINKO sessions rather than UI-only events.
 */
object LinkoSessionHistoryStore {
    private const val PREFS = "linko_session_history"
    private const val KEY = "entries"
    private const val MAX_ENTRIES = 100

    private val _entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false
    private var prefs: android.content.SharedPreferences? = null
    private var active: HistoryEntry? = null
    private var lastPhase: LinkoConnectionPhase = LinkoConnectionPhase.Idle

    data class HistoryEntry(
        val id: String,
        val sessionId: String,
        val peerDisplayName: String,
        val peerLinkoId: String?,
        val role: String,
        val status: String,
        val startedAt: Long,
        val endedAt: Long?,
        val bytesIn: Long,
        val bytesOut: Long,
        val reason: String?
    ) {
        val totalBytes: Long get() = max(0L, bytesIn) + max(0L, bytesOut)
        val durationMs: Long get() = max(0L, (endedAt ?: System.currentTimeMillis()) - startedAt)
    }

    fun start(context: Context) {
        synchronized(lock) {
            if (started) return
            started = true
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val loaded = load()
            val now = System.currentTimeMillis()
            val recovered = loaded.map { entry ->
                if (entry.endedAt == null) entry.copy(status = "Interrupted", endedAt = now, reason = "App restarted while the session was active") else entry
            }
            _entries.value = recovered
            persist(recovered)
        }
        scope.launch {
            LinkoEngineBridge.connection.collect { state ->
                handleState(state)
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            active = null
            _entries.value = emptyList()
            prefs?.edit()?.remove(KEY)?.apply()
        }
    }

    private fun handleState(state: LinkoEngineConnectionState) {
        synchronized(lock) {
            when (state.phase) {
                LinkoConnectionPhase.Idle -> {
                    if (active != null && lastPhase != LinkoConnectionPhase.Idle) {
                        val ended = finalizeActive(state, statusFromDetail(state.detail, "Stopped"), state.detail)
                        active = null
                        persist(ended)
                    }
                }
                LinkoConnectionPhase.Failed -> {
                    if (active != null) {
                        val ended = finalizeActive(state, statusFromDetail(state.error ?: state.detail, "Failed"), state.error ?: state.detail)
                        active = null
                        persist(ended)
                    }
                }
                else -> {
                    if (active == null) {
                        active = HistoryEntry(
                            id = UUID.randomUUID().toString(),
                            sessionId = state.sessionId.orEmpty().ifBlank { "local-${UUID.randomUUID()}" },
                            peerDisplayName = state.peerDisplayName?.takeIf { it.isNotBlank() } ?: "LINKO Friend",
                            peerLinkoId = state.peerLinkoId?.takeIf { it.isNotBlank() },
                            role = if (state.isProvider) "Provider" else "Receiver",
                            status = statusFromPhase(state.phase),
                            startedAt = System.currentTimeMillis(),
                            endedAt = null,
                            bytesIn = state.bytesIn.coerceAtLeast(0L),
                            bytesOut = state.bytesOut.coerceAtLeast(0L),
                            reason = null
                        )
                        persist(upsertActive(_entries.value, active!!))
                    } else {
                        val current = active!!
                        active = current.copy(
                            sessionId = state.sessionId?.takeIf { it.isNotBlank() } ?: current.sessionId,
                            peerDisplayName = state.peerDisplayName?.takeIf { it.isNotBlank() } ?: current.peerDisplayName,
                            peerLinkoId = state.peerLinkoId?.takeIf { it.isNotBlank() } ?: current.peerLinkoId,
                            role = if (state.isProvider) "Provider" else current.role,
                            status = statusFromPhase(state.phase),
                            bytesIn = max(current.bytesIn, state.bytesIn.coerceAtLeast(0L)),
                            bytesOut = max(current.bytesOut, state.bytesOut.coerceAtLeast(0L))
                        )
                        persist(upsertActive(_entries.value, active!!))
                    }
                }
            }
            lastPhase = state.phase
        }
    }

    private fun finalizeActive(state: LinkoEngineConnectionState, status: String, reason: String?): List<HistoryEntry> {
        val current = active ?: return _entries.value
        val finished = current.copy(
            sessionId = state.sessionId?.takeIf { it.isNotBlank() } ?: current.sessionId,
            peerDisplayName = state.peerDisplayName?.takeIf { it.isNotBlank() } ?: current.peerDisplayName,
            peerLinkoId = state.peerLinkoId?.takeIf { it.isNotBlank() } ?: current.peerLinkoId,
            bytesIn = max(current.bytesIn, state.bytesIn.coerceAtLeast(0L)),
            bytesOut = max(current.bytesOut, state.bytesOut.coerceAtLeast(0L)),
            status = status,
            endedAt = System.currentTimeMillis(),
            reason = reason?.takeIf { it.isNotBlank() }
        )
        return upsertFinished(_entries.value, finished)
    }

    private fun upsertActive(existing: List<HistoryEntry>, item: HistoryEntry): List<HistoryEntry> {
        val without = existing.filterNot { it.id == item.id }
        return listOf(item) + without.take(MAX_ENTRIES - 1)
    }

    private fun upsertFinished(existing: List<HistoryEntry>, item: HistoryEntry): List<HistoryEntry> {
        val without = existing.filterNot { it.id == item.id }
        return listOf(item) + without.take(MAX_ENTRIES - 1)
    }

    private fun statusFromPhase(phase: LinkoConnectionPhase): String = when (phase) {
        LinkoConnectionPhase.Connected -> "Connected"
        LinkoConnectionPhase.Connecting,
        LinkoConnectionPhase.Authenticating,
        LinkoConnectionPhase.Signaling,
        LinkoConnectionPhase.Establishing,
        LinkoConnectionPhase.Securing,
        LinkoConnectionPhase.Routing -> "Connecting"
        LinkoConnectionPhase.Failed -> "Failed"
        LinkoConnectionPhase.Idle -> "Stopped"
    }

    private fun statusFromDetail(detail: String?, fallback: String): String {
        val value = detail.orEmpty().lowercase()
        return when {
            value.contains("deny") -> "Declined"
            value.contains("expire") -> "Expired"
            value.contains("revoke") -> "Revoked"
            value.contains("disconnect") || value.contains("interrupted") -> "Disconnected"
            value.contains("stop") -> "Stopped"
            value.contains("connect") && !value.contains("connecting") && fallback == "Stopped" -> "Disconnected"
            else -> fallback
        }
    }

    private fun load(): List<HistoryEntry> {
        val raw = prefs?.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    add(
                        HistoryEntry(
                            id = o.optString("id"),
                            sessionId = o.optString("sessionId"),
                            peerDisplayName = o.optString("peerDisplayName", "LINKO Friend"),
                            peerLinkoId = o.optString("peerLinkoId").takeIf { it.isNotBlank() },
                            role = o.optString("role", "Receiver"),
                            status = o.optString("status", "Ended"),
                            startedAt = o.optLong("startedAt", 0L),
                            endedAt = if (o.has("endedAt") && !o.isNull("endedAt")) o.optLong("endedAt") else null,
                            bytesIn = o.optLong("bytesIn", 0L),
                            bytesOut = o.optLong("bytesOut", 0L),
                            reason = o.optString("reason").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }.filter { it.id.isNotBlank() && it.startedAt > 0L }
        }.getOrDefault(emptyList())
    }

    private fun persist(entries: List<HistoryEntry>) {
        _entries.value = entries.take(MAX_ENTRIES)
        val array = JSONArray()
        _entries.value.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("sessionId", entry.sessionId)
                    put("peerDisplayName", entry.peerDisplayName)
                    put("peerLinkoId", entry.peerLinkoId)
                    put("role", entry.role)
                    put("status", entry.status)
                    put("startedAt", entry.startedAt)
                    if (entry.endedAt == null) put("endedAt", JSONObject.NULL) else put("endedAt", entry.endedAt)
                    put("bytesIn", entry.bytesIn)
                    put("bytesOut", entry.bytesOut)
                    put("reason", entry.reason)
                }
            )
        }
        prefs?.edit()?.putString(KEY, array.toString())?.apply()
    }
}
