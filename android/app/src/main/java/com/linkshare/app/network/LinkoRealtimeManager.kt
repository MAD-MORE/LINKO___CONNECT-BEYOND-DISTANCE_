package com.linkshare.app.network

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.linkshare.app.MainActivity
import com.linkshare.app.R
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.provider.LinkoProviderService
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.presenceDataFlow
import io.github.jan.supabase.realtime.track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object LinkoRealtimeManager {
    private const val TAG = "LinkoRealtimeManager"
    private const val FRIEND_CHANNEL = "linko-friend-events"
    private const val PRESENCE_CHANNEL = "linko-presence"
    private const val SESSION_CHANNEL = "linko-session-events"
    private const val NOTIFICATION_CHANNEL = "linko_realtime_v2"
    private const val NOTIFICATION_ID = 9201

    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _events = MutableSharedFlow<LinkoRealtimeEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<LinkoRealtimeEvent> = _events.asSharedFlow()
    private val presenceSnapshot = ConcurrentHashMap<String, LinkoPresence>()
    @Volatile private var foreground = false
    private var client: SupabaseClient? = null
    private var friendChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null
    private var sessionChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null
    private var presenceChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null
    private var auth: LinkoAuth? = null
    private var appContext: Context? = null

    fun currentPresence(userId: String): LinkoPresence? = presenceSnapshot[userId]
    fun currentPresenceSnapshot(): Map<String, LinkoPresence> = presenceSnapshot.toMap()

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        appContext = context.applicationContext
        auth = LinkoAuth(appContext!!)
        runCatching { ensureNotificationChannel() }

        scope.launch {
            var backoffMs = 1_000L
            while (started.get() && isActive) {
                try {
                    // Wait for valid access token
                    var token = auth?.currentAccessToken()
                    while (started.get() && token.isNullOrBlank()) {
                        delay(1_000L)
                        token = auth?.currentAccessToken()
                    }
                    if (!started.get() || !isActive) break

                    Log.i(TAG, "Initializing Supabase Realtime client...")
                    val supabase = createSupabaseClient(
                        supabaseUrl = com.linkshare.app.BuildConfig.LINKO_SUPABASE_URL,
                        supabaseKey = com.linkshare.app.BuildConfig.LINKO_SUPABASE_PUBLISHABLE_KEY
                    ) { install(Realtime) }
                    client = supabase

                    val rt = supabase.pluginManager.getPlugin(Realtime)
                    rt.setAuth(token)
                    rt.connect()

                    subscribeFriendEvents(supabase)
                    subscribeSessionEvents(supabase)
                    subscribePresence(supabase)

                    Log.i(TAG, "Supabase Realtime connected and listening across all channels")
                    backoffMs = 1_000L // Reset backoff on success

                    // Stay connected until stop or token changes
                    while (started.get() && isActive) {
                        delay(15_000L)
                        val currentToken = auth?.currentAccessToken()
                        if (currentToken != token && !currentToken.isNullOrBlank()) {
                            Log.i(TAG, "Access token refreshed — updating realtime auth")
                            token = currentToken
                            runCatching { rt.setAuth(currentToken) }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Realtime connection error: ${e.message}, reconnecting in ${backoffMs}ms", e)
                    _events.tryEmit(LinkoRealtimeEvent.TransportError(e.message ?: "realtime_disconnected"))
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
                }
            }
        }
    }

    fun setForeground(isForeground: Boolean) { foreground = isForeground }

    fun stop() {
        if (!started.compareAndSet(true, false)) return
        scope.launch {
            runCatching {
                val realtime = client?.pluginManager?.getPlugin(Realtime)
                presenceChannel?.let { realtime?.removeChannel(it) }
                friendChannel?.let { realtime?.removeChannel(it) }
                sessionChannel?.let { realtime?.removeChannel(it) }
                realtime?.disconnect()
            }
            presenceChannel = null
            friendChannel = null
            sessionChannel = null
            client = null
            presenceSnapshot.clear()
        }
    }

    private suspend fun subscribeFriendEvents(supabase: SupabaseClient) {
        val channel = supabase.channel(FRIEND_CHANNEL)
        friendChannel = channel
        val flow = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "friend_requests" }
        scope.launch {
            runCatching { flow.collect { action -> handleFriendChange(action) } }
                .onFailure {
                    Log.w(TAG, "Friend change flow error: ${it.message}")
                    _events.tryEmit(LinkoRealtimeEvent.TransportError(it.message ?: "friend_realtime_error"))
                }
        }
        channel.subscribe(blockUntilSubscribed = true)
    }

    private suspend fun subscribeSessionEvents(supabase: SupabaseClient) {
        val channel = supabase.channel(SESSION_CHANNEL)
        sessionChannel = channel
        val flow = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "sessions" }
        scope.launch {
            runCatching {
                flow.collect { action -> handleSessionChange(action) }
            }.onFailure {
                Log.w(TAG, "Session change flow error: ${it.message}")
                _events.tryEmit(LinkoRealtimeEvent.TransportError(it.message ?: "session_realtime_error"))
            }
        }
        channel.subscribe(blockUntilSubscribed = true)
    }

    private fun handleSessionChange(action: PostgresAction) {
        val record = recordToJson(action) ?: return
        val sessionId = record.optString("id").takeIf { it.isNotBlank() } ?: return
        val state = record.optString("state").takeIf { it.isNotBlank() } ?: return
        val receiverDeviceId = record.optString("receiver_device_id")
        val providerDeviceId = record.optString("provider_device_id")
        val myDeviceId = auth?.currentDeviceId().orEmpty()

        _events.tryEmit(LinkoRealtimeEvent.SessionStateChanged(sessionId, state))

        when (state) {
            "requested" -> {
                // If I am the provider or provider is this device, alert incoming request
                if (providerDeviceId.isBlank() || providerDeviceId == myDeviceId) {
                    val event = LinkoRealtimeEvent.IncomingConnectionRequest(sessionId)
                    _events.tryEmit(event)
                    if (!foreground) {
                        postConnectionRequestNotification(sessionId, receiverDeviceId)
                    }
                }
            }
            "approved" -> {
                // If I am the receiver, alert that provider approved
                if (receiverDeviceId == myDeviceId && !foreground) {
                    postNotification("⚡ Connection Request Approved", "Your friend approved the connection. Secure tunnel is starting.")
                }
            }
            "denied" -> {
                if (receiverDeviceId == myDeviceId && !foreground) {
                    postNotification("Connection Request Declined", "Your friend declined the connection request.")
                }
            }
        }
    }

    private suspend fun subscribePresence(supabase: SupabaseClient) {
        val channel = supabase.channel(PRESENCE_CHANNEL)
        presenceChannel = channel
        val flow = channel.presenceDataFlow<LinkoPresence>()
        scope.launch {
            runCatching {
                flow.collect { states ->
                    states.forEach { presence ->
                        presenceSnapshot[presence.userId] = presence
                        _events.tryEmit(LinkoRealtimeEvent.PresenceChanged(presence))
                    }
                }
            }.onFailure { _events.tryEmit(LinkoRealtimeEvent.TransportError(it.message ?: "presence_realtime_error")) }
        }
        channel.subscribe(blockUntilSubscribed = true)
        val currentUserId = auth?.currentAccessToken()?.let(::tokenSubject)
        val deviceId = auth?.currentDeviceId().orEmpty()
        if (!currentUserId.isNullOrBlank()) {
            val ownPresence = LinkoPresence(currentUserId, deviceId, "online", true)
            presenceSnapshot[currentUserId] = ownPresence
            runCatching { channel.track(ownPresence) }
                .onFailure { _events.tryEmit(LinkoRealtimeEvent.TransportError(it.message ?: "presence_track_failed")) }
            _events.tryEmit(LinkoRealtimeEvent.PresenceChanged(ownPresence))
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
            is PostgresAction.Delete -> if (senderId == userId || receiverId == userId) notify(LinkoRealtimeEvent.FriendRemoved(id))
        }
    }

    private fun notify(event: LinkoRealtimeEvent) {
        _events.tryEmit(event)
        if (foreground) return
        val title = when (event) {
            is LinkoRealtimeEvent.IncomingConnectionRequest -> "⚡ Incoming LINKO Connection Request"
            is LinkoRealtimeEvent.FriendRequestReceived -> "New LINKO Friend Request"
            is LinkoRealtimeEvent.FriendRequestAccepted -> "LINKO Friend Request Accepted"
            is LinkoRealtimeEvent.FriendRequestDeclined -> "LINKO Friend Request Declined"
            is LinkoRealtimeEvent.FriendRemoved -> "LINKO Friend Removed"
            else -> return
        }
        val text = when (event) {
            is LinkoRealtimeEvent.IncomingConnectionRequest -> "A friend is requesting to share your internet connection. Tap to review."
            is LinkoRealtimeEvent.FriendRequestReceived -> "Open LINKO to accept or decline the friend request."
            is LinkoRealtimeEvent.FriendRequestAccepted -> "You are now connected friends on LINKO."
            is LinkoRealtimeEvent.FriendRequestDeclined -> "The friend request was declined."
            is LinkoRealtimeEvent.FriendRemoved -> "Your friend list was updated."
            else -> return
        }
        postNotification(title, text)
    }

    private fun postConnectionRequestNotification(sessionId: String, receiverDeviceId: String, requesterName: String? = null) {
        val context = appContext ?: return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Action: AUTHORIZE & SHARE
        val acceptIntent = Intent(context, LinkoProviderService::class.java).apply {
            action = LinkoProviderService.ACTION_ACCEPT
            putExtra(LinkoProviderService.EXTRA_REQUEST_ID, sessionId)
        }
        val acceptPending = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                context,
                sessionId.hashCode(),
                acceptIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                context,
                sessionId.hashCode(),
                acceptIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        // Action: DECLINE
        val declineIntent = Intent(context, LinkoProviderService::class.java).apply {
            action = LinkoProviderService.ACTION_DECLINE
            putExtra(LinkoProviderService.EXTRA_REQUEST_ID, sessionId)
        }
        val declinePending = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                context,
                sessionId.hashCode() + 1,
                declineIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                context,
                sessionId.hashCode() + 1,
                declineIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        // Content tap: Open App & Navigate to request
        val openIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("EXTRA_REQUEST_ID", sessionId)
        }
        val openPending = PendingIntent.getActivity(
            context,
            sessionId.hashCode() + 2,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displayName = requesterName ?: "A verified friend"

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("⚡ Connection Request")
            .setContentText("$displayName wants to use your internet")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$displayName is requesting permission to share your internet connection."))
            .setContentIntent(openPending)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setAutoCancel(true)
            .addAction(R.drawable.ic_launcher, "AUTHORIZE & SHARE", acceptPending)
            .addAction(R.drawable.ic_launcher, "DECLINE", declinePending)
            .build()

        manager.notify(sessionId.hashCode(), notification)
    }

    private fun postNotification(title: String, text: String) {
        val context = appContext ?: return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, NOTIFICATION_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureNotificationChannel() {
        val context = appContext ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL,
            "LINKO Realtime Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Instant alerts for friend requests and connection sharing"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 200, 300)
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                .build()
            setSound(soundUri, audioAttributes)
        }
        manager.createNotificationChannel(channel)
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
data class LinkoPresence(val userId: String, val deviceId: String, val state: String, val online: Boolean)

sealed interface LinkoRealtimeEvent {
    data class IncomingConnectionRequest(val sessionId: String, val peerName: String? = null) : LinkoRealtimeEvent
    data class FriendRequestReceived(val requestId: String) : LinkoRealtimeEvent
    data class FriendRequestSent(val requestId: String) : LinkoRealtimeEvent
    data class FriendRequestAccepted(val requestId: String) : LinkoRealtimeEvent
    data class FriendRequestDeclined(val requestId: String) : LinkoRealtimeEvent
    data class FriendRemoved(val requestId: String) : LinkoRealtimeEvent
    data class SessionStateChanged(val sessionId: String?, val state: String?) : LinkoRealtimeEvent
    data class PresenceChanged(val presence: LinkoPresence) : LinkoRealtimeEvent
    data class TransportError(val message: String) : LinkoRealtimeEvent
}
