package com.sikorokoro44.messengerpro.security

import com.sikorokoro44.messengerpro.data.network.ApiResult
import com.sikorokoro44.messengerpro.data.network.AuthError

/**
 * Gates an authenticated operation on a locally valid session and performs at most one refresh
 * and one replay after an explicit 401. No endpoint is constructed here.
 */
class AuthenticatedRequestRunner(
    private val sessions: SessionManager,
) {
    suspend fun <T> execute(operation: suspend (accessToken: String) -> ApiResult<T>): ApiResult<T> {
        val session = sessions.validSession()
            ?: return ApiResult.Failure(AuthError.Unauthorized())

        return when (val first = operation(session.accessToken)) {
            is ApiResult.Success -> first
            is ApiResult.Failure -> {
                if (first.error !is AuthError.Unauthorized) return first
                val rotated = sessions.refreshNow()
                    ?: return ApiResult.Failure(AuthError.Unauthorized())
                operation(rotated.accessToken)
            }
        }
    }
}
