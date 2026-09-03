package com.linkshare.app.network

import android.app.NotificationChannel
import android.app.NotificationManager
import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.linkshare.app.MainActivity
import com.linkshare.app.R
import com.linkshare.app.auth.LinkoAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Single source of truth for LINKO in-app request/activity notifications.
 * It is backed by the real realtime event stream and keeps notification state
 * alive while the process is running, so navigating away from Notifications
 * does not drop events.
 */
data class LinkoNotification(
    val id: String,
    val title: String,
    val message: String,
    val kind: Kind,
    val requestId: String? = null,
    val actorUserId: String? = null,
    val actorName: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    enum class Kind {
        FRIEND_REQUEST_INCOMING,
        FRIEND_REQUEST_SENT,
        FRIEND_ACCEPTED,
        FRIEND_DECLINED,
        FRIEND_REMOVED,
        CONNECTION,
        FRIEND_ONLINE,
        FRIEND_OFFLINE,
        CONNECTION_CONNECTED,
        CONNECTION_FAILED,
        REALTIME_ERROR,
    }
}

object LinkoNotificationCenter {
    private const val CHANNEL_ID = "linko_request_alerts_v3"
    private const val CHANNEL_NAME = "LINKO Requests"
    private const val BASE_NOTIFICATION_ID = 31_000
    private const val DIAGNOSTIC_NOTIFICATION_ID = BASE_NOTIFICATION_ID + 70
    private const val MAX_ITEMS = 100

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _notifications = MutableStateFlow<List<LinkoNotification>>(emptyList())
    private val knownFriends = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val lastPresence = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    val notifications: StateFlow<List<LinkoNotification>> = _notifications.asStateFlow()

    @Volatile private var started = false
    private var appContext: Context? = null

    fun start(context: Context) {
        if (started) return
        synchronized(this) {
            if (started) return
            started = true
            appContext = context.applicationContext
            ensureChannel()
            scope.launch {
                refreshFriendCache()
                LinkoRealtimeManager.events.collect { event -> handle(event) }
            }
            scope.launch {
                LinkoConnectionDiagnostics.snapshot.collect { snapshot ->
                    if (snapshot.headline != "Ready" || snapshot.stage != ConnectionStage.REQUESTING) {
                        postDiagnosticsNotification(snapshot)
                    }
                }
            }
        }
    }

    fun add(item: LinkoNotification) {
        val current = _notifications.value
        if (current.any { it.id == item.id }) return
        _notifications.value = (listOf(item) + current).take(MAX_ITEMS)
    }

    fun remove(id: String) {
        _notifications.value = _notifications.value.filterNot { it.id == id }
    }

    fun clear() {
        _notifications.value = emptyList()
    }

