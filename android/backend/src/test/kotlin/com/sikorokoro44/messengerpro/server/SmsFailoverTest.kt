package com.sikorokoro44.messengerpro.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class SmsFailoverTest {
    private class RecordingGateway(private val delivery: SmsDelivery) : SmsGateway {
        var calls: Int = 0
            private set
        var lastPhone: String? = null
            private set
        var lastCode: String? = null
            private set
        var lastRequestId: String? = null
            private set

        override fun send(phone: String, code: String, requestId: String): SmsDelivery {
            calls++
            lastPhone = phone
            lastCode = code
            lastRequestId = requestId
            return delivery
        }
    }

    private fun config() = ServerConfig(
        pepper = "failover-test-pepper-do-not-commit".toByteArray(),
        environment = "test",
    )

    private val sent = SmsDelivery(SmsDelivery.Status.SENT, "primary-1")
    private val cleanFailure = SmsDelivery(SmsDelivery.Status.FAILED)
    private val ambiguousFailure = SmsDelivery(SmsDelivery.Status.FAILED, "primary-attempt-1")

    @Test
    fun primarySuccessDoesNotUseSecondaryRoute() {
        val primary = RecordingGateway(sent)
        val secondary = RecordingGateway(sent.copy(providerMessageId = "secondary-1"))
        val gateway = FailoverSmsGateway(primary, secondary)

        val delivery = gateway.send("+26876123456", "482913", "request-1")

        assertEquals(sent, delivery)
        assertEquals(1, primary.calls)
        assertEquals(0, secondary.calls)
    }

    @Test
    fun cleanPrimaryFailureUsesSecondaryRouteWithTheSameRequestScope() {
        val primary = RecordingGateway(cleanFailure)
        val secondary = RecordingGateway(SmsDelivery(SmsDelivery.Status.SENT, "secondary-1"))
        val gateway = FailoverSmsGateway(primary, secondary)

        val delivery = gateway.send("+26876123456", "482913", "request-1")

        assertEquals("secondary-1", delivery.providerMessageId)
        assertEquals(1, primary.calls)
        assertEquals(1, secondary.calls)
        assertEquals("+26876123456", secondary.lastPhone)
        assertEquals("482913", secondary.lastCode)
        assertEquals("request-1", secondary.lastRequestId)
    }

    @Test
    fun ambiguousPrimaryFailureDoesNotSendASecondCode() {
        val primary = RecordingGateway(ambiguousFailure)
        val secondary = RecordingGateway(sent.copy(providerMessageId = "secondary-1"))
        val gateway = FailoverSmsGateway(primary, secondary)

        val delivery = gateway.send("+26876123456", "482913", "request-1")

        assertEquals(ambiguousFailure, delivery)
        assertEquals(1, primary.calls)
        assertEquals(0, secondary.calls)
    }

    @Test
    fun secondaryHandoffUsesItsOwnProviderIdInMetadataOnlyDeliveryRecord() {
        val clock = FakeClock()
        val primary = RecordingGateway(cleanFailure)
        val secondary = RecordingGateway(SmsDelivery(SmsDelivery.Status.SENT, "secondary-1"))
        val harness = AuthServiceFactory.inMemory(config(), clock, FailoverSmsGateway(primary, secondary))

        val result = harness.service.requestOtp("+26876123456", "ip-hash")
        val handoff = harness.smsDeliveries.latest(result.requestId)

        assertNotNull(handoff)
        assertEquals("secondary-1", handoff!!.providerMessageId)
        assertEquals(SmsDeliveryState.HANDED_OFF, handoff.state)
        assertEquals(1, primary.calls)
        assertEquals(1, secondary.calls)
        assertFalse(handoff.toString().contains("+26876123456"))
        assertFalse(handoff.toString().contains(secondary.lastCode!!))
    }

    @Test
    fun secondaryFailureGrantsNoAccountOrSession() {
        val primary = RecordingGateway(cleanFailure)
        val secondary = RecordingGateway(SmsDelivery(SmsDelivery.Status.FAILED, "secondary-fail-1"))
        val harness = AuthServiceFactory.inMemory(
            config(),
            FakeClock(),
            FailoverSmsGateway(primary, secondary),
        )

        val failure = runCatching { harness.service.requestOtp("+26876123456", "ip-hash") }
            .exceptionOrNull() as AuthException

        assertEquals(AuthFailure.PROVIDER_UNAVAILABLE, failure.failure)
        assertEquals(0, harness.accounts.count())
        assertEquals(0, harness.sessions.count())
        assertEquals(SmsDeliveryState.FAILED, harness.smsDeliveries.latest("id-1")!!.state)
    }
}
