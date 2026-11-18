package com.sikorokoro44.messengerpro.data.auth

/** Eswatini is the default locale for the first release; users can override the dial code in the UI. */
const val DEFAULT_COUNTRY_DIAL_CODE = "268"

data class PhoneNumber(
    val e164: String,
    val countryDialCode: String,
    val nationalNumber: String,
)

enum class InvalidPhoneReason {
    EMPTY,
    INVALID_CHARACTERS,
    TOO_SHORT,
    TOO_LONG,
}

sealed interface PhoneValidationResult {
    data class Valid(val phone: PhoneNumber) : PhoneValidationResult
    data class Invalid(val reason: InvalidPhoneReason) : PhoneValidationResult
}

/**
 * Client-side canonicalisation only. The server must repeat this check before issuing a code;
 * this utility exists to prevent malformed requests and to drive the UI, not to grant identity.
 */
object PhoneNormalizer {
    private val separators = Regex("[\\s\\-().]")
    private const val minDigits = 8
    private const val maxDigits = 15

    fun normalize(input: String, defaultCountryDialCode: String = DEFAULT_COUNTRY_DIAL_CODE): PhoneValidationResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return PhoneValidationResult.Invalid(InvalidPhoneReason.EMPTY)
        if (trimmed.any(Char::isLetter)) return PhoneValidationResult.Invalid(InvalidPhoneReason.INVALID_CHARACTERS)

        val cleaned = trimmed.replace(separators, "")
        if (cleaned.any { it != '+' && !it.isDigit() }) {
            return PhoneValidationResult.Invalid(InvalidPhoneReason.INVALID_CHARACTERS)
        }
        if (cleaned.count { it == '+' } > 1 || (cleaned.contains('+') && !cleaned.startsWith('+'))) {
            return PhoneValidationResult.Invalid(InvalidPhoneReason.INVALID_CHARACTERS)
        }

        val digits = when {
            cleaned.startsWith('+') -> cleaned.drop(1)
            cleaned.startsWith("00") -> cleaned.drop(2)
            cleaned.startsWith('0') -> defaultCountryDialCode + cleaned.drop(1)
            else -> cleaned
        }

        if (digits.length < minDigits) return PhoneValidationResult.Invalid(InvalidPhoneReason.TOO_SHORT)
        if (digits.length > maxDigits) return PhoneValidationResult.Invalid(InvalidPhoneReason.TOO_LONG)
        if (!digits.all(Char::isDigit)) return PhoneValidationResult.Invalid(InvalidPhoneReason.INVALID_CHARACTERS)

        val e164 = "+$digits"
        val matchedCountry = if (digits.startsWith(defaultCountryDialCode)) defaultCountryDialCode else ""
        val national = if (matchedCountry.isNotEmpty() && digits.length > matchedCountry.length) {
            digits.drop(matchedCountry.length)
        } else {
            digits
        }
        return PhoneValidationResult.Valid(PhoneNumber(e164, matchedCountry, national))
    }
}
