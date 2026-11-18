package com.sikorokoro44.messengerpro.server

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

/** Six-digit code from a CSPRNG. Injectable [random] keeps tests deterministic. */
class OtpCodeGenerator(private val random: SecureRandom = SecureRandom()) {
    fun generate(): String = buildString(CODE_LENGTH) {
        repeat(CODE_LENGTH) { append(random.nextInt(10)) }
    }

    companion object {
        const val CODE_LENGTH = 6
    }
}

/**
 * HMAC-SHA256 with a server-side pepper plus a per-request salt. Only the resulting verifier
 * is stored; the plaintext code is never persisted or logged. [matches] uses a constant-time
 * comparison.
 */
class OtpHasher(private val pepper: ByteArray) {
    fun hash(code: String, salt: ByteArray): String {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(pepper, "HmacSHA256"))
        mac.update(salt)
        return mac.doFinal(code.toByteArray(StandardCharsets.UTF_8)).toHex()
    }

    fun matches(code: String, salt: ByteArray, expectedVerifier: String): Boolean {
        val actual = hash(code, salt)
        return MessageDigest.isEqual(
            actual.toByteArray(StandardCharsets.UTF_8),
            expectedVerifier.toByteArray(StandardCharsets.UTF_8),
        )
    }
}

/** Server-owned OTP state. No plaintext code is present anywhere in this record. */
data class OtpRecord(
    val id: String,
    val phone: String,
    val verifier: String,
    val salt: ByteArray,
    val expiresAt: Long,
    val createdAt: Long,
    @Volatile var attempts: Int = 0,
    @Volatile var consumedAt: Long? = null,
    @Volatile var failedAt: Long? = null,
)

interface OtpRepository {
    fun save(record: OtpRecord)
    fun find(id: String): OtpRecord?
    fun invalidateActiveForPhone(phone: String, now: Long)
    /** Atomic single-use consume. Returns false if another caller already consumed it. */
    fun consume(id: String, at: Long): Boolean
}

/**
 * In-memory reference implementation for CI. A deployed adapter must implement the same
 * semantics with a single SQL statement such as:
 * `UPDATE otp_requests SET consumed_at = ? WHERE id = ? AND consumed_at IS NULL RETURNING id`.
 */
class InMemoryOtpRepository : OtpRepository {
    private val records = linkedMapOf<String, OtpRecord>()

    @Synchronized
    override fun save(record: OtpRecord) {
        records[record.id] = record
    }

    @Synchronized
    override fun find(id: String): OtpRecord? = records[id]

    @Synchronized
    override fun invalidateActiveForPhone(phone: String, now: Long) {
        records.values.forEach { record ->
            if (record.phone == phone && record.consumedAt == null && record.failedAt == null) {
                record.failedAt = now
            }
        }
    }

    @Synchronized
    override fun consume(id: String, at: Long): Boolean {
        val record = records[id] ?: return false
        if (record.consumedAt != null || record.failedAt != null) return false
        record.consumedAt = at
        return true
    }
}
