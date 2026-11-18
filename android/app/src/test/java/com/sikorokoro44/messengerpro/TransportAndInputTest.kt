package com.sikorokoro44.messengerpro

import com.sikorokoro44.messengerpro.data.auth.OtpInputFormatter
import com.sikorokoro44.messengerpro.data.auth.PhoneInputFormatter
import com.sikorokoro44.messengerpro.data.network.AuthError
import com.sikorokoro44.messengerpro.data.network.HttpClientPolicy
import com.sikorokoro44.messengerpro.data.network.HttpErrorMapper
import com.sikorokoro44.messengerpro.data.network.RetryAfterParser
import com.sikorokoro44.messengerpro.data.network.RetryBudget
import com.sikorokoro44.messengerpro.data.network.RetryPolicy
import com.sikorokoro44.messengerpro.data.network.TransportPolicy
import com.sikorokoro44.messengerpro.domain.auth.CooldownTimer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportAndInputTest {
    @Test
    fun transportPolicyRejectsAnythingButHttpsWithoutEmbeddedCredentials() {
        assertTrue(TransportPolicy.isSecureBaseUrl("https://api.example.com/auth"))
        assertTrue(TransportPolicy.isSecureBaseUrl("HTTPS://api.example.com/"))
        assertTrue(TransportPolicy.isSecureBaseUrl("https://api.example.com:8443"))
        assertFalse(TransportPolicy.isSecureBaseUrl(""))
        assertFalse(TransportPolicy.isSecureBaseUrl("http://api.example.com"))
        assertFalse(TransportPolicy.isSecureBaseUrl("https://user:pass@api.example.com"))
        assertFalse(TransportPolicy.isSecureBaseUrl("https://api.example.com/?key=value"))
        assertEquals(
            "https://api.example.com",
            TransportPolicy.requireSecureBaseUrl("https://api.example.com///"),
        )
        assertTrue(TransportPolicy.isSecureBaseUrl("https://api.example.com", setOf("API.EXAMPLE.COM")))
        assertFalse(TransportPolicy.isSecureBaseUrl("https://evil.example.com", setOf("api.example.com")))
    }

    @Test
    fun httpPolicyIsBounded() {
        assertEquals(10_000L, HttpClientPolicy.DEFAULT.connectTimeoutMs)
        assertFalse(runCatching { HttpClientPolicy(connectTimeoutMs = 0) }.isSuccess)
        assertFalse(runCatching { HttpClientPolicy(callTimeoutMs = 1, connectTimeoutMs = 10_000) }.isSuccess)
        assertFalse(runCatching { HttpClientPolicy(readTimeoutMs = 200_000) }.isSuccess)
    }

    @Test
    fun httpErrorsMapToStableSafeContract() {
        assertTrue(HttpErrorMapper.map(400, "INVALID_PHONE", null) is AuthError.InvalidPhone)
        assertTrue(HttpErrorMapper.map(401, "CODE_EXPIRED", null) is AuthError.CodeExpired)
        assertTrue(HttpErrorMapper.map(401, "CODE_CONSUMED", null) is AuthError.CodeConsumed)
        assertTrue(HttpErrorMapper.map(403, null, "45") is AuthError.TooManyAttempts)
        assertTrue(HttpErrorMapper.map(429, "COOLDOWN_ACTIVE", "30") is AuthError.CooldownActive)
        assertTrue(HttpErrorMapper.map(429, null, "99999") is AuthError.RateLimited)

        val throttled = HttpErrorMapper.map(429, null, "99999") as AuthError.RateLimited
        assertEquals(3_600L, throttled.retryAfterSeconds)
        val server = HttpErrorMapper.map(503, "internal detail must not leak", null) as AuthError.Server
        assertFalse(server.message.contains("internal detail"))
    }

    @Test
    fun retryAndBudgetAreBounded() {
        val policy = RetryPolicy()
        assertEquals(500L, policy.delayFor(1))
        assertEquals(1_000L, policy.delayFor(2))
        assertEquals(2_000L, policy.delayFor(3))
        assertEquals(8_000L, policy.delayFor(10))
        assertFalse(runCatching { RetryPolicy(maxAttempts = 99) }.isSuccess)

        val budget = RetryBudget(maxCalls = 2)
        assertTrue(budget.canSpend())
        budget.spend()
        budget.spend()
        assertFalse(budget.canSpend())
        assertFalse(runCatching { budget.spend() }.isSuccess)
        assertEquals(2, budget.used())
    }

    @Test
    fun cooldownCountdownUsesInjectedClock() {
        var now = 10_000L
        val timer = CooldownTimer { now }
        timer.start(3L)
        assertEquals(3L, timer.remainingSeconds())
        now += 1_100L
        assertEquals(2L, timer.remainingSeconds())
        now += 2_000L
        assertEquals(0L, timer.remainingSeconds())
        timer.start(5L)
        timer.clear()
        assertEquals(0L, timer.remainingSeconds())
    }

    @Test
    fun inputFormattersSanitizeWithoutChangingCanonicalRules() {
        assertEquals("+268 76 123 456", PhoneInputFormatter.format("076123456", "268"))
        assertEquals("+268 76 123 456", PhoneInputFormatter.format("+268 76 123 456", "268"))
        assertEquals("+268 76 123 456", PhoneInputFormatter.format("0026876123456", "268"))
        assertEquals("", PhoneInputFormatter.format("abc", "268"))
        assertEquals("482913", OtpInputFormatter.sanitize("48a2-9 13xyz999"))
        assertTrue(OtpInputFormatter.isComplete("482913"))
        assertFalse(OtpInputFormatter.isComplete("48291"))
    }

    @Test
    fun retryAfterParserRejectsNonNumericValues() {
        assertEquals(null, RetryAfterParser.parse("Wed, 21 Oct 2015 07:28:00 GMT"))
        assertEquals(45L, RetryAfterParser.parse("45"))
    }
}
