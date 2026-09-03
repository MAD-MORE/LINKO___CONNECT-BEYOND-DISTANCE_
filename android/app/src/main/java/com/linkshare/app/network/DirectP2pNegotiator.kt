package com.linkshare.app.network

import android.util.Log
import com.linkshare.app.tunnel.EncryptedDatagramTunnel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramSocket
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Direct-only ICE-style UDP negotiation.
 * Supabase is signaling/control only. There is no TURN, relay, proxy, or server data path.
 */
object DirectP2pNegotiator {
    data class Result(val socket: DatagramSocket, val peer: InetSocketAddress)

    private enum class CandidateType { HOST, SRFLX, PRFLX }
    private data class IceCandidate(
        val id: String, val foundation: String, val type: CandidateType,
        val address: InetAddress, val port: Int, val priority: Int,
        val generation: Int, val sequence: Long,
    ) { val endpoint: InetSocketAddress get() = InetSocketAddress(address, port) }
    private data class CandidatePair(
        val local: IceCandidate, val remote: IceCandidate,
        val endpoint: InetSocketAddress, val score: Long,
    ) { val key: String get() = "${local.id}|${remote.id}|${endpoint.hostString}:${endpoint.port}" }
    private data class Success(val pair: CandidatePair, val rttMs: Long, val firstSeenAt: Long)
    private data class PendingCheck(val transactionId: String, val pair: CandidatePair, val sentAt: Long)

    private const val CHECK_TIMEOUT_MS = 16_000L
    private const val SIGNALING_POLL_TIMEOUT_MS = 1_500
    private const val CHECK_INTERVAL_MS = 180L
    private const val CHECK_RETRIES = 4
    private const val NOMINATION_RETRIES = 4
    private const val NOMINATION_ACK_TIMEOUT_MS = 1_100L
    private const val FIRST_SUCCESS_GRACE_MS = 650L
    private const val HANDSHAKE_TIMEOUT_MS = 1_200L
    private const val MAX_PAIR_CHECKS = 24
    private const val MAX_SIGNAL_AGE_MS = 120_000L

