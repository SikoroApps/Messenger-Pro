package com.sikorokoro44.messengerpro.data.network

import java.net.URI

/**
 * The public auth endpoint is configuration, not a secret, but it must never be cleartext or
 * carry credentials in the URL. Any value that fails this policy is treated as unconfigured.
 */
object TransportPolicy {
    fun isSecureBaseUrl(value: String, allowedHosts: Set<String> = emptySet()): Boolean =
        runCatching { requireSecureBaseUrl(value, allowedHosts) }.isSuccess

    fun requireSecureBaseUrl(value: String, allowedHosts: Set<String> = emptySet()): String {
        val trimmed = value.trim()
        require(trimmed.isNotEmpty()) { "Auth base URL is empty" }
        val uri = runCatching { URI(trimmed) }.getOrElse {
            throw IllegalArgumentException("Auth base URL is malformed")
        }
        require(uri.scheme.equals("https", ignoreCase = true)) { "Cleartext auth endpoints are forbidden" }
        require(!uri.host.isNullOrEmpty()) { "Auth base URL has no host" }
        if (allowedHosts.isNotEmpty()) {
            require(allowedHosts.any { it.equals(uri.host, ignoreCase = true) }) {
                "Auth host is not allowlisted"
            }
        }
        require(uri.userInfo == null) { "Credentials must not be embedded in the auth URL" }
        require(uri.fragment.isNullOrEmpty()) { "Auth base URL must not contain a fragment" }
        require(uri.query.isNullOrEmpty()) { "Auth base URL must not contain a query string" }
        return trimmed.trimEnd('/')
    }
}
