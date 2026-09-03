from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace(path: str, old: str, new: str, count: int = -1) -> None:
    p = ROOT / path
    text = p.read_text()
    if old not in text:
        raise SystemExit(f"Patch anchor not found in {path}: {old[:120]!r}")
    p.write_text(text.replace(old, new, count))


# Bridge: retain exact low-level failure reason and publish every state into diagnostics.
replace(
    "android/app/src/main/java/com/linkshare/app/network/LinkoEngineBridge.kt",
    "    fun reportTunnelState(state: String, detail: String? = null) = publish(state, detail)\n",
    "    fun reportTunnelState(state: String, detail: String? = null) = publish(state, detail)\n\n    fun reportConnectionDiagnostic(\n        stage: ConnectionStage,\n        event: String,\n        message: String,\n        severity: ConnectionSeverity = ConnectionSeverity.INFO,\n        metadata: Map<String, String> = emptyMap(),\n    ) {\n        LinkoConnectionDiagnostics.record(stage, event, message, severity, _connection.value.sessionId, metadata)\n    }\n",
)
replace(
    "android/app/src/main/java/com/linkshare/app/network/LinkoEngineBridge.kt",
    "        watchFriendPresence(userId)\n        val generation = beginNewConnection()\n",
    "        watchFriendPresence(userId)\n        LinkoConnectionDiagnostics.begin(peerName = friendName)\n        val generation = beginNewConnection()\n",
)
replace(
    "android/app/src/main/java/com/linkshare/app/network/LinkoEngineBridge.kt",
    "        val generation = beginNewConnection()\n        val engineScope = scope ?: return publishAndNotify(\"engine_scope_unavailable\", onState)\n\n        connectionJob = engineScope.launch {\n            try {\n                publishAndNotify(\"connecting\", onState)\n                assertCurrent(generation)\n                control.ensureRegistered()\n                assertCurrent(generation)\n                val session = control.requestSession(providerId)",
    "        val generation = beginNewConnection()\n        LinkoConnectionDiagnostics.begin(peerName = _connection.value.peerDisplayName)\n        val engineScope = scope ?: return publishAndNotify(\"engine_scope_unavailable\", onState)\n\n        connectionJob = engineScope.launch {\n            try {\n                publishAndNotify(\"connecting\", onState)\n                assertCurrent(generation)\n                control.ensureRegistered()\n                assertCurrent(generation)\n                val session = control.requestSession(providerId)",
)
replace(
    "android/app/src/main/java/com/linkshare/app/network/LinkoEngineBridge.kt",
    "        Log.e(TAG, \"ENGINE_CONNECTION_FAILED generation=$generation session=${sessionId ?: \"none\"} reason=$message\", error)\n        terminateReceiverForFailure(sessionId, generation)\n        publishAndNotify(normalizeFailureState(message), onState)\n",
    "        Log.e(TAG, \"ENGINE_CONNECTION_FAILED generation=$generation session=${sessionId ?: \"none\"} reason=$message\", error)\n        LinkoConnectionDiagnostics.fail(message, sessionId)\n        terminateReceiverForFailure(sessionId, generation)\n        publishAndNotify(normalizeFailureState(message), onState, message)\n",
)
replace(
    "android/app/src/main/java/com/linkshare/app/network/LinkoEngineBridge.kt",
    "    private fun publishAndNotify(state: String, onState: (String) -> Unit) {\n        publish(state)\n        notifyOnMain(onState, state)\n    }\n",
    "    private fun publishAndNotify(state: String, onState: (String) -> Unit, detail: String? = null) {\n        publish(state, detail)\n        notifyOnMain(onState, state)\n    }\n",
)
replace(
    "android/app/src/main/java/com/linkshare/app/network/LinkoEngineBridge.kt",
    "        _connection.update {\n            it.copy(\n                phase = phase,\n                detail = message,\n                error = if (phase == LinkoConnectionPhase.Failed) message else null,\n            )\n        }\n    }\n",
    "        _connection.update {\n            it.copy(\n                phase = phase,\n                detail = message,\n                error = if (phase == LinkoConnectionPhase.Failed) message else null,\n            )\n        }\n\n        val severity = when {\n            phase == LinkoConnectionPhase.Failed -> ConnectionSeverity.ERROR\n            phase == LinkoConnectionPhase.Connected -> ConnectionSeverity.SUCCESS\n            state == \"signaling_retry\" -> ConnectionSeverity.WARNING\n            else -> ConnectionSeverity.INFO\n        }\n        LinkoConnectionDiagnostics.record(\n            stage = LinkoConnectionDiagnostics.stageForState(state),\n            event = state.uppercase(),\n            message = message,\n            severity = severity,\n            sessionId = _connection.value.sessionId,\n        )\n    }\n",
)