    suspend fun establish(
        sessionId: String,
        sessionKey: ByteArray,
        role: EncryptedDatagramTunnel.Role,
        signaling: LinkoSignalingClient,
        socket: DatagramSocket = DatagramSocket(0),
        timeoutMs: Long = CHECK_TIMEOUT_MS,
    ): Result = withContext(Dispatchers.IO) {
        require(sessionKey.size == 32) { "sessionKey must be 32 bytes" }
        require(runCatching { UUID.fromString(sessionId) }.isSuccess) { "sessionId must be a UUID" }

        val deadline = System.currentTimeMillis() + timeoutMs.coerceIn(8_000L, 30_000L)
        val generation = (System.currentTimeMillis() and 0x7fffffff).toInt()
        val localUfrag = randomToken(8)
        val localTieBreaker = randomToken(16)
        val controlling = role == EncryptedDatagramTunnel.Role.PROVIDER

        socket.reuseAddress = true
        socket.soTimeout = SIGNALING_POLL_TIMEOUT_MS

        val localCandidates = gatherCandidates(socket, deadline, generation)
        if (localCandidates.isEmpty()) {
            socket.close()
            throw LinkoNetworkException("NO_LOCAL_UDP_CANDIDATE")
        }
        Log.i(TAG, "ICE_GATHERED session=$sessionId generation=$generation candidates=${localCandidates.size} port=${socket.localPort}")

        val seenSignals = ConcurrentHashMap.newKeySet<String>()
        val remoteCandidates = ConcurrentHashMap<String, IceCandidate>()
        val launchedPairs = ConcurrentHashMap.newKeySet<String>()
        val successfulPairs = ConcurrentHashMap<String, Success>()
        val pendingChecks = ConcurrentHashMap<String, PendingCheck>()
        val selected = AtomicBoolean(false)
        val selectedPeer = arrayOfNulls<InetSocketAddress>(1)
        val receiverPaused = AtomicBoolean(false)
        var remoteGeneration: Int? = null
        var highestRemoteSequence = -1L
        var lastFailure = "DIRECT_CHECK_TIMEOUT"

        suspend fun sendOffer() {
            runCatching {
                signaling.send(sessionId, SignalKind.OFFER,
                    JSONObject()
                        .put("ice", 2)
                        .put("iceGeneration", generation)
                        .put("iceUfrag", localUfrag)
                        .put("iceTieBreaker", localTieBreaker)
                        .put("controlling", controlling)
                        .put("supports", JSONObject().put("host", true).put("srflx", true).put("relay", false))
                        .put("sentAt", System.currentTimeMillis()))
            }.onFailure { lastFailure = "SIGNALING_SEND_FAILED" }
        }

        suspend fun sendLocalCandidates() {
            localCandidates.forEach { candidate ->
                runCatching {
                    signaling.send(sessionId, SignalKind.ICE,
                        JSONObject()
                            .put("ice", 2)
                            .put("iceGeneration", generation)
                            .put("candidateId", candidate.id)
                            .put("foundation", candidate.foundation)
                            .put("candidateType", candidate.type.name.lowercase())
                            .put("candidate", candidate.address.hostAddress)
                            .put("port", candidate.port)
                            .put("protocol", "udp")
                            .put("priority", candidate.priority)
                            .put("seq", candidate.sequence)
                            .put("sentAt", System.currentTimeMillis())
                            .put("type", candidate.type.name.lowercase()))
                }.onFailure { Log.w(TAG, "ICE_CANDIDATE_SEND_FAILED id=${candidate.id}: ${it.message}") }
            }
            runCatching {
                signaling.send(sessionId, SignalKind.ICE,
                    JSONObject().put("ice", 2).put("iceGeneration", generation)
                        .put("endOfCandidates", true)
                        .put("seq", localCandidates.maxOfOrNull { it.sequence } ?: 0L)
                        .put("sentAt", System.currentTimeMillis()))
            }
        }

        suspend fun processSignal(signal: SignalEnvelope) {
            if (!seenSignals.add(signal.id)) return
            if (signal.sessionId.isNotBlank() && signal.sessionId != sessionId) return
            if (signal.createdAtEpochMillis > 0 && System.currentTimeMillis() - signal.createdAtEpochMillis > MAX_SIGNAL_AGE_MS) return

            when (signal.kind) {
                SignalKind.OFFER, SignalKind.ANSWER -> {
                    val incomingGeneration = signal.payload.optInt("iceGeneration", -1)
                    if (incomingGeneration >= 0) {
                        if (remoteGeneration != null && incomingGeneration < remoteGeneration!!) return
                        remoteGeneration = incomingGeneration
                    }
                    if (signal.kind == SignalKind.OFFER) {
                        runCatching {
                            signaling.send(sessionId, SignalKind.ANSWER,
                                JSONObject()
                                    .put("ice", 2)
                                    .put("iceGeneration", generation)
                                    .put("iceUfrag", localUfrag)
                                    .put("iceTieBreaker", localTieBreaker)
                                    .put("controlling", controlling)
                                    .put("answerToGeneration", incomingGeneration))
                        }.onFailure { lastFailure = "SIGNALING_ANSWER_FAILED" }
                    }
                }
                SignalKind.ICE -> {
                    val incomingGeneration = signal.payload.optInt("iceGeneration", remoteGeneration ?: -1)
                    if (incomingGeneration >= 0 && remoteGeneration != null && incomingGeneration != remoteGeneration) {
                        if (incomingGeneration < remoteGeneration!!) return
                        remoteCandidates.clear()
                        launchedPairs.clear()
                        highestRemoteSequence = -1L
                    }
                    if (incomingGeneration >= 0) remoteGeneration = incomingGeneration
                    val sequence = signal.payload.optLong("seq", 0L)
                    if (sequence > 0 && sequence <= highestRemoteSequence) return
                    if (sequence > highestRemoteSequence) highestRemoteSequence = sequence
                    if (signal.payload.optBoolean("endOfCandidates", false)) return

                    val host = signal.payload.optString("candidate").trim()
                    val port = signal.payload.optInt("port", -1)
                    if (host.isBlank() || port !in 1..65535) return
                    val address = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return
                    val type = when (signal.payload.optString("candidateType", signal.payload.optString("type", "host")).lowercase()) {
                        "host" -> CandidateType.HOST
                        "srflx" -> CandidateType.SRFLX
                        else -> return
                    }
                    val id = signal.payload.optString("candidateId").ifBlank { "legacy-${address.hostAddress}:$port" }
                    remoteCandidates[id] = IceCandidate(
                        id, signal.payload.optString("foundation", id), type, address, port,
                        signal.payload.optInt("priority", candidatePriority(type)), incomingGeneration, sequence,
                    )
                }
            }
        }

        sendOffer()
        sendLocalCandidates()

        try {
            coroutineScope {
                val signalingJob = launch(Dispatchers.IO) {
                    while (isActive && !selected.get() && System.currentTimeMillis() < deadline) {
                        runCatching { signaling.receive(sessionId) }
                            .onFailure { lastFailure = if (it is LinkoSignalingException) "SIGNALING_RPC_${it.statusCode}" else "SIGNALING_RECEIVE_TIMEOUT" }
                            .getOrDefault(emptyList())
                            .forEach { processSignal(it) }
                        delay(70L)
                    }
                }

                val receiverJob = launch(Dispatchers.IO) {
                    while (isActive && !selected.get() && System.currentTimeMillis() < deadline) {
                        if (receiverPaused.get()) { delay(30L); continue }
                        try {
                            val received = EncryptedDatagramTunnel(
                                socket, InetSocketAddress("0.0.0.0", 9), sessionId, role, sessionKey,
                            ).receiveAny(450) ?: continue
                            when (received.type) {
                                EncryptedDatagramTunnel.PacketType.PING -> handlePing(
                                    received, socket, sessionId, sessionKey, role, localCandidates, generation, successfulPairs,
                                )
                                EncryptedDatagramTunnel.PacketType.PONG -> {
                                    val payload = decodePayload(received.payload) ?: continue
                                    if (payload.optString("ice").ifBlank { payload.optString("probe") } != "2") continue
                                    if (payload.optInt("generation", generation) != generation) continue
                                    val pending = pendingChecks.remove(payload.optString("transaction")) ?: continue
                                    val rtt = (System.currentTimeMillis() - pending.sentAt).coerceAtLeast(1L)
                                    successfulPairs[pending.pair.key] = Success(pending.pair.copy(endpoint = received.source), rtt, System.currentTimeMillis())
                                    Log.i(TAG, "ICE_CHECK_SUCCEEDED pair=${pending.pair.key} source=${received.source} rtt=${rtt}ms")
                                }
                                EncryptedDatagramTunnel.PacketType.HANDSHAKE -> {
                                    val payload = decodePayload(received.payload) ?: continue
                                    when (payload.optString("kind")) {
                                        "nominate" -> if (!controlling && payload.optInt("generation", generation) == generation) {
                                            val endpoint = received.source
                                            val localId = payload.optString("localCandidateId")
                                            val matches = successfulPairs.values.any { it.pair.local.id == localId && it.pair.endpoint == endpoint } || successfulPairs.values.any { it.pair.endpoint == endpoint }
                                            if (matches) {
                                                runCatching {
                                                    EncryptedDatagramTunnel(socket, endpoint, sessionId, role, sessionKey).send(
                                                        jsonBytes(JSONObject().put("kind", "nomination_ack").put("generation", generation).put("nominationId", payload.optString("nominationId"))),
                                                        EncryptedDatagramTunnel.PacketType.HANDSHAKE,
                                                    )
                                                    selectedPeer[0] = endpoint
                                                    Log.i(TAG, "ICE_NOMINATED_BY_REMOTE endpoint=$endpoint")
                                                }.onFailure { lastFailure = "NOMINATION_ACK_SEND_FAILED" }
                                            }
                                        }
                                        "final_ready" -> runCatching {
                                            EncryptedDatagramTunnel(socket, received.source, sessionId, role, sessionKey).send(
                                                jsonBytes(JSONObject().put("kind", "final_ready_ack").put("generation", generation).put("nonce", payload.optString("nonce"))),
                                                EncryptedDatagramTunnel.PacketType.HANDSHAKE,
                                            )
                                            selectedPeer[0] = received.source
                                            selected.set(true)
                                        }.onFailure { lastFailure = "FINAL_HANDSHAKE_ACK_FAILED" }
                                    }
                                }
                                else -> Unit
                            }
                        } catch (_: java.net.SocketTimeoutException) {
                            // normal polling timeout
                        } catch (_: Exception) {
                            lastFailure = "DIRECT_RECEIVE_FAILED"
                        }
                    }
                }

                while (isActive && !selected.get() && System.currentTimeMillis() < deadline) {
                    buildPairs(
                        localCandidates,
                        remoteCandidates.values.sortedWith(compareByDescending<IceCandidate> { it.priority }.thenBy { it.id }),
                    ).sortedByDescending { it.score }.take(MAX_PAIR_CHECKS).forEach { pair ->
                        if (launchedPairs.add(pair.key)) launchCheckWorker(this, pair, socket, sessionId, sessionKey, role, generation, deadline, pendingChecks)
                    }

                    if (controlling && successfulPairs.isNotEmpty()) {
                        val firstSuccessAt = successfulPairs.values.minOf { it.firstSeenAt }
                        if (System.currentTimeMillis() - firstSuccessAt >= FIRST_SUCCESS_GRACE_MS) {
                            val candidates = successfulPairs.values.sortedWith(compareByDescending<Success> { it.pair.score }.thenBy { it.rttMs })
                            for (success in candidates) {
                                if (System.currentTimeMillis() >= deadline) break
                                receiverPaused.set(true)
                                val nominationOk = try {
                                    nominate(success, UUID.randomUUID().toString(), socket, sessionId, sessionKey, role, generation, deadline)
                                } finally { receiverPaused.set(false) }
                                if (!nominationOk) continue
                                receiverPaused.set(true)
                                val finalOk = try {
                                    completeFinalHandshake(socket, success.pair.endpoint, sessionId, sessionKey, role, generation, deadline, success.pair.local.id, success.pair.remote.id)
                                } finally { receiverPaused.set(false) }
                                if (finalOk) {
                                    selectedPeer[0] = success.pair.endpoint
                                    selected.set(true)
                                    break
                                }
                            }
                        }
                    }
                    delay(60L)
                }
                signalingJob.cancel()
                receiverJob.cancel()
            }
        } catch (error: kotlinx.coroutines.CancellationException) {
            socket.close()
            throw error
        } catch (error: Exception) {
            socket.close()
            throw LinkoNetworkException("DIRECT_NEGOTIATION_INTERNAL_FAILURE", (error as? LinkoSignalingException)?.statusCode)
        }

        val peer = selectedPeer[0]
        if (selected.get() && peer != null) {
            Log.i(TAG, "ICE_CONNECTED session=$sessionId generation=$generation peer=$peer")
            Result(socket, peer)
        } else {
            socket.close()
            val reason = when {
                remoteCandidates.isEmpty() -> "NO_REMOTE_CANDIDATE"
                successfulPairs.isEmpty() -> lastFailure.ifBlank { "DIRECT_UDP_BLOCKED" }
                else -> "NOMINATION_FAILED"
            }
            Log.e(TAG, "ICE_FAILED session=$sessionId generation=$generation reason=$reason local=${localCandidates.size} remote=${remoteCandidates.size} successful=${successfulPairs.size}")
            throw LinkoNetworkException(reason)
        }
    }

