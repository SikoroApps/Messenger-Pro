package com.sikorokoro44.messengerpro.server

/**
 * Server-owned state for provider-managed OTP. There is deliberately no code, hash or salt here:
 * eSMS Verify owns the secret, and this record only binds its opaque verification id to a phone
 * and a one-time local consume marker.
 */
data class ProviderOtpRecord(
    val id: String,
    val phone: String,
    val verificationId: String,
    val expiresAt: Long,
    val createdAt: Long,
    @Volatile var attempts: Int = 0,
    @Volatile var consumedAt: Long? = null,
    @Volatile var failedAt: Long? = null,
)

interface ProviderOtpRepository {
    fun save(record: ProviderOtpRecord)
    fun find(id: String): ProviderOtpRecord?
    fun invalidateActiveForPhone(phone: String, now: Long)
    /** Atomic single-use consume; returns false when another caller consumed the request first. */
    fun consume(id: String, at: Long): Boolean
}

class InMemoryProviderOtpRepository : ProviderOtpRepository {
    private val records = linkedMapOf<String, ProviderOtpRecord>()

    @Synchronized
    override fun save(record: ProviderOtpRecord) {
        records[record.id] = record
    }

    @Synchronized
    override fun find(id: String): ProviderOtpRecord? = records[id]

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

/**
 * Same security policy as [AuthService], but verification is performed by a managed provider.
 * The plaintext code passes straight from the client to the provider check call; it is never
 * persisted, hashed locally, logged, or returned by this service.
 */
class ProviderOtpService(
    private val config: ServerConfig,
    private val clock: Clock,
    private val ids: IdGenerator,
    private val provider: ManagedOtpProvider,
    private val otps: ProviderOtpRepository,
    private val accounts: AccountRepository,
    private val sessions: SessionRepository,
    private val rateLimiter: RateLimiter,
    private val cooldown: ResendCooldown,
    private val tokens: TokenService,
    private val audit: AuditService? = null,
    private val abuse: AbuseSignalLogger? = null,
) {
    data class OtpRequestResult(
        val requestId: String,
        val retryAfterSeconds: Long,
        val expiresInSeconds: Long,
    )

    data class VerifyResult(
        val tokens: Tokens,
        val accountState: String,
    )

    private val idempotency = mutableMapOf<String, Pair<Long, OtpRequestResult>>()

    fun requestOtp(phone: String, ipHash: String, idempotencyKey: String? = null): OtpRequestResult {
        if (idempotencyKey != null) {
            val cached = synchronized(idempotency) { idempotency[idempotencyKey] }
            if (cached != null && clock.nowMillis() - cached.first < config.idempotencyTtlMs) {
                return cached.second
            }
        }

        if (!ServerPhonePolicy.isValidE164(phone) || !ServerPhonePolicy.matchesKnownCountryLength(phone)) {
            abuse?.record(AbuseSignal.INVALID_PHONE, phone)
            throw AuthException(AuthFailure.INVALID_PHONE)
        }

        val phoneHash = DiagnosticsRedactor.subjectHash(phone, config.pepper)
        try {
            rateLimiter.enforce("global", "all", config.globalLimit, config.globalWindowMs)
            rateLimiter.enforce("ip", ipHash, config.perIpLimit, config.perIpWindowMs)
            rateLimiter.enforce("phone", phoneHash, config.perPhoneLimit, config.perPhoneWindowMs)
        } catch (e: IllegalArgumentException) {
            abuse?.record(AbuseSignal.RATE_LIMIT_HIT, phone)
            throw AuthException(AuthFailure.RATE_LIMITED)
        }

        cooldown.enforce(phoneHash)?.let {
            abuse?.record(AbuseSignal.OTP_REQUEST_BURST, phone)
            throw AuthException(AuthFailure.COOLDOWN_ACTIVE)
        }

        val now = clock.nowMillis()
        val requestId = ids.newId()
        val start = try {
            provider.start(phone, requestId)
        } catch (e: ESmsException) {
            audit?.record(AuditAction.OTP_REQUESTED, phone, requestId, AuditOutcome.FAILURE)
            throw AuthException(mapStartFailure(e.reason))
        } catch (e: IllegalArgumentException) {
            throw AuthException(AuthFailure.INVALID_PHONE)
        }

        otps.invalidateActiveForPhone(phone, now)
        otps.save(
            ProviderOtpRecord(
                id = requestId,
                phone = phone,
                verificationId = start.verificationId,
                expiresAt = now + config.otpTtlMs,
                createdAt = now,
            ),
        )

        audit?.record(AuditAction.OTP_REQUESTED, phone, requestId, AuditOutcome.SUCCESS)
        cooldown.mark(phoneHash)
        val result = OtpRequestResult(
            requestId = requestId,
            retryAfterSeconds = (config.cooldownMs + 999) / 1000,
            expiresInSeconds = config.otpTtlMs / 1000,
        )
        if (idempotencyKey != null) {
            synchronized(idempotency) { idempotency[idempotencyKey] = now to result }
        }
        return result
    }

    fun verifyOtp(
        phone: String,
        requestId: String,
        code: String,
        deviceLabel: String? = null,
    ): VerifyResult {
        if (!ServerPhonePolicy.isValidE164(phone) ||
            code.length != 6 || !code.all(Char::isDigit)
        ) {
            abuse?.record(AbuseSignal.INVALID_CODE_BURST, phone)
            throw AuthException(AuthFailure.INVALID_CODE)
        }

        val record = otps.find(requestId) ?: run {
            abuse?.record(AbuseSignal.REPLAY_ATTEMPT, phone)
            throw AuthException(AuthFailure.REQUEST_NOT_FOUND)
        }
        val now = clock.nowMillis()
        if (record.phone != phone || record.failedAt != null) {
            abuse?.record(AbuseSignal.INVALID_CODE_BURST, phone)
            throw AuthException(AuthFailure.INVALID_CODE)
        }
        if (record.consumedAt != null) {
            abuse?.record(AbuseSignal.REPLAY_ATTEMPT, phone)
            throw AuthException(AuthFailure.CODE_CONSUMED)
        }
        if (record.expiresAt <= now) {
            record.failedAt = now
            throw AuthException(AuthFailure.CODE_EXPIRED)
        }
        if (record.attempts >= config.maxAttempts) {
            record.failedAt = now
            throw AuthException(AuthFailure.TOO_MANY_ATTEMPTS)
        }

        record.attempts++
        val check = try {
            provider.check(record.verificationId, code)
        } catch (e: ESmsException) {
            throw AuthException(mapCheckFailure(e.reason))
        } catch (e: IllegalArgumentException) {
            throw AuthException(AuthFailure.INVALID_CODE)
        }

        when (check.status) {
            ManagedOtpStatus.APPROVED -> Unit
            ManagedOtpStatus.PENDING, ManagedOtpStatus.FAILED -> {
                abuse?.record(AbuseSignal.INVALID_CODE_BURST, phone)
                throw AuthException(AuthFailure.INVALID_CODE)
            }
            ManagedOtpStatus.EXPIRED -> {
                record.failedAt = now
                throw AuthException(AuthFailure.CODE_EXPIRED)
            }
            ManagedOtpStatus.CANCELED -> {
                record.failedAt = now
                throw AuthException(AuthFailure.INVALID_CODE)
            }
            ManagedOtpStatus.UNKNOWN -> throw AuthException(AuthFailure.PROVIDER_UNAVAILABLE)
        }

        if (!otps.consume(requestId, now)) {
            abuse?.record(AbuseSignal.REPLAY_ATTEMPT, phone)
            throw AuthException(AuthFailure.CODE_CONSUMED)
        }

        val account = accounts.upsertByPhone(phone, now)
        audit?.record(AuditAction.OTP_VERIFIED, phone, requestId, AuditOutcome.SUCCESS)
        return VerifyResult(tokens.issue(account.id, deviceLabel), account.state)
    }

    private fun mapStartFailure(reason: ESmsFailureReason): AuthFailure = when (reason) {
        ESmsFailureReason.INVALID_RECIPIENT -> AuthFailure.INVALID_PHONE
        ESmsFailureReason.RATE_LIMITED -> AuthFailure.RATE_LIMITED
        else -> AuthFailure.PROVIDER_UNAVAILABLE
    }

    private fun mapCheckFailure(reason: ESmsFailureReason): AuthFailure = when (reason) {
        ESmsFailureReason.INVALID_RECIPIENT -> AuthFailure.INVALID_CODE
        ESmsFailureReason.RATE_LIMITED -> AuthFailure.RATE_LIMITED
        else -> AuthFailure.PROVIDER_UNAVAILABLE
    }
}

data class ProviderAuthHarness(
    val service: ProviderOtpService,
    val otps: InMemoryProviderOtpRepository,
    val accounts: InMemoryAccountRepository,
    val sessions: InMemorySessionRepository,
    val auditLog: InMemoryAuditLog,
    val abuseLog: InMemoryAbuseEventLog,
)

object ProviderAuthServiceFactory {
    fun inMemory(
        config: ServerConfig,
        clock: Clock,
        provider: ManagedOtpProvider,
        ids: IdGenerator = FixedIdGenerator("provider-id"),
    ): ProviderAuthHarness {
        val otps = InMemoryProviderOtpRepository()
        val accounts = InMemoryAccountRepository(ids)
        val sessions = InMemorySessionRepository()
        val auditLog = InMemoryAuditLog()
        val abuseLog = InMemoryAbuseEventLog()
        val service = ProviderOtpService(
            config = config,
            clock = clock,
            ids = ids,
            provider = provider,
            otps = otps,
            accounts = accounts,
            sessions = sessions,
            rateLimiter = RateLimiter(clock),
            cooldown = ResendCooldown(clock, config.cooldownMs),
            tokens = TokenService(config.pepper, ids, clock, sessions),
            audit = AuditService(clock, config.pepper, auditLog),
            abuse = AbuseSignalLogger(clock, config.pepper, abuseLog),
        )
        return ProviderAuthHarness(service, otps, accounts, sessions, auditLog, abuseLog)
    }
}
