package com.sikorokoro44.messengerpro.server

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/** A single outbound provider call. The secret is present only in [headers]. */
data class ProviderHttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String,
)

data class ProviderHttpResponse(
    val statusCode: Int,
    val body: String,
)

/** Injectable transport so automated tests never contact a real provider. */
interface ProviderHttpTransport {
    fun send(request: ProviderHttpRequest): ProviderHttpResponse
}

data class ManagedOtpStart(
    val verificationId: String,
    val sandbox: Boolean,
)

enum class ManagedOtpStatus {
    APPROVED,
    PENDING,
    FAILED,
    EXPIRED,
    CANCELED,
    UNKNOWN,
}

data class ManagedOtpCheck(
    val status: ManagedOtpStatus,
    val sandbox: Boolean,
)

/**
 * Provider-managed OTP. The server never generates, reads or persists the code; it only keeps
 * the opaque verification id and asks the provider to check the user-entered value.
 */
interface ManagedOtpProvider {
    fun start(phone: String, requestId: String): ManagedOtpStart
    fun check(verificationId: String, code: String): ManagedOtpCheck
}

enum class ESmsFailureReason {
    INVALID_RECIPIENT,
    INSUFFICIENT_BALANCE,
    SENDER_NOT_APPROVED,
    RATE_LIMITED,
    PROVIDER_UNAVAILABLE,
    INVALID_RESPONSE,
    SANDBOX_REQUIRED,
}

/**
 * Carries only a generic reason. Provider prose, response bodies, codes and keys are never
 * included in the message.
 */
class ESmsException(val reason: ESmsFailureReason) : Exception(reason.name)

/**
 * eSMS Africa Verify adapter. Automated builds accept only `esms_test_` keys so a production
 * credential can never accidentally be used by CI or by a test double.
 */
class ESmsVerifyGateway(
    private val apiKey: String,
    private val transport: ProviderHttpTransport,
) : ManagedOtpProvider {
    init {
        require(apiKey.startsWith("esms_test_")) {
            "eSMS Verify adapter accepts only sandbox test keys"
        }
    }

    override fun start(phone: String, requestId: String): ManagedOtpStart {
        require(ServerPhonePolicy.isValidE164(phone) && ServerPhonePolicy.matchesKnownCountryLength(phone)) {
            "Invalid E.164 phone"
        }
        require(requestId.isNotBlank()) { "Request id is required" }

        val body = JSONObject()
            .put("to", phone)
            .put("code_length", 6)
            .put("expiry_seconds", 300)
            .toString()
        val response = transport.send(
            ProviderHttpRequest(
                method = "POST",
                url = START_URL,
                headers = commonHeaders() + ("Idempotency-Key" to "mp-otp-$requestId"),
                body = body,
            ),
        )
        val json = parse(response)
        val verificationId = json.optString("verification_id").takeIf { it.isNotBlank() }
            ?: throw ESmsException(ESmsFailureReason.INVALID_RESPONSE)
        return ManagedOtpStart(verificationId = verificationId, sandbox = isSandbox(json))
    }

    override fun check(verificationId: String, code: String): ManagedOtpCheck {
        require(verificationId.isNotBlank()) { "Verification id is required" }
        require(code.length == 6 && code.all(Char::isDigit)) { "Invalid code shape" }

        val body = JSONObject()
            .put("verification_id", verificationId)
            .put("code", code)
            .toString()
        val response = transport.send(
            ProviderHttpRequest(
                method = "POST",
                url = CHECK_URL,
                headers = commonHeaders(),
                body = body,
            ),
        )
        val json = parse(response)
        val status = when (json.optString("status").lowercase()) {
            "approved" -> ManagedOtpStatus.APPROVED
            "pending" -> ManagedOtpStatus.PENDING
            "failed" -> ManagedOtpStatus.FAILED
            "expired" -> ManagedOtpStatus.EXPIRED
            "canceled", "cancelled" -> ManagedOtpStatus.CANCELED
            else -> ManagedOtpStatus.UNKNOWN
        }
        return ManagedOtpCheck(status = status, sandbox = isSandbox(json))
    }

    private fun commonHeaders(): Map<String, String> = mapOf(
        "Authorization" to "Bearer $apiKey",
        "Content-Type" to "application/json",
        "Accept" to "application/json",
    )

    private fun parse(response: ProviderHttpResponse): JSONObject {
        if (response.statusCode !in 200..299) {
            throw ESmsException(mapHttpFailure(response.statusCode))
        }
        return try {
            JSONObject(response.body)
        } catch (_: Exception) {
            throw ESmsException(ESmsFailureReason.INVALID_RESPONSE)
        }
    }

    private fun mapHttpFailure(statusCode: Int): ESmsFailureReason = when (statusCode) {
        400 -> ESmsFailureReason.INVALID_RECIPIENT
        402 -> ESmsFailureReason.INSUFFICIENT_BALANCE
        403 -> ESmsFailureReason.SENDER_NOT_APPROVED
        429 -> ESmsFailureReason.RATE_LIMITED
        else -> ESmsFailureReason.PROVIDER_UNAVAILABLE
    }

    private fun isSandbox(json: JSONObject): Boolean {
        val environment = json.optString("environment").lowercase()
        val livemode = if (json.has("livemode")) json.optBoolean("livemode", true) else true
        return environment == "test" || !livemode || json.has("sandbox_code")
    }

    private companion object {
        const val START_URL = "https://sms.esmsafrica.io/v1/verify/start"
        const val CHECK_URL = "https://sms.esmsafrica.io/v1/verify/check"
    }
}

/**
 * Host-pinned HTTP transport for deployed JVM services. Redirects are disabled so a provider
 * response cannot move a bearer key to another host.
 */
class HttpsProviderTransport(
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 15_000,
) : ProviderHttpTransport {
    @Throws(IOException::class)
    override fun send(request: ProviderHttpRequest): ProviderHttpResponse {
        val url = URL(request.url)
        require(url.protocol.equals("https", ignoreCase = true)) { "HTTPS is required" }
        require(url.host == "sms.esmsafrica.io") { "Unexpected provider host" }
        require(url.path.startsWith("/v1/verify/")) { "Unexpected provider path" }

        val connection = url.openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.requestMethod = request.method
        request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        connection.doOutput = request.body.isNotEmpty()
        if (connection.doOutput) {
            connection.outputStream.use { stream ->
                stream.write(request.body.toByteArray(StandardCharsets.UTF_8))
            }
        }

        val statusCode = connection.responseCode
        val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } ?: ""
        connection.disconnect()
        return ProviderHttpResponse(statusCode, body)
    }
}
