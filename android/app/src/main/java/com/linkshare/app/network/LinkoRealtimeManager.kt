package com.linkshare.app.network

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.linkshare.app.MainActivity
import com.linkshare.app.R
import com.linkshare.app.auth.LinkoAuth
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.presenceDataFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

object LinkoRealtimeManager {
    private const val FRIEND_CHANNEL = "linko-friend-events"
    private const val PRESENCE_CHANNEL = "linko-presence"
    private const val SESSION_CHANNEL = "linko-session-events"
    private const val NOTIFICATION_CHANNEL = "linko_realtime"
    private const val NOTIFICATION_ID = 9201

    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _events = MutableSharedFlow<LinkoRealtimeEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<LinkoRealtimeEvent> = _events.asSharedFlow()
    @Volatile private var foreground = false
    private var client: SupabaseClient? = null
    private var friendChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null
    private var sessionChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null
    private var presenceChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null
    private var auth: LinkoAuth? = null
    private var appContext: Context? = null

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        appContext = context.applicationContext
        auth = LinkoAuth(appContext!!)
        ensureNotificationChannel()
        val supabase = createSupabaseClient(
            supabaseUrl = com.linkshare.app.BuildConfig.LINKO_SUPABASE_URL,
            supabaseKey = com.linkshare.app.BuildConfig.LINKO_SUPABASE_PUBLISHABLE_KEY
        ) {
            install(Realtime)
        }
        supabase.realtime.setAuth(auth?.currentAccessToken())
        client = supabase
        scope.launch {
            while (started.get() && auth?.currentAccessToken().isNullOrBlank()) delay(1_000L)
            if (!started.get()) return@launch
            runCatching {
                supabase.realtime.connect()
                subscribeFriendEvents(supabase)
                subscribeSessionEvents(supabase)
                subscribePresence(supabase)
            }.onFailure {
                _events.tryEmit(LinkoRealtimeEvent.TransportError(it.message ?: "realtime_start_failed"))
                started.set(false)
            }
        }
    }

    fun setForeground(isForeground: Boolean) {
        foreground = isForeground
    }

    fun stop() {
        if (!started.compareAndSet(true, false)) return
        scope.launch {
            runCatching {
                val realtime = client?.realtime
                presenceChannel?.let { realtime?.removeChannel(it) }
                friendChannel?.let { realtime?.removeChannel(it) }
                sessionChannel?.let { realtime?.removeChannel(it) }
                realtime?.disconnect()
            }
            presenceChannel = null
            friendChannel = null
            sessionChannel = null
            client = null
        }
    }

    private suspend fun subscribeFriendEvents(supabase: SupabaseClient) {
        val channel = supabase.channel(FRIEND_CHANNEL)
        friendChannel = channel
        channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "friend_requests"
        }
            .onEach(::handleFriendChange)
            .catch { _events.tryEmit(LinkoRealtimeEvent.TransportError(it.message ?: "friend_realtime_error")) }
            .launchIn(scope)
        channel.subscribe(blockUntilSubscribed = true)
    }

    private suspend fun subscribeSessionEvents(supabase: SupabaseClient) {
        val channel = supabase.channel(SESSION_CHANNEL)
        sessionChannel = channel
        channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "sessions"
        }
            .onEach { action ->
                val record = recordToJson(action)
                _events.tryEmit(
                    LinkoRealtimeEvent.SessionStateChanged(
                        record?.optString("id")?.takeIf { it.isNotBlank() },
                        record?.optString("state")?.takeIf { it.isNotBlank() },
                    )
                )
            }
            .catch { _events.tryEmit(LinkoRealtimeEvent.TransportError(it.message ?: "session_realtime_error")) }
            .launchIn(scope)
        channel.subscribe(blockUntilSubscribed = true)
    }

    private suspend fun subscribePresence(supabase: SupabaseClient) {
        val channel = supabase.channel(PRESENCE_CHANNEL)
        presenceChannel = channel
        channel.presenceDataFlow<LinkoPresence>()
            .onEach { states ->
                states.forEach { presence ->
                    _events.tryEmit(LinkoRealtimeEvent.PresenceChanged(presence))
                }
            }
            .catch { _events.tryEmit(LinkoRealtimeEvent.TransportError(it.message ?: "presence_realtime_error")) }
            .launchIn(scope)
        channel.subscribe(blockUntilSubscribed = true)
        val currentUserId = auth?.currentAccessToken()?.let(::tokenSubject)
        val deviceId = auth?.currentDeviceId().orEmpty()
        if (!currentUserId.isNullOrBlank()) {
            channel.track(LinkoPresence(currentUserId, deviceId, "online", true))
        }
    }

    private fun handleFriendChange(action: PostgresAction) {
        val record = recordToJson(action) ?: return
        val id = record.optString("id")
        val senderId = record.optString("sender_id")
        val receiverId = record.optString("receiver_id")
        val status = record.optString("status")
        val userId = tokenSubject(auth?.currentAccessToken().orEmpty()) ?: return

        when (action) {
            is PostgresAction.Insert -> {
                if (receiverId == userId) notify(LinkoRealtimeEvent.FriendRequestReceived(id))
                if (senderId == userId) _events.tryEmit(LinkoRealtimeEvent.FriendRequestSent(id))
            }
            is PostgresAction.Update, is PostgresAction.Select -> {
                if (senderId != userId && receiverId != userId) return
                when (status) {
                    "accepted" -> notify(LinkoRealtimeEvent.FriendRequestAccepted(id))
                    "declined" -> notify(LinkoRealtimeEvent.FriendRequestDeclined(id))
                    "pending" -> if (receiverId == userId) notify(LinkoRealtimeEvent.FriendRequestReceived(id))
                }
            }
            is PostgresAction.Delete -> {
                if (senderId == userId || receiverId == userId) notify(LinkoRealtimeEvent.FriendRemoved(id))
            }
        }
    }

    private fun notify(event: LinkoRealtimeEvent) {
        _events.tryEmit(event)
        if (foreground) return
        val title = when (event) {
            is LinkoRealtimeEvent.FriendRequestReceived -> "New LINKO friend request"
            is LinkoRealtimeEvent.FriendRequestAccepted -> "LINKO friend request accepted"
            is LinkoRealtimeEvent.FriendRequestDeclined -> "LINKO friend request declined"
            is LinkoRealtimeEvent.FriendRemoved -> "LINKO friend removed"
            else -> return
        }
        val text = when (event) {
            is LinkoRealtimeEvent.FriendRequestReceived -> "Open LINKO to accept or decline the request."
            is LinkoRealtimeEvent.FriendRequestAccepted -> "You are now friends."
            is LinkoRealtimeEvent.FriendRequestDeclined -> "The request was declined."
            is LinkoRealtimeEvent.FriendRemoved -> "Your friendship list changed."
            else -> return
        }
        postNotification(title, text)
    }

    private fun postNotification(title: String, text: String) {
        val context = appContext ?: return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build(),
        )
    }

    private fun ensureNotificationChannel() {
        val context = appContext ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL,
                "LINKO Realtime",
                NotificationManager.IMPORTANCE_HIGH,
            )
        )
    }

    private fun recordToJson(action: PostgresAction): JSONObject? = runCatching {
        when (action) {
            is PostgresAction.Delete -> JSONObject(action.oldRecord.toString())
            is PostgresAction.Insert -> JSONObject(action.record.toString())
            is PostgresAction.Update -> JSONObject(action.record.toString())
            is PostgresAction.Select -> JSONObject(action.record.toString())
        }
    }.getOrNull()

    private fun tokenSubject(token: String): String? {
        return runCatching {
            val parts = token.split('.')
            if (parts.size < 2) return@runCatching null
            val raw = parts[1].let { it + "=".repeat((4 - it.length % 4) % 4) }
            val payload = String(java.util.Base64.getUrlDecoder().decode(raw))
            JSONObject(payload).optString("sub").takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}

@Serializable
data class LinkoPresence(
    val userId: String,
    val deviceId: String,
    val state: String,
    val online: Boolean,
)

sealed interface LinkoRealtimeEvent {
    data class FriendRequestReceived(val requestId: String) : LinkoRealtimeEvent
    data class FriendRequestSent(val requestId: String) : LinkoRealtimeEvent
    data class FriendRequestAccepted(val requestId: String) : LinkoRealtimeEvent
    data class FriendRequestDeclined(val requestId: String) : LinkoRealtimeEvent
    data class FriendRemoved(val requestId: String) : LinkoRealtimeEvent
    data class SessionStateChanged(val sessionId: String?, val state: String?) : LinkoRealtimeEvent
    data class PresenceChanged(val presence: LinkoPresence) : LinkoRealtimeEvent
    data class TransportError(val message: String) : LinkoRealtimeEvent
}
