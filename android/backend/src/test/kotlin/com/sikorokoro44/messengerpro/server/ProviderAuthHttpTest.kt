package com.sikorokoro44.messengerpro.server

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

/**
 * End-to-end HTTP contract test with a single fake transport standing in for both pinned eSMS
 * and Supabase hosts. No real provider or database is contacted and no secret is printed.
 */
class ProviderAuthHttpTest {
    private companion object {
        const val PHONE = "+26876123456"
        const val CODE = "739184"
        const val WRONG_CODE = "482193"
        const val ESMS_KEY = "esms_test_http_fixture"
        const val SERVICE_KEY = "sb_secret_http_fixture"
        const val SUPABASE_URL = "https://project.supabase.co"
    }

    private class FakeBackend : ProviderHttpTransport {
        val requests = mutableListOf<ProviderHttpRequest>()
        val otps = linkedMapOf<String, MutableMap<String, Any?>>()
        val accounts = linkedMapOf<String, MutableMap<String, Any?>>()
        val sessions = linkedMapOf<String, MutableMap<String, Any?>>()
        val consumed = linkedMapOf<String, String>()
        var startCalls = 0
        var checkCalls = 0
        var startStatusCode = 200
        var lastCheckedCode: String? = null

        override fun send(request: ProviderHttpRequest): ProviderHttpResponse {
            requests += request
            return when {
                request.url.startsWith("https://sms.esmsafrica.io/v1/verify/start") -> start(request)
                request.url.startsWith("https://sms.esmsafrica.io/v1/verify/check") -> check(request)
                request.url.startsWith("$SUPABASE_URL/rest/v1/") -> rest(request)
                else -> ProviderHttpResponse(404, "{}")
            }
        }

        private fun start(request: ProviderHttpRequest): ProviderHttpResponse {
            startCalls++
            if (startStatusCode != 200) {
                return ProviderHttpResponse(
                    startStatusCode,
                    """{"error":"insufficient_balance","sandbox_code":"$CODE"}""",
                )
            }
            val body = JSONObject(request.body)
            val idempotencyKey = request.headers["Idempotency-Key"] ?: "unknown"
            val verificationId = "ver_" + idempotencyKey.removePrefix("mp-otp-")
            return ProviderHttpResponse(
                200,
                JSONObject()
                    .put("verification_id", verificationId)
                    .put("status", "pending")
                    .put("to", body.optString("to").replace(Regex("[0-9]"), "*"))
                    .put("environment", "test")
                    .put("livemode", false)
                    .put("sandbox_code", CODE)
                    .toString(),
            )
        }

        private fun check(request: ProviderHttpRequest): ProviderHttpResponse {
            checkCalls++
            val body = JSONObject(request.body)
            lastCheckedCode = body.optString("code")
            val status = if (lastCheckedCode == CODE) "approved" else "failed"
            return ProviderHttpResponse(
                200,
                JSONObject()
                    .put("status", status)
                    .put("environment", "test")
                    .put("livemode", false)
                    .toString(),
            )
        }

        private fun rest(request: ProviderHttpRequest): ProviderHttpResponse {
            val path = URL(request.url).path
            return when {
                path == "/rest/v1/rpc/mp_upsert_account" && request.method == "POST" -> upsertAccount(request)
                path == "/rest/v1/provider_otp_requests" && request.method == "POST" -> saveOtp(request)
                path == "/rest/v1/provider_otp_requests" && request.method == "GET" -> findOtp(request)
                path == "/rest/v1/provider_otp_requests" && request.method == "PATCH" -> patchOtp(request)
                path == "/rest/v1/sessions" && request.method == "POST" -> saveSession(request)
                path == "/rest/v1/sessions" && request.method == "GET" -> findSession(request)
                path == "/rest/v1/sessions" && request.method == "PATCH" -> patchSession(request)
                path == "/rest/v1/consumed_refresh_tokens" && request.method == "POST" -> markConsumed(request)
                path == "/rest/v1/consumed_refresh_tokens" && request.method == "GET" -> isConsumed(request)
                else -> arrayResponse(emptyList())
            }
        }

        private fun upsertAccount(request: ProviderHttpRequest): ProviderHttpResponse {
            val body = JSONObject(request.body)
            val phone = body.getString("p_phone")
            val now = body.getLong("p_now")
            val account = accounts.values.firstOrNull { it["phone_e164"] == phone }
                ?: mutableMapOf<String, Any?>(
                    "id" to "acct-${accounts.size + 1}",
                    "phone_e164" to phone,
                    "state" to "active",
                    "display_name" to null,
                    "created_at" to now,
                ).also { accounts[it["id"] as String] = it }
            return arrayResponse(listOf(json(account)))
        }