    private suspend fun handle(event: LinkoRealtimeEvent) {
        when (event) {
            is LinkoRealtimeEvent.FriendRequestReceived -> {
                val details = findRequest(event.requestId)
                val profile = details?.optJSONObject("profile")
                val name = event.senderName?.takeIf { it.isNotBlank() }
                    ?: profile?.optString("display_name")?.takeIf { it.isNotBlank() }
                    ?: "A LINKO user"
                val actorUserId = profile?.optString("user_id")?.takeIf { it.isNotBlank() }
                add(LinkoNotification("friend-request:${event.requestId}", "New Friend Request", "$name wants to be your LINKO friend.", LinkoNotification.Kind.FRIEND_REQUEST_INCOMING, event.requestId, actorUserId, name))
                postFriendRequestNotification(event.requestId, name)
            }
            is LinkoRealtimeEvent.FriendRequestSent -> add(LinkoNotification("friend-sent:${event.requestId}", "Friend Request Sent", "Your LINKO friend request was sent and is waiting for a response.", LinkoNotification.Kind.FRIEND_REQUEST_SENT, event.requestId))
            is LinkoRealtimeEvent.FriendRequestAccepted -> {
                add(LinkoNotification("friend-accepted:${event.requestId}", "Friend Request Accepted", "Your LINKO friend request was accepted. You can now connect and share internet.", LinkoNotification.Kind.FRIEND_ACCEPTED, event.requestId))
                postSimpleNotification("Friend Request Accepted", "Your LINKO friend request was accepted.")
                refreshFriendCache()
            }
            is LinkoRealtimeEvent.FriendRequestDeclined -> {
                add(LinkoNotification("friend-declined:${event.requestId}", "Friend Request Declined", "Your LINKO friend request was declined.", LinkoNotification.Kind.FRIEND_DECLINED, event.requestId))
                postSimpleNotification("Friend Request Declined", "Your LINKO friend request was declined.")
            }
            is LinkoRealtimeEvent.PresenceChanged -> handlePresence(event.presence)
            is LinkoRealtimeEvent.FriendRemoved -> {
                add(LinkoNotification("friend-removed:${event.requestId}", "Friend Removed", "A LINKO friendship was removed.", LinkoNotification.Kind.FRIEND_REMOVED, event.requestId))
                refreshFriendCache()
            }
            is LinkoRealtimeEvent.IncomingConnectionRequest -> {
                val name = event.peerName?.takeIf { it.isNotBlank() } ?: "A trusted friend"
                add(LinkoNotification("connection-request:${event.sessionId}", "Incoming Connection Request", "$name wants to use your LINKO Internet connection.", LinkoNotification.Kind.CONNECTION, event.sessionId, actorName = name))
                postSimpleNotification("Incoming LINKO Connection", "$name wants to connect to your Internet.", BASE_NOTIFICATION_ID + event.sessionId.hashCode().absoluteValueSafe())
            }
            is LinkoRealtimeEvent.SessionStateChanged -> {
                val state = event.state ?: return
                val message = when (state) {
                    "requested" -> "A connection request was created."
                    "approved" -> "Connection request approved."
                    "connected", "direct_verified" -> "LINKO connection is established."
                    "denied" -> "Connection request was declined."
                    "revoked" -> "LINKO connection ended."
                    "expired" -> "LINKO connection session expired."
                    "provider_connection_failed", "failed" -> "LINKO could not establish the connection."
                    "disconnected" -> "The LINKO connection was disconnected."
                    else -> "Connection state changed to $state."
                }
                val kind = when (state) {
                    "connected", "direct_verified" -> LinkoNotification.Kind.CONNECTION_CONNECTED
                    "provider_connection_failed", "failed", "denied", "expired", "revoked", "disconnected" -> LinkoNotification.Kind.CONNECTION_FAILED
                    else -> LinkoNotification.Kind.CONNECTION
                }
                add(LinkoNotification("session:${event.sessionId ?: state}:$state", if (kind == LinkoNotification.Kind.CONNECTION_CONNECTED) "LINKO Connected" else "Connection Update", message, kind, event.sessionId))
                if (state == "connected" || state == "direct_verified") postSimpleNotification("LINKO Connected", message, BASE_NOTIFICATION_ID + 60)
                else if (state == "provider_connection_failed" || state == "failed" || state == "denied" || state == "expired" || state == "revoked" || state == "disconnected") postSimpleNotification("LINKO Connection Update", message, BASE_NOTIFICATION_ID + 61)
            }
            is LinkoRealtimeEvent.TransportError -> {
                val message = event.message.ifBlank { "LINKO realtime service was interrupted. Reconnecting…" }
                add(LinkoNotification("realtime-error:${message.hashCode()}", "LINKO Connection Service", message, LinkoNotification.Kind.REALTIME_ERROR))
                postSimpleNotification("LINKO Connection Service", message, BASE_NOTIFICATION_ID + 90)
            }
            else -> Unit
        }
    }

    private suspend fun handlePresence(presence: LinkoPresence) {
        val auth = appContext?.let { LinkoAuth(it) }
        if (presence.userId == auth?.currentUserId()) return
        if (!knownFriends.containsKey(presence.userId)) refreshFriendCache()
        val friendName = knownFriends[presence.userId] ?: return
        val wasOnline = lastPresence.put(presence.userId, presence.online)
        if (wasOnline == null || wasOnline == presence.online) return
        val kind = if (presence.online) LinkoNotification.Kind.FRIEND_ONLINE else LinkoNotification.Kind.FRIEND_OFFLINE
        val title = if (presence.online) "Your Friend Is Online" else "Your Friend Is Offline"
        val message = if (presence.online) "$friendName is now online on LINKO." else "$friendName is now offline on LINKO."
        add(LinkoNotification("presence:${presence.userId}:${if (presence.online) "online" else "offline"}", title, message, kind, actorUserId = presence.userId, actorName = friendName))
        postSimpleNotification(title, message, BASE_NOTIFICATION_ID + presence.userId.hashCode().absoluteValueSafe())
    }

