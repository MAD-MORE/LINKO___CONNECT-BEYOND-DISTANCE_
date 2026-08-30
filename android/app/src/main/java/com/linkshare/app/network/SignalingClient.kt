package com.linkshare.app.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import android.util.Log

class SignalingClient(private val deviceId: String) {
    private val client = OkHttpClient()
    private var ws: WebSocket? = null

    fun connect() {
        val url = "${ProdConfig.SIGNALING_WS}?device_id=$deviceId"
        val req = Request.Builder().url(url).build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                Log.i("Signaling", "connected")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.i("Signaling", "msg: $text")
                // TODO: parse incoming signaling messages and route to WireGuard/tunnel layer
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                Log.i("Signaling", "binary msg")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, resp: okhttp3.Response?) {
                Log.w("Signaling", "failure", t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i("Signaling", "closed: $code $reason")
            }
        })
    }

    fun send(message: String) {
        ws?.send(message)
    }

    fun close() {
        ws?.close(1000, "bye")
    }
}
