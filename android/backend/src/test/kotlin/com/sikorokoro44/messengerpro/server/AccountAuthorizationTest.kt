package com.sikorokoro44.messengerpro.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Cross-account negative authorization tests for the reference-core model of the row-ownership
 * rule a deployed Supabase RLS policy must enforce. These tests are NOT evidence of deployed RLS.
 */
class AccountAuthorizationTest {
    private class RecordingSmsGateway : SmsGateway {
        var lastCode: String? = null

        override fun send(phone: String, code: String, requestId: String): SmsDelivery {
            lastCode = code
            return SmsDelivery(SmsDelivery.Status.SENT, "provider-$requestId")
        }
    }

    private data class Rig(val h: ServerHarness, val sms: RecordingSmsGateway, val clock: FakeClock)

    private fun config() = ServerConfig(
        pepper = "authz-test-pepper-do-not-commit".toByteArray(),
        environment = "test",
    )

    private fun rig(): Rig {
        val clock = FakeClock()
        val sms = RecordingSmsGateway()
        return Rig(AuthServiceFactory.inMemory(config(), clock, sms), sms, clock)
    }

    private fun verify(r: Rig, phone: String): Tokens {
        val request = r.h.service.requestOtp(phone, "ip-hash")
        val code = r.sms.lastCode!!
        return r.h.service.verifyOtp(phone, request.requestId, code).tokens
    }

    private fun authorizationFailureOf(block: () -> Unit): AccountAuthorizationFailure = try {
        block()
        throw AssertionError("Expected AccountAuthorizationException")
    } catch (e: AccountAuthorizationException) {
        e.reason
    }

    private fun authFailureOf(block: () -> Unit): AuthFailure = try {
        block()
        throw AssertionError("Expected AuthException")
    } catch (e: AuthException) {
        e.failure
    }

    @Test
    fun ownerCanReadAndModifyOnlyTheirOwnAccountRow() {
        val r = rig()
        val a = verify(r, "+26876123456")
        val b = verify(r, "+26876123457")
        val accountA = r.h.accounts.findById(r.h.sessions.findById(a.sessionId)!!.accountId)!!
        val accountB = r.h.accounts.findById(r.h.sessions.findById(b.sessionId)!!.accountId)!!
        assertNotEquals(accountA.id, accountB.id)

        assertEquals(accountA.id, r.h.service.readOwnAccount(a.refreshToken, accountA.id).id)
        assertEquals(
            "Ama",
            r.h.service.updateOwnDisplayName(a.refreshToken, accountA.id, "  Ama  ").displayName,
        )
        assertEquals("Ama", r.h.service.readOwnAccount(a.refreshToken, accountA.id).displayName)
        assertNull(r.h.accounts.findById(accountB.id)!!.displayName)
    }

    @Test
    fun differentAccountCannotReadOrModifyAnotherAccountRow() {
        val r = rig()
        val a = verify(r, "+26876123456")
        val b = verify(r, "+26876123457")
        val accountA = r.h.accounts.findById(r.h.sessions.findById(a.sessionId)!!.accountId)!!
        val accountB = r.h.accounts.findById(r.h.sessions.findById(b.sessionId)!!.accountId)!!

        assertEquals(
            AccountAuthorizationFailure.CROSS_ACCOUNT_DENIED,
            authorizationFailureOf { r.h.service.readOwnAccount(a.refreshToken, accountB.id) },
        )
        assertEquals(
            AccountAuthorizationFailure.CROSS_ACCOUNT_DENIED,
            authorizationFailureOf {
                r.h.service.updateOwnDisplayName(a.refreshToken, accountB.id, "Taken over")
            },
        )
        assertNull(r.h.accounts.findById(accountB.id)!!.displayName)
        assertEquals(accountA.id, r.h.service.readOwnAccount(a.refreshToken, accountA.id).id)
    }

