package com.sikorokoro44.messengerpro.server

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import org.json.JSONException
import org.json.JSONObject
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

private const val MAX_BODY_BYTES = 8 * 1024
private const val JSON_TYPE = "application/json; charset=utf-8"

/**
 * Exposes the documented auth contract over real HTTP sockets for integration testing of the
 * reference core. It binds to loopback only and is NOT a production TLS deployment: a hosted
 * adapter must add its own TLS, provider credentials, durable repositories and deployment
 * authorization before any Batch 2 exit gate can close.
 */
class AuthHttpHandler(
    private val service: AuthService,
    private val config: ServerConfig,
) : HttpHandler {
    override fun handle(exchange: HttpExchange) {
        try {
            if (exchange.requestMethod != "POST") {
                send(exchange, STATUS_METHOD, errorBody(AuthFailure.UNAVAILABLE))
                return
            }
            val payload = readJson(exchange)
            when (exchange.requestURI.path) {
                OTP_REQUEST_PATH -> handleOtpRequest(exchange, payload)
                OTP_VERIFY_PATH -> handleOtpVerify(exchange, payload)
                REFRESH_PATH -> handleRefresh(exchange, payload)
                LOGOUT_PATH -> handleLogout(exchange, payload)
                else -> send(exchange, STATUS_NOT_FOUND, errorBody(AuthFailure.UNAVAILABLE))
            }
        } catch (e: AuthException) {
            sendError(exchange, e.failure)
        } catch (_: JSONException) {
            sendError(exchange, AuthFailure.UNAVAILABLE)
        } catch (_: Exception) {
            sendError(exchange, AuthFailure.UNAVAILABLE)
        } finally {
            exchange.close()
        }
    }

    private fun handleOtpRequest(exchange: HttpExchange, payload: JSONObject) {
        val phone = payload.optString("phone_e164")
        if (phone.isEmpty()) throw AuthException(AuthFailure.INVALID_PHONE)
        val ipHash = DiagnosticsRedactor.subjectHash(
            exchange.remoteAddress.address.hostAddress,
            config.pepper,
        )
        val key = exchange.requestHeaders.getFirst("Idempotency-Key")
        // Bind a retry key to the hashed subject so one client cannot replay another number's
        // cached response by guessing its key.
        val scopedKey = key?.let { "${DiagnosticsRedactor.subjectHash(phone, config.pepper)}:$it" }
        val result = service.requestOtp(phone, ipHash, scopedKey)
        val body = JSONObject().apply {
            put("request_id", result.requestId)
            put("expires_in", result.expiresInSeconds)
            put("retry_after", result.retryAfterSeconds)
        }
        send(exchange, STATUS_OK, body.toString(), result.retryAfterSeconds)
    }

    private fun handleOtpVerify(exchange: HttpExchange, payload: JSONObject) {
        val phone = payload.optString("phone_e164")
        val requestId = payload.optString("request_id")
        val code = payload.optString("code")
        if (phone.isEmpty() || requestId.isEmpty() || code.length != 6 || !code.all(Char::isDigit)) {
            throw AuthException(AuthFailure.INVALID_CODE)
        }
        val result = service.verifyOtp(phone, requestId, code)
        val body = JSONObject().apply {
            put("verified", true)
            put("account_state", result.accountState)
            put("access_token", result.tokens.accessToken)
            put("refresh_token", result.tokens.refreshToken)
            put("expires_in", result.tokens.expiresInSeconds)
        }
        send(exchange, STATUS_OK, body.toString())
    }

    private fun handleRefresh(exchange: HttpExchange, payload: JSONObject) {
        val refreshToken = payload.optString("refresh_token")
        if (refreshToken.isEmpty()) throw AuthException(AuthFailure.SESSION_REVOKED)
        send(exchange, STATUS_OK, tokenBody(service.refresh(refreshToken)).toString())
    }

    private fun handleLogout(exchange: HttpExchange, payload: JSONObject) {
        val refreshToken = payload.optString("refresh_token")
        if (refreshToken.isEmpty()) throw AuthException(AuthFailure.SESSION_REVOKED)
        service.logout(refreshToken)
        send(exchange, STATUS_OK, "{}")
    }

    private fun tokenBody(tokens: Tokens) = JSONObject().apply {
        put("access_token", tokens.accessToken)
        put("refresh_token", tokens.refreshToken)
        put("expires_in", tokens.expiresInSeconds)
    }

    private fun readJson(exchange: HttpExchange): JSONObject {
        val declaredLength = exchange.requestHeaders.getFirst("Content-Length")?.toLongOrNull()
        if (declaredLength != null && declaredLength > MAX_BODY_BYTES) {
            throw AuthException(AuthFailure.UNAVAILABLE)
        }
        val bytes = exchange.requestBody.use { it.readNBytes(MAX_BODY_BYTES + 1) }
        if (bytes.size > MAX_BODY_BYTES) throw AuthException(AuthFailure.UNAVAILABLE)
        return JSONObject(String(bytes, StandardCharsets.UTF_8))
    }

    private fun sendError(exchange: HttpExchange, failure: AuthFailure) {
        val retryAfter = retryAfterSeconds(failure)
        send(exchange, statusFor(failure), errorBody(failure), retryAfter)
    }

    private fun statusFor(failure: AuthFailure) = when (failure) {
        AuthFailure.INVALID_PHONE, AuthFailure.INVALID_CODE, AuthFailure.REQUEST_NOT_FOUND -> STATUS_BAD_REQUEST
        AuthFailure.CODE_EXPIRED, AuthFailure.CODE_CONSUMED, AuthFailure.SESSION_REVOKED -> STATUS_UNAUTHORIZED
        AuthFailure.RATE_LIMITED, AuthFailure.COOLDOWN_ACTIVE, AuthFailure.TOO_MANY_ATTEMPTS -> STATUS_TOO_MANY_REQUESTS
        AuthFailure.PROVIDER_UNAVAILABLE, AuthFailure.UNAVAILABLE -> STATUS_SERVER_ERROR
    }

    private fun retryAfterSeconds(failure: AuthFailure): Long? = when (failure) {
        AuthFailure.COOLDOWN_ACTIVE -> seconds(config.cooldownMs)
        AuthFailure.RATE_LIMITED -> seconds(config.perPhoneWindowMs)
        AuthFailure.TOO_MANY_ATTEMPTS -> seconds(config.otpTtlMs)
        else -> null
    }

    private fun seconds(millis: Long) = (millis + 999L) / 1000L

    /**
     * Only stable codes and constant generic text are returned. No phone, code, token, provider
     * detail, stack trace or repository error can reach the client through this body.
     */
    private fun errorBody(failure: AuthFailure): String = JSONObject().apply {
        put("error", failure.code)
        put("message", failure.publicMessage)
        retryAfterSeconds(failure)?.let { put("retry_after", it) }
    }.toString()

    private fun send(
        exchange: HttpExchange,
        status: Int,
        body: String,
        retryAfter: Long? = null,
    ) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", JSON_TYPE)
        exchange.responseHeaders.set("Cache-Control", "no-store")
        retryAfter?.let { exchange.responseHeaders.set("Retry-After", it.toString()) }
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private companion object {
        const val OTP_REQUEST_PATH = "/auth/otp/request"
        const val OTP_VERIFY_PATH = "/auth/otp/verify"
        const val REFRESH_PATH = "/auth/session/refresh"
        const val LOGOUT_PATH = "/auth/session/logout"
        const val STATUS_OK = 200
        const val STATUS_BAD_REQUEST = 400
        const val STATUS_UNAUTHORIZED = 401
        const val STATUS_NOT_FOUND = 404
        const val STATUS_METHOD = 405
        const val STATUS_TOO_MANY_REQUESTS = 429
        const val STATUS_SERVER_ERROR = 503
    }
}

/** Test/dev wrapper. The address is hard-coded to loopback so it cannot be exposed as a server. */
class LoopbackAuthServer(handler: AuthHttpHandler) : AutoCloseable {
    private val executor = Executors.newFixedThreadPool(2)
    private val server: HttpServer = HttpServer.create(
        InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
        0,
    )

    val port: Int = server.address.port

    init {
        server.createContext("/", handler)
        server.executor = executor
        server.start()
    }

    override fun close() {
        server.stop(0)
        executor.shutdown()
    }
}