    private fun launchCheckWorker(
        scope: CoroutineScope, pair: CandidatePair, socket: DatagramSocket, sessionId: String,
        sessionKey: ByteArray, role: EncryptedDatagramTunnel.Role, generation: Int,
        deadline: Long, pendingChecks: ConcurrentHashMap<String, PendingCheck>,
    ) {
        scope.launch(Dispatchers.IO) {
            repeat(CHECK_RETRIES) { attempt ->
                if (System.currentTimeMillis() >= deadline) return@launch
                val transaction = UUID.randomUUID().toString()
                pendingChecks[transaction] = PendingCheck(transaction, pair, System.currentTimeMillis())
                try {
                    EncryptedDatagramTunnel(socket, pair.endpoint, sessionId, role, sessionKey).send(
                        jsonBytes(JSONObject().put("ice", 2).put("check", true).put("transaction", transaction)
                            .put("generation", generation).put("localCandidateId", pair.local.id)
                            .put("remoteCandidateId", pair.remote.id).put("attempt", attempt + 1)),
                        EncryptedDatagramTunnel.PacketType.PING,
                    )
                } catch (_: Exception) {
                    pendingChecks.remove(transaction)
                    return@launch
                }
                delay(CHECK_INTERVAL_MS)
                if (!pendingChecks.containsKey(transaction)) return@launch
                pendingChecks.remove(transaction)
            }
        }
    }

