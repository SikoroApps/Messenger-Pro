package com.sikorokoro44.messengerpro.server

/**
 * Provider-independent secondary-route policy. Failover happens only when the primary route
 * reported failure without a provider message id. A failed attempt that already has a provider id
 * is treated as ambiguous: retrying it could deliver a second code to the same number, so the
 * result is returned unchanged for the caller to handle as a failed request.
 */
class FailoverSmsGateway(
    private val primary: SmsGateway,
    private val secondary: SmsGateway,
) : SmsGateway {
    override fun send(phone: String, code: String, requestId: String): SmsDelivery {
        val first = primary.send(phone, code, requestId)
        if (first.status == SmsDelivery.Status.SENT) return first
        if (!first.providerMessageId.isNullOrBlank()) return first
        return secondary.send(phone, code, requestId)
    }
}
