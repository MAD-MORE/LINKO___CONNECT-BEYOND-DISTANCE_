package com.linkshare.app.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.linkshare.app.auth.LinkoAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Handles Accept/Decline directly from a real Android friend-request notification. */
class LinkoNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID)?.trim().orEmpty()
        if (requestId.isBlank()) return

        val accepted = when (intent.action) {
            ACTION_ACCEPT_FRIEND -> true
            ACTION_DECLINE_FRIEND -> false
            else -> return
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val auth = LinkoAuth(context.applicationContext)
                val api = LinkoFriendsApi { auth.currentAccessToken() }
                api.respond(requestId, accepted)
                val title = if (accepted) "Friend Request Accepted" else "Friend Request Declined"
                val message = if (accepted) {
                    "The LINKO friend request was accepted successfully."
                } else {
                    "The LINKO friend request was declined successfully."
                }
                LinkoNotificationCenter.add(
                    LinkoNotification(
                        id = "local-response:$requestId:${if (accepted) "accepted" else "declined"}",
                        title = title,
                        message = message,
                        kind = if (accepted) LinkoNotification.Kind.FRIEND_ACCEPTED else LinkoNotification.Kind.FRIEND_DECLINED,
                        requestId = requestId,
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Friend request action failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "LINKO_REQUEST_ACTION"
        const val ACTION_ACCEPT_FRIEND = "com.linkshare.app.action.ACCEPT_FRIEND_REQUEST"
        const val ACTION_DECLINE_FRIEND = "com.linkshare.app.action.DECLINE_FRIEND_REQUEST"
        const val EXTRA_REQUEST_ID = "EXTRA_FRIEND_REQUEST_ID"
    }
}