    private suspend fun nominate(
        success: Success, nominationId: String, socket: DatagramSocket, sessionId: String,
        sessionKey: ByteArray, role: EncryptedDatagramTunnel.Role, generation: Int, deadline: Long,
    ): Boolean {
        val payload = jsonBytes(JSONObject().put("kind", "nominate").put("generation", generation)
            .put("nominationId", nominationId).put("localCandidateId", success.pair.local.id)
            .put("remoteCandidateId", success.pair.remote.id).put("score", success.pair.score).put("rttMs", success.rttMs))
        repeat(NOMINATION_RETRIES) {
            if (System.currentTimeMillis() >= deadline) return false
            if (!runCatching { EncryptedDatagramTunnel(socket, success.pair.endpoint, sessionId, role, sessionKey).send(payload, EncryptedDatagramTunnel.PacketType.HANDSHAKE); true }.getOrDefault(false)) return@repeat
            val until = minOf(deadline, System.currentTimeMillis() + NOMINATION_ACK_TIMEOUT_MS)
            while (System.currentTimeMillis() < until) {
                val received = runCatching { EncryptedDatagramTunnel(socket, InetSocketAddress("0.0.0.0", 9), sessionId, role, sessionKey).receiveAny(180) }.getOrNull() ?: continue
                if (received.type != EncryptedDatagramTunnel.PacketType.HANDSHAKE || received.source != success.pair.endpoint) continue
                val ack = decodePayload(received.payload) ?: continue
                if (ack.optString("kind") == "nomination_ack" && ack.optString("nominationId") == nominationId) return true
            }
        }
        return false
    }

