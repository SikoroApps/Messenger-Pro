package com.sikorokoro44.messengerpro.data.network

/** Stable, client-safe error surface. No OTP value or raw phone number is ever carried in an error. */
sealed interface AuthError {
    val code: String
    val message: String
    val retryAfterSeconds: Long?
    val retryable: Boolean

    data class InvalidPhone(
        override val message: String = "Enter a valid phone number.",
    ) : AuthError {
        override val code = "INVALID_PHONE"
        override val retryAfterSeconds = null
        override val retryable = false
    }

    data class InvalidCode(
        override val message: String = "That code is not correct.",
    ) : AuthError {
        override val code = "INVALID_CODE"
        override val retryAfterSeconds = null
        override val retryable = false
    }

    data class CodeExpired(
        override val message: String = "That code has expired. Request a new one.",
    ) : AuthError {
        override val code = "CODE_EXPIRED"
        override val retryAfterSeconds = null
        override val retryable = false
    }

    data class RateLimited(
        override val retryAfterSeconds: Long,
        override val message: String = "Too many attempts. Try again later.",
    ) : AuthError {
        override val code = "RATE_LIMITED"
        override val retryable = true
    }

    data class CooldownActive(
        override val retryAfterSeconds: Long,
        override val message: String = "Please wait before requesting another code.",
    ) : AuthError {
        override val code = "COOLDOWN_ACTIVE"
        override val retryable = true
    }

    data class TooManyAttempts(
        override val retryAfterSeconds: Long,
        override val message: String = "Too many wrong codes. Request a new code.",
    ) : AuthError {
        override val code = "TOO_MANY_ATTEMPTS"
        override val retryable = false
    }

    data class CodeConsumed(
        override val message: String = "That code was already used.",
    ) : AuthError {
        override val code = "CODE_CONSUMED"
        override val retryAfterSeconds = null
        override val retryable = false
    }

    data class Network(
        override val message: String = "Network unavailable. Check your connection and retry.",
        override val retryAfterSeconds: Long? = null,
    ) : AuthError {
        override val code = "NETWORK"
        override val retryable = true
    }

    data class Server(
        override val message: String = "The service could not complete that request.",
    ) : AuthError {
        override val code = "SERVER"
        override val retryAfterSeconds = null
        override val retryable = true
    }

    data class NotConfigured(
        override val message: String = "The authentication backend is not configured yet.",
    ) : AuthError {
        override val code = "NOT_CONFIGURED"
        override val retryAfterSeconds = null
        override val retryable = false
    }
}

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Failure(val error: AuthError) : ApiResult<Nothing>
}

data class OtpRequest(
    val phoneE164: String,
    val locale: String,
    val client: String = "android",
)

data class OtpRequestResponse(
    val requestId: String,
    val expiresInSeconds: Long,
    val retryAfterSeconds: Long,
)

data class OtpVerifyRequest(
    val phoneE164: String,
    val requestId: String,
    val code: String,
)

data class SessionTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
)

data class RefreshRequest(val refreshToken: String)
data class LogoutRequest(val refreshToken: String)

/** Identity operations are abstract so no endpoint or credential is ever constructed in the UI layer. */
interface AuthApi {
    suspend fun requestOtp(request: OtpRequest): ApiResult<OtpRequestResponse>
    suspend fun verifyOtp(request: OtpVerifyRequest): ApiResult<SessionTokens>
    suspend fun refresh(request: RefreshRequest): ApiResult<SessionTokens>
    suspend fun logout(request: LogoutRequest): ApiResult<Unit>
}

/**
 * Honest default until a backend is deployed. It never fabricates verification, a session, or an
 * SMS delivery; it reports NOT_CONFIGURED so the UI can surface a real error state.
 */
class UnconfiguredAuthApi : AuthApi {
    override suspend fun requestOtp(request: OtpRequest): ApiResult<OtpRequestResponse> =
        ApiResult.Failure(AuthError.NotConfigured())

    override suspend fun verifyOtp(request: OtpVerifyRequest): ApiResult<SessionTokens> =
        ApiResult.Failure(AuthError.NotConfigured())

    override suspend fun refresh(request: RefreshRequest): ApiResult<SessionTokens> =
        ApiResult.Failure(AuthError.NotConfigured())

    override suspend fun logout(request: LogoutRequest): ApiResult<Unit> =
        ApiResult.Failure(AuthError.NotConfigured())
}
