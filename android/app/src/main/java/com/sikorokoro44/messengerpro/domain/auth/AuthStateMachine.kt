package com.sikorokoro44.messengerpro.domain.auth

import com.sikorokoro44.messengerpro.data.auth.PhoneNumber
import com.sikorokoro44.messengerpro.data.network.AuthError
import com.sikorokoro44.messengerpro.data.network.OtpRequestResponse
import com.sikorokoro44.messengerpro.data.network.SessionTokens

/** Opening an OTP screen is never an authenticated state. */
sealed interface AuthState {
    object PhoneEntry : AuthState
    data class RequestingOtp(val phoneE164: String) : AuthState
    data class OtpEntry(
        val phoneE164: String,
        val requestId: String,
        val retryAfterSeconds: Long,
    ) : AuthState
    data class VerifyingCode(val phoneE164: String, val requestId: String) : AuthState
    data class Authenticated(val tokens: SessionTokens) : AuthState
    data class Failed(val previous: AuthState, val error: AuthError) : AuthState
}

sealed interface AuthEvent {
    data class PhoneSubmitted(val phone: PhoneNumber) : AuthEvent
    data class OtpRequestSucceeded(val response: OtpRequestResponse) : AuthEvent
    data class OtpRequestFailed(val error: AuthError) : AuthEvent
    data class CodeSubmitted(val code: String) : AuthEvent
    data class VerificationSucceeded(val tokens: SessionTokens) : AuthEvent
    data class VerificationFailed(val error: AuthError) : AuthEvent
    data class CooldownChanged(val retryAfterSeconds: Long) : AuthEvent
    object ResendRequested : AuthEvent
    object LoggedOut : AuthEvent
}

/**
 * Explicit transitions only. Unknown transitions are ignored, so a crafted UI event cannot skip
 * server verification and jump directly to [AuthState.Authenticated].
 */
class AuthStateMachine(initial: AuthState = AuthState.PhoneEntry) {
    private var currentState: AuthState = initial

    fun state(): AuthState = currentState

    fun send(event: AuthEvent): AuthState {
        currentState = when (val state = currentState) {
            is AuthState.PhoneEntry, is AuthState.Failed -> when (event) {
                is AuthEvent.PhoneSubmitted -> AuthState.RequestingOtp(event.phone.e164)
                AuthEvent.ResendRequested -> {
                    val previous = (state as? AuthState.Failed)?.previous
                    when (previous) {
                        is AuthState.OtpEntry -> AuthState.RequestingOtp(previous.phoneE164)
                        is AuthState.VerifyingCode -> AuthState.RequestingOtp(previous.phoneE164)
                        else -> state
                    }
                }
                else -> state
            }

            is AuthState.RequestingOtp -> when (event) {
                is AuthEvent.OtpRequestSucceeded -> AuthState.OtpEntry(
                    phoneE164 = state.phoneE164,
                    requestId = event.response.requestId,
                    retryAfterSeconds = event.response.retryAfterSeconds,
                )
                is AuthEvent.OtpRequestFailed -> AuthState.Failed(state, event.error)
                else -> state
            }

            is AuthState.OtpEntry -> when (event) {
                is AuthEvent.CodeSubmitted -> AuthState.VerifyingCode(state.phoneE164, state.requestId)
                is AuthEvent.CooldownChanged -> state.copy(retryAfterSeconds = event.retryAfterSeconds)
                AuthEvent.ResendRequested -> AuthState.RequestingOtp(state.phoneE164)
                is AuthEvent.OtpRequestFailed -> AuthState.Failed(state, event.error)
                else -> state
            }

            is AuthState.VerifyingCode -> when (event) {
                is AuthEvent.VerificationSucceeded -> AuthState.Authenticated(event.tokens)
                is AuthEvent.VerificationFailed -> AuthState.Failed(state, event.error)
                else -> state
            }

            is AuthState.Authenticated -> when (event) {
                AuthEvent.LoggedOut -> AuthState.PhoneEntry
                else -> state
            }
        }
        return currentState
    }
}
