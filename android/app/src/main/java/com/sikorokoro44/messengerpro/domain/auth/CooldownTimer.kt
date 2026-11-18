package com.sikorokoro44.messengerpro.domain.auth

/** Local cooldown display only. The server remains authoritative for resend enforcement. */
class CooldownTimer(private val clock: () -> Long = { System.currentTimeMillis() }) {
    private var endsAtMillis = 0L

    fun start(seconds: Long) {
        endsAtMillis = clock() + maxOf(0L, seconds) * 1_000L
    }

    fun clear() {
        endsAtMillis = 0L
    }

    fun remainingSeconds(): Long {
        if (endsAtMillis <= 0L) return 0L
        return maxOf(0L, (endsAtMillis - clock() + 999L) / 1_000L)
    }
}
