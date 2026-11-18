package com.sikorokoro44.messengerpro.data.network

import kotlin.random.Random

/** Applies jitter to an exponential delay without allowing it to exceed the configured ceiling. */
interface BackoffJitter {
    fun apply(exponentialDelayMs: Long): Long
}

/** Full jitter spreads retries over the whole bounded exponential window to avoid thundering herds. */
class FullJitter(private val random: Random = Random.Default) : BackoffJitter {
    override fun apply(exponentialDelayMs: Long): Long =
        random.nextLong(1L, exponentialDelayMs + 1L)
}

/** Bounded, jittered exponential backoff for transient failures; no unbounded retry loop. */
data class RetryPolicy(
    val maxAttempts: Int = 3,
    val baseDelayMs: Long = 500L,
    val maxDelayMs: Long = 8_000L,
    val jitter: BackoffJitter = FullJitter(),
) {
    init {
        require(maxAttempts in 1..5) { "Retry attempts must be between 1 and 5" }
        require(baseDelayMs in 1L..maxDelayMs) { "Base delay must be positive and bounded" }
        require(maxDelayMs <= 30_000L) { "Maximum retry delay must be at most 30 seconds" }
    }

    /** Retries are allowed only for transient failures of idempotent work below the hard cap. */
    fun shouldRetry(attempt: Int, transient: Boolean, idempotent: Boolean): Boolean =
        transient && idempotent && attempt in 1 until maxAttempts

    /** [attempt] is one-based; the exponential window doubles before full jitter is applied. */
    fun delayFor(attempt: Int): Long {
        require(attempt >= 1) { "Attempt must be one-based" }
        var exponential = baseDelayMs
        repeat(attempt - 1) { exponential = minOf(maxDelayMs, exponential * 2) }
        return jitter.apply(exponential).coerceIn(1L, maxDelayMs)
    }
}

/** Caps the total number of network calls across a user-initiated sign-in attempt. */
class RetryBudget(private val maxCalls: Int = 4) {
    private var used = 0

    fun canSpend(): Boolean = used < maxCalls

    fun spend() {
        require(canSpend()) { "Retry budget exhausted" }
        used++
    }

    fun used(): Int = used
}
