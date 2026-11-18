package com.sikorokoro44.messengerpro.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsDeliveryTest {
    private class RecordingSmsGateway(
        var fail: Boolean = false,
        var providerId: String? = "provider-1",
    ) : SmsGateway {
        var lastCode: String? = null
        var lastPhone: String? = null

        override fun send(phone: String, code: String, requestId: String): SmsDelivery {
            lastCode = code
            lastPhone = phone
            return if (fail) {
                SmsDelivery(SmsDelivery.Status.FAILED, providerId)
            } else {
                SmsDelivery(SmsDelivery.Status.SENT, providerId)
            }
        }
    }

    private fun config() = ServerConfig(
        pepper = "server-pepper-do-not-commit".toByteArray(),
        environment = "test",
        cooldownMs = 60_000L,
        perPhoneLimit = 100,
        perIpLimit = 1000,
        globalLimit = 10_000,
    )

    @Test
    fun handoffAndDeliveryReceiptStoreMetadataOnly() {
        val clock = FakeClock()
        val sms = RecordingSmsGateway()
        val harness = AuthServiceFactory.inMemory(config(), clock, sms)

        val result = harness.service.requestOtp("+26876123456", "ip-hash")

        val handoff = harness.smsDeliveries.latest(result.requestId)
        assertNotNull(handoff)
        assertEquals(SmsDeliveryState.HANDED_OFF, handoff!!.state)
        assertEquals("provider-1", handoff.providerMessageId)
        assertEquals(1, harness.smsDeliveries.count())
        assertFalse(handoff.toString().contains("+26876123456"))
        assertFalse(handoff.toString().contains(sms.lastCode!!))

        val receipts = SmsDeliveryReceiptService(clock, harness.smsDeliveries)
        receipts.recordReceipt(result.requestId, "provider-1", delivered = true)

        val delivered = harness.smsDeliveries.latest(result.requestId)
        assertEquals(SmsDeliveryState.DELIVERED, delivered!!.state)
        assertEquals(null, delivered.failureReason)
        assertEquals(2, harness.smsDeliveries.count())
    }

    @Test
    fun providerFailureIsRecordedAndGrantsNoAccountOrSession() {
        val clock = FakeClock()
        val sms = RecordingSmsGateway(fail = true, providerId = "provider-fail-1")
        val harness = AuthServiceFactory.inMemory(config(), clock, sms)

        val thrown = try {
            harness.service.requestOtp("+26876123456", "ip-hash")
            throw AssertionError("Expected AuthException")
        } catch (e: AuthException) {
            e
        }
        assertEquals(AuthFailure.PROVIDER_UNAVAILABLE, thrown.failure)

        val failed = harness.smsDeliveries.latest("id-1")
        assertNotNull(failed)
        assertEquals(SmsDeliveryState.FAILED, failed!!.state)
        assertEquals(SmsFailureReason.PROVIDER_UNAVAILABLE, failed.failureReason)
        assertEquals(0, harness.accounts.count())
        assertEquals(0, harness.sessions.count())
    }

    @Test
    fun receiptsRejectMismatchedProviderIdsAndTerminalStateChanges() {
        val clock = FakeClock()
        val log = InMemorySmsDeliveryLog()
        val receipts = SmsDeliveryReceiptService(clock, log)
        receipts.recordHandoff("request-1", "provider-1")

        assertFalse(
            runCatching {
                receipts.recordReceipt("request-1", "other-provider", delivered = true)
            }.isSuccess,
        )

        receipts.recordReceipt("request-1", "provider-1", delivered = true)
        assertEquals(SmsDeliveryState.DELIVERED, log.latest("request-1")!!.state)
        assertFalse(
            runCatching {
                receipts.recordReceipt(
                    "request-1",
                    "provider-1",
                    delivered = false,
                    reason = SmsFailureReason.EXPIRED,
                )
            }.isSuccess,
        )
        assertEquals(2, log.count())
    }

    @Test
    fun deliveryEventRequiresAnOpaqueProviderId() {
        assertFalse(
            runCatching {
                SmsDeliveryEvent("request-1", "", SmsDeliveryState.HANDED_OFF, 1L)
            }.isSuccess,
        )
        assertFalse(
            runCatching {
                SmsDeliveryEvent("request-1", "provider-1", SmsDeliveryState.FAILED, 1L)
            }.isSuccess,
        )
        assertTrue(
            runCatching {
                SmsDeliveryEvent(
                    "request-1",
                    "provider-1",
                    SmsDeliveryState.FAILED,
                    1L,
                    SmsFailureReason.UNKNOWN,
                )
            }.isSuccess,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun sandboxGatewayCannotBeUsedInProduction() {
        SandboxSmsGateway("production")
    }
}
