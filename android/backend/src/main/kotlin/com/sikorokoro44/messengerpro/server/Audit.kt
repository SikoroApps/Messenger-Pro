package com.sikorokoro44.messengerpro.server

/** Generic abuse categories. No provider prose, phone number, OTP or token is represented. */
enum class AbuseSignal {
    INVALID_PHONE,
    INVALID_CODE_BURST,
    OTP_REQUEST_BURST,
    RATE_LIMIT_HIT,
    REPLAY_ATTEMPT,
    SESSION_REUSE,
}

/** Coarse audit actions. The outcome is never a detailed upstream error message. */
enum class AuditAction {
    OTP_REQUESTED,
    OTP_VERIFIED,
    SESSION_ROTATED,
    SESSION_REVOKED,
}

enum class AuditOutcome { SUCCESS, FAILURE, THROTTLED }

/**
 * Hash-only audit record. [subjectHash] and [correlationHash] are keyed digests; no raw phone,
 * request id, code, token, message or provider response is ever stored.
 */
data class AuditRecord(
    val action: AuditAction,
    val subjectHash: String,
    val correlationHash: String,
    val outcome: AuditOutcome,
    val occurredAt: Long,
)

data class AbuseEvent(
    val signal: AbuseSignal,
    val subjectHash: String,
    val occurredAt: Long,
)

interface AuditLog {
    fun record(record: AuditRecord)
    fun all(): List<AuditRecord>
}

interface AbuseEventLog {
    fun record(event: AbuseEvent)
    fun all(): List<AbuseEvent>
}

class InMemoryAuditLog : AuditLog {
    private val records = mutableListOf<AuditRecord>()

    @Synchronized
    override fun record(record: AuditRecord) {
        records += record
    }

    @Synchronized
    override fun all(): List<AuditRecord> = records.toList()
}

class InMemoryAbuseEventLog : AbuseEventLog {
    private val events = mutableListOf<AbuseEvent>()

    @Synchronized
    override fun record(event: AbuseEvent) {
        events += event
    }

    @Synchronized
    override fun all(): List<AbuseEvent> = events.toList()
}

/** Converts raw identifiers to keyed hashes before anything reaches a durable log. */
class AuditService(
    private val clock: Clock,
    private val pepper: ByteArray,
    private val log: AuditLog,
) {
    fun record(
        action: AuditAction,
        subject: String,
        correlationId: String,
        outcome: AuditOutcome,
    ) {
        log.record(
            AuditRecord(
                action = action,
                subjectHash = DiagnosticsRedactor.subjectHash(subject, pepper),
                correlationHash = DiagnosticsRedactor.subjectHash(correlationId, pepper),
                outcome = outcome,
                occurredAt = clock.nowMillis(),
            ),
        )
    }
}

/** Records only generic signals and keyed subject hashes for abuse monitoring. */
class AbuseSignalLogger(
    private val clock: Clock,
    private val pepper: ByteArray,
    private val log: AbuseEventLog,
) {
    fun record(signal: AbuseSignal, subject: String) {
        log.record(
            AbuseEvent(
                signal = signal,
                subjectHash = DiagnosticsRedactor.subjectHash(subject, pepper),
                occurredAt = clock.nowMillis(),
            ),
        )
    }
}
