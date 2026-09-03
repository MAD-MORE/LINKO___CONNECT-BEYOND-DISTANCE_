package com.linkshare.app.network

import android.util.Log
import com.linkshare.app.tunnel.EncryptedDatagramTunnel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Direct-only UDP negotiation.
 *
 * Supabase is signaling only. No relay is used and no Internet traffic passes through Supabase.
 * Both peers use the same UDP socket for STUN discovery and simultaneous hole-punching so the
 * NAT mapping created by STUN is the mapping used for the actual direct path.
 */
object DirectP2pNegotiator {
    data class Result(val socket: DatagramSocket, val peer: InetSocketAddress)

    suspend fun establish(
        sessionId: String,
        sessionKey: ByteArray,
        role: EncryptedDatagramTunnel.Role,
        signaling: LinkoSignalingClient,
        socket: DatagramSocket = DatagramSocket(0),
        timeoutMs: Long = 25_000L,
    ): Result = withContext(Dispatchers.IO) {
        require(sessionKey.size == 32) { "sessionKey must be 32 bytes" }
        val deadline = System.currentTimeMillis() + timeoutMs

        socket.reuseAddress = true
        socket.soTimeout = CHECK_RECEIVE_TIMEOUT_MS

        val localCandidates = gatherCandidates(socket, deadline)
        if (localCandidates.isEmpty()) {
            socket.close()
            throw LinkoNetworkException("direct_candidate_gather_failed")
        }
        Log.i(TAG, "CANDIDATES_LOCAL count=${localCandidates.size} localPort=${socket.localPort}")
        localCandidates.forEach { Log.i(TAG, "CANDIDATE_LOCAL ${it.type} ${it.address.hostAddress}:${it.port}") }

        localCandidates.forEach { candidate ->
            runCatching {
                signaling.send(
                    sessionId,
                    SignalKind.ICE,
                    JSONObject()
                        .put("candidate", candidate.address.hostAddress)
                        .put("port", candidate.port)
                        .put("type", candidate.type)
                        .put("protocol", "udp")
                        .put("priority", candidatePriority(candidate)),
                )
            }.onFailure { error ->
                Log.w(TAG, "CANDIDATE_SEND_FAILED endpoint=${candidate.address.hostAddress}:${candidate.port} ${error.message}")
            }
        }

        val tried = ConcurrentHashMap.newKeySet<String>()
        val selectedPeer = arrayOfNulls<InetSocketAddress>(1)
        val selected = AtomicBoolean(false)
        var lastFailure = "direct_udp_path_failed"

        coroutineScope {
            while (System.currentTimeMillis() < deadline && !selected.get()) {
                val remoteCandidates = runCatching { signaling.receive(sessionId) }
                    .onFailure { lastFailure = "signaling_receive_failed" }
                    .getOrElse { emptyList() }
                    .asSequence()
                    .filter { it.kind == SignalKind.ICE && it.senderDeviceId.isNotBlank() }
                    .mapNotNull { signal ->
                        val host = signal.payload.optString("candidate").trim()
                        val port = signal.payload.optInt("port", -1)
                        if (host.isBlank() || port !in 1..65535) null else {
                            val endpoint = runCatching { InetSocketAddress(host, port) }.getOrNull()
                                ?: return@mapNotNull null
                            CandidateRef(endpoint, signal.payload.optString("type", "unknown"))
                        }
                    }
                    .distinctBy { "${it.endpoint.hostString}:${it.endpoint.port}" }
                    .sortedByDescending { candidatePriority(it.type) }
                    .toList()

                if (remoteCandidates.isNotEmpty()) {
                    Log.i(TAG, "CANDIDATES_REMOTE count=${remoteCandidates.size}")
                }

                remoteCandidates.forEach { candidate ->
                    val advertisedKey = "${candidate.endpoint.hostString}:${candidate.endpoint.port}"
                    if (!tried.add(advertisedKey)) return@forEach
                    launch(Dispatchers.IO) {
                        val candidateDeadline = minOf(
                            deadline,
                            System.currentTimeMillis() + CANDIDATE_CHECK_WINDOW_MS,
                        )
                        while (isActive && System.currentTimeMillis() < candidateDeadline && !selected.get()) {
                            try {
                                EncryptedDatagramTunnel(
                                    socket = socket,
                                    peer = candidate.endpoint,
                                    sessionId = sessionId,
                                    role = role,
                                    sessionKey = sessionKey,
                                ).sendPing()
                                Log.d(TAG, "P2P_CHECK_SENT advertised=$advertisedKey")
                                delay(PUNCH_INTERVAL_MS)
                            } catch (error: Exception) {
                                lastFailure = error.message ?: "direct_udp_check_failed"
                                return@launch
                            }
                        }
                    }
                }

                repeat(RECEIVE_BATCH_PER_ROUND) {
                    if (selected.get() || System.currentTimeMillis() >= deadline) return@repeat
                    try {
                        val inbound = EncryptedDatagramTunnel(
                            socket = socket,
                            peer = InetSocketAddress("0.0.0.0", 9),
                            sessionId = sessionId,
                            role = role,
                            sessionKey = sessionKey,
                        ).receiveAny(CHECK_RECEIVE_TIMEOUT_MS)
                            ?: return@repeat

                        val source = inbound.source
                        Log.i(TAG, "P2P_CHECK_RECEIVED source=${source.hostString}:${source.port} type=${inbound.type}")
                        val observedTunnel = EncryptedDatagramTunnel(
                            socket = socket,
                            peer = source,
                            sessionId = sessionId,
                            role = role,
                            sessionKey = sessionKey,
                        )

                        when (inbound.type) {
                            EncryptedDatagramTunnel.PacketType.PING -> {
                                val timestamp = inbound.payload.toLongOrNull() ?: System.currentTimeMillis()
                                observedTunnel.sendPong(timestamp)
                                observedTunnel.sendPing()
                                val confirmation = observedTunnel.receive(CHECK_RECEIVE_TIMEOUT_MS)
                                if (confirmation?.type == EncryptedDatagramTunnel.PacketType.PONG && selected.compareAndSet(false, true)) {
                                    selectedPeer[0] = source
                                    Log.i(TAG, "P2P_PAIR_SELECTED local=${socket.localPort} remote=${source.hostString}:${source.port}")
                                }
                            }
                            EncryptedDatagramTunnel.PacketType.PONG -> {
                                if (selected.compareAndSet(false, true)) {
                                    selectedPeer[0] = source
                                    Log.i(TAG, "P2P_PAIR_SELECTED local=${socket.localPort} remote=${source.hostString}:${source.port}")
                                }
                            }
                            else -> Unit
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                        return@repeat
                    } catch (error: Exception) {
                        lastFailure = error.message ?: "direct_udp_receive_failed"
                    }
                }

                if (!selected.get()) delay(80L)
            }
        }

        if (selected.get() && selectedPeer[0] != null) {
            return@withContext Result(socket, selectedPeer[0]!!)
        }

        socket.close()
        Log.e(TAG, "P2P_NEGOTIATION_TIMEOUT reason=$lastFailure localCandidates=${localCandidates.size}")
        throw LinkoNetworkException(lastFailure)
    }

    private data class CandidateRef(val endpoint: InetSocketAddress, val type: String)

    private fun gatherCandidates(
        socket: DatagramSocket,
        deadline: Long,
    ): List<LinkoStunClient.Candidate> {
        val candidates = linkedMapOf<String, LinkoStunClient.Candidate>()

        fun add(candidate: LinkoStunClient.Candidate) {
            if (candidate.address is Inet4Address &&
                !candidate.address.isLoopbackAddress &&
                !candidate.address.isLinkLocalAddress
            ) {
                candidates.putIfAbsent("${candidate.address.hostAddress}:${candidate.port}", candidate)
            }
        }

        val localPort = socket.localPort.takeIf { it > 0 } ?: return emptyList()

        runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { iface ->
                if (!iface.isUp || iface.isLoopback || iface.isVirtual) return@forEach
                iface.inetAddresses.toList().forEach { address ->
                    if (address is Inet4Address && !address.isLoopbackAddress && !address.isLinkLocalAddress) {
                        add(LinkoStunClient.Candidate(address, localPort, "host"))
                    }
                }
            }
        }.onFailure { Log.w(TAG, "HOST_CANDIDATE_GATHER_FAILED ${it.message}") }

        for ((host, port) in LinkoStunClient.DEFAULT_STUN_SERVERS) {
            if (System.currentTimeMillis() >= deadline) break
            runCatching { LinkoStunClient.discover(socket, host, port, 1_500) }
                .getOrNull()
                ?.let(::add)
        }

        return candidates.values.sortedByDescending(::candidatePriority)
    }

    private fun candidatePriority(candidate: LinkoStunClient.Candidate): Int = candidatePriority(candidate.type)

    private fun candidatePriority(type: String): Int = when (type.lowercase()) {
        "host" -> 200
        "srflx" -> 100
        else -> 10
    }

    private fun ByteArray.toLongOrNull(): Long? =
        if (size < 8) null else java.nio.ByteBuffer.wrap(this).order(java.nio.ByteOrder.BIG_ENDIAN).long

    private const val TAG = "LINKO_P2P"
    private const val CHECK_RECEIVE_TIMEOUT_MS = 500
    private const val CANDIDATE_CHECK_WINDOW_MS = 2_500L
    private const val PUNCH_INTERVAL_MS = 120L
    private const val RECEIVE_BATCH_PER_ROUND = 8
}
