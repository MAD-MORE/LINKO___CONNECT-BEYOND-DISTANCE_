/**
 * FCM push notifications for Linko.
 *
 * Used to wake Provider devices when a connection request arrives,
 * so providers don't miss requests when their screen is off.
 *
 * Requires FIREBASE_SERVER_KEY environment variable (Legacy HTTP v1 FCM key).
 * For production, upgrade to FCM HTTP v2 with service account credentials.
 */

const FCM_ENDPOINT = "https://fcm.googleapis.com/fcm/send";
const serverKey = process.env.FCM_SERVER_KEY ?? "";

export type NotificationType =
  | "connection_request"
  | "session_approved"
  | "session_revoked"
  | "session_expired";

export interface PushPayload {
  type: NotificationType;
  sessionId?: string;
  fromUserId?: string;
  fromDisplayName?: string;
  message?: string;
}

/**
 * Send a push notification to a device via FCM.
 * @param fcmToken - The device's FCM registration token
 * @param payload - Notification payload
 * @returns true on success, false on failure (non-fatal)
 */
export async function sendPushNotification(
  fcmToken: string,
  payload: PushPayload
): Promise<boolean> {
  if (!serverKey) {
    console.warn("[notifications] FCM_SERVER_KEY not set — push notification skipped");
    return false;
  }
  if (!fcmToken) {
    console.warn("[notifications] No FCM token — push notification skipped");
    return false;
  }

  const body: Record<string, unknown> = {
    to: fcmToken,
    data: payload,
    android: {
      priority: "high",
      ttl: "60s",
    },
  };

  // Add a visible notification for connection requests (shows even when app is killed)
  if (payload.type === "connection_request") {
    body.notification = {
      title: "Linko — Connection Request",
      body: payload.fromDisplayName
        ? `${payload.fromDisplayName} wants to use your connection`
        : "Someone wants to use your connection",
      sound: "default",
      click_action: "OPEN_PROVIDER_SCREEN",
    };
  }

  try {
    const response = await fetch(FCM_ENDPOINT, {
      method: "POST",
      headers: {
        Authorization: `key=${serverKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(body),
    });

    if (!response.ok) {
      const text = await response.text();
      console.error("[notifications] FCM error:", response.status, text);
      return false;
    }

    const result = (await response.json()) as { success?: number; failure?: number };
    if (result.failure && result.failure > 0) {
      console.warn("[notifications] FCM delivery failure:", result);
      return false;
    }

    return true;
  } catch (err) {
    console.error("[notifications] FCM send exception:", err);
    return false;
  }
}

/**
 * Notify a Provider that a Receiver has requested a connection.
 */
export async function notifyConnectionRequest(params: {
  providerFcmToken: string;
  sessionId: string;
  receiverUserId: string;
  receiverDisplayName?: string;
}): Promise<void> {
  await sendPushNotification(params.providerFcmToken, {
    type: "connection_request",
    sessionId: params.sessionId,
    fromUserId: params.receiverUserId,
    fromDisplayName: params.receiverDisplayName ?? "A friend",
  });
}

/**
 * Notify a Receiver that the Provider approved their session.
 */
export async function notifySessionApproved(params: {
  receiverFcmToken: string;
  sessionId: string;
}): Promise<void> {
  await sendPushNotification(params.receiverFcmToken, {
    type: "session_approved",
    sessionId: params.sessionId,
    message: "Your connection request was approved",
  });
}
