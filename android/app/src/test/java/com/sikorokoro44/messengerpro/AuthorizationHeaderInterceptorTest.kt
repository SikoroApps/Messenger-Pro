package com.sikorokoro44.messengerpro

import com.sikorokoro44.messengerpro.data.network.AuthorizationHeaderInterceptor
import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.TimeUnit

class AuthorizationHeaderInterceptorTest {
    private class RecordingChain(private val source: Request) : Interceptor.Chain {
        var forwarded: Request? = null
            private set

        override fun request(): Request = source

        override fun proceed(request: Request): Response {
            forwarded = request
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .build()
        }

        override fun connection(): Connection? = null

        override fun call(): Call = throw UnsupportedOperationException("Not used by this interceptor")

        override fun connectTimeoutMillis(): Int = 0

        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this

        override fun readTimeoutMillis(): Int = 0

        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this

        override fun writeTimeoutMillis(): Int = 0

        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    }

    private fun intercept(url: String, token: String?, allowed: Set<String>, existing: String? = null): Request {
        val source = Request.Builder().url(url).apply {
            if (existing != null) header("Authorization", existing)
        }.build()
        val chain = RecordingChain(source)
        AuthorizationHeaderInterceptor({ token }, allowed).intercept(chain)
        return chain.forwarded ?: throw AssertionError("Interceptor did not forward the request")
    }

    @Test
    fun tokenIsAttachedOnlyToAllowlistedHttpsHosts() {
        val forwarded = intercept(
            url = "https://api.example.com/secure",
            token = "secret-access-token",
            allowed = setOf("API.EXAMPLE.COM"),
        )
        assertEquals("Bearer secret-access-token", forwarded.header("Authorization"))
    }

    @Test
    fun preexistingTokenIsStrippedFromUnrelatedHosts() {
        val forwarded = intercept(
            url = "https://other.example.com/unrelated",
            token = "secret-access-token",
            allowed = setOf("api.example.com"),
            existing = "Bearer stale-token",
        )
        assertNull(forwarded.header("Authorization"))
    }

    @Test
    fun cleartextRequestsNeverReceiveAToken() {
        val forwarded = intercept(
            url = "http://api.example.com/secure",
            token = "secret-access-token",
            allowed = setOf("api.example.com"),
            existing = "Bearer stale-token",
        )
        assertNull(forwarded.header("Authorization"))
    }

    @Test
    fun blankOrMissingTokensNeverAttachAHeader() {
        val blank = intercept("https://api.example.com", "   ", setOf("api.example.com"))
        assertNull(blank.header("Authorization"))

        val missing = intercept("https://api.example.com", null, setOf("api.example.com"))
        assertNull(missing.header("Authorization"))
    }
}
