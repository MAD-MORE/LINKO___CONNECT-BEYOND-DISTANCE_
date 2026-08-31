package com.linkshare.app.network

import kotlinx.coroutines.delay
import org.json.JSONObject
import java.net.DatagramSocket
import java.net.InetAddress

/** Exchanges server-reflexive UDP candidates and selects direct transport before relay fallback. */
class LinkoIceCoordinator(private val signaling: LinkoSignalingClient) {
    data class RemoteCandidate(val host: String, val port: Int, val type: String)

    suspend fun publishLocalCandidate(sessionId: String, socket: DatagramSocket, stunHost: String = "stun.l.google.com", stunPort: Int = 19302): LinkoStunClient.Candidate? {
        val candidate = runCatching { LinkoStunClient.discover(socket, stunHost, stunPort) }.getOrNull() ?: return null
        signaling.send(sessionId, SignalKind.ICE, LinkoStunClientCandidateJson.create(candidate))
        return candidate
    }

    suspend fun awaitRemoteCandidate(sessionId: String, timeoutMs: Long = 5_000L): RemoteCandidate? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            signaling.receive(sessionId).forEach { signal ->
                if (signal.kind != SignalKind.ICE) return@forEach
                val host = signal.payload.optString("candidate").trim()
                val port = signal.payload.optInt("port", -1)
                val type = signal.payload.optString("type", "srflx")
                if (host.isNotBlank() && port in 1..65535) return RemoteCandidate(host, port, type)
            }
            delay(250L)
        }
        return null
    }

    fun probe(candidate: RemoteCandidate, timeoutMs: Int = 750): Boolean = runCatching {
        DatagramSocket().use { socket ->
            socket.soTimeout = timeoutMs
            socket.connect(InetAddress.getByName(candidate.host), candidate.port)
            true
        }
    }.getOrDefault(false)
}

private object LinkoStunClientCandidateJson {
    fun create(candidate: LinkoStunClient.Candidate) = JSONObject()
        .put("candidate", candidate.address.hostAddress)
        .put("port", candidate.port)
        .put("type", candidate.type)
        .put("protocol", "udp")
}
