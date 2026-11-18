package com.sikorokoro44.messengerpro.server

import java.security.SecureRandom
import java.security.MessageDigest
import java.nio.charset.StandardCharsets

/** Injectable time source so expiry and cooldown boundaries are deterministic in CI. */
interface Clock {
    fun nowMillis(): Long
}

class SystemClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

class FakeClock(initialMillis: Long = 1_700_000_000_000L) : Clock {
    @Volatile
    var now: Long = initialMillis
        private set

    override fun nowMillis(): Long = now

    fun advance(millis: Long) {
        require(millis >= 0) { "Clock cannot move backwards" }
        now += millis
    }
}

/** Random ids are generated server-side with a CSPRNG; never by the client. */
interface IdGenerator {
    fun newId(): String
}

class SecureRandomIdGenerator : IdGenerator {
    private val random = SecureRandom()

    override fun newId(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

class FixedIdGenerator(private val prefix: String = "id") : IdGenerator {
    private var counter = 0

    @Synchronized
    override fun newId(): String {
        counter++
        return "$prefix-$counter"
    }
}

/** Stable, generic error codes. The transport layer maps these to non-enumerating messages. */
enum class AuthFailure(val code: String) {
    INVALID_PHONE("INVALID_PHONE"),
    RATE_LIMITED("RATE_LIMITED"),
    COOLDOWN_ACTIVE("COOLDOWN_ACTIVE"),
    PROVIDER_UNAVAILABLE("PROVIDER_UNAVAILABLE"),
    INVALID_CODE("INVALID_CODE"),
    CODE_EXPIRED("CODE_EXPIRED"),
    TOO_MANY_ATTEMPTS("TOO_MANY_ATTEMPTS"),
    CODE_CONSUMED("CODE_CONSUMED"),
    REQUEST_NOT_FOUND("REQUEST_NOT_FOUND"),
    SESSION_REVOKED("SESSION_REVOKED"),
    UNAVAILABLE("UNAVAILABLE"),
    ;

    /**
     * Generic, constant user-facing text. It deliberately does not reveal whether a phone
     * number is registered or whether a request id exists.
     */
    val publicMessage: String
        get() = when (this) {
            INVALID_PHONE -> "Enter a valid phone number."
            RATE_LIMITED -> "Too many attempts. Try again later."
            COOLDOWN_ACTIVE -> "Please wait before requesting another code."
            PROVIDER_UNAVAILABLE -> "We could not send a code. Please try again."
            INVALID_CODE, CODE_EXPIRED, TOO_MANY_ATTEMPTS, CODE_CONSUMED, REQUEST_NOT_FOUND ->
                "That code is not valid. Request a new one and try again."
            SESSION_REVOKED -> "Your session has ended. Please sign in again."
            UNAVAILABLE -> "Something went wrong. Please try again."
        }
}

class AuthException(val failure: AuthFailure) : Exception(failure.code)

/** Server-side re-check of the client-normalised value. E.164 only. */
object ServerPhonePolicy {
    private const val MAX_E164_DIGITS = 15
    private const val MIN_E164_DIGITS = 8
    private val NATIONAL_LENGTH = mapOf("268" to 8, "27" to 9)

    fun isValidE164(value: String?): Boolean {
        if (value == null || !value.startsWith("+")) return false
        val digits = value.drop(1)
        if (digits.length !in MIN_E164_DIGITS..MAX_E164_DIGITS) return false
        if (!digits.all(Char::isDigit)) return false
        if (digits.first() == '0') return false
        return true
    }

    /** Known country lengths are enforced; unknown countries fall back to generic E.164. */
    fun matchesKnownCountryLength(value: String?): Boolean {
        if (!isValidE164(value)) return false
        val digits = value!!.drop(1)
        val dialCode = NATIONAL_LENGTH.keys.firstOrNull { digits.startsWith(it) } ?: return true
        return digits.length - dialCode.length == NATIONAL_LENGTH[dialCode]
    }
}

/** Keeps personal data and secrets out of diagnostics. OTP values are never rendered. */
object DiagnosticsRedactor {
    fun phone(value: String): String =
        if (value.length <= 4) "***" else "${value.take(3)}***${value.takeLast(2)}"

    fun token(value: String): String = "***"

    fun subjectHash(value: String, pepper: ByteArray): String =
        hmacHex(pepper, value.toByteArray(StandardCharsets.UTF_8))
}

internal fun hmacHex(pepper: ByteArray, payload: ByteArray): String {
    val mac = javax.crypto.Mac.getInstance("HmacSHA256")
    mac.init(javax.crypto.spec.SecretKeySpec(pepper, "HmacSHA256"))
    return mac.doFinal(payload).toHex()
}

internal fun sha256Hex(payload: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(payload).toHex()

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
