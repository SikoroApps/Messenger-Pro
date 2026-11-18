package com.sikorokoro44.messengerpro.data.auth

/**
 * Parses a verification code from an OS-delivered SMS message.
 *
 * This is deliberately contract-only: it does not read SMS, request permissions or decide that a
 * message came from the app's backend. SMS Retriever callers must pass the expected 11-character
 * app hash, and every caller requires an explicit verification keyword so ordinary six-digit
 * numbers in a message are never treated as a code.
 */
object SmsOtpExtractor {
    private val codePattern = Regex("""(?<!\d)(\d{6})(?!\d)""")
    private val verificationKeyword = Regex("""\b(code|verification|verify|otp)\b""", RegexOption.IGNORE_CASE)

    fun extract(message: String?, expectedAppHash: String? = null): String? {
        if (message.isNullOrBlank()) return null
        val trimmed = message.trim()
        if (!verificationKeyword.containsMatchIn(trimmed)) return null

        if (!expectedAppHash.isNullOrBlank() && !trimmed.endsWith(expectedAppHash)) return null

        val candidates = codePattern.findAll(trimmed)
            .map { it.value }
            .toSet()
        return candidates.singleOrNull()
    }
}