    private suspend fun completeFinalHandshake(
        socket: DatagramSocket, endpoint: InetSocketAddress, sessionId: String, sessionKey: ByteArray,
        role: EncryptedDatagramTunnel.Role, generation: Int, deadline: Long,
        localCandidateId: String, remoteCandidateId: String,
    ): Boolean {
        val nonce = randomToken(18)
        val payload = jsonBytes(JSONObject().put("kind", "final_ready").put("generation", generation)
            .put("nonce", nonce).put("localCandidateId", localCandidateId).put("remoteCandidateId", remoteCandidateId)
            .put("sentAt", System.currentTimeMillis()))
        repeat(3) {
            if (System.currentTimeMillis() >= deadline) return false
            runCatching { EncryptedDatagramTunnel(socket, endpoint, sessionId, role, sessionKey).send(payload, EncryptedDatagramTunnel.PacketType.HANDSHAKE) }
            val until = minOf(deadline, System.currentTimeMillis() + HANDSHAKE_TIMEOUT_MS)
            while (System.currentTimeMillis() < until) {
                val received = runCatching { EncryptedDatagramTunnel(socket, InetSocketAddress("0.0.0.0", 9), sessionId, role, sessionKey).receiveAny(180) }.getOrNull() ?: continue
                if (received.type != EncryptedDatagramTunnel.PacketType.HANDSHAKE || received.source != endpoint) continue
                val ack = decodePayload(received.payload) ?: continue
                if (ack.optString("kind") == "final_ready_ack" && ack.optString("nonce") == nonce) return true
            }
        }
        return false
    }