        private fun saveOtp(request: ProviderHttpRequest): ProviderHttpResponse {
            val body = JSONObject(request.body)
            val id = body.getString("id")
            otps[id] = mutableMapOf(
                "id" to id,
                "phone_e164" to body.getString("phone_e164"),
                "verification_id" to body.getString("verification_id"),
                "expires_at" to body.getLong("expires_at"),
                "created_at" to body.getLong("created_at"),
                "attempts" to body.optInt("attempts"),
                "consumed_at" to null,
                "failed_at" to null,
            )
            return arrayResponse(listOf(JSONObject().put("id", id)))
        }

        private fun findOtp(request: ProviderHttpRequest): ProviderHttpResponse {
            val id = queryValue(request.url, "id") ?: return arrayResponse(emptyList())
            val row = otps[id] ?: return arrayResponse(emptyList())
            return arrayResponse(listOf(json(row)))
        }

        private fun patchOtp(request: ProviderHttpRequest): ProviderHttpResponse {
            val id = queryValue(request.url, "id")
            val phone = queryValue(request.url, "phone_e164")
            val body = JSONObject(request.body)
            val targets = if (id != null) {
                listOfNotNull(otps[id])
            } else {
                otps.values.filter { phone == null || it["phone_e164"] == phone }
            }
            val updated = mutableListOf<Any>()
            targets.forEach { row ->
                if (row["consumed_at"] == null && row["failed_at"] == null) {
                    body.keys().forEach { key -> row[key] = body.get(key) }
                    updated += JSONObject().put("id", row["id"])
                }
            }
            return arrayResponse(updated)
        }

        private fun saveSession(request: ProviderHttpRequest): ProviderHttpResponse {
            val body = JSONObject(request.body)
            val id = body.getString("id")
            val label = if (body.isNull("device_label")) null else body.optString("device_label")
            sessions[id] = mutableMapOf(
                "id" to id,
                "account_id" to body.getString("account_id"),
                "refresh_hash" to body.getString("refresh_hash"),
                "expires_at" to body.getLong("expires_at"),
                "created_at" to body.getLong("created_at"),
                "device_label" to label,
                "revoked_at" to null,
            )
            return arrayResponse(listOf(JSONObject().put("id", id)))
        }

        private fun findSession(request: ProviderHttpRequest): ProviderHttpResponse {
            val hash = queryValue(request.url, "refresh_hash") ?: return arrayResponse(emptyList())
            val row = sessions.values.firstOrNull { it["refresh_hash"] == hash }
                ?: return arrayResponse(emptyList())
            return arrayResponse(listOf(json(row)))
        }

        private fun patchSession(request: ProviderHttpRequest): ProviderHttpResponse {
            val id = queryValue(request.url, "id") ?: return arrayResponse(emptyList())
            val row = sessions[id] ?: return arrayResponse(emptyList())
            if (row["revoked_at"] != null) return arrayResponse(emptyList())
            row["revoked_at"] = JSONObject(request.body).getLong("revoked_at")
            return arrayResponse(listOf(JSONObject().put("id", id)))
        }

        private fun markConsumed(request: ProviderHttpRequest): ProviderHttpResponse {
            val body = JSONObject(request.body)
            val hash = body.getString("refresh_hash")
            consumed[hash] = body.getString("account_id")
            return arrayResponse(listOf(JSONObject().put("refresh_hash", hash)))
        }

        private fun isConsumed(request: ProviderHttpRequest): ProviderHttpResponse {
            val hash = queryValue(request.url, "refresh_hash") ?: return arrayResponse(emptyList())
            val accountId = consumed[hash] ?: return arrayResponse(emptyList())
            return arrayResponse(
                listOf(JSONObject().put("refresh_hash", hash).put("account_id", accountId)),
            )
        }

        private fun arrayResponse(values: List<Any>): ProviderHttpResponse {
            val array = JSONArray()
            values.forEach { array.put(it) }
            return ProviderHttpResponse(200, array.toString())
        }

        private fun json(row: Map<String, Any?>): JSONObject {
            val json = JSONObject()
            row.forEach { (key, value) -> json.put(key, value ?: JSONObject.NULL) }
            return json
        }

        private fun queryValue(url: String, key: String): String? {
            val query = URL(url).query ?: return null
            return query.split("&").mapNotNull { part ->
                val separator = part.indexOf('=')
                if (separator < 0) return@mapNotNull null
                val parsedKey = URLDecoder.decode(part.substring(0, separator), "UTF-8")
                if (parsedKey != key) return@mapNotNull null
                URLDecoder.decode(part.substring(separator + 1), "UTF-8").removePrefix("eq.")
            }.firstOrNull()
        }
    }

