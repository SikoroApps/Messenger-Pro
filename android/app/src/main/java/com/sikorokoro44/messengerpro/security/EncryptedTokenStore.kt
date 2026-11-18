package com.sikorokoro44.messengerpro.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Tokens are encrypted at rest with keys held by the Android Keystore. Never plain preferences. */
class EncryptedTokenStore(context: Context) : TokenStore {
    private val preferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun save(session: StoredSession) {
        preferences.edit()
            .putString(KEY_ACCESS, session.tokens.accessToken)
            .putString(KEY_REFRESH, session.tokens.refreshToken)
            .putLong(KEY_EXPIRES_IN, session.tokens.expiresInSeconds)
            .putLong(KEY_SAVED_AT, session.savedAtMillis)
            .apply()
    }

    override fun load(): StoredSession? {
        val access = preferences.getString(KEY_ACCESS, null) ?: return null
        val refresh = preferences.getString(KEY_REFRESH, null) ?: return null
        return StoredSession(
            tokens = com.sikorokoro44.messengerpro.data.network.SessionTokens(
                accessToken = access,
                refreshToken = refresh,
                expiresInSeconds = preferences.getLong(KEY_EXPIRES_IN, 0L),
            ),
            savedAtMillis = preferences.getLong(KEY_SAVED_AT, 0L),
        )
    }

    override fun clear() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "mp_secure_session"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_EXPIRES_IN = "expires_in"
        const val KEY_SAVED_AT = "saved_at"
    }
}