# Direct P2P: expose real ICE milestones and aggregate check counts without secrets.
replace(
    "android/app/src/main/java/com/linkshare/app/network/DirectP2pNegotiator.kt",
    "import java.util.concurrent.atomic.AtomicBoolean\n",
    "import java.util.concurrent.atomic.AtomicBoolean\nimport java.util.concurrent.atomic.AtomicInteger\n",
)
replace(
    "        Log.i(TAG, \"ICE_GATHERED session=$sessionId generation=$generation candidates=${localCandidates.size} port=${socket.localPort}\")\n",
    "        Log.i(TAG, \"ICE_GATHERED session=$sessionId generation=$generation candidates=${localCandidates.size} port=${socket.localPort}\")\n        LinkoConnectionDiagnostics.record(\n            ConnectionStage.ICE_GATHERING, \"ICE_GATHERED\",\n            \"Gathered ${localCandidates.size} local UDP candidates\",\n            ConnectionSeverity.SUCCESS, sessionId,\n            mapOf(\"localCandidates\" to localCandidates.size.toString()),\n        )\n",
)
replace(
    "        var lastFailure = \"DIRECT_CHECK_TIMEOUT\"\n",
    "        var lastFailure = \"DIRECT_CHECK_TIMEOUT\"\n        val connectivityChecks = AtomicInteger(0)\n",
)
replace(
    "                        lastFailure = if (it is LinkoSignalingException) \"SIGNALING_RPC_${it.statusCode}\" else \"SIGNALING_RECEIVE_TIMEOUT\" },\n                            .getOrDefault(emptyList()).forEach { processSignal(it) }\n",
    "                        lastFailure = if (it is LinkoSignalingException) \"SIGNALING_RPC_${it.statusCode}\" else \"SIGNALING_RECEIVE_TIMEOUT\" },\n                            .getOrDefault(emptyList()).forEach { processSignal(it) }\n",
)
replace(
    "                    remoteCandidates[id] = IceCandidate(id, signal.payload.optString(\"foundation\", id), type, address, port,\n                        signal.payload.optInt(\"priority\", candidatePriority(type)), incomingGeneration, sequence)\n",
    "                    remoteCandidates[id] = IceCandidate(id, signal.payload.optString(\"foundation\", id), type, address, port,\n                        signal.payload.optInt(\"priority\", candidatePriority(type)), incomingGeneration, sequence)\n                    LinkoConnectionDiagnostics.record(\n                        ConnectionStage.ICE_GATHERING, \"ICE_REMOTE_CANDIDATE\",\n                        \"Received remote ${type.name.lowercase()} candidate\",\n                        ConnectionSeverity.INFO, sessionId,\n                        mapOf(\"localCandidates\" to localCandidates.size.toString(), \"remoteCandidates\" to remoteCandidates.size.toString()),\n                    )\n",
)
replace(
    "                                    successfulPairs[pending.pair.key] = Success(pending.pair.copy(endpoint = received.source), rtt, System.currentTimeMillis())\n                                    Log.i(TAG, \"ICE_CHECK_SUCCEEDED pair=${pending.pair.key} source=${received.source} rtt=${rtt}ms\")\n",
    "                                    successfulPairs[pending.pair.key] = Success(pending.pair.copy(endpoint = received.source), rtt, System.currentTimeMillis())\n                                    Log.i(TAG, \"ICE_CHECK_SUCCEEDED pair=${pending.pair.key} source=${received.source} rtt=${rtt}ms\")\n                                    LinkoConnectionDiagnostics.record(\n                                        ConnectionStage.ICE_CHECKING, \"ICE_CHECK_SUCCEEDED\",\n                                        \"UDP connectivity check succeeded (${rtt} ms)\", ConnectionSeverity.SUCCESS, sessionId,\n                                        mapOf(\"localCandidates\" to localCandidates.size.toString(), \"remoteCandidates\" to remoteCandidates.size.toString(),\n                                            \"checks\" to connectivityChecks.get().toString(), \"successfulChecks\" to successfulPairs.size.toString()),\n                                    )\n",
)
replace(
    "                val reason = when {\n                remoteCandidates.isEmpty() -> \"NO_REMOTE_CANDIDATE\"\n                successfulPairs.isEmpty() -> lastFailure.ifBlank { \"DIRECT_UDP_BLOCKED\" }\n                else -> \"NOMINATION_FAILED\"\n            }\n",
    "                val reason = when {\n                remoteCandidates.isEmpty() -> \"NO_REMOTE_CANDIDATE\"\n                successfulPairs.isEmpty() -> lastFailure.ifBlank { \"DIRECT_UDP_BLOCKED\" }\n                else -> \"NOMINATION_FAILED\"\n            }\n",
)
# Exact failure anchor (indentation in current file is stable).
replace(
    "            Log.e(TAG, \"ICE_FAILED session=$sessionId generation=$generation reason=$reason local=${localCandidates.size} remote=${remoteCandidates.size} successful=${successfulPairs.size}\")\n            throw LinkoNetworkException(reason)\n",
    "            Log.e(TAG, \"ICE_FAILED session=$sessionId generation=$generation reason=$reason local=${localCandidates.size} remote=${remoteCandidates.size} successful=${successfulPairs.size}\")\n            LinkoConnectionDiagnostics.fail(\n                reason, sessionId,\n                mapOf(\"localCandidates\" to localCandidates.size.toString(), \"remoteCandidates\" to remoteCandidates.size.toString(),\n                    \"checks\" to connectivityChecks.get().toString(), \"successfulChecks\" to successfulPairs.size.toString()),\n            )\n            throw LinkoNetworkException(reason)\n",
)
replace(
    "                val transaction = UUID.randomUUID().toString()\n                pendingChecks[transaction] = PendingCheck(transaction, pair, System.currentTimeMillis())\n",
    "                val transaction = UUID.randomUUID().toString()\n                connectivityChecks.incrementAndGet()\n                LinkoConnectionDiagnostics.record(ConnectionStage.ICE_CHECKING, \"ICE_CHECK_ATTEMPT\",\n                    \"Checking candidate pair ${pair.key}\", ConnectionSeverity.INFO, sessionId,\n                    mapOf(\"checks\" to connectivityChecks.get().toString()))\n                pendingChecks[transaction] = PendingCheck(transaction, pair, System.currentTimeMillis())\n",
)
replace(
    "                                            Log.i(TAG, \"ICE_NOMINATION_RECEIVED localGeneration=$generation remoteGeneration=${remoteGeneration ?: -1} senderGeneration=$senderGeneration\")\n",
    "                                            Log.i(TAG, \"ICE_NOMINATION_RECEIVED localGeneration=$generation remoteGeneration=${remoteGeneration ?: -1} senderGeneration=$senderGeneration\")\n                                            LinkoConnectionDiagnostics.record(ConnectionStage.NOMINATING, \"ICE_NOMINATION_RECEIVED\",\n                                                \"Remote selected a validated direct path\", ConnectionSeverity.INFO, sessionId,\n                                                mapOf(\"successfulChecks\" to successfulPairs.size.toString()))\n",
)
replace(
    "                                                Log.w(TAG, \"ICE_NOMINATION_REJECTED reason=UNVALIDATED_ENDPOINT endpoint=$endpoint\")\n",
    "                                                Log.w(TAG, \"ICE_NOMINATION_REJECTED reason=UNVALIDATED_ENDPOINT endpoint=$endpoint\")\n                                                LinkoConnectionDiagnostics.record(ConnectionStage.NOMINATING, \"ICE_NOMINATION_REJECTED\",\n                                                    \"Rejected nomination because endpoint was not validated\", ConnectionSeverity.WARNING, sessionId)\n",
)
replace(
    "                return true\n            }\n        }\n        Log.w(TAG, \"ICE_NOMINATION_FAILED nominationId=$nominationId endpoint=${success.pair.endpoint}\")\n",
    "                LinkoConnectionDiagnostics.record(ConnectionStage.NOMINATING, \"ICE_NOMINATION_ACK_RECEIVED\",\n                    \"Direct path nomination acknowledged\", ConnectionSeverity.SUCCESS, sessionId)\n                return true\n            }\n        }\n        LinkoConnectionDiagnostics.record(ConnectionStage.NOMINATING, \"ICE_NOMINATION_FAILED\",\n            \"Direct path nomination was not acknowledged\", ConnectionSeverity.ERROR, sessionId)\n        Log.w(TAG, \"ICE_NOMINATION_FAILED nominationId=$nominationId endpoint=${success.pair.endpoint}\")\n",
)
replace(
    "                Log.i(TAG, \"ICE_FINAL_READY_ACK_RECEIVED nonce=$nonce peer=${received.source}\")\n                return true\n",
    "                Log.i(TAG, \"ICE_FINAL_READY_ACK_RECEIVED nonce=$nonce peer=${received.source}\")\n                LinkoConnectionDiagnostics.record(ConnectionStage.HANDSHAKE, \"ICE_FINAL_READY_ACK_RECEIVED\",\n                    \"Final direct handshake acknowledged\", ConnectionSeverity.SUCCESS, sessionId)\n                return true\n",
)
replace(
    "        Log.w(TAG, \"ICE_FINAL_HANDSHAKE_FAILED peer=$peer\")\n        return false\n",
    "        LinkoConnectionDiagnostics.record(ConnectionStage.HANDSHAKE, \"ICE_FINAL_HANDSHAKE_FAILED\",\n            \"Final direct handshake was not acknowledged\", ConnectionSeverity.ERROR, sessionId)\n        Log.w(TAG, \"ICE_FINAL_HANDSHAKE_FAILED peer=$peer\")\n        return false\n",
)

