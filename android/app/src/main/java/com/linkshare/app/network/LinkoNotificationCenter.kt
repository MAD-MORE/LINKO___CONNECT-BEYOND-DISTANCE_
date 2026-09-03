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
                add(
                    LinkoNotification(
                        id = "friend-request:${event.requestId}",
                        title = "New Friend Request",
                        message = "$name wants to be your LINKO friend.",
                        kind = LinkoNotification.Kind.FRIEND_REQUEST_INCOMING,
                        requestId = event.requestId,
                        actorUserId = actorUserId,
                        actorName = name,
                    )
                )
                postFriendRequestNotification(event.requestId, name)
            }

            is LinkoRealtimeEvent.FriendRequestSent -> add(
                LinkoNotification(
                    id = "friend-sent:${event.requestId}",
                    title = "Friend Request Sent",
                    message = "Your LINKO friend request was sent and is waiting for a response.",
                    kind = LinkoNotification.Kind.FRIEND_REQUEST_SENT,
                    requestId = event.requestId,
                )
            )

            is LinkoRealtimeEvent.FriendRequestAccepted -> {
                add(
                    LinkoNotification(
                        id = "friend-accepted:${event.requestId}",
                        title = "Friend Request Accepted",
                        message = "Your LINKO friend request was accepted. You can now connect and share internet.",
                        kind = LinkoNotification.Kind.FRIEND_ACCEPTED,
                        requestId = event.requestId,
                    )
                )
                postSimpleNotification("Friend Request Accepted", "Your LINKO friend request was accepted.")
            }

            is LinkoRealtimeEvent.FriendRequestDeclined -> {
                add(
                    LinkoNotification(
                        id = "friend-declined:${event.requestId}",
                        title = "Friend Request Declined",
                        message = "Your LINKO friend request was declined.",
                        kind = LinkoNotification.Kind.FRIEND_DECLINED,
                        requestId = event.requestId,
                    )
                )
                postSimpleNotification("Friend Request Declined", "Your LINKO friend request was declined.")
            }

            is LinkoRealtimeEvent.PresenceChanged -> handlePresence(event.presence)

            is LinkoRealtimeEvent.FriendRemoved -> add(
                LinkoNotification(
                    id = "friend-removed:${event.requestId}",
                    title = "Friend Removed",
                    message = "A LINKO friendship was removed.",
                    kind = LinkoNotification.Kind.FRIEND_REMOVED,
                    requestId = event.requestId,
                )
            )

            is LinkoRealtimeEvent.IncomingConnectionRequest -> add(
                LinkoNotification(
                    id = "connection-request:${event.sessionId}",
                    title = "Incoming Connection Request",
                    message = "A trusted friend is asking to use your internet connection.",
                    kind = LinkoNotification.Kind.CONNECTION,
                    requestId = event.sessionId,
                )
            )

            is LinkoRealtimeEvent.SessionStateChanged -> {
                val state = event.state ?: return
                val message = when (state) {
                    "requested" -> "A connection request was created."
                    "approved" -> "Connection request approved."
                    "connected" -> "LINKO connection is established."
                    "denied" -> "Connection request was declined."
                    "revoked" -> "LINKO connection ended."
                    "expired" -> "LINKO connection session expired."
                    else -> "Connection state changed to $state."
                }
                add(
                    LinkoNotification(
                        id = "session:${event.sessionId ?: state}:$state",
                        title = "Connection Update",
                        message = message,
                        kind = LinkoNotification.Kind.CONNECTION,
                        requestId = event.sessionId,
                    )
                )
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
        add(LinkoNotification(
            id = "presence:${presence.userId}:${if (presence.online) "online" else "offline"}",
            title = title,
            message = message,
            kind = kind,
            actorUserId = presence.userId,
            actorName = friendName,
        ))
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

    private suspend fun findRequest(requestId: String): JSONObject? {
        return runCatching {
            val array = LinkoFriendsApiHolder.api.requests().optJSONArray("requests") ?: return null
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                if (item.optString("id") == requestId) return item
            }
            null
        }.getOrNull()
    }

    private fun postFriendRequestNotification(requestId: String, senderName: String) {
        val context = appContext ?: return
        if (!notificationsAllowed(context)) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = BASE_NOTIFICATION_ID + requestId.hashCode().absoluteValueSafe()

        val acceptIntent = Intent(context, LinkoNotificationActionReceiver::class.java).apply {
            action = LinkoNotificationActionReceiver.ACTION_ACCEPT_FRIEND
            putExtra(LinkoNotificationActionReceiver.EXTRA_REQUEST_ID, requestId)
        }
        val declineIntent = Intent(context, LinkoNotificationActionReceiver::class.java).apply {
            action = LinkoNotificationActionReceiver.ACTION_DECLINE_FRIEND
            putExtra(LinkoNotificationActionReceiver.EXTRA_REQUEST_ID, requestId)
        }
        val openIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("EXTRA_NOTIFICATION_REQUEST_ID", requestId)
        }
        val acceptPending = broadcastPending(context, acceptIntent, notificationId + 1)
        val declinePending = broadcastPending(context, declineIntent, notificationId + 2)
        val openPending = PendingIntent.getActivity(
            context,
            notificationId + 3,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

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

    private fun notificationsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun broadcastPending(context: Context, intent: Intent, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun ensureChannel() {
        val context = appContext ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Incoming LINKO friend requests and request results"
                enableVibration(true)
            }
        )
    }

    private fun Int.absoluteValueSafe(): Int = if (this == Int.MIN_VALUE) Int.MAX_VALUE else kotlin.math.abs(this)
}
