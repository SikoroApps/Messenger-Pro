package com.sikorokoro44.messengerpro.domain

import com.sikorokoro44.messengerpro.server.hmacHex
import java.nio.charset.StandardCharsets

/**
 * Contact discovery is opt-in. Raw contact names/numbers never leave the device; only keyed
 * hashes of E.164 numbers are submitted, and only hashes present in the caller's set can match.
 */
class ContactMatchPolicy(private val lookupPepper: ByteArray) {
    data class Match(val phoneHash: String, val displayName: String?)

    fun hashPhone(phoneE164: String): String =
        hmacHex(lookupPepper, phoneE164.toByteArray(StandardCharsets.UTF_8))

    fun match(
        localHashes: Collection<String>,
        serverMatches: Map<String, String?>,
    ): List<Match> {
        val allowed = localHashes.toSet()
        return serverMatches
            .filterKeys { it in allowed }
            .map { (hash, name) -> Match(hash, name) }
            .sortedBy { it.phoneHash }
    }
}

/** Privacy defaults are conservative; discovery is off until the user explicitly enables it. */
data class PrivacySettings(
    val discoverByPhone: Boolean = false,
    val showPhoneNumber: Boolean = false,
    val readReceipts: Boolean = true,
    val autoDownloadMedia: Boolean = false,
)

data class BlockEntry(val phoneHash: String, val blockedAt: Long)

enum class ReportReason { SPAM, HARASSMENT, FRAUD, OTHER }

data class Report(val targetPhoneHash: String, val reason: ReportReason, val detail: String? = null)
