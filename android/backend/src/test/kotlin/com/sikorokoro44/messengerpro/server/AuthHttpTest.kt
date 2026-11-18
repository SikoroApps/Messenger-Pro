package com.sikorokoro44.messengerpro.server

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

class AuthHttpTest {
    private class RecordingSmsGateway(var fail: Boolean = false) : SmsGateway {
        var lastCode: String? = null
        var calls = 0

        override fun send(phone: String, code: String, requestId: String): SmsDelivery {
            calls++
            lastCode = code
            return if (fail) {
                SmsDelivery(SmsDelivery.Status.FAILED)
            } else {
                SmsDelivery(SmsDelivery.Status.SENT, "provider-$calls")
            }
        }
    }

    private class Rig(
        val server: LoopbackAuthServer,
        val harness: ServerHarness,
        val sms: RecordingSmsGateway,
    ) : AutoCloseable {
        private val client = HttpClient.newHttpClient()

        fun post(path: String, body: String, idempotencyKey: String? = null): HttpResponse<String> {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:${server.port}$path"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            if (idempotencyKey != null) request = request.header("Idempotency-Key", idempotencyKey)
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString())
        }

        override fun close() {
            server.close()
        }
    }

    private fun rig(
        cooldownMs: Long = 0L,
        failSms: Boolean = false,
        perPhoneLimit: Int = 100,
    ): Rig {
        val clock = FakeClock()
        val sms = RecordingSmsGateway(failSms)
        val config = ServerConfig(
            pepper = "http-test-pepper-do-not-commit".toByteArray(),
            environment = "test",
            cooldownMs = cooldownMs,
            perPhoneLimit = perPhoneLimit,
            perIpLimit = 1000,
            globalLimit = 10_000,
        )
        val harness = AuthServiceFactory.inMemory(config, clock, sms)
        return Rig(LoopbackAuthServer(AuthHttpHandler(harness.service, config)), harness, sms)
    }

    private fun otpRequestJson(phone: String = "+26876123456") = JSONObject().apply {
        put("phone_e164", phone)
        put("locale", "ss-ZA")
        put("client", "android")
    }.toString()

    private fun verifyJson(
        phone: String,
        requestId: String,
        code: String,
        deviceLabel: String? = null,
    ) = JSONObject().apply {
        put("phone_e164", phone)
        put("request_id", requestId)
        put("code", code)
        deviceLabel?.let { put("device_label", it) }
    }.toString()

    private fun tokenJson(token: String) = JSONObject().put("refresh_token", token).toString()

    @Test
    fun otpContractOverRealHttpNeverReturnsTheCodeOrPhone() {
        val phone = "+26876123456"
        rig().use { r ->
            val requested = r.post("/auth/otp/request", otpRequestJson(phone), "idem-http-1")
            assertEquals(200, requested.statusCode())
            val requestBody = JSONObject(requested.body())
            val requestId = requestBody.getString("request_id")
            assertTrue(requestId.isNotBlank())
            assertEquals(300L, requestBody.getLong("expires_in"))
            assertEquals(0L, requestBody.getLong("retry_after"))
            assertFalse(requested.body().contains(phone))
            assertFalse(requested.body().contains(r.sms.lastCode!!))

            val verified = r.post("/auth/otp/verify", verifyJson(phone, requestId, r.sms.lastCode!!))
            assertEquals(200, verified.statusCode())
            val body = JSONObject(verified.body())
            assertTrue(body.getBoolean("verified"))
            assertEquals("active", body.getString("account_state"))
            assertTrue(body.getString("access_token").isNotBlank())
            assertTrue(body.getString("refresh_token").isNotBlank())
            assertFalse(verified.body().contains(phone))
            assertFalse(verified.body().contains(r.sms.lastCode!!))
            assertEquals(1, r.harness.accounts.count())
            assertEquals(1, r.harness.sessions.count())
        }
    }

    @Test
    fun verificationCreatesBoundedDeviceSessionRecordAndPreservesItOnRefresh() {
        val phone = "+26876123456"
        rig().use { r ->
            val requested = r.post("/auth/otp/request", otpRequestJson(phone), "device-idem-1")
            val requestId = JSONObject(requested.body()).getString("request_id")
            val verified = r.post(
                "/auth/otp/verify",
                verifyJson(phone, requestId, r.sms.lastCode!!, "  Pixel <7>\nPro  "),
            )
            assertEquals(200, verified.statusCode())
            val body = JSONObject(verified.body())
            val sessionId = body.getString("session_id")
            val session = r.harness.sessions.findById(sessionId)
            assertNotNull(session)
            assertEquals("Pixel 7 Pro", session!!.deviceLabel)
            assertEquals(1, r.harness.sessions.count())

            val refreshed = r.post(
                "/auth/session/refresh",
                tokenJson(body.getString("refresh_token")),
            )
            assertEquals(200, refreshed.statusCode())
            val rotatedId = JSONObject(refreshed.body()).getString("session_id")
            assertEquals("Pixel 7 Pro", r.harness.sessions.findById(rotatedId)!!.deviceLabel)
            assertEquals(2, r.harness.sessions.count())
            assertEquals(64, DeviceLabelPolicy.sanitize("x".repeat(100))!!.length)
        }
    }

    @Test
    fun idempotencyKeyOverHttpPreventsDuplicateSms() {
        rig().use { r ->
            val first = r.post("/auth/otp/request", otpRequestJson(), "network-retry-1")
            val second = r.post("/auth/otp/request", otpRequestJson(), "network-retry-1")
            assertEquals(200, first.statusCode())
            assertEquals(200, second.statusCode())
            assertEquals(
                JSONObject(first.body()).getString("request_id"),
                JSONObject(second.body()).getString("request_id"),
            )
            assertEquals(1, r.sms.calls)
        }
    }

    @Test
    fun httpErrorsUseStableCodesAndReplayIsRejected() {
        val phone = "+26876123456"
        rig().use { r ->
            val requested = r.post("/auth/otp/request", otpRequestJson(phone))
            val requestId = JSONObject(requested.body()).getString("request_id")
            val code = r.sms.lastCode!!

            val wrong = r.post("/auth/otp/verify", verifyJson(phone, requestId, if (code == "000000") "111111" else "000000"))
            assertEquals(400, wrong.statusCode())
            assertEquals("INVALID_CODE", JSONObject(wrong.body()).getString("error"))
            assertFalse(wrong.body().contains("registered"))

            val verified = r.post("/auth/otp/verify", verifyJson(phone, requestId, code))
            assertEquals(200, verified.statusCode())
            val tokens = JSONObject(verified.body())

            val replay = r.post("/auth/otp/verify", verifyJson(phone, requestId, code))
            assertEquals(401, replay.statusCode())
            assertEquals("CODE_CONSUMED", JSONObject(replay.body()).getString("error"))

            val rotatedResponse = r.post("/auth/session/refresh", tokenJson(tokens.getString("refresh_token")))
            assertEquals(200, rotatedResponse.statusCode())
            val rotated = JSONObject(rotatedResponse.body())
            assertNotEquals(tokens.getString("refresh_token"), rotated.getString("refresh_token"))

            val reused = r.post("/auth/session/refresh", tokenJson(tokens.getString("refresh_token")))
            assertEquals(401, reused.statusCode())
            assertEquals("SESSION_REVOKED", JSONObject(reused.body()).getString("error"))

            val logout = r.post("/auth/session/logout", tokenJson(rotated.getString("refresh_token")))
            assertEquals(200, logout.statusCode())
            assertEquals("{}", logout.body())

            val afterLogout = r.post("/auth/session/refresh", tokenJson(rotated.getString("refresh_token")))
            assertEquals(401, afterLogout.statusCode())
            assertEquals("SESSION_REVOKED", JSONObject(afterLogout.body()).getString("error"))
        }
    }

    @Test
    fun cooldownIsEnforcedOverHttpWithRetryAfter() {
        rig(cooldownMs = 60_000L).use { r ->
            assertEquals(200, r.post("/auth/otp/request", otpRequestJson()).statusCode())
            val throttled = r.post("/auth/otp/request", otpRequestJson())
            assertEquals(429, throttled.statusCode())
            assertEquals("COOLDOWN_ACTIVE", JSONObject(throttled.body()).getString("error"))
            assertEquals("60", throttled.headers().firstValue("Retry-After").orElse(null))
            assertEquals(1, r.sms.calls)
        }
    }

    @Test
    fun providerFailureReturnsGeneric503AndGrantsNothing() {
        rig(failSms = true).use { r ->
            val response = r.post("/auth/otp/request", otpRequestJson())
            assertEquals(503, response.statusCode())
            assertEquals("PROVIDER_UNAVAILABLE", JSONObject(response.body()).getString("error"))
            assertFalse(response.body().contains("+26876123456"))
            assertFalse(response.body().contains(r.sms.lastCode!!))
            assertEquals(0, r.harness.accounts.count())
            assertEquals(0, r.harness.sessions.count())
        }
    }

    @Test
    fun malformedInputAndUnknownRoutesFailClosed() {
        rig().use { r ->
            val invalidPhone = r.post("/auth/otp/request", otpRequestJson("not-a-phone"))
            assertEquals(400, invalidPhone.statusCode())
            assertEquals("INVALID_PHONE", JSONObject(invalidPhone.body()).getString("error"))

            val unknown = r.post("/auth/not-real", "{}")
            assertEquals(404, unknown.statusCode())

            val oversized = r.post("/auth/otp/request", "x".repeat(8 * 1024 + 1))
            assertEquals(503, oversized.statusCode())
            assertEquals("UNAVAILABLE", JSONObject(oversized.body()).getString("error"))
            assertEquals(0, r.sms.calls)
        }
    }
}