    private suspend fun refreshFriendCache() {
        runCatching {
            val array = LinkoFriendsApiHolder.api.friends().optJSONArray("friends") ?: return
            val active = HashSet<String>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val userId = item.optString("user_id").takeIf { it.isNotBlank() } ?: continue
                active += userId
                knownFriends[userId] = item.optString("display_name").ifBlank { "LINKO Friend" }
            }
            knownFriends.keys.retainAll(active)
        }
    }

    private suspend fun findRequest(requestId: String): JSONObject? = runCatching {
        val array = LinkoFriendsApiHolder.api.requests().optJSONArray("requests") ?: return null
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            if (item.optString("id") == requestId) return item
        }
        null
    }.getOrNull()

    private fun postFriendRequestNotification(requestId: String, senderName: String) {
        val context = appContext ?: return
        if (!notificationsAllowed(context)) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = BASE_NOTIFICATION_ID + requestId.hashCode().absoluteValueSafe()
        val acceptIntent = Intent(context, LinkoNotificationActionReceiver::class.java).apply { action = LinkoNotificationActionReceiver.ACTION_ACCEPT_FRIEND; putExtra(LinkoNotificationActionReceiver.EXTRA_REQUEST_ID, requestId) }
        val declineIntent = Intent(context, LinkoNotificationActionReceiver::class.java).apply { action = LinkoNotificationActionReceiver.ACTION_DECLINE_FRIEND; putExtra(LinkoNotificationActionReceiver.EXTRA_REQUEST_ID, requestId) }
        val openIntent = Intent(context, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP); putExtra("EXTRA_NOTIFICATION_REQUEST_ID", requestId) }
        val acceptPending = broadcastPending(context, acceptIntent, notificationId + 1)
        val declinePending = broadcastPending(context, declineIntent, notificationId + 2)
        val openPending = PendingIntent.getActivity(context, notificationId + 3, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("New LINKO Friend Request")
            .setContentText("$senderName wants to be your friend")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$senderName sent you a LINKO friend request. Accept or decline it now."))
            .setContentIntent(openPending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(R.drawable.ic_launcher, "ACCEPT", acceptPending)
            .addAction(R.drawable.ic_launcher, "DECLINE", declinePending)
            .build()
            .also { manager.notify(notificationId, it) }
    }

    private fun postDiagnosticsNotification(snapshot: ConnectionDiagnosticsSnapshot) {
        val context = appContext ?: return
        if (!notificationsAllowed(context)) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val terminal = snapshot.stage == ConnectionStage.CONNECTED || snapshot.stage == ConnectionStage.FAILED
        val title = when (snapshot.stage) {
            ConnectionStage.CONNECTED -> "LINKO Connected"
            ConnectionStage.FAILED -> "LINKO Connection Failed"
            else -> "LINKO · ${snapshot.stage.name.replace('_', ' ')}"
        }
        val counts = "Local ${snapshot.localCandidates} · Remote ${snapshot.remoteCandidates} · Checks ${snapshot.checks} · Success ${snapshot.successfulChecks}"
        val eventLines = snapshot.recentEvents.take(5).asReversed().joinToString("\n") { event ->
            val marker = when (event.severity) {
                ConnectionSeverity.ERROR -> "✗"
                ConnectionSeverity.SUCCESS -> "✓"
                ConnectionSeverity.WARNING -> "!"
                ConnectionSeverity.INFO -> "·"
            }
            "$marker ${event.message}"
        }
        val headline = snapshot.failureReason?.let { "$it\n$counts" } ?: "${snapshot.headline}\n$counts"
        val bigText = "$headline\n\n$eventLines".trim()
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, DIAGNOSTIC_NOTIFICATION_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(
            DIAGNOSTIC_NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title)
                .setContentText(headline.lineSequence().firstOrNull() ?: headline)
                .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
                .setContentIntent(pending)
                .setOngoing(!terminal)
                .setAutoCancel(terminal)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build(),
        )
    }

    private fun postSimpleNotification(title: String, message: String, notificationId: Int = BASE_NOTIFICATION_ID) {
        val context = appContext ?: return
        if (!notificationsAllowed(context)) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(notificationId, NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build())
    }

    private fun notificationsAllowed(context: Context): Boolean = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun broadcastPending(context: Context, intent: Intent, requestCode: Int): PendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun ensureChannel() {
        val context = appContext ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Incoming LINKO friend requests and connection diagnostics"
            enableVibration(true)
        })
    }

    private fun Int.absoluteValueSafe(): Int = if (this == Int.MIN_VALUE) Int.MAX_VALUE else kotlin.math.abs(this)
}
