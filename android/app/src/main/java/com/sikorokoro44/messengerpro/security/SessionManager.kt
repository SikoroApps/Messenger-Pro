package com.sikorokoro44.messengerpro.security

import com.sikorokoro44.messengerpro.data.network.ApiResult
import com.sikorokoro44.messengerpro.data.network.AuthApi
import com.sikorokoro44.messengerpro.data.network.LogoutRequest
import com.sikorokoro44.messengerpro.data.network.RefreshRequest
import com.sikorokoro44.messengerpro.data.network.SessionTokens

/** Local session lifecycle: validate, rotate through the server, and purge on logout. */
class SessionManager(
    private val api: AuthApi,
    private val store: TokenStore,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    fun save(tokens: SessionTokens) {
        store.save(StoredSession(tokens, clock()))
    }

    suspend fun validSession(): SessionTokens? {
        val stored = store.load() ?: return null
        if (!isNearExpiry(stored)) return stored.tokens

        return when (val result = api.refresh(RefreshRequest(stored.tokens.refreshToken))) {
            is ApiResult.Success -> {
                save(result.data)
                result.data
            }
            is ApiResult.Failure -> {
                // A failed rotation invalidates the local session; never keep a stale token.
                store.clear()
                null
            }
        }
    }

    /** Force a rotation after an explicit 401, even when the local token is not near expiry. */
    suspend fun refreshNow(): SessionTokens? {
        val stored = store.load() ?: return null
        return when (val result = api.refresh(RefreshRequest(stored.tokens.refreshToken))) {
            is ApiResult.Success -> {
                save(result.data)
                result.data
            }
            is ApiResult.Failure -> {
                store.clear()
                null
            }
        }
    }

    suspend fun logout() {
        store.load()?.let { api.logout(LogoutRequest(it.tokens.refreshToken)) }
        store.clear()
    }

    fun isNearExpiry(stored: StoredSession, now: Long = clock()): Boolean {
        val expiresAt = stored.savedAtMillis + stored.tokens.expiresInSeconds * 1_000L
        return now >= expiresAt - refreshSkewMillis
    }

    private companion object {
        const val refreshSkewMillis = 30_000L
    }
}
