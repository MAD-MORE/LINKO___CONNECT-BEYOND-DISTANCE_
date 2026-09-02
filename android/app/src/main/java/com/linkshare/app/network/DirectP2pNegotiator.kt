package com.linkshare.app.network

import com.linkshare.app.tunnel.EncryptedDatagramTunnel
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface

/**
 * Direct-only UDP negotiation. Supabase is control-plane signaling only; Internet traffic never
 * traverses Supabase or a relay. Candidate discovery and authenticated checks happen on the same
 * UDP socket so the NAT mapping is preserved for the data tunnel.
 */
object DirectP2pNegotiator {
    data class Result(val socket: DatagramSocket, val peer: InetSocketAddress)

    suspend fun establish(
        sessionId: String,
        sessionKey: ByteArray,
        role: EncryptedDatagramTunnel.Role,
        signaling: LinkoSignalingClient,
        socket: DatagramSocket = DatagramSocket(0),
        timeoutMs: Long = 20_000L,
    ): Result {
        require(sessionKey.size == 32) { "sessionKey must be 32 bytes" }
        val deadline = System.currentTimeMillis() + timeoutMs

        // Gather usable local candidates plus multiple server-reflexive candidates.
        // Host candidates help peers on the same LAN; srflx candidates handle NAT traversal.
        val localCandidates = gatherCandidates(socket, deadline)
        if (localCandidates.isEmpty()) throw LinkoNetworkException("direct_candidate_gather_failed")

        localCandidates.forEach { candidate ->
            signaling.send(
                sessionId,
                SignalKind.ICE,
                JSONObject()
                    .put("candidate", candidate.address.hostAddress)
                    .put("port", candidate.port)
                    .put("type", candidate.type)
                    .put("protocol", "udp")
                    .put("priority", candidatePriority(candidate))
            )
        }

        val tried = linkedSetOf<String>()
        while (System.currentTimeMillis() < deadline) {
            val remaining = (deadline - System.currentTimeMillis()).coerceAtLeast(250L)
            val remoteCandidates = runCatching { signaling.receive(sessionId) }
                .getOrDefault(emptyList())
                .asSequence()
                .filter { it.kind == SignalKind.ICE && it.senderDeviceId.isNotBlank() }
                .mapNotNull { signal ->
                    val host = signal.payload.optString("candidate").trim()
                    val port = signal.payload.optInt("port", -1)
                    if (host.isBlank() || port !in 1..65535) null else {
                        val endpoint = runCatching { InetSocketAddress(host, port) }.getOrNull() ?: return@mapNotNull null
                        CandidateRef(endpoint, signal.payload.optString("type", "unknown"))
                    }
                }
                .distinctBy { "${it.endpoint.hostString}:${it.endpoint.port}" }
                .sortedByDescending { candidatePriority(it.type) }
                .toList()

            for (candidate in remoteCandidates) {
                if (System.currentTimeMillis() >= deadline) break
                val key = "${candidate.endpoint.hostString}:${candidate.endpoint.port}"
                if (!tried.add(key)) continue

                // First punch the mapping. This byte is intentionally not trusted as proof of
                // connectivity; only the authenticated PONG below can select the path.
                repeat(3) { attempt ->
                    runCatching {
                        val probe = byteArrayOf(0x4c, 0x4b, 0x4f, 0x32, 0x50, attempt.toByte())
                        socket.send(DatagramPacket(probe, probe.size, candidate.endpoint))
                    }
                    delay(80L)
                }

                val tunnel = EncryptedDatagramTunnel(
                    socket = socket,
                    peer = candidate.endpoint,
                    sessionId = sessionId,
                    role = role,
                    sessionKey = sessionKey,
                )

                // Authenticated liveness check. Short receives let us rotate through candidate
                // pairs quickly instead of spending seconds on a dead NAT mapping.
                repeat(5) {
                    if (System.currentTimeMillis() >= deadline) return@repeat
                    runCatching { tunnel.sendPing() }
                    val received = tunnel.receive(minOf(350, remaining.toInt().coerceAtLeast(100)))
                    if (received?.type == EncryptedDatagramTunnel.PacketType.PONG) {
                        return Result(socket, candidate.endpoint)
                    }
                }
            }
            delay(120L)
        }

        socket.close()
        throw LinkoNetworkException("direct_udp_path_failed")
    }

    private data class CandidateRef(val endpoint: InetSocketAddress, val type: String)

    private fun gatherCandidates(socket: DatagramSocket, deadline: Long): List<LinkoStunClient.Candidate> {
        val candidates = linkedMapOf<String, LinkoStunClient.Candidate>()
        fun add(candidate: LinkoStunClient.Candidate) {
            if (candidate.address is Inet4Address && !candidate.address.isLoopbackAddress) {
                candidates.putIfAbsent("${candidate.address.hostAddress}:${candidate.port}", candidate)
            }
        }

        val hostPort = socket.localPort.takeIf { it > 0 } ?: return emptyList()
        runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { iface ->
                if (!iface.isUp || iface.isLoopback || iface.isVirtual) return@forEach
                iface.inetAddresses.toList().forEach { address ->
                    if (address is Inet4Address && !address.isLoopbackAddress && !address.isLinkLocalAddress) {
                        add(LinkoStunClient.Candidate(address, hostPort, "host"))
                    }
                }
            }
        }

        for ((host, port) in LinkoStunClient.DEFAULT_STUN_SERVERS) {
            if (System.currentTimeMillis() >= deadline) break
            val candidate = runCatching { LinkoStunClient.discover(socket, host, port, 1_500) }.getOrNull()
            if (candidate != null) add(candidate)
        }
        return candidates.values.sortedByDescending(::candidatePriority)
    }

    private fun candidatePriority(candidate: LinkoStunClient.Candidate): Int = candidatePriority(candidate.type)

    private fun candidatePriority(type: String): Int = when (type.lowercase()) {
        "host" -> 200
        "srflx" -> 100
        else -> 10
    }
}
