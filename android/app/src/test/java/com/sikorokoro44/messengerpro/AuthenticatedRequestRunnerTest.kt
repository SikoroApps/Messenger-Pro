package com.sikorokoro44.messengerpro

import com.sikorokoro44.messengerpro.data.network.ApiResult
import com.sikorokoro44.messengerpro.data.network.AuthApi
import com.sikorokoro44.messengerpro.data.network.AuthError
import com.sikorokoro44.messengerpro.data.network.LogoutRequest
import com.sikorokoro44.messengerpro.data.network.OtpRequest
import com.sikorokoro44.messengerpro.data.network.OtpRequestResponse
import com.sikorokoro44.messengerpro.data.network.OtpVerifyRequest
import com.sikorokoro44.messengerpro.data.network.RefreshRequest
import com.sikorokoro44.messengerpro.data.network.SessionTokens
import com.sikorokoro44.messengerpro.security.AuthenticatedRequestRunner
import com.sikorokoro44.messengerpro.security.SessionManager
import com.sikorokoro44.messengerpro.security.StoredSession
import com.sikorokoro44.messengerpro.security.TokenStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class MapStore : TokenStore {
    private var stored: StoredSession? = null
    override fun save(session: StoredSession) { stored = session }
    override fun load(): StoredSession? = stored
    override fun clear() { stored = null }
}

private class RefreshApi : AuthApi {
    var refreshCount = 0
    var refreshShouldFail = false
    override suspend fun requestOtp(request: OtpRequest): ApiResult<OtpRequestResponse> =
        ApiResult.Failure(AuthError.NotConfigured())
    override suspend fun verifyOtp(request: OtpVerifyRequest): ApiResult<SessionTokens> =
        ApiResult.Failure(AuthError.NotConfigured())
    override suspend fun refresh(request: RefreshRequest): ApiResult<SessionTokens> {
        refreshCount++
        return if (refreshShouldFail) {
            ApiResult.Failure(AuthError.Unauthorized())
        } else {
            ApiResult.Success(SessionTokens("rotated", "new-refresh", 900L))
        }
    }
    override suspend fun logout(request: LogoutRequest): ApiResult<Unit> = ApiResult.Success(Unit)
}

class AuthenticatedRequestRunnerTest {
    private fun setup(): Triple<RefreshApi, SessionManager, AuthenticatedRequestRunner> {
        val api = RefreshApi()
        val manager = SessionManager(api, MapStore(), clock = { 1_000L })
        manager.save(SessionTokens("original", "refresh", 900L))
        return Triple(api, manager, AuthenticatedRequestRunner(manager))
    }

    @Test
    fun validTokenIsPassedToTheOperation() = runBlocking {
        val (_, _, runner) = setup()
        val result = runner.execute { token -> ApiResult.Success(token) }
        assertEquals("original", (result as ApiResult.Success).data)
    }

    @Test
    fun unauthorizedResponseTriggersOneRefreshAndReplay() = runBlocking {
        val (api, _, runner) = setup()
        val attempts = mutableListOf<String>()
        val result = runner.execute { token ->
            attempts += token
            if (attempts.size == 1) ApiResult.Failure(AuthError.Unauthorized()) else ApiResult.Success("ok")
        }
        assertEquals("ok", (result as ApiResult.Success).data)
        assertEquals(listOf("original", "rotated"), attempts)
        assertEquals(1, api.refreshCount)
    }

    @Test
    fun failedRefreshNeverReplaysTheOperation() = runBlocking {
        val (api, _, runner) = setup()
        api.refreshShouldFail = true
        var attempts = 0
        val result = runner.execute { _ ->
            attempts++
            ApiResult.Failure(AuthError.Unauthorized())
        }
        assertTrue(result is ApiResult.Failure)
        assertEquals(1, attempts)
        assertEquals(1, api.refreshCount)
    }

    @Test
    fun nonUnauthorizedErrorsAreNotRetried() = runBlocking {
        val (api, _, runner) = setup()
        var attempts = 0
        val result = runner.execute { _ ->
            attempts++
            ApiResult.Failure(AuthError.Server())
        }
        assertTrue(result is ApiResult.Failure)
        assertEquals(1, attempts)
        assertEquals(0, api.refreshCount)
    }
}
