package com.sikorokoro44.messengerpro

import com.sikorokoro44.messengerpro.data.network.ApiResult
import com.sikorokoro44.messengerpro.data.network.AuthError
import com.sikorokoro44.messengerpro.data.network.HttpAuthApi
import com.sikorokoro44.messengerpro.data.network.LogoutRequest
import com.sikorokoro44.messengerpro.data.network.OtpRequest
import com.sikorokoro44.messengerpro.data.network.OtpVerifyRequest
import com.sikorokoro44.messengerpro.data.network.RefreshRequest
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpAuthApiTest {
    private fun secureServer(): Pair<MockWebServer, OkHttpClient> {
        val held = HeldCertificate.Builder()
            .commonName("localhost")
            .addSubjectAlternativeName("localhost")
            .build()
        val certificates = HandshakeCertificates.Builder()
            .heldCertificate(held)
            .addTrustedCertificate(held.certificate)
            .build()
        val server = MockWebServer()
        server.useHttps(certificates.sslSocketFactory, false)
        server.start()
        val client = OkHttpClient.Builder()
            .sslSocketFactory(certificates.sslSocketFactory, certificates.trustManager)
            .build()
        return server to client
    }

    private fun MockWebServer.api(client: OkHttpClient) = HttpAuthApi("https://localhost:$port", client)

    @Test
    fun otpRequestParsesServerResponseOverTls() = runBlocking {
        val (server, client) = secureServer()
        try {
            server.enqueue(
                MockResponse().setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .setBody("""{"request_id":"req-9","expires_in":300,"retry_after":60}"""),
            )
            val result = server.api(client).requestOtp(OtpRequest("+26876123456", "ss-ZA"))
            assertTrue(result is ApiResult.Success)
            val data = (result as ApiResult.Success).data
            assertEquals("req-9", data.requestId)
            assertEquals(300L, data.expiresInSeconds)
            assertEquals(60L, data.retryAfterSeconds)
            assertEquals("/auth/otp/request", server.takeRequest().path)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun verificationAndSessionCallsParseTokens() = runBlocking {
        val (server, client) = secureServer()
        try {
            val tokens = """{"access_token":"a1","refresh_token":"r1","expires_in":900}"""
            server.enqueue(MockResponse().setResponseCode(200).setBody(tokens))
            server.enqueue(MockResponse().setResponseCode(200).setBody(tokens))
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

            val api = server.api(client)
            val verified = api.verifyOtp(OtpVerifyRequest("+26876123456", "req-9", "482913"))
            assertEquals("a1", (verified as ApiResult.Success).data.accessToken)

            val refreshed = api.refresh(RefreshRequest("r1"))
            assertEquals("r1", (refreshed as ApiResult.Success).data.refreshToken)

            val logout = api.logout(LogoutRequest("r1"))
            assertTrue(logout is ApiResult.Success)
            val paths = listOf(server.takeRequest().path, server.takeRequest().path, server.takeRequest().path)
            assertEquals(listOf("/auth/otp/verify", "/auth/session/refresh", "/auth/session/logout"), paths)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun throttleAndInvalidPhoneUseTheSafeErrorContract() = runBlocking {
        val (server, client) = secureServer()
        try {
            server.enqueue(
                MockResponse().setResponseCode(429).addHeader("Retry-After", "12")
                    .setBody("""{"error":"RATE_LIMITED","detail":"secret internal value"}"""),
            )
            server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"INVALID_PHONE"}"""))

            val api = server.api(client)
            val throttled = api.requestOtp(OtpRequest("+26876123456", "ss-ZA"))
            val error = (throttled as ApiResult.Failure).error as AuthError.RateLimited
            assertEquals(12L, error.retryAfterSeconds)
            assertFalse(error.message.contains("secret"))

            val invalid = api.requestOtp(OtpRequest("bad", "ss-ZA"))
            assertTrue((invalid as ApiResult.Failure).error is AuthError.InvalidPhone)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun malformedOrUnreachableBackendNeverYieldsSuccess() = runBlocking {
        val (server, client) = secureServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200).setBody("not json"))
            val api = server.api(client)
            assertTrue(api.requestOtp(OtpRequest("+26876123456", "ss-ZA")) is ApiResult.Failure)
        } finally {
            server.shutdown()
        }

        val (offlineServer, offlineClient) = secureServer()
        offlineServer.shutdown()
        val unreachable = HttpAuthApi("https://localhost:${offlineServer.port}", offlineClient)
        val result = unreachable.requestOtp(OtpRequest("+26876123456", "ss-ZA"))
        assertTrue(result is ApiResult.Failure)
        assertTrue((result as ApiResult.Failure).error is AuthError.Network)
    }

    @Test(expected = IllegalArgumentException::class)
    fun cleartextBaseUrlIsRejectedAtConstruction() {
        val (server, client) = secureServer()
        try {
            HttpAuthApi("http://localhost:${server.port}", client)
        } finally {
            server.shutdown()
        }
    }
}
