package com.sikorokoro44.messengerpro.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderOtpServiceTest {
    private companion object {
        const val PHONE = "+26876123456"
        const val CODE = "000000"
    }

    private class FakeProvider(
        private val start: ManagedOtpStart = ManagedOtpStart("ver_1", true),
        private val statuses: MutableList<ManagedOtpStatus> = mutableListOf(ManagedOtpStatus.APPROVED),
    ) : ManagedOtpProvider {
        var startCalls = 0
        var checkCalls = 0
        var startFailure: ESmsFailureReason? = null
        var lastCode: String? = null

        override fun start(phone: String, requestId: String): ManagedOtpStart {
            startCalls++
            startFailure?.let { throw ESmsException(it) }
            return start
        }

        override fun check(verificationId: String, code: String): ManagedOtpCheck {
            checkCalls++
            lastCode = code
            val status = if (statuses.size > 1) statuses.removeAt(0) else statuses.first()
            return ManagedOtpCheck(status, true)
        }
    }

    private fun config(maxAttempts: Int = 5) = ServerConfig(
        pepper = "provider-pepper-do-not-commit".toByteArray(),
        environment = "test",
        cooldownMs = 0L,
        maxAttempts = maxAttempts,
        perPhoneLimit = 100,
        perIpLimit = 1000,
        globalLimit = 10_000,
    )

    private fun harness(
        clock: FakeClock = FakeClock(),
        provider: FakeProvider = FakeProvider(),
        maxAttempts: Int = 5,
    ) = ProviderAuthServiceFactory.inMemory(config(maxAttempts), clock, provider)

    private fun failureOf(block: () -> Unit): AuthFailure = try {
        block()
        throw AssertionError("Expected AuthException")
    } catch (e: AuthException) {
        e.failure
    }

    @Test
    fun approvedProviderCheckIssuesSessionOnceAndStoresNoCode() {
        val provider = FakeProvider()
        val h = harness(provider = provider)
        val request = h.service.requestOtp(PHONE, "ip-hash", "idem-1")
        val record = h.otps.find(request.requestId)!!

        assertEquals("ver_1", record.verificationId)
        assertFalse(record.toString().contains(CODE))

        val verified = h.service.verifyOtp(PHONE, request.requestId, CODE, "Pixel test")
        assertNotNull(verified.tokens.accessToken)
        assertNotNull(verified.tokens.refreshToken)
        assertEquals(1, h.accounts.count())
        assertEquals(1, h.sessions.count())
        assertNotNull(record.consumedAt)
        assertEquals("Pixel test", h.sessions.findById(verified.tokens.sessionId)!!.deviceLabel)
        assertFalse(h.otps.find(request.requestId).toString().contains(CODE))
        assertFalse(h.auditLog.all().toString().contains(CODE))
        assertFalse(h.abuseLog.all().toString().contains(CODE))
        assertEquals(CODE, provider.lastCode)

        assertEquals(AuthFailure.CODE_CONSUMED, failureOf {
            h.service.verifyOtp(PHONE, request.requestId, CODE)
        })
        assertEquals(1, provider.checkCalls)
        assertEquals(1, h.sessions.count())
    }

    @Test
    fun pendingAndFailedChecksGrantNothingButCountAttempts() {
        val provider = FakeProvider(
            statuses = mutableListOf(ManagedOtpStatus.PENDING, ManagedOtpStatus.FAILED),
        )
        val h = harness(provider = provider)
        val request = h.service.requestOtp(PHONE, "ip-hash")
        val record = h.otps.find(request.requestId)!!

        assertEquals(AuthFailure.INVALID_CODE, failureOf {
            h.service.verifyOtp(PHONE, request.requestId, CODE)
        })
        assertEquals(AuthFailure.INVALID_CODE, failureOf {
            h.service.verifyOtp(PHONE, request.requestId, CODE)
        })
        assertEquals(2, record.attempts)
        assertNull(record.consumedAt)
        assertEquals(0, h.accounts.count())
        assertEquals(0, h.sessions.count())
        assertEquals(2, provider.checkCalls)
    }

    @Test
    fun expiredAndCanceledChecksRejectWithoutIssuingTokens() {
        val provider = FakeProvider(statuses = mutableListOf(ManagedOtpStatus.EXPIRED))
        val h = harness(provider = provider)
        val request = h.service.requestOtp(PHONE, "ip-hash")

        assertEquals(AuthFailure.CODE_EXPIRED, failureOf {
            h.service.verifyOtp(PHONE, request.requestId, CODE)
        })
        assertNotNull(h.otps.find(request.requestId)!!.failedAt)
        assertEquals(0, h.sessions.count())

        val canceledProvider = FakeProvider(statuses = mutableListOf(ManagedOtpStatus.CANCELED))
        val canceledHarness = harness(provider = canceledProvider)
        val canceledRequest = canceledHarness.service.requestOtp(PHONE, "ip-hash")
        assertEquals(AuthFailure.INVALID_CODE, failureOf {
            canceledHarness.service.verifyOtp(PHONE, canceledRequest.requestId, CODE)
        })
        assertEquals(0, canceledHarness.sessions.count())
    }

    @Test
    fun attemptLimitStopsFurtherProviderChecks() {
        val provider = FakeProvider(
            statuses = mutableListOf(ManagedOtpStatus.FAILED, ManagedOtpStatus.FAILED),
        )
        val h = harness(provider = provider, maxAttempts = 2)
        val request = h.service.requestOtp(PHONE, "ip-hash")

        repeat(2) {
            assertEquals(AuthFailure.INVALID_CODE, failureOf {
                h.service.verifyOtp(PHONE, request.requestId, CODE)
            })
        }
        assertEquals(AuthFailure.TOO_MANY_ATTEMPTS, failureOf {
            h.service.verifyOtp(PHONE, request.requestId, CODE)
        })
        assertEquals(2, provider.checkCalls)
        assertEquals(0, h.sessions.count())
    }

    @Test
    fun providerStartFailureGrantsNothingAndUsesGenericError() {
        val provider = FakeProvider()
        provider.startFailure = ESmsFailureReason.INSUFFICIENT_BALANCE
        val h = harness(provider = provider)

        assertEquals(AuthFailure.PROVIDER_UNAVAILABLE, failureOf {
            h.service.requestOtp(PHONE, "ip-hash")
        })
        assertTrue(h.otps.find("provider-id-1") == null)
        assertEquals(0, h.accounts.count())
        assertEquals(0, h.sessions.count())
        assertTrue(h.auditLog.all().all { it.subjectHash.length == 64 })
    }

    @Test
    fun idempotencyKeyDoesNotStartTwoProviderVerifications() {
        val provider = FakeProvider()
        val h = harness(provider = provider)
        val first = h.service.requestOtp(PHONE, "ip-hash", "idem-key")
        val second = h.service.requestOtp(PHONE, "ip-hash", "idem-key")

        assertEquals(first.requestId, second.requestId)
        assertEquals(1, provider.startCalls)
        assertNotNull(h.otps.find(first.requestId))
    }
}