    private class Rig(
        val server: LoopbackProviderAuthServer,
        val backend: FakeBackend,
        val runtime: ProviderAuthRuntime,
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

    private fun rig(maxAttempts: Int = 2): Rig {
        val clock = FakeClock()
        val backend = FakeBackend()
        val config = ServerConfig(
            pepper = "provider-http-pepper-do-not-commit".toByteArray(),
            environment = "test",
            cooldownMs = 0L,
            maxAttempts = maxAttempts,
            perPhoneLimit = 100,
            perIpLimit = 1000,
            globalLimit = 10_000,
        )
        val runtime = ProviderAuthRuntimeFactory.create(
            config = config,
            clock = clock,
            ids = FixedIdGenerator("provider-http"),
            esmsApiKey = ESMS_KEY,
            supabaseUrl = SUPABASE_URL,
            supabaseServiceKey = SERVICE_KEY,
            providerTransport = backend,
            supabaseTransport = backend,
            withHashOnlyLogs = true,
        )
        return Rig(
            LoopbackProviderAuthServer(ProviderAuthHttpHandler(runtime.service, config)),
            backend,
            runtime,
        )
    }

    private fun otpRequestJson(phone: String = PHONE) = JSONObject().apply {
        put("phone_e164", phone)
        put("client", "android")
    }.toString()

    private fun verifyJson(phone: String, requestId: String, code: String, label: String? = null) =
        JSONObject().apply {
            put("phone_e164", phone)
            put("request_id", requestId)
            put("code", code)
            label?.let { put("device_label", it) }
        }.toString()

    private fun tokenJson(token: String) = JSONObject().put("refresh_token", token).toString()

    private fun requestIdOf(response: HttpResponse<String>): String {
        assertEquals(200, response.statusCode())
        return JSONObject(response.body()).getString("request_id")
    }

    @Test
    fun providerManagedOtpFlowOverHttpUsesPinnedAdaptersAndGenericResponses() {
        rig().use { r ->
            val requested = r.post("/auth/otp/request", otpRequestJson(), "http-idem-1")
            val requestId = requestIdOf(requested)
            assertFalse(requested.body().contains(PHONE))
            assertFalse(requested.body().contains(CODE))
            assertFalse(requested.body().contains(ESMS_KEY))

            val start = r.backend.requests.first { it.url.contains("/v1/verify/start") }
            assertEquals("https://sms.esmsafrica.io/v1/verify/start", start.url)
            assertEquals("Bearer $ESMS_KEY", start.headers["Authorization"])
            assertEquals("mp-otp-$requestId", start.headers["Idempotency-Key"])
            assertFalse(start.body.contains(ESMS_KEY))

            val verified = r.post(
                "/auth/otp/verify",
                verifyJson(PHONE, requestId, CODE, "  Pixel <7>\nPro  "),
            )
            assertEquals(200, verified.statusCode())
            val tokens = JSONObject(verified.body())
            assertEquals("active", tokens.getString("account_state"))
            assertFalse(verified.body().contains(PHONE))
            assertFalse(verified.body().contains(CODE))
            assertFalse(verified.body().contains(SERVICE_KEY))
            assertEquals(CODE, r.backend.lastCheckedCode)
            assertEquals(1, r.backend.checkCalls)
            assertEquals(1, r.backend.accounts.size)

            val check = r.backend.requests.first { it.url.contains("/v1/verify/check") }
            assertEquals("https://sms.esmsafrica.io/v1/verify/check", check.url)
            assertEquals("Bearer $ESMS_KEY", check.headers["Authorization"])
            assertFalse(check.body.contains(ESMS_KEY))

            val supabaseRequests = r.backend.requests.filter { it.url.startsWith(SUPABASE_URL) }
            assertTrue(supabaseRequests.isNotEmpty())
            assertTrue(supabaseRequests.all { it.headers["apikey"] == SERVICE_KEY })
            assertTrue(supabaseRequests.none { it.headers.containsKey("Authorization") })
            assertTrue(supabaseRequests.any { it.url.endsWith("/rest/v1/rpc/mp_upsert_account") })
            assertTrue(supabaseRequests.any { it.url.contains("/rest/v1/sessions") && it.method == "POST" })

            val sessionId = tokens.getString("session_id")
            assertEquals("Pixel 7 Pro", r.backend.sessions[sessionId]!!["device_label"])

            val rotated = r.post("/auth/session/refresh", tokenJson(tokens.getString("refresh_token")))
            assertEquals(200, rotated.statusCode())
            val rotatedTokens = JSONObject(rotated.body())
            assertNotEquals(tokens.getString("refresh_token"), rotatedTokens.getString("refresh_token"))

            val reused = r.post("/auth/session/refresh", tokenJson(tokens.getString("refresh_token")))
            assertEquals(401, reused.statusCode())
            assertEquals("SESSION_REVOKED", JSONObject(reused.body()).getString("error"))

            val logout = r.post("/auth/session/logout", tokenJson(rotatedTokens.getString("refresh_token")))
            assertEquals(200, logout.statusCode())
            assertEquals("{}", logout.body())

            val afterLogout = r.post(
                "/auth/session/refresh",
                tokenJson(rotatedTokens.getString("refresh_token")),
            )
            assertEquals(401, afterLogout.statusCode())

            assertFalse(r.runtime.auditLog!!.all().toString().contains(CODE))
            assertFalse(r.runtime.auditLog.all().toString().contains(PHONE))
            assertFalse(r.runtime.abuseLog!!.all().toString().contains(CODE))
        }
    }

    @Test
    fun wrongCodeIsCountedAndReplayNeverCallsProviderAgain() {
        rig(maxAttempts = 3).use { r ->
            val requestId = requestIdOf(r.post("/auth/otp/request", otpRequestJson(), "http-idem-2"))
            val wrong = r.post("/auth/otp/verify", verifyJson(PHONE, requestId, WRONG_CODE))
            assertEquals(400, wrong.statusCode())
            assertEquals("INVALID_CODE", JSONObject(wrong.body()).getString("error"))
            assertFalse(wrong.body().contains(CODE))
            assertTrue(r.backend.accounts.isEmpty())
            assertTrue(r.backend.sessions.isEmpty())

            val verified = r.post("/auth/otp/verify", verifyJson(PHONE, requestId, CODE))
            assertEquals(200, verified.statusCode())
            val replay = r.post("/auth/otp/verify", verifyJson(PHONE, requestId, CODE))
            assertEquals(401, replay.statusCode())
            assertEquals("CODE_CONSUMED", JSONObject(replay.body()).getString("error"))
            assertEquals(2, r.backend.checkCalls)
            assertEquals(2, (r.backend.otps[requestId]!!["attempts"] as Number).toInt())
        }
    }

    @Test
    fun attemptLimitIsDurableAcrossSeparateHttpVerifications() {
        rig(maxAttempts = 2).use { r ->
            val requestId = requestIdOf(r.post("/auth/otp/request", otpRequestJson(), "http-idem-3"))
            repeat(2) {
                val response = r.post("/auth/otp/verify", verifyJson(PHONE, requestId, WRONG_CODE))
                assertEquals(400, response.statusCode())
                assertEquals("INVALID_CODE", JSONObject(response.body()).getString("error"))
            }
            val limited = r.post("/auth/otp/verify", verifyJson(PHONE, requestId, WRONG_CODE))
            assertEquals(429, limited.statusCode())
            assertEquals("TOO_MANY_ATTEMPTS", JSONObject(limited.body()).getString("error"))
            assertEquals(2, r.backend.checkCalls)
            assertTrue(r.backend.accounts.isEmpty())
            assertTrue(r.backend.sessions.isEmpty())
        }
    }

    @Test
    fun idempotencyKeyOverHttpStartsOnlyOneProviderVerification() {
        rig().use { r ->
            val first = r.post("/auth/otp/request", otpRequestJson(), "http-idem-4")
            val second = r.post("/auth/otp/request", otpRequestJson(), "http-idem-4")
            assertEquals(200, first.statusCode())
            assertEquals(200, second.statusCode())
            assertEquals(
                JSONObject(first.body()).getString("request_id"),
                JSONObject(second.body()).getString("request_id"),
            )
            assertEquals(1, r.backend.startCalls)
            assertEquals(1, r.backend.otps.size)
        }
    }

    @Test
    fun providerStartFailureReturnsGenericErrorAndGrantsNothing() {
        rig().use { r ->
            r.backend.startStatusCode = 402
            val response = r.post("/auth/otp/request", otpRequestJson(), "http-idem-5")
            assertEquals(503, response.statusCode())
            assertEquals("PROVIDER_UNAVAILABLE", JSONObject(response.body()).getString("error"))
            assertFalse(response.body().contains(SERVICE_KEY))
            assertFalse(response.body().contains(CODE))
            assertTrue(r.backend.otps.isEmpty())
            assertTrue(r.backend.accounts.isEmpty())
            assertTrue(r.backend.sessions.isEmpty())
        }
    }
}
