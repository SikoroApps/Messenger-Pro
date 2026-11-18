package com.sikorokoro44.messengerpro.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ESmsVerifyGatewayTest {
    private companion object {
        const val KEY = "esms_test_9f8c7d6b5a"
        const val SANDBOX_CODE = "482193"
    }

    private class FakeTransport(
        private var response: ProviderHttpResponse,
    ) : ProviderHttpTransport {
        val requests = mutableListOf<ProviderHttpRequest>()

        override fun send(request: ProviderHttpRequest): ProviderHttpResponse {
            requests += request
            return response
        }

        fun respondWith(statusCode: Int, body: String) {
            response = ProviderHttpResponse(statusCode, body)
        }
    }

    private fun gateway(transport: ProviderHttpTransport): ESmsVerifyGateway =
        ESmsVerifyGateway(KEY, transport)

    private fun failureOf(block: () -> Unit): ESmsException = try {
        block()
        throw AssertionError("Expected ESmsException")
    } catch (e: ESmsException) {
        e
    }

    @Test
    fun productionKeyIsRejectedBeforeAnyNetworkCall() {
        val transport = FakeTransport(ProviderHttpResponse(200, "{}"))
        val thrown = try {
            ESmsVerifyGateway("esms_live_production", transport)
            throw AssertionError("Expected rejection")
        } catch (e: IllegalArgumentException) {
            e
        }
        assertTrue(thrown.message!!.contains("sandbox"))
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun startUsesPinnedEndpointAndDoesNotExposeTheSandboxCode() {
        val transport = FakeTransport(
            ProviderHttpResponse(
                200,
                """{"verification_id":"ver_1","status":"pending","to":"+2687***","sandbox_code":"$SANDBOX_CODE","environment":"test","livemode":false}""",
            ),
        )
        val result = gateway(transport).start("+26876123456", "req-1")

        assertEquals("ver_1", result.verificationId)
        assertTrue(result.sandbox)
        val request = transport.requests.single()
        assertEquals("POST", request.method)
        assertEquals("https://sms.esmsafrica.io/v1/verify/start", request.url)
        assertEquals("Bearer $KEY", request.headers["Authorization"])
        assertEquals("mp-otp-req-1", request.headers["Idempotency-Key"])
        assertFalse(request.body.contains(KEY))
        assertFalse(request.body.contains(SANDBOX_CODE))
        assertFalse(result.toString().contains(SANDBOX_CODE))
        assertFalse(result.toString().contains(KEY))
    }

    @Test
    fun checkMapsStableProviderStatuses() {
        val expected = mapOf(
            "approved" to ManagedOtpStatus.APPROVED,
            "pending" to ManagedOtpStatus.PENDING,
            "failed" to ManagedOtpStatus.FAILED,
            "expired" to ManagedOtpStatus.EXPIRED,
            "canceled" to ManagedOtpStatus.CANCELED,
            "cancelled" to ManagedOtpStatus.CANCELED,
            "unexpected" to ManagedOtpStatus.UNKNOWN,
        )
        expected.forEach { (providerStatus, mapped) ->
            val transport = FakeTransport(
                ProviderHttpResponse(200, """{"status":"$providerStatus","environment":"test"}"""),
            )
            val result = gateway(transport).check("ver_1", "123456")
            assertEquals(providerStatus, mapped, result.status)
            assertTrue(result.sandbox)
            val request = transport.requests.single()
            assertEquals("https://sms.esmsafrica.io/v1/verify/check", request.url)
            assertEquals("Bearer $KEY", request.headers["Authorization"])
            assertTrue(request.body.contains("123456"))
            assertFalse(request.body.contains(KEY))
        }
    }

    @Test
    fun httpErrorsUseGenericReasonsWithoutProviderProseOrSecrets() {
        val transport = FakeTransport(
            ProviderHttpResponse(429, """{"error":"too_many_requests","sandbox_code":"$SANDBOX_CODE"}"""),
        )
        val thrown = failureOf { gateway(transport).start("+26876123456", "req-1") }

        assertEquals(ESmsFailureReason.RATE_LIMITED, thrown.reason)
        assertEquals("RATE_LIMITED", thrown.message)
        assertFalse(thrown.message!!.contains("too_many_requests"))
        assertFalse(thrown.message!!.contains(SANDBOX_CODE))
        assertFalse(thrown.message!!.contains(KEY))
    }

    @Test
    fun invalidProviderResponseIsRejectedWithoutEchoingTheBody() {
        val transport = FakeTransport(
            ProviderHttpResponse(200, """{"verification_id":"","secret":"$KEY"}"""),
        )
        val thrown = failureOf { gateway(transport).start("+26876123456", "req-1") }

        assertEquals(ESmsFailureReason.INVALID_RESPONSE, thrown.reason)
        assertFalse(thrown.message!!.contains(KEY))
    }
}
