package com.sikorokoro44.messengerpro.security

import com.sikorokoro44.messengerpro.data.network.SessionTokens

data class StoredSession(val tokens: SessionTokens, val savedAtMillis: Long)

/** Abstraction so session logic can be tested without Android Keystore. */
interface TokenStore {
    fun save(session: StoredSession)
    fun load(): StoredSession?
    fun clear()
}
