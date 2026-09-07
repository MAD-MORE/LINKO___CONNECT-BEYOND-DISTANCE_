package com.linkshare.app.network

/**
 * Transport-health classifier for a live LINKO session.
 *
 * The algorithm deliberately separates transient degradation from terminal loss:
 * - one bad sample is noise;
 * - repeated bad samples move the session through DEGRADED -> RECOVERING;
 * - only sustained failures reach LOST;
 * - healthy samples clear the failure streak before returning to HEALTHY.
 *
 * It is deterministic and has no Android dependencies so it can be unit tested thoroughly.
 */
class LinkoNetworkResilience(
    private val degradedFailureThreshold: Int = 2,
    private val recoveringFailureThreshold: Int = 3,
    private val lostFailureThreshold: Int = 8,
    private val healthyRecoveryThreshold: Int = 2,
) {
    enum class State { HEALTHY, DEGRADED, RECOVERING, LOST }

    data class Sample(
        val probeSucceeded: Boolean,
        val roundTripMs: Long = 0L,
        val packetLossPercent: Int = 0,
        val realtimeConnected: Boolean = true,
    )

    data class Decision(
        val state: State,
        val score: Int,
        val consecutiveFailures: Int,
        val consecutiveHealthy: Int,
        val shouldRetry: Boolean,
        val shouldTerminate: Boolean,
    )

    private var state = State.HEALTHY
    private var consecutiveFailures = 0
    private var consecutiveHealthy = 0

    fun reset() {
        state = State.HEALTHY
        consecutiveFailures = 0
        consecutiveHealthy = 0
    }

    fun observe(sample: Sample): Decision {
        val score = score(sample)
        val bad = isBad(sample, score)

        if (bad) {
            consecutiveFailures += 1
            consecutiveHealthy = 0
        } else {
            consecutiveHealthy += 1
            consecutiveFailures = 0
        }

        state = when (state) {
            State.HEALTHY -> when {
                consecutiveFailures >= recoveringFailureThreshold -> State.RECOVERING
                consecutiveFailures >= degradedFailureThreshold -> State.DEGRADED
                else -> State.HEALTHY
            }
            State.DEGRADED -> when {
                consecutiveFailures >= lostFailureThreshold -> State.LOST
                consecutiveFailures >= recoveringFailureThreshold -> State.RECOVERING
                consecutiveHealthy >= healthyRecoveryThreshold -> State.HEALTHY
                else -> State.DEGRADED
            }
            State.RECOVERING -> when {
                consecutiveFailures >= lostFailureThreshold -> State.LOST
                consecutiveHealthy >= healthyRecoveryThreshold -> State.HEALTHY
                else -> State.RECOVERING
            }
            State.LOST -> when {
                consecutiveHealthy >= healthyRecoveryThreshold -> State.HEALTHY
                else -> State.LOST
            }
        }

        return Decision(
            state = state,
            score = score,
            consecutiveFailures = consecutiveFailures,
            consecutiveHealthy = consecutiveHealthy,
            shouldRetry = state == State.DEGRADED || state == State.RECOVERING,
            shouldTerminate = state == State.LOST,
        )
    }

    private fun isBad(sample: Sample, score: Int): Boolean {
        if (!sample.probeSucceeded) return true
        if (!sample.realtimeConnected) return score < 65
        return score < 60
    }

    private fun score(sample: Sample): Int {
        var score = 100
        score -= when {
            sample.roundTripMs <= 0L -> 0
            sample.roundTripMs <= 250L -> 0
            sample.roundTripMs <= 600L -> 10
            sample.roundTripMs <= 1_200L -> 25
            else -> 40
        }
        score -= when {
            sample.packetLossPercent <= 2 -> 0
            sample.packetLossPercent <= 5 -> 10
            sample.packetLossPercent <= 12 -> 25
            else -> 40
        }
        if (!sample.realtimeConnected) score -= 15
        if (!sample.probeSucceeded) score -= 45
        return score.coerceIn(0, 100)
    }
}
