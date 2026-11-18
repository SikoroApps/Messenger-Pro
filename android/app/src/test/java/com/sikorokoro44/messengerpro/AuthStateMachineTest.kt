package com.sikorokoro44.messengerpro

import com.sikorokoro44.messengerpro.data.auth.PhoneNumber
import com.sikorokoro44.messengerpro.data.network.AuthError
import com.sikorokoro44.messengerpro.data.network.OtpRequestResponse
import com.sikorokoro44.messengerpro.data.network.SessionTokens
import com.sikorokoro44.messengerpro.domain.auth.AuthEvent
import com.sikorokoro44.messengerpro.domain.auth.AuthState
import com.sikorokoro44.messengerpro.domain.auth.AuthStateMachine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthStateMachineTest {
    private val phone = PhoneNumber("+26876123456", "268", "76123456")
    private val requestResponse = OtpRequestResponse("req-1", 300L, 60L)
    private val tokens = SessionTokens("access", "refresh", 900L)

    private fun machineAtOtpEntry(): AuthStateMachine = AuthStateMachine().apply {
        send(AuthEvent.PhoneSubmitted(phone))
        send(AuthEvent.OtpRequestSucceeded(requestResponse))
    }

    @Test
    fun openingOtpScreenNeverAuthenticates() {
        val machine = machineAtOtpEntry()
        val state = machine.state()
        assertTrue(state is AuthState.OtpEntry)
        assertFalse(state is AuthState.Authenticated)
        assertEquals("req-1", (state as AuthState.OtpEntry).requestId)
    }

    @Test
    fun verificationRequiresExplicitCodeSuccessTransition() {
        val machine = machineAtOtpEntry()
        machine.send(AuthEvent.CodeSubmitted("123456"))
        assertTrue(machine.state() is AuthState.VerifyingCode)

        machine.send(AuthEvent.VerificationSucceeded(tokens))
        val state = machine.state()
        assertTrue(state is AuthState.Authenticated)
        assertEquals("access", (state as AuthState.Authenticated).tokens.accessToken)
    }

    @Test
    fun invalidTransitionsAreIgnored() {
        val machine = AuthStateMachine()
        machine.send(AuthEvent.VerificationSucceeded(tokens))
        assertEquals(AuthState.PhoneEntry, machine.state())

        machine.send(AuthEvent.PhoneSubmitted(phone))
        machine.send(AuthEvent.OtpRequestSucceeded(requestResponse))
        machine.send(AuthEvent.PhoneSubmitted(phone))
        assertTrue(machine.state() is AuthState.OtpEntry)
    }

    @Test
    fun failuresCanRetryAndLogoutResetsToPhoneEntry() {
        val machine = machineAtOtpEntry()
        machine.send(AuthEvent.CodeSubmitted("000000"))
        machine.send(AuthEvent.VerificationFailed(AuthError.InvalidCode()))
        assertTrue(machine.state() is AuthState.Failed)

        machine.send(AuthEvent.ResendRequested)
        assertTrue(machine.state() is AuthState.RequestingOtp)

        machine.send(AuthEvent.OtpRequestSucceeded(requestResponse))
        machine.send(AuthEvent.CodeSubmitted("123456"))
        machine.send(AuthEvent.VerificationSucceeded(tokens))
        machine.send(AuthEvent.LoggedOut)
        assertEquals(AuthState.PhoneEntry, machine.state())
    }

    @Test
    fun cooldownUpdatesDoNotAuthenticate() {
        val machine = machineAtOtpEntry()
        machine.send(AuthEvent.CooldownChanged(30L))
        val state = machine.state() as AuthState.OtpEntry
        assertEquals(30L, state.retryAfterSeconds)
        assertFalse(state is AuthState.Authenticated)
    }
}
