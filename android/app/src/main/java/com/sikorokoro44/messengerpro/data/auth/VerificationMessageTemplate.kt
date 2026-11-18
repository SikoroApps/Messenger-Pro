package com.sikorokoro44.messengerpro.data.auth

/**
 * Locale-tagged verification SMS copy shared by the server contract and the client parser.
 * Each template contains exactly one isolated six-digit code so OS autofill can find it.
 * The siSwati phrase is a product-copy contract and still requires human review before rollout.
 */
enum class VerificationLocale(
    val languageTag: String,
    val verificationPhrase: String,
) {
    ENGLISH("en", "verification code"),
    SWATI("ss", "ikhodi yokuqinisekisa"),
    ;

    companion object {
        fun fromLanguageTag(languageTag: String?): VerificationLocale {
            val language = languageTag?.substringBefore('-')?.lowercase().orEmpty()
            return entries.firstOrNull { it.languageTag == language } ?: ENGLISH
        }
    }
}

/** Renders the short verification message used by the OTP delivery contract. */
object VerificationMessageTemplate {
    fun render(
        locale: VerificationLocale,
        code: String,
        appHash: String? = null,
    ): String {
        require(code.length == 6 && code.all { it in '0'..'9' }) {
            "Verification codes must contain exactly six digits"
        }
        val retrieverSuffix = appHash?.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
        return "Messenger Pro ${locale.verificationPhrase}: $code.$retrieverSuffix"
    }

    /** Keywords accepted by the client parser for this locale's template. */
    fun keywordsFor(locale: VerificationLocale): Set<String> =
        setOf("code", "verification", "verify", "otp", locale.verificationPhrase)
}