    @Test
    fun handBuiltPrincipalCannotPairAVictimAccountWithAnAttackersSession() {
        val r = rig()
        val a = verify(r, "+26876123456")
        val b = verify(r, "+26876123457")
        val accountA = r.h.accounts.findById(r.h.sessions.findById(a.sessionId)!!.accountId)!!
        val accountB = r.h.accounts.findById(r.h.sessions.findById(b.sessionId)!!.accountId)!!
        val guard = AuthorizedAccountAccess(r.h.accounts, r.h.sessions, r.clock)

        assertEquals(
            AccountAuthorizationFailure.UNAUTHENTICATED,
            authorizationFailureOf {
                guard.read(CallerPrincipal(accountB.id, a.sessionId), accountB.id)
            },
        )
        assertEquals(
            AccountAuthorizationFailure.UNAUTHENTICATED,
            authorizationFailureOf {
                guard.read(CallerPrincipal("", a.sessionId), accountA.id)
            },
        )
        assertEquals(
            AccountAuthorizationFailure.UNAUTHENTICATED,
            authorizationFailureOf {
                guard.read(CallerPrincipal(accountA.id, "missing-session"), accountA.id)
            },
        )
    }

    @Test
    fun unauthenticatedAndRevokedSessionsAreRejectedBeforeRowAccess() {
        val r = rig()
        val a = verify(r, "+26876123456")
        val accountA = r.h.accounts.findById(r.h.sessions.findById(a.sessionId)!!.accountId)!!

        assertEquals(
            AuthFailure.SESSION_REVOKED,
            authFailureOf { r.h.service.readOwnAccount("not-a-token", accountA.id) },
        )
        r.h.service.logout(a.refreshToken)
        assertEquals(
            AuthFailure.SESSION_REVOKED,
            authFailureOf { r.h.service.readOwnAccount(a.refreshToken, accountA.id) },
        )
    }

    @Test
    fun authorizationFailuresDoNotRevealWhetherATargetRowExists() {
        val r = rig()
        val a = verify(r, "+26876123456")
        val b = verify(r, "+26876123457")
        val accountB = r.h.accounts.findById(r.h.sessions.findById(b.sessionId)!!.accountId)!!

        val missingOwnRow = authorizationFailureOf { r.h.service.readOwnAccount(a.refreshToken, "id-missing") }
        val crossAccountExisting = authorizationFailureOf { r.h.service.readOwnAccount(a.refreshToken, accountB.id) }
        val crossAccountMissing = authorizationFailureOf { r.h.service.readOwnAccount(a.refreshToken, "id-also-missing") }

        assertEquals(AccountAuthorizationFailure.ACCOUNT_NOT_FOUND, missingOwnRow)
        assertEquals(AccountAuthorizationFailure.CROSS_ACCOUNT_DENIED, crossAccountExisting)
        assertEquals(AccountAuthorizationFailure.CROSS_ACCOUNT_DENIED, crossAccountMissing)
        val messages = setOf(
            missingOwnRow.publicMessage,
            crossAccountExisting.publicMessage,
            crossAccountMissing.publicMessage,
        )
        assertEquals(1, messages.size)
        assertFalse(messages.single().contains(accountB.phone))
        assertEquals("Account data is not available.", messages.single())
    }

    @Test
    fun displayNameIsBoundedAndSanitizedBeforeStorage() {
        val r = rig()
        val a = verify(r, "+26876123456")
        val accountA = r.h.accounts.findById(r.h.sessions.findById(a.sessionId)!!.accountId)!!

        val raw = "  <script>Ama\n</script> ${"x".repeat(80)} "
        val saved = r.h.service.updateOwnDisplayName(a.refreshToken, accountA.id, raw)
        assertEquals(AccountDisplayNamePolicy.MAX_LENGTH, saved.displayName!!.length)
        assertFalse(saved.displayName!!.contains('<'))
        assertFalse(saved.displayName!!.contains('>'))
        assertFalse(saved.displayName!!.contains('\n'))
        assertEquals(
            AccountAuthorizationFailure.INVALID_DISPLAY_NAME,
            authorizationFailureOf {
                r.h.service.updateOwnDisplayName(a.refreshToken, accountA.id, "   ")
            },
        )
        assertEquals(saved.displayName, r.h.accounts.findById(accountA.id)!!.displayName)
    }
}
