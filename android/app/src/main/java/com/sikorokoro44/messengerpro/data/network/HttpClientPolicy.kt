package com.sikorokoro44.messengerpro.data.network

/** Bounded connection policy so a hostile or broken endpoint cannot hang the identity flow. */
data class HttpClientPolicy(
    val connectTimeoutMs: Long = 10_000L,
    val readTimeoutMs: Long = 15_000L,
    val writeTimeoutMs: Long = 15_000L,
    val callTimeoutMs: Long = 20_000L,
) {
    init {
        listOf(connectTimeoutMs, readTimeoutMs, writeTimeoutMs, callTimeoutMs).forEach {
            require(it in 1L..MAX_TIMEOUT_MS) { "HTTP timeout must be between 1 and $MAX_TIMEOUT_MS ms" }
        }
        require(callTimeoutMs >= connectTimeoutMs) { "Call timeout must cover connection setup" }
    }

    companion object {
        const val MAX_TIMEOUT_MS = 120_000L
        val DEFAULT = HttpClientPolicy()
    }
}
