package com.sikorokoro44.messengerpro

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sikorokoro44.messengerpro.data.network.SessionTokens
import com.sikorokoro44.messengerpro.security.EncryptedTokenStore
import com.sikorokoro44.messengerpro.security.StoredSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class EncryptedTokenStoreTest {
    @Test
    fun tokensAreEncryptedAtRestAndClearRemovesThem() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = EncryptedTokenStore(context)
        val tokens = SessionTokens("access-secret-xyz", "refresh-secret-xyz", 900L)

        store.save(StoredSession(tokens, 1_000_000L))
        assertEquals(tokens, store.load()?.tokens)

        val prefsFile = File(context.applicationInfo.dataDir, "shared_prefs/mp_secure_session.xml")
        assertFalse(prefsFile.readText().contains("access-secret-xyz"))
        assertFalse(prefsFile.readText().contains("refresh-secret-xyz"))

        store.clear()
        assertNull(store.load())
    }
}
