package com.sikorokoro44.messengerpro.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditTest {
    private class RecordingSmsGateway : SmsGateway {
        var lastCode: String? = null

        override fun send(phone: String, code: String, requestId: String): SmsDelivery {
            lastCode = code
            return SmsDelivery(SmsDelivery.Status.SENT, "provider-$requestId")
        }
    }

    private fun config() = ServerConfig(
        pepper = "audit-test-pepper-do-not-commit".toByteArray(),
        environment = "test",
    )

    private fun harness(clock: FakeClock = FakeClock(), sms: RecordingSmsGateway = RecordingSmsGateway()) =
        AuthServiceFactory.inMemory(config(), clock, sms)

    @Test
    fun requestAndVerificationAuditRecordsContainOnlyKeyedHashes() {
        val sms = RecordingSmsGateway()
        val h = harness(sms = sms)

        val request = h.service.requestOtp("+26876123456", "ip-hash")
        val verified = h.service.verifyOtp("+26876123456", request.requestId, sms.lastCode!!)

        val records = h.auditLog.all()
        assertEquals(2, records.size)
        assertEquals(AuditAction.OTP_REQUESTED, records[0].action)
        assertEquals(AuditOutcome.SUCCESS, records[0].outcome)
        assertEquals(AuditAction.OTP_VERIFIED, records[1].action)
        val dump = records.toString()
        assertFalse(dump.contains("+26876123456"))
        assertFalse(dump.contains(request.requestId))
        assertFalse(dump.contains(sms.lastCode!!))
        assertFalse(dump.contains(verified.tokens.refreshToken))
        assertTrue(records.all { it.subjectHash.length == 64 })
        assertTrue(records.all { it.correlationHash.length == 64 })
        assertFalse(records[0].subjectHash == records[0].correlationHash)
    }

    @Test
    fun invalidPhoneAndMalformedCodeRecordOnlyGenericAbuseSignals() {
        val h = harness()

        assertFails(AuthFailure.INVALID_PHONE) {
            h.service.requestOtp("not-a-phone", "ip-hash")
        }
        assertFails(AuthFailure.INVALID_CODE) {
            h.service.verifyOtp("+26876123456", "id-1", "123")
        }
        assertFails(AuthFailure.REQUEST_NOT_FOUND) {
            h.service.verifyOtp("+26876123456", "unknown-id", "123456")
        }

        val signals = h.abuseLog.all().map { it.signal }
        assertEquals(
            listOf(
                AbuseSignal.INVALID_PHONE,
                AbuseSignal.INVALID_CODE_BURST,
                AbuseSignal.REPLAY_ATTEMPT,
            ),
            signals,
        )
        val dump = h.abuseLog.all().toString()
        assertFalse(dump.contains("not-a-phone"))
        assertFalse(dump.contains("+26876123456"))
        assertFalse(dump.contains("unknown-id"))
        assertTrue(h.abuseLog.all().all { it.subjectHash.length == 64 })
    }

    @Test
    fun rateLimitAndCooldownAbuseEventsStayMetadataOnly() {
        val phone = "+26876123456"
        val cooldownHarness = AuthServiceFactory.inMemory(
            config().copy(cooldownMs = 60_000L), FakeClock(), RecordingSmsGateway())
        cooldownHarness.service.requestOtp(phone, "ip-hash")
        assertFails(AuthFailure.COOLDOWN_ACTIVE) {
            cooldownHarness.service.requestOtp(phone, "ip-hash-2")
        }

        val rateHarness = AuthServiceFactory.inMemory(
            config().copy(cooldownMs = 0L, perPhoneLimit = 2), FakeClock(), RecordingSmsGateway())
        rateHarness.service.requestOtp(phone, "ip-hash")
        rateHarness.service.requestOtp(phone, "ip-hash")
        assertFails(AuthFailure.RATE_LIMITED) {
            rateHarness.service.requestOtp(phone, "ip-hash")
        }

        // The cooldown harness records a resend burst; the rate harness disables the cooldown and
        // records a quota hit instead. Neither event may carry a phone, IP, code or message body.
        assertEquals(
            listOf(AbuseSignal.OTP_REQUEST_BURST),
            cooldownHarness.abuseLog.all().map { it.signal },
        )
        assertEquals(
            listOf(AbuseSignal.RATE_LIMIT_HIT),
            rateHarness.abuseLog.all().map { it.signal },
        )
        for (h in listOf(cooldownHarness, rateHarness)) {
            val events = h.abuseLog.all()
            assertTrue(events.isNotEmpty())
            val dump = events.toString()
            assertFalse(dump.contains(phone))
            assertFalse(dump.contains("ip-hash"))
            assertTrue(events.all { it.subjectHash.length == 64 })
            assertTrue(events.all { it.signal in setOf(AbuseSignal.OTP_REQUEST_BURST, AbuseSignal.RATE_LIMIT_HIT) })
        }
    }

    @Test
    fun replayedCodeAndReusedRefreshTokenAreRecordedAsAbuseSignals() {
        val sms = RecordingSmsGateway()
        val h = harness(sms = sms)
        val request = h.service.requestOtp("+26876123456", "ip-hash")
        val code = sms.lastCode!!
        val verified = h.service.verifyOtp("+26876123456", request.requestId, code)

        assertFails(AuthFailure.CODE_CONSUMED) {
            h.service.verifyOtp("+26876123456", request.requestId, code)
        }
        val oldRefresh = verified.tokens.refreshToken
        h.service.refresh(oldRefresh)
        assertFails(AuthFailure.SESSION_REVOKED) {
            h.service.refresh(oldRefresh)
        }

        val signals = h.abuseLog.all().map { it.signal }
        assertTrue(AbuseSignal.REPLAY_ATTEMPT in signals)
        assertTrue(AbuseSignal.SESSION_REUSE in signals)
        val dump = h.abuseLog.all().toString()
        assertFalse(dump.contains(code))
        assertFalse(dump.contains(verified.tokens.refreshToken))
        assertFalse(dump.contains("+26876123456"))
    }

    @Test
    fun logoutAuditIsRecordedOnlyWhenASessionWasActuallyRevoked() {
        val sms = RecordingSmsGateway()
        val h = harness(sms = sms)
        val request = h.service.requestOtp("+26876123456", "ip-hash")
        val refresh = h.service.verifyOtp("+26876123456", request.requestId, sms.lastCode!!).tokens.refreshToken

        h.service.logout(refresh)
        h.service.logout(refresh)

        val revocations = h.auditLog.all().filter { it.action == AuditAction.SESSION_REVOKED }
        assertEquals(1, revocations.size)
        assertEquals(AuditOutcome.SUCCESS, revocations.single().outcome)
        assertFalse(revocations.toString().contains(refresh))
    }

    @Test
    fun auditServicesHashRawInputBeforeLogging() {
        val clock = FakeClock()
        val pepper = "direct-audit-pepper".toByteArray()
        val auditLog = InMemoryAuditLog()
        val abuseLog = InMemoryAbuseEventLog()
        val audit = AuditService(clock, pepper, auditLog)
        val abuse = AbuseSignalLogger(clock, pepper, abuseLog)

        audit.record(AuditAction.OTP_REQUESTED, "+26876123456", "request-secret", AuditOutcome.SUCCESS)
        abuse.record(AbuseSignal.INVALID_CODE_BURST, "+26876123456")

        val auditDump = auditLog.all().toString()
        assertFalse(auditDump.contains("+26876123456"))
        assertFalse(auditDump.contains("request-secret"))
        assertEquals(
            DiagnosticsRedactor.subjectHash("+26876123456", pepper),
            auditLog.all().single().subjectHash,
        )
        assertEquals(
            DiagnosticsRedactor.subjectHash("+26876123456", pepper),
            abuseLog.all().single().subjectHash,
        )
    }

    private fun assertFails(expected: AuthFailure, block: () -> Unit) {
        val thrown = try {
            block()
            throw AssertionError("Expected AuthException($expected)")
        } catch (e: AuthException) {
            e
        }
        assertEquals(expected, thrown.failure)
    }
}
