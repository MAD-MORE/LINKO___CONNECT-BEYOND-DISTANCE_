package com.linkshare.app.network

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LinkoConnectionDiagnosticsTest {
    @BeforeTest
    fun setup() {
        LinkoConnectionDiagnostics.reset()
    }

    @AfterTest
    fun teardown() {
        LinkoConnectionDiagnostics.reset()
    }

    @Test
    fun directFailurePreservesReasonAndCounts() {
        LinkoConnectionDiagnostics.begin(sessionId = "session", peerName = "friend")
        LinkoConnectionDiagnostics.record(
            stage = ConnectionStage.ICE_CHECKING,
            event = "ICE_CHECK_SUCCEEDED",
            message = "UDP connectivity check succeeded (12 ms)",
            severity = ConnectionSeverity.SUCCESS,
            sessionId = "session",
            metadata = mapOf("localCandidates" to "4", "remoteCandidates" to "3", "checks" to "8", "successfulChecks" to "1"),
        )
        LinkoConnectionDiagnostics.fail(
            "DIRECT_UDP_BLOCKED",
            sessionId = "session",
            metadata = mapOf("localCandidates" to "4", "remoteCandidates" to "3", "checks" to "24", "successfulChecks" to "0"),
        )

        val snapshot = LinkoConnectionDiagnostics.snapshot.value
        assertEquals(ConnectionStage.FAILED, snapshot.stage)
        assertEquals("DIRECT_UDP_BLOCKED", snapshot.failureReason)
        assertEquals(4, snapshot.localCandidates)
        assertEquals(3, snapshot.remoteCandidates)
        assertEquals(24, snapshot.checks)
        assertEquals(0, snapshot.successfulChecks)
        assertTrue(snapshot.recentEvents.any { it.event == "ICE_CHECK_SUCCEEDED" })
        assertTrue(snapshot.recentEvents.any { it.event == "CONNECTION_FAILED" })
    }

    @Test
    fun stateMappingReflectsRealTransportStages() {
        assertEquals(ConnectionStage.ICE_GATHERING, LinkoConnectionDiagnostics.stageForState("ice_gathered"))
        assertEquals(ConnectionStage.ICE_CHECKING, LinkoConnectionDiagnostics.stageForState("direct_connecting"))
        assertEquals(ConnectionStage.NOMINATING, LinkoConnectionDiagnostics.stageForState("nomination"))
        assertEquals(ConnectionStage.HANDSHAKE, LinkoConnectionDiagnostics.stageForState("final_ready"))
        assertEquals(ConnectionStage.PACKET_FLOW, LinkoConnectionDiagnostics.stageForState("packet_flow"))
        assertEquals(ConnectionStage.CONNECTED, LinkoConnectionDiagnostics.stageForState("connected"))
    }
}
