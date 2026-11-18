package com.sikorokoro44.messengerpro.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthServiceTest {
    private class RecordingSmsGateway(var fail: Boolean = false) : SmsGateway {
        var lastCode: String? = null
        var calls = 0

        override fun send(phone: String, code: String, requestId: String): SmsDelivery {
            calls++
            lastCode = code
            return if (fail) {
                SmsDelivery(SmsDelivery.Status.FAILED)
            } else {
                SmsDelivery(SmsDelivery.Status.SENT, "provider-$calls")
            }
        }
    }

    private fun config(
        cooldownMs: Long = 0L,
        otpTtlMs: Long = 5 * 60 * 1000L,
        maxAttempts: Int = 5,
        perPhoneLimit: Int = 100,
    ) = ServerConfig(
        pepper = "server-pepper-do-not-commit".toByteArray(),
        environment = "test",
        cooldownMs = cooldownMs,
        otpTtlMs = otpTtlMs,
        maxAttempts = maxAttempts,
        perPhoneLimit = perPhoneLimit,
        perIpLimit = 1000,
        globalLimit = 10_000,
    )

    private fun rig(clock: FakeClock, sms: RecordingSmsGateway, cfg: ServerConfig = config()) =
        AuthServiceFactory.inMemory(cfg, clock, sms)

    private fun failureOf(block: () -> Unit): AuthFailure {
        val thrown = try {
            block()
            throw AssertionError("Expected AuthException")
        } catch (e: AuthException) {
            e
        }
        return thrown.failure
    }

    @Test
    fun requestStoresOnlyAVerifierAndGrantsNothing() {
        val clock = FakeClock()
        val sms = RecordingSmsGateway()
        val h = rig(clock, sms, config(cooldownMs = 60_000L))

        val result = h.service.requestOtp("+26876123456", "ip-hash", "idem-1")

        assertEquals(6, sms.lastCode?.length)
        assertTrue(sms.lastCode!!.all(Char::isDigit))
        val record = h.otps.find(result.requestId)
        assertNotNull(record)
        assertFalse(record!!.verifier.contains(sms.lastCode!!))
        assertEquals(0, h.accounts.count())
        assertEquals(0, h.sessions.count())
        assertEquals(60L, result.retryAfterSeconds)
        assertEquals(300L, result.expiresInSeconds)
    }

    @Test
    fun correctCodeVerifiesExactlyOnceThenReplayIsRejected() {
        val clock = FakeClock()
        val sms = RecordingSmsGateway()
        val h = rig(clock, sms)
        val request = h.service.requestOtp("+26876123456", "ip-hash")

        val first = h.service.verifyOtp("+26876123456", request.requestId, sms.lastCode!!)
        assertEquals("active", first.accountState)
        assertNotNull(first.tokens.accessToken)
        assertNotNull(first.tokens.refreshToken)
        assertEquals(1, h.accounts.count())
        assertEquals(1, h.sessions.count())

        assertEquals(AuthFailure.CODE_CONSUMED, failureOf {
            h.service.verifyOtp("+26876123456", request.requestId, sms.lastCode!!)
        })
        assertEquals(1, h.accounts.count())
        assertEquals(1, h.sessions.count())
    }

    @Test
    fun consumedCodeCannotCreateANewSessionAfterTheOriginalSessionIsRevoked() {
        val clock = FakeClock()
        val sms = RecordingSmsGateway()
        val h = rig(clock, sms)
        val request = h.service.requestOtp("+26876123456", "ip-hash")
        val code = sms.lastCode!!
        val verified = h.service.verifyOtp("+26876123456", request.requestId, code)

        // Revoke the session the code originally created; the consumed marker must still stand.
        h.service.logout(verified.tokens.refreshToken)
        assertNotNull(h.sessions.findById(verified.tokens.sessionId)!!.revokedAt)
        val sessionsAfterLogout = h.sessions.count()

        assertEquals(AuthFailure.CODE_CONSUMED, failureOf {
            h.service.verifyOtp("+26876123456", request.requestId, code)
        })
        assertEquals(sessionsAfterLogout, h.sessions.count())
        assertEquals(1, h.accounts.count())
    }

    @Test
    fun otpConsumeIsAtomicSingleUse() {
        val records = InMemoryOtpRepository()
        val now = 1_000L
        val record = OtpRecord(
            id = "req-1",
            phone = "+26876123456",
            verifier = "verifier",
            salt = ByteArray(16),
            expiresAt = now + 5_000L,
            createdAt = now,
        )
        records.save(record)

        assertTrue(records.consume("req-1", now + 1))
        assertEquals(now + 1, record.consumedAt)
        assertFalse(records.consume("req-1", now + 2))
        assertEquals(now + 1, record.consumedAt)
    }

    @Test
    fun codeFromAnotherRequestCannotVerifyADifferentSignInAttempt() {
        val clock = FakeClock()
        val sms = RecordingSmsGateway()
        val h = rig(clock, sms)

        val first = h.service.requestOtp("+26876123456", "ip-hash")
        val firstCode = sms.lastCode!!
        var second = h.service.requestOtp("+26876123457", "ip-hash")
        var secondCode = sms.lastCode!!
        var retries = 0
        while (secondCode == firstCode && retries++ < 10) {
            second = h.service.requestOtp("+26876123457", "ip-hash")
            secondCode = sms.lastCode!!
        }
        assertNotEquals(firstCode, secondCode)

        assertEquals(AuthFailure.INVALID_CODE, failureOf {
            h.service.verifyOtp("+26876123457", second.requestId, firstCode)
        })
        assertEquals(0, h.accounts.count())
        assertEquals(0, h.sessions.count())

        val verified = h.service.verifyOtp("+26876123457", second.requestId, secondCode)
        assertEquals("active", verified.accountState)
        assertEquals(1, h.accounts.count())
        assertEquals(1, h.sessions.count())
        assertNotEquals(first.requestId, second.requestId)
    }

    @Test
    fun expiredAndInvalidCodesAreRejected() {
        val clock = FakeClock()
        val sms = RecordingSmsGateway()
        val h = rig(clock, sms, config(otpTtlMs = 1000L))
        val request = h.service.requestOtp("+26876123456", "ip-hash")

        clock.advance(1001L)
        assertEquals(AuthFailure.CODE_EXPIRED, failureOf {
            h.service.verifyOtp("+26876123456", request.requestId, sms.lastCode!!)
        })
        assertEquals(0, h.accounts.count())

        val request2 = h.service.requestOtp("+26876123457", "ip-hash")
        val wrong = if (sms.lastCode != "000000") "000000" else "111111"
        assertEquals(AuthFailure.INVALID_CODE, failureOf {
            h.service.verifyOtp("+26876123457", request2.requestId, wrong)
        })
    }

    @Test
    fun wrongCodeAttemptsAreBounded() {
        val clock = FakeClock()
        val sms = RecordingSmsGateway()
        val h = rig(clock, sms, config(maxAttempts = 3))
        val request = h.service.requestOtp("+26876123456", "ip-hash")
        val wrong = if (sms.lastCode != "000000") "000000" else "111111"

        repeat(3) {
            assertEquals(AuthFailure.INVALID_CODE, failureOf {
                h.service.verifyOtp("+26876123456", request.requestId, wrong)
            })
        }
        assertEquals(AuthFailure.TOO_MANY_ATTEMPTS, failureOf {
            h.service.verifyOtp("+26876123456", request.requestId, sms.lastCode!!)
        })
        assertEquals(0, h.accounts.count())
    }

    @Test
    fun resendInvalidatesThePreviousCode() {
        val clock = FakeClock()
        val sms = RecordingSmsGateway()
        val h = rig(clock, sms)
        val first = h.service.requestOtp("+26876123456", "ip-hash")
        val firstCode = sms.lastCode!!
        clock.advance(60_001L)
        val second = h.service.requestOtp("+26876123456", "ip-hash")
        val secondCode = sms.lastCode!!

        assertNotEquals(first.requestId, second.requestId)
        assertEquals(AuthFailure.INVALID_CODE, failureOf {
            h.service.verifyOtp("+26876123456", first.requestId, firstCode)
        })
        val verified = h.service.verifyOtp("+26876123456", second.requestId, secondCode)
        assertNotNull(verified.tokens.accessToken)
    }

    @Test
    fun cooldownAndPerPhoneRateLimitAreServerEnforced() {
        val clock = FakeClock()
        val sms = RecordingSmsGateway()
        val h = rig(clock, sms, config(cooldownMs = 60_000L, perPhoneLimit = 100))

        h.service.requestOtp("+26876123456", "ip-hash")
        assertEquals(AuthFailure.COOLDOWN_ACTIVE, failureOf {
            h.service.requestOtp("+26876123456", "ip-hash-2")
        })
        assertEquals(1, sms.calls)
    }

    @Test
    fun idempotencyKeyDoesNotSendADuplicateCode() {
        val clock = FakeClock()
        val sms = RecordingSmsGateway()
        val h = rig(clock, sms)
        val a = h.service.requestOtp("+26876123456", "ip-hash", "network-retry-1")
        val b = h.service.requestOtp("+26876123456", "ip-hash", "network-retry-1")

        assertEquals(a.requestId, b.requestId)
        assertEquals(1, sms.calls)
    }

    @Test
    fun providerFailureLeavesNoUsableCodeOrAccount() {
        val clock = FakeClock()
        val sms = RecordingSmsGateway(fail = true)
        val h = rig(clock, sms)

        assertEquals(AuthFailure.PROVIDER_UNAVAILABLE, failureOf {
            h.service.requestOtp("+26876123456", "ip-hash")
        })
        // No session or account can be created without a successful provider hand-off.
        assertEquals(0, h.accounts.count())
        assertEquals(0, h.sessions.count())
    }

    @Test
    fun refreshRotatesAndReuseRevokesTheWholeFamily() {
        val clock = FakeClock()
        val sms = RecordingSmsGateway()
        val h = rig(clock, sms)
        val request = h.service.requestOtp("+26876123456", "ip-hash")
        val verified = h.service.verifyOtp("+26876123456", request.requestId, sms.lastCode!!)

        val rotated = h.service.refresh(verified.tokens.refreshToken)
        assertNotEquals(verified.tokens.refreshToken, rotated.refreshToken)
        assertEquals(2, h.sessions.count())

        assertEquals(AuthFailure.SESSION_REVOKED, failureOf {
            h.service.refresh(verified.tokens.refreshToken)
        })
        val rotatedHash = DiagnosticsRedactor.subjectHash(
            rotated.refreshToken,
            "server-pepper-do-not-commit".toByteArray(),
        )
        assertTrue(h.sessions.findByRefreshHash(rotatedHash)!!.revokedAt != null)
    }

    @Test
    fun logoutRevokesTheSession() {
        val clock = FakeClock()
        val sms = RecordingSmsGateway()
        val h = rig(clock, sms)
        val request = h.service.requestOtp("+26876123456", "ip-hash")
        val verified = h.service.verifyOtp("+26876123456", request.requestId, sms.lastCode!!)

        h.service.logout(verified.tokens.refreshToken)
        assertEquals(AuthFailure.SESSION_REVOKED, failureOf {
            h.service.refresh(verified.tokens.refreshToken)
        })
    }

    @Test
    fun phonePolicyAndRedactionDoNotLeakPersonalData() {
        assertFalse(ServerPhonePolicy.isValidE164("076123456"))
        assertFalse(ServerPhonePolicy.isValidE164("+2687612"))
        assertTrue(ServerPhonePolicy.isValidE164("+26876123456"))
        assertFalse(ServerPhonePolicy.matchesKnownCountryLength("+2687612345"))
        assertTrue(DiagnosticsRedactor.phone("+26876123456").contains("***"))
        assertFalse(DiagnosticsRedactor.phone("+26876123456").contains("76123456"))
        assertEquals("***", DiagnosticsRedactor.token("top-secret"))
        assertFalse(AuthFailure.INVALID_CODE.publicMessage.contains("registered"))
    }
}
