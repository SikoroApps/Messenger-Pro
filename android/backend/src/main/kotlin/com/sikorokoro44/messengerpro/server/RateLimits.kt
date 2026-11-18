package com.sikorokoro44.messengerpro.server

/** 60-second resend cooldown, enforced server-side with an injectable clock. */
class ResendCooldown(private val clock: Clock, private val cooldownMs: Long = 60_000L) {
    private val lastRequest = mutableMapOf<String, Long>()

    @Synchronized
    fun remainingSeconds(subjectHash: String): Long {
        val last = lastRequest[subjectHash] ?: return 0L
        val remaining = cooldownMs - (clock.nowMillis() - last)
        return if (remaining > 0) (remaining + 999) / 1000 else 0L
    }

    @Synchronized
    fun enforce(subjectHash: String): Long? {
        val remaining = remainingSeconds(subjectHash)
        return if (remaining > 0) remaining else null
    }

    @Synchronized
    fun mark(subjectHash: String) {
        lastRequest[subjectHash] = clock.nowMillis()
    }
}

/** Sliding-window limits. Subjects are pre-hashed so no raw phone/IP is retained. */
class RateLimiter(private val clock: Clock) {
    private data class Event(val scope: String, val subjectHash: String, val at: Long)
    private val events = mutableListOf<Event>()

    @Synchronized
    fun enforce(scope: String, subjectHash: String, limit: Int, windowMs: Long) {
        val now = clock.nowMillis()
        events.removeAll { now - it.at >= windowMs }
        val count = events.count { it.scope == scope && it.subjectHash == subjectHash }
        require(count < limit) { "rate_limited:$scope" }
        events += Event(scope, subjectHash, now)
    }

    @Synchronized
    fun clear() = events.clear()
}
