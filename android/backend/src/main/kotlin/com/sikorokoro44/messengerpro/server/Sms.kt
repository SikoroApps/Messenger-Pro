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