    private fun handlePing(
        received: EncryptedDatagramTunnel.ReceivedPacket, socket: DatagramSocket, sessionId: String,
        sessionKey: ByteArray, role: EncryptedDatagramTunnel.Role, localCandidates: List<IceCandidate>,
        generation: Int, successfulPairs: ConcurrentHashMap<String, Success>,
    ) {
        val payload = decodePayload(received.payload) ?: return
        if (!payload.optBoolean("check", false) || payload.optString("ice") != "2") return
        if (payload.optInt("generation", generation) != generation) return
        val transaction = payload.optString("transaction").ifBlank { return }
        // The sender's localCandidateId identifies the sender's candidate, not ours.
        // Never look it up in our local candidate list. The datagram source is the
        // authoritative reflexive endpoint; choose a compatible local interface.
        val local = localCandidates.firstOrNull { (it.endpoint.address is Inet6Address) == (received.source.address is Inet6Address) }
            ?: localCandidates.firstOrNull()
            ?: return
        val remote = IceCandidate(
            id = "prflx-${received.source.hostString}:${received.source.port}", foundation = "prflx",
            type = CandidateType.PRFLX, address = received.source.address, port = received.source.port,
            priority = candidatePriority(CandidateType.PRFLX), generation = generation, sequence = Long.MAX_VALUE,
        )
        val pair = CandidatePair(local, remote, received.source, pairScore(local, remote))
        successfulPairs.putIfAbsent(pair.key, Success(pair, 0L, System.currentTimeMillis()))
        runCatching {
            EncryptedDatagramTunnel(socket, received.source, sessionId, role, sessionKey).send(
                jsonBytes(JSONObject().put("ice", "2").put("probe", "2").put("transaction", transaction).put("generation", generation).put("candidateId", local.id)),
                EncryptedDatagramTunnel.PacketType.PONG,
            )
        }.onFailure { Log.w(TAG, "ICE_PONG_SEND_FAILED source=${received.source}: ${it.message}") }
    }

    private fun buildPairs(local: List<IceCandidate>, remote: List<IceCandidate>): List<CandidatePair> =
        local.flatMap { l -> remote.map { r -> CandidatePair(l, r, r.endpoint, pairScore(l, r)) } }
            .filter { (it.local.endpoint.address is Inet6Address) == (it.remote.endpoint.address is Inet6Address) }

    private fun pairScore(local: IceCandidate, remote: IceCandidate): Long {
        val localBonus = when (local.type) { CandidateType.HOST -> 200_000L; CandidateType.SRFLX -> 100_000L; CandidateType.PRFLX -> 90_000L }
        val remoteBonus = when (remote.type) { CandidateType.HOST -> 200_000L; CandidateType.SRFLX -> 100_000L; CandidateType.PRFLX -> 90_000L }
        return localBonus + remoteBonus + local.priority + remote.priority
    }

    private fun candidatePriority(type: CandidateType): Int = when (type) {
        CandidateType.HOST -> 126 * 256
        CandidateType.SRFLX -> 100 * 256
        CandidateType.PRFLX -> 90 * 256
    }

    private suspend fun gatherCandidates(socket: DatagramSocket, deadline: Long, generation: Int): List<IceCandidate> {
        val result = linkedMapOf<String, IceCandidate>()
        val localPort = socket.localPort.takeIf { it > 0 } ?: return emptyList()
        var sequence = 1L
        fun add(address: InetAddress, type: CandidateType, port: Int = localPort) {
            if (address.isLoopbackAddress || address.isLinkLocalAddress || address.isMulticastAddress || address.isAnyLocalAddress) return
            val candidate = IceCandidate(
                "${type.name.lowercase()}-${address.hostAddress}:$port",
                "${type.name.lowercase()}-${address.javaClass.simpleName}-${address.hostAddress.hashCode()}",
                type, address, port, candidatePriority(type), generation, sequence++,
            )
            result.putIfAbsent("${address.hostAddress}:$port", candidate)
        }
        runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { iface ->
                if (!iface.isUp || iface.isLoopback || iface.isVirtual) return@forEach
                iface.inetAddresses.toList().forEach { address -> add(address, CandidateType.HOST) }
            }
        }.onFailure { Log.w(TAG, "HOST_CANDIDATE_GATHER_FAILED: ${it.message}") }

        for ((host, port) in LinkoStunClient.DEFAULT_STUN_SERVERS) {
            if (System.currentTimeMillis() >= deadline) break
            runCatching { LinkoStunClient.discover(socket, host, port, 1_200) }
                .getOrNull()?.let { add(it.address, CandidateType.SRFLX, it.port) }
        }
        return result.values.sortedWith(compareByDescending<IceCandidate> { it.priority }.thenBy { it.id })
    }

    private fun decodePayload(payload: ByteArray): JSONObject? = runCatching { JSONObject(payload.toString(Charsets.UTF_8)) }.getOrNull()
    private fun jsonBytes(json: JSONObject): ByteArray = json.toString().toByteArray(Charsets.UTF_8)
    private fun randomToken(length: Int): String = ByteArray(length).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private const val TAG = "LINKO_ICE"
}
