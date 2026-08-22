package com.linkshare.app.tunnel

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import kotlin.coroutines.resume

class RelayTunnelClient(
    private val endpoint: String,
    private val bearerToken: String,
    private val sessionId: String,
    private val peerId: String,
    private val sessionKey: ByteArray,
) {
    private val client = OkHttpClient()
    private var socket: WebSocket? = null

    suspend fun connect(): Boolean = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $bearerToken")
            .build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                webSocket.send("{\"peerId\":\"$peerId\",\"sessionId\":\"$sessionId\"}")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("\"type\":\"ready\"")) {
                    if (continuation.isActive) continuation.resume(true)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                if (continuation.isActive) continuation.resume(false)
            }
        })
        continuation.invokeOnCancellation { socket?.cancel() }
    }

    fun sendPacket(packet: ByteArray): Boolean {
        val encrypted = EncryptedFrame.encrypt(packet, sessionKey)
        return socket?.send(ByteString.of(*encrypted)) == true
    }

    fun close() {
        socket?.close(1000, "session closed")
        socket = null
        client.dispatcher.executorService.shutdown()
    }
}
