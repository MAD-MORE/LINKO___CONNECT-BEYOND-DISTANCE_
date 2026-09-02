package com.linkshare.app.network

import com.linkshare.app.tunnel.EncryptedDatagramTunnel
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * Direct-only UDP negotiation. Supabase is used only to exchange candidates;
 * application traffic never traverses Supabase.
 *
 * The same DatagramSocket is returned to the caller so the NAT mapping created
 * during STUN/hole-punching is retained for the encrypted data tunnel.
 */
object DirectP2pNegotiator {
    data class Result(val socket: DatagramSocket, val peer: InetSocketAddress)

    suspend fun establish(
        sessionId: String,
        sessionKey: ByteArray,
        role: EncryptedDatagramTunnel.Role,
        signaling: LinkoSignalingClient,
        socket: DatagramSocket = DatagramSocket(0),
        timeoutMs: Long = 15_000L,
    ): Result {
        require(sessionKey.size == 32) { "sessionKey must be 32 bytes" }
        val deadline = System.currentTimeMillis() + timeoutMs

        // Keep the socket outside the VPN before it is ever used for data.
        val localCandidates = mutableListOf<LinkoStunClient.Candidate>()
        for ((host, port) in LinkoStunClient.DEFAULT_STUN_SERVERS) {
            val candidate = runCatching { LinkoStunClient.discover(socket, host, port, 2_500) }.getOrNull()
            if (candidate != null) {
                localCandidates += candidate
                break
            }
        }
        if (localCandidates.isEmpty()) throw LinkoNetworkException("direct_stun_failed")

        localCandidates.forEach { candidate ->
            signaling.send(
                sessionId,
                SignalKind.ICE,
                JSONObject()
                    .put("candidate", candidate.address.hostAddress)
                    .put("port", candidate.port)
                    .put("type", candidate.type)
                    .put("protocol", "udp")
            )
        }

        val tried = linkedSetOf<String>()
        while (System.currentTimeMillis() < deadline) {
            val remote = runCatching { signaling.receive(sessionId) }.getOrDefault(emptyList())
                .asSequence()
                .filter { it.kind == SignalKind.ICE }
                .mapNotNull { signal ->
                    val host = signal.payload.optString("candidate").trim()
                    val port = signal.payload.optInt("port", -1)
                    if (host.isBlank() || port !in 1..65535) null else InetSocketAddress(host, port)
                }
                .distinctBy { "${it.hostString}:${it.port}" }
                .toList()

            for (peer in remote) {
                val key = "${peer.hostString}:${peer.port}"
                if (!tried.add(key)) continue

                // Punch the candidate using the same socket. Both peers do this
                // independently, which is required for many NAT mappings.
                repeat(4) {
                    runCatching {
                        val probe = ByteArray(1) { 0x4c }
                        socket.send(DatagramPacket(probe, probe.size, peer))
                    }
                    delay(120)
                }

                val tunnel = EncryptedDatagramTunnel(
                    socket = socket,
                    peer = peer,
                    sessionId = sessionId,
                    role = role,
                    sessionKey = sessionKey,
                )

                // The encrypted ping is the authenticated connectivity check.
                // Do not report success until the opposite peer proves it can
                // decrypt our traffic and returns a valid PONG.
                repeat(6) {
                    runCatching { tunnel.sendPing() }
                    val received = tunnel.receive(700)
                    if (received?.type == EncryptedDatagramTunnel.PacketType.PONG) {
                        return Result(socket, peer)
                    }
                }
            }
            delay(250)
        }

        socket.close()
        throw LinkoNetworkException("direct_udp_path_failed")
    }
}
