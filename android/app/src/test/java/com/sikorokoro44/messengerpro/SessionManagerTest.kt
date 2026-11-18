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
import com.sikorokoro44.messengerpro.security.SessionManager
import com.sikorokoro44.messengerpro.security.StoredSession
import com.sikorokoro44.messengerpro.security.TokenStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private class MemoryTokenStore : TokenStore {
    private var stored: StoredSession? = null
    override fun save(session: StoredSession) { stored = session }
    override fun load(): StoredSession? = stored
    override fun clear() { stored = null }
}

private class RecordingAuthApi : AuthApi {
    var refreshResult: ApiResult<SessionTokens> = ApiResult.Failure(AuthError.Network())
    var refreshCalls = 0
    var logoutCalls = 0

    override suspend fun requestOtp(request: OtpRequest): ApiResult<OtpRequestResponse> =
        ApiResult.Failure(AuthError.NotConfigured())

    override suspend fun verifyOtp(request: OtpVerifyRequest): ApiResult<SessionTokens> =
        ApiResult.Failure(AuthError.NotConfigured())

    override suspend fun refresh(request: RefreshRequest): ApiResult<SessionTokens> {
        refreshCalls++
        return refreshResult
    }

    override suspend fun logout(request: LogoutRequest): ApiResult<Unit> {
        logoutCalls++
        return ApiResult.Success(Unit)
    }
}

class SessionManagerTest {
    private val original = SessionTokens("access-1", "refresh-1", 900L)

    @Test
    fun freshSessionIsReusedWithoutRefresh() = runBlocking {
        val store = MemoryTokenStore()
        val api = RecordingAuthApi()
        var now = 1_000_000L
        val manager = SessionManager(api, store, clock = { now })

        manager.save(original)
        assertEquals(original, manager.validSession())
        assertEquals(0, api.refreshCalls)
    }

    @Test
    fun nearExpiryRotatesThroughServerAndPersistsNewTokens() = runBlocking {
        val store = MemoryTokenStore()
        val api = RecordingAuthApi()
        val rotated = SessionTokens("access-2", "refresh-2", 900L)
        api.refreshResult = ApiResult.Success(rotated)
        var now = 1_000_000L
        val manager = SessionManager(api, store, clock = { now })

        manager.save(original)
        now += 900_000L - 30_000L
        assertEquals(rotated, manager.validSession())
        assertEquals(1, api.refreshCalls)
        assertEquals(rotated, store.load()?.tokens)
    }

    @Test
    fun failedRotationClearsLocalSession() = runBlocking {
        val store = MemoryTokenStore()
        val api = RecordingAuthApi()
        var now = 1_000_000L
        val manager = SessionManager(api, store, clock = { now })

        manager.save(original)
        now += 900_000L
        assertNull(manager.validSession())
        assertNull(store.load())
    }

    @Test
    fun logoutRevokesRemotelyAndPurgesLocally() = runBlocking {
        val store = MemoryTokenStore()
        val api = RecordingAuthApi()
        val manager = SessionManager(api, store, clock = { 1_000_000L })

        manager.save(original)
        manager.logout()
        assertEquals(1, api.logoutCalls)
        assertNull(store.load())
    }
}
