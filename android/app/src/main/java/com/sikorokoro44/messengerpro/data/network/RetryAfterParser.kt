package com.sikorokoro44.messengerpro.data.network

/** Parses server-supplied retry timing without trusting the device clock alone. */
object RetryAfterParser {
    private const val maxSeconds = 3_600L

    fun parse(headerValue: String?): Long? {
        val seconds = headerValue?.trim()?.toLongOrNull() ?: return null
        if (seconds <= 0) return 0L
        return minOf(seconds, maxSeconds)
    }
}
