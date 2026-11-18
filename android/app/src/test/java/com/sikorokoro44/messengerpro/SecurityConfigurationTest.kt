package com.sikorokoro44.messengerpro

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Automated assertions for security configuration that is declared outside Kotlin code.
 * Unit tests run with the module directory as the working directory on Gradle, but the
 * lookup walks upward so the test also works when invoked from a parent directory.
 */
class SecurityConfigurationTest {
    private fun androidManifest(): File {
        var directory = File(System.getProperty("user.dir"))
        repeat(6) {
            val candidate = File(directory, "src/main/AndroidManifest.xml")
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return candidate
        }
        error("AndroidManifest.xml was not found from ${System.getProperty("user.dir")}")
    }

    @Test
    fun manifestDisablesCleartextAndRequestsNoBroadSmsPermission() {
        val manifest = androidManifest().readText()

        assertTrue(
            "Cleartext traffic must be disabled in the manifest",
            manifest.contains("android:usesCleartextTraffic=\"false\"")
        )
        assertTrue(
            "Offline detection needs network-state access",
            manifest.contains("android.permission.ACCESS_NETWORK_STATE")
        )
        for (permission in listOf("READ_SMS", "RECEIVE_SMS", "SEND_SMS")) {
            assertFalse(
                "Broad SMS permission $permission must not be requested",
                manifest.contains("android.permission.$permission")
            )
        }
    }
}
