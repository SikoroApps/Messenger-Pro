package com.sikorokoro44.messengerpro.data.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches the current access token only to allowlisted HTTPS hosts. Any pre-existing
 * Authorization header is removed first, so a token can never follow a redirect or reuse of a
 * shared OkHttpClient to an unrelated origin. Tokens are never logged.
 */
class AuthorizationHeaderInterceptor(
    private val accessTokenProvider: () -> String?,
    private val allowedHosts: Set<String>,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val outgoing = original.newBuilder().removeHeader(AUTHORIZATION)
        val token = accessTokenProvider()?.trim().orEmpty()
        val host = original.url.host
        val hostAllowed = allowedHosts.any { it.equals(host, ignoreCase = true) }

        if (token.isNotEmpty() && hostAllowed && original.url.isHttps) {
            outgoing.header(AUTHORIZATION, "Bearer $token")
        }
        return chain.proceed(outgoing.build())
    }

    private companion object {
        const val AUTHORIZATION = "Authorization"
    }
}