# UI: show actual stage, progress, counts, and last diagnostic events.
replace(
    "import com.linkshare.app.network.LinkoEngineBridge\n",
    "import com.linkshare.app.network.LinkoEngineBridge\nimport com.linkshare.app.network.LinkoConnectionDiagnostics\nimport com.linkshare.app.network.ConnectionStage\n",
)
replace(
    "    val state by LinkoEngineBridge.connection.collectAsStateWithLifecycle()\n",
    "    val state by LinkoEngineBridge.connection.collectAsStateWithLifecycle()\n    val diagnostics by LinkoConnectionDiagnostics.snapshot.collectAsStateWithLifecycle()\n",
)
replace(
    "            Ring(\n                color,\n                160.dp,\n                pulse = state.phase != LinkoConnectionPhase.Idle && state.phase != LinkoConnectionPhase.Connected && state.phase != LinkoConnectionPhase.Failed,\n                label = when (state.phase) {\n                    LinkoConnectionPhase.Connected -> \"LIVE\"\n                    LinkoConnectionPhase.Failed -> \"STOPPED\"\n                    LinkoConnectionPhase.Signaling -> \"WAITING\"\n                    else -> \"SYNCING\"\n                }\n            )\n",
    "            Ring(\n                color,\n                160.dp,\n                pulse = state.phase != LinkoConnectionPhase.Idle && state.phase != LinkoConnectionPhase.Connected && state.phase != LinkoConnectionPhase.Failed,\n                label = when {\n                    state.phase == LinkoConnectionPhase.Connected -> \"LIVE\"\n                    state.phase == LinkoConnectionPhase.Failed -> \"FAILED\"\n                    state.isProvider -> \"SHARING\"\n                    diagnostics.stage == ConnectionStage.ICE_CHECKING -> \"CHECKING\"\n                    diagnostics.stage == ConnectionStage.HANDSHAKE -> \"HANDSHAKE\"\n                    diagnostics.stage == ConnectionStage.NOMINATING -> \"NOMINATE\"\n                    diagnostics.stage == ConnectionStage.PACKET_FLOW -> \"PACKET\"\n                    diagnostics.stage == ConnectionStage.TUNNEL_STARTING -> \"TUNNEL\"\n                    state.phase == LinkoConnectionPhase.Signaling -> \"WAITING\"\n                    else -> \"SYNCING\"\n                }\n            )\n",
)
replace(
    "            Text(\n                when (state.phase) {\n",
    "            Text(\n                when {\n                    state.phase == LinkoConnectionPhase.Failed -> \"DIRECT CONNECTION FAILED\"\n                    diagnostics.stage == ConnectionStage.ICE_CHECKING -> \"CHECKING DIRECT UDP PATH\"\n                    diagnostics.stage == ConnectionStage.NOMINATING -> \"SELECTING BEST DIRECT PATH\"\n                    diagnostics.stage == ConnectionStage.HANDSHAKE -> \"VERIFYING DIRECT PATH\"\n                    diagnostics.stage == ConnectionStage.PACKET_FLOW -> \"VERIFYING INTERNET PACKETS\"\n",
)
# Restore the original when branches' Kotlin syntax by replacing the old leading cases in the altered expression.
replace(
    "                    LinkoConnectionPhase.Connected -> \"TUNNEL CONNECTED\"\n                    LinkoConnectionPhase.Failed -> \"CONNECTION STOPPED\"\n                    LinkoConnectionPhase.Signaling -> \"WAITING FOR FRIEND\"\n                    else -> \"ESTABLISHING CONNECTION\"\n                },\n",
    "                    state.phase == LinkoConnectionPhase.Connected -> \"TUNNEL CONNECTED\"\n                    state.phase == LinkoConnectionPhase.Signaling -> \"WAITING FOR FRIEND\"\n                    else -> \"ESTABLISHING CONNECTION\"\n                },\n",
)
replace(
    "            if (!vpnGranted) {\n",
    "            // Real transport diagnostics; no fake timed progress.\n            LinkoCard {\n                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {\n                    Text(diagnostics.stage.name.replace('_', ' '), color = color, fontSize = 11.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)\n                    Text(\"${(diagnostics.progress * 100).toInt()}%\", color = TextPrimary, fontSize = 11.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)\n                }\n                Spacer(Modifier.height(6.dp))\n                Text(diagnostics.headline, color = TextSub, fontSize = 10.sp, fontFamily = JetBrainsMono, lineHeight = 15.sp)\n                if (diagnostics.localCandidates > 0 || diagnostics.remoteCandidates > 0 || diagnostics.checks > 0) {\n                    Spacer(Modifier.height(8.dp))\n                    Text(\"LOCAL ${diagnostics.localCandidates}  ·  REMOTE ${diagnostics.remoteCandidates}  ·  CHECKS ${diagnostics.checks}  ·  SUCCESS ${diagnostics.successfulChecks}\", color = TextMuted, fontSize = 9.sp, fontFamily = JetBrainsMono)\n                }\n                if (diagnostics.failureReason != null) {\n                    Spacer(Modifier.height(8.dp))\n                    Text(\"FAILURE: ${diagnostics.failureReason}\", color = Red, fontSize = 9.sp, fontFamily = JetBrainsMono, lineHeight = 14.sp)\n                }\n                if (diagnostics.recentEvents.isNotEmpty()) {\n                    Spacer(Modifier.height(8.dp))\n                    diagnostics.recentEvents.take(5).forEach { event ->\n                        Text(\"• ${event.event}: ${event.message}\", color = if (event.severity.name == \"ERROR\") Red else TextSub, fontSize = 9.sp, fontFamily = JetBrainsMono, lineHeight = 13.sp)\n                    }\n                }\n            }\n            Spacer(Modifier.height(10.dp))\n\n            if (!vpnGranted) {\n",
)
replace(
    "                        LinkoEngineBridge.disconnect()\n                        onFailed()\n",
    "                        LinkoEngineBridge.reconnect { }\n",
    1,
)

