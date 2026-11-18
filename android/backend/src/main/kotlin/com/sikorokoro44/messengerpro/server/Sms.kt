package com.sikorokoro44.messengerpro.server

/** Provider-independent hand-off. Provider credentials never enter the client or repository. */
interface SmsGateway {
    fun send(phone: String, code: String, requestId: String): SmsDelivery
}

data class SmsDelivery(
    val status: Status,
    val providerMessageId: String? = null,
) {
    enum class Status { SENT, FAILED }
}

/**
 * Deterministic development/test adapter. It does not send a real message and is only allowed
 * when the deployment environment is explicitly non-production.
 */
class SandboxSmsGateway(private val environment: String) : SmsGateway {
    init {
        require(environment.equals("dev", ignoreCase = true) || environment.equals("test", ignoreCase = true)) {
            "Sandbox SMS gateway is forbidden in production"
        }
    }

    override fun send(phone: String, code: String, requestId: String): SmsDelivery =
        SmsDelivery(SmsDelivery.Status.SENT, "sandbox-$requestId")
}

/** Adapter used by negative tests and by failover simulation. */
class FailingSmsGateway : SmsGateway {
    override fun send(phone: String, code: String, requestId: String): SmsDelivery =
        SmsDelivery(SmsDelivery.Status.FAILED)
}

/** Generic failure categories only; provider prose is never persisted or returned to clients. */
enum class SmsFailureReason {
    PROVIDER_UNAVAILABLE,
    INVALID_RECIPIENT,
    EXPIRED,
    UNKNOWN,
}

enum class SmsDeliveryState { HANDED_OFF, DELIVERED, FAILED }

/**
 * Metadata-only delivery record. It never contains a phone number, OTP, raw provider payload or
 * diagnostic text; request and provider ids are opaque correlation values.
 */
data class SmsDeliveryEvent(
    val requestId: String,
    val providerMessageId: String,
    val state: SmsDeliveryState,
    val occurredAt: Long,
    val failureReason: SmsFailureReason? = null,
) {
    init {
        require(requestId.isNotBlank()) { "Request id is required" }
        require(providerMessageId.isNotBlank()) { "Provider message id is required" }
        require((state == SmsDeliveryState.FAILED) == (failureReason != null)) {
            "Failure reason is required only for failed delivery events"
        }
    }
}

interface SmsDeliveryLog {
    fun record(event: SmsDeliveryEvent)
    fun latest(requestId: String): SmsDeliveryEvent?
    fun count(): Int
}

class InMemorySmsDeliveryLog : SmsDeliveryLog {
    private val events = linkedMapOf<String, MutableList<SmsDeliveryEvent>>()

    @Synchronized
    override fun record(event: SmsDeliveryEvent) {
        events.getOrPut(event.requestId) { mutableListOf() } += event
    }

    @Synchronized
    override fun latest(requestId: String): SmsDeliveryEvent? = events[requestId]?.lastOrNull()

    @Synchronized
    override fun count(): Int = events.values.sumOf { it.size }
}

/**
 * Applies deterministic receipt transitions. A receipt must match the provider message id from
 * the hand-off, and terminal states cannot be overwritten by a later or conflicting receipt.
 */
class SmsDeliveryReceiptService(
    private val clock: Clock,
    private val log: SmsDeliveryLog,
) {
    fun recordHandoff(requestId: String, providerMessageId: String) {
        record(requestId, providerMessageId, SmsDeliveryState.HANDED_OFF, null)
    }

    fun recordProviderFailure(
        requestId: String,
        providerMessageId: String,
        reason: SmsFailureReason = SmsFailureReason.PROVIDER_UNAVAILABLE,
    ) {
        record(requestId, providerMessageId, SmsDeliveryState.FAILED, reason)
    }

    fun recordReceipt(
        requestId: String,
        providerMessageId: String,
        delivered: Boolean,
        reason: SmsFailureReason = SmsFailureReason.UNKNOWN,
    ) {
        record(
            requestId,
            providerMessageId,
            if (delivered) SmsDeliveryState.DELIVERED else SmsDeliveryState.FAILED,
            if (delivered) null else reason,
        )
    }

    fun latest(requestId: String): SmsDeliveryEvent? = log.latest(requestId)

    private fun record(
        requestId: String,
        providerMessageId: String,
        state: SmsDeliveryState,
        reason: SmsFailureReason?,
    ) {
        val previous = log.latest(requestId)
        require(previous == null || previous.providerMessageId == providerMessageId) {
            "Delivery receipt provider id does not match the hand-off"
        }
        require(previous?.state != SmsDeliveryState.DELIVERED && previous?.state != SmsDeliveryState.FAILED) {
            "Delivery receipt cannot overwrite a terminal state"
        }
        log.record(
            SmsDeliveryEvent(
                requestId = requestId,
                providerMessageId = providerMessageId,
                state = state,
                occurredAt = clock.nowMillis(),
                failureReason = reason,
            ),
        )
    }
}
