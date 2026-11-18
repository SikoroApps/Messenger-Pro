package com.sikorokoro44.messengerpro.data.network

/** Converts server responses into the client-safe error contract without echoing server text. */
object HttpErrorMapper {
    private const val defaultRetryAfterSeconds = 60L

    fun map(status: Int, errorCode: String?, retryAfterHeader: String?): AuthError {
        val retryAfter = RetryAfterParser.parse(retryAfterHeader) ?: defaultRetryAfterSeconds
        return when (status) {
            400 -> when (errorCode) {
                "INVALID_PHONE" -> AuthError.InvalidPhone()
                "INVALID_CODE", "INVALID_REQUEST_ID" -> AuthError.InvalidCode()
                else -> AuthError.InvalidCode()
            }

            401 -> when (errorCode) {
                "CODE_EXPIRED" -> AuthError.CodeExpired()
                "CODE_CONSUMED" -> AuthError.CodeConsumed()
                "UNAUTHORIZED", "SESSION_REVOKED" -> AuthError.Unauthorized()
                else -> AuthError.InvalidCode()
            }

            403 -> AuthError.TooManyAttempts(retryAfterSeconds = retryAfter)
            429 -> when (errorCode) {
                "COOLDOWN_ACTIVE" -> AuthError.CooldownActive(retryAfterSeconds = retryAfter)
                "TOO_MANY_ATTEMPTS" -> AuthError.TooManyAttempts(retryAfterSeconds = retryAfter)
                else -> AuthError.RateLimited(retryAfterSeconds = retryAfter)
            }

            in 500..599 -> AuthError.Server()
            else -> AuthError.Server()
        }
    }
}
