package com.sikorokoro44.messengerpro

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Static evidence for the public-configuration/secret boundary. Runtime secret handling is tested
 * separately in the backend adapters; this test proves the client never declares or embeds those
 * values in its checked-in sources or build configuration.
 */
class SecretBoundaryTest {
    private companion object {
        val FORBIDDEN_MARKERS = listOf(
            "SUPABASE_SERVICE_ROLE_KEY",
            "SUPABASE_ACCESS_TOKEN",
            "ESMS_API_KEY",
            "service_role",
            "sb_secret_",
            "esms_test_",
        )
    }

    private fun repositoryRoot(): File {
        var directory = File(System.getProperty("user.dir"))
        repeat(8) {
            if (File(directory, "features/feature-catalogue.json").isFile) return directory
            directory = directory.parentFile ?: return directory
        }
        error("Repository root was not found from ${System.getProperty("user.dir")}")
    }

    @Test
    fun clientBuildConfigExposesOnlyPublicEndpointSettings() {
        val gradle = File(repositoryRoot(), "android/app/build.gradle.kts").readText()
        val fields = Regex("buildConfigField\\(\\s*\"String\"\\s*,\\s*\"([^\"]+)\"")
            .findAll(gradle)
            .map { it.groupValues[1] }
            .toSet()
        assertEquals(setOf("AUTH_BASE_URL", "AUTH_HOST_ALLOWLIST"), fields)

        for (marker in FORBIDDEN_MARKERS) {
            assertFalse("Build configuration contains forbidden marker $marker", gradle.contains(marker))
        }
    }

    @Test
    fun clientSourcesContainNoServerSecretMaterial() {
        val main = File(repositoryRoot(), "android/app/src/main")
        val files = main.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "xml") }.toList()
        assertTrue("Expected client sources to scan", files.isNotEmpty())

        files.forEach { file ->
            val text = file.readText()
            FORBIDDEN_MARKERS.forEach { marker ->
                assertFalse(
                    "${file.relativeTo(repositoryRoot())} contains forbidden marker $marker",
                    text.contains(marker, ignoreCase = true),
                )
            }
        }
    }

    private fun assertEquals(expected: Set<String>, actual: Set<String>) {
        if (expected != actual) {
            throw AssertionError("Expected build config fields $expected but found $actual")
        }
    }
}
