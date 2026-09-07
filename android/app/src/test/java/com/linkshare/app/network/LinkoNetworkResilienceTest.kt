package com.linkshare.app.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkoNetworkResilienceTest {
    @Test
    fun oneMissDoesNotDisconnect() {
        val algorithm = LinkoNetworkResilience()

        val decision = algorithm.observe(LinkoNetworkResilience.Sample(probeSucceeded = false))

        assertEquals(LinkoNetworkResilience.State.HEALTHY, decision.state)
        assertFalse(decision.shouldTerminate)
    }

    @Test
    fun repeatedFailuresEnterRecoveryBeforeLoss() {
        val algorithm = LinkoNetworkResilience()

        algorithm.observe(LinkoNetworkResilience.Sample(probeSucceeded = false))
        val degraded = algorithm.observe(LinkoNetworkResilience.Sample(probeSucceeded = false))
        val recovering = algorithm.observe(LinkoNetworkResilience.Sample(probeSucceeded = false))

        assertEquals(LinkoNetworkResilience.State.DEGRADED, degraded.state)
        assertTrue(degraded.shouldRetry)
        assertEquals(LinkoNetworkResilience.State.RECOVERING, recovering.state)
        assertFalse(recovering.shouldTerminate)
    }

    @Test
    fun healthySamplesRecoverWithoutFlapping() {
        val algorithm = LinkoNetworkResilience()

        repeat(3) { algorithm.observe(LinkoNetworkResilience.Sample(probeSucceeded = false)) }
        val firstGood = algorithm.observe(LinkoNetworkResilience.Sample(probeSucceeded = true, roundTripMs = 120))
        val secondGood = algorithm.observe(LinkoNetworkResilience.Sample(probeSucceeded = true, roundTripMs = 120))

        assertEquals(LinkoNetworkResilience.State.RECOVERING, firstGood.state)
        assertEquals(LinkoNetworkResilience.State.HEALTHY, secondGood.state)
        assertFalse(secondGood.shouldRetry)
    }

    @Test
    fun sustainedFailuresEventuallyTerminate() {
        val algorithm = LinkoNetworkResilience()
        var decision = algorithm.observe(LinkoNetworkResilience.Sample(probeSucceeded = false))

        repeat(7) {
            decision = algorithm.observe(LinkoNetworkResilience.Sample(probeSucceeded = false))
        }

        assertEquals(LinkoNetworkResilience.State.LOST, decision.state)
        assertTrue(decision.shouldTerminate)
    }

    @Test
    fun highLatencyCanDegradeEvenWhenProbeSucceeds() {
        val algorithm = LinkoNetworkResilience()
        val sample = LinkoNetworkResilience.Sample(
            probeSucceeded = true,
            roundTripMs = 1_500,
            packetLossPercent = 15,
            realtimeConnected = true,
        )

        val first = algorithm.observe(sample)
        val second = algorithm.observe(sample)

        assertEquals(LinkoNetworkResilience.State.HEALTHY, first.state)
        assertEquals(LinkoNetworkResilience.State.DEGRADED, second.state)
    }
}
