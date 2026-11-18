package com.sikorokoro44.messengerpro.data.network

/** Bounded exponential backoff for transient failures; no unbounded retry loop. */
data class RetryPolicy(
    val maxAttempts: Int = 3,
    val baseDelayMs: Long = 500L,
    val maxDelayMs: Long = 8_000L,
) {
    init {
        require(maxAttempts in 1..5) { "Retry attempts must be between 1 and 5" }
        require(baseDelayMs in 1L..maxDelayMs) { "Base delay must be positive and bounded" }
        require(maxDelayMs <= 30_000L) { "Maximum retry delay must be at most 30 seconds" }
    }

    /** [attempt] is one-based: the first failure produces [baseDelayMs]. */
    fun delayFor(attempt: Int): Long {
        require(attempt >= 1) { "Attempt must be one-based" }
        var delay = baseDelayMs
        repeat(attempt - 1) { delay = minOf(maxDelayMs, delay * 2) }
        return delay
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