# Notification: keep a live, useful foreground update without flooding the notification history.
replace(
    "            scope.launch {\n                refreshFriendCache()\n                LinkoRealtimeManager.events.collect { event -> handle(event) }\n            }\n",
    "            scope.launch {\n                refreshFriendCache()\n                LinkoRealtimeManager.events.collect { event -> handle(event) }\n            }\n            scope.launch {\n                LinkoConnectionDiagnostics.snapshot.collect { snapshot ->\n                    if (snapshot.recentEvents.isNotEmpty()) postDiagnosticsNotification(snapshot)\n                }\n            }\n",
)
replace(
    "    private fun postSimpleNotification(title: String, message: String, notificationId: Int = BASE_NOTIFICATION_ID) {\n",
    "    private fun postDiagnosticsNotification(snapshot: ConnectionDiagnosticsSnapshot) {\n        val context = appContext ?: return\n        if (!notificationsAllowed(context)) return\n        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager\n        val terminal = snapshot.stage == ConnectionStage.CONNECTED || snapshot.stage == ConnectionStage.FAILED\n        val lines = snapshot.recentEvents.take(5).asReversed().joinToString(\"\\n\") { event ->\n            val mark = when (event.severity) { ConnectionSeverity.ERROR -> \"✗\"; ConnectionSeverity.SUCCESS -> \"✓\"; ConnectionSeverity.WARNING -> \"!\"; else -> \"·\" }\n            \"$mark ${event.message}\"\n        }\n        val counts = \"Local ${snapshot.localCandidates} · Remote ${snapshot.remoteCandidates} · Checks ${snapshot.checks} · Success ${snapshot.successfulChecks}\"\n        val title = if (snapshot.stage == ConnectionStage.FAILED) \"LINKO Connection Failed\" else if (snapshot.stage == ConnectionStage.CONNECTED) \"LINKO Connected\" else \"LINKO · ${snapshot.stage.name.replace('_', ' ')}\"\n        val text = if (snapshot.failureReason != null) \"${snapshot.failureReason}\\n$counts\" else \"${snapshot.headline}\\n$counts\"\n        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)\n        val pending = PendingIntent.getActivity(context, BASE_NOTIFICATION_ID + 70, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)\n        manager.notify(BASE_NOTIFICATION_ID + 70, NotificationCompat.Builder(context, CHANNEL_ID)\n            .setSmallIcon(R.drawable.ic_launcher)\n            .setContentTitle(title)\n            .setContentText(text)\n            .setStyle(NotificationCompat.BigTextStyle().bigText(\"$text\\n\\n$lines\"))\n            .setContentIntent(pending)\n            .setOngoing(!terminal)\n            .setAutoCancel(terminal)\n            .setPriority(NotificationCompat.PRIORITY_HIGH)\n            .build())\n    }\n\n    private fun postSimpleNotification(title: String, message: String, notificationId: Int = BASE_NOTIFICATION_ID) {\n",
)

# Ring: actually honor pulse/idle flags when delegating to the renderer.
replace(
    "        GlobeRadar(\n            color = color,\n            size = size,\n            label = label,\n        )\n",
    "        GlobeRadar(\n            color = color,\n            size = size,\n            pulse = pulse,\n            idle = idle,\n            label = label,\n        )\n",
)
replace(
    "fun GlobeRadar(color: Color, size: Dp = 190.dp, label: String? = \"ONLINE\") {\n",
    "fun GlobeRadar(\n    color: Color,\n    size: Dp = 190.dp,\n    pulse: Boolean = false,\n    idle: Boolean = false,\n    label: String? = \"ONLINE\",\n) {\n",
)
replace(
    "            drawCircle(color.copy(alpha = if (flowing) 0.075f else 0.045f), radius * 1.08f, center)\n",
    "            drawCircle(color.copy(alpha = if (idle) 0.025f else if (flowing) 0.075f else 0.045f), radius * 1.08f, center)\n            if (pulse) drawCircle(color.copy(alpha = 0.05f), radius * 1.16f, center)\n",
)

raise SystemExit(0)
