package com.sikorokoro44.messengerpro.server

import java.security.SecureRandom

data class Account(
    val id: String,
    val phone: String,
    val state: String,
    val createdAt: Long,
)

interface AccountRepository {
    fun findByPhone(phone: String): Account?
    fun upsertByPhone(phone: String, now: Long): Account
    fun count(): Int
}

/** An account is created only after a successful server-side verification. */
class InMemoryAccountRepository(private val ids: IdGenerator) : AccountRepository {
    private val accounts = mutableMapOf<String, Account>()

    @Synchronized
    override fun findByPhone(phone: String): Account? = accounts[phone]

    @Synchronized
    override fun upsertByPhone(phone: String, now: Long): Account {
        accounts[phone]?.let { return it }
        val account = Account(ids.newId(), phone, "active", now)
        accounts[phone] = account
        return account
    }

    @Synchronized
    override fun count(): Int = accounts.size
}

data class ServerConfig(
    val pepper: ByteArray,
    val environment: String = "dev",
    val otpTtlMs: Long = 5 * 60 * 1000L,
    val cooldownMs: Long = 60 * 1000L,
    val maxAttempts: Int = 5,
    val perPhoneLimit: Int = 5,
    val perPhoneWindowMs: Long = 15 * 60 * 1000L,
    val perIpLimit: Int = 20,
    val perIpWindowMs: Long = 60 * 60 * 1000L,
    val globalLimit: Int = 500,
    val globalWindowMs: Long = 60 * 60 * 1000L,
    val idempotencyTtlMs: Long = 10 * 60 * 1000L,
)

/**
 * Server-authoritative identity core.
 *
 * The client never learns the code, never decides success, and never sees an account or a
 * session before verification. Every mutable transition is server-owned and atomic in the
 * repository contract. Deployment adapters (SQL/Edge Functions) implement the same interfaces.
 */
class AuthService(
    private val config: ServerConfig,
    private val clock: Clock,
    private val ids: IdGenerator,
    private val sms: SmsGateway,
    private val otps: OtpRepository,
    private val accounts: AccountRepository,
    private val sessions: SessionRepository,
    private val rateLimiter: RateLimiter,
    private val cooldown: ResendCooldown,
    private val tokens: TokenService,
    private val deliveries: SmsDeliveryReceiptService,
    private val audit: AuditService? = null,
    private val abuse: AbuseSignalLogger? = null,
) {
    private val hasher = OtpHasher(config.pepper)
    private val codeGenerator = OtpCodeGenerator()
    private val idempotency = mutableMapOf<String, Pair<Long, OtpRequestResult>>()

    data class OtpRequestResult(
        val requestId: String,
        val retryAfterSeconds: Long,
        val expiresInSeconds: Long,
    )

    data class VerifyResult(
        val tokens: Tokens,
        val accountState: String,
    )

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

        val code = codeGenerator.generate()
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        val now = clock.nowMillis()
        val requestId = ids.newId()
        val record = OtpRecord(
            id = requestId,
            phone = phone,
            verifier = hasher.hash(code, salt),
            salt = salt,
            expiresAt = now + config.otpTtlMs,
            createdAt = now,
        )

        otps.invalidateActiveForPhone(phone, now)
        otps.save(record)

        val smsDelivery = sms.send(phone, code, requestId)
        if (smsDelivery.status != SmsDelivery.Status.SENT || smsDelivery.providerMessageId.isNullOrBlank()) {
            record.failedAt = now
            smsDelivery.providerMessageId?.takeIf { it.isNotBlank() }?.let {
                deliveries.recordProviderFailure(requestId, it, SmsFailureReason.PROVIDER_UNAVAILABLE)
            }
            audit?.record(AuditAction.OTP_REQUESTED, phone, requestId, AuditOutcome.FAILURE)
            throw AuthException(AuthFailure.PROVIDER_UNAVAILABLE)
        }

        deliveries.recordHandoff(requestId, smsDelivery.providerMessageId!!)
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
            code.length != OtpCodeGenerator.CODE_LENGTH || !code.all(Char::isDigit)
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
        if (record.attempts >= config.maxAttempts) throw AuthException(AuthFailure.TOO_MANY_ATTEMPTS)

        if (!hasher.matches(code, record.salt, record.verifier)) {
            record.attempts++
            abuse?.record(AbuseSignal.INVALID_CODE_BURST, phone)
            throw AuthException(AuthFailure.INVALID_CODE)
        }

        if (!otps.consume(requestId, now)) {
            abuse?.record(AbuseSignal.REPLAY_ATTEMPT, phone)
            throw AuthException(AuthFailure.CODE_CONSUMED)
        }

        val account = accounts.upsertByPhone(phone, now)
        audit?.record(AuditAction.OTP_VERIFIED, phone, requestId, AuditOutcome.SUCCESS)
        return VerifyResult(tokens.issue(account.id, deviceLabel), account.state)
    }

    fun refresh(refreshToken: String): Tokens = try {
        tokens.rotate(refreshToken).also { rotated ->
            audit?.record(AuditAction.SESSION_ROTATED, refreshToken, rotated.sessionId, AuditOutcome.SUCCESS)
        }
    } catch (e: AuthException) {
        abuse?.record(AbuseSignal.SESSION_REUSE, refreshToken)
        throw e
    }

    fun logout(refreshToken: String) {
        if (tokens.logout(refreshToken)) {
            audit?.record(AuditAction.SESSION_REVOKED, refreshToken, "logout", AuditOutcome.SUCCESS)
        }
    }
}

/** In-memory wiring of the reference implementation, used by CI and local review. */
data class ServerHarness(
    val service: AuthService,
    val otps: InMemoryOtpRepository,
    val accounts: InMemoryAccountRepository,
    val sessions: InMemorySessionRepository,
    val smsDeliveries: InMemorySmsDeliveryLog,
    val auditLog: InMemoryAuditLog,
    val abuseLog: InMemoryAbuseEventLog,
)

object AuthServiceFactory {
    fun inMemory(
        config: ServerConfig,
        clock: Clock,
        sms: SmsGateway,
        ids: IdGenerator = FixedIdGenerator(),
    ): ServerHarness {
        val otps = InMemoryOtpRepository()
        val accounts = InMemoryAccountRepository(ids)
        val sessions = InMemorySessionRepository()
        val smsDeliveries = InMemorySmsDeliveryLog()
        val auditLog = InMemoryAuditLog()
        val abuseLog = InMemoryAbuseEventLog()
        val service = AuthService(
            config = config,
            clock = clock,
            ids = ids,
            sms = sms,
            otps = otps,
            accounts = accounts,
            sessions = sessions,
            rateLimiter = RateLimiter(clock),
            cooldown = ResendCooldown(clock, config.cooldownMs),
            tokens = TokenService(config.pepper, ids, clock, sessions),
            deliveries = SmsDeliveryReceiptService(clock, smsDeliveries),
            audit = AuditService(clock, config.pepper, auditLog),
            abuse = AbuseSignalLogger(clock, config.pepper, abuseLog),
        )
        return ServerHarness(service, otps, accounts, sessions, smsDeliveries, auditLog, abuseLog)
    }
}
