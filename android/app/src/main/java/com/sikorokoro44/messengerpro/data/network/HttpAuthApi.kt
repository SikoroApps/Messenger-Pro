package com.sikorokoro44.messengerpro.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Real HTTP implementation of the auth contract. It is deliberately inert until a valid public
 * HTTPS base URL is configured; no code, token, credential or phone value is ever logged.
 */
class HttpAuthApi(
    baseUrl: String,
    client: OkHttpClient,
    policy: HttpClientPolicy = HttpClientPolicy.DEFAULT,
    private val errorMapper: HttpErrorMapper = HttpErrorMapper,
) : AuthApi {
    private val origin: String = TransportPolicy.requireSecureBaseUrl(baseUrl)

    private val httpClient: OkHttpClient = client.newBuilder()
        .connectTimeout(policy.connectTimeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(policy.readTimeoutMs, TimeUnit.MILLISECONDS)
        .writeTimeout(policy.writeTimeoutMs, TimeUnit.MILLISECONDS)
        .callTimeout(policy.callTimeoutMs, TimeUnit.MILLISECONDS)
        .build()

    override suspend fun requestOtp(request: OtpRequest): ApiResult<OtpRequestResponse> =
        post(OTP_REQUEST_PATH, JSONObject().apply {
            put("phone_e164", request.phoneE164)
            put("locale", request.locale)
            put("client", request.client)
        }) { body ->
            OtpRequestResponse(
                requestId = body.getString("request_id"),
                expiresInSeconds = body.getLong("expires_in"),
                retryAfterSeconds = body.getLong("retry_after"),
            )
        }

    override suspend fun verifyOtp(request: OtpVerifyRequest): ApiResult<SessionTokens> =
        post(OTP_VERIFY_PATH, JSONObject().apply {
            put("phone_e164", request.phoneE164)
            put("request_id", request.requestId)
            put("code", request.code)
        }) { body -> body.toSessionTokens() }

    override suspend fun refresh(request: RefreshRequest): ApiResult<SessionTokens> =
        post(REFRESH_PATH, JSONObject().apply { put("refresh_token", request.refreshToken) }) { body ->
            body.toSessionTokens()
        }

    override suspend fun logout(request: LogoutRequest): ApiResult<Unit> =
        post(LOGOUT_PATH, JSONObject().apply { put("refresh_token", request.refreshToken) }) { }

    private suspend fun <T> post(
        path: String,
        payload: JSONObject,
        parse: (JSONObject) -> T,
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(origin + path)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "application/json")
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext ApiResult.Failure(
                        errorMapper.map(
                            status = response.code,
                            errorCode = bodyText.errorCode(),
                            retryAfterHeader = response.header("Retry-After"),
                        ),
                    )
                }
                val result = runCatching { parse(JSONObject(bodyText)) }
                result.fold(
                    onSuccess = { ApiResult.Success(it) },
                    onFailure = { ApiResult.Failure(AuthError.Server()) },
                )
            }
        } catch (_: IOException) {
            ApiResult.Failure(AuthError.Network())
        } catch (_: JSONException) {
            ApiResult.Failure(AuthError.Server())
        }
    }

    private fun String.errorCode(): String? = runCatching {
        JSONObject(this).optString("error").ifEmpty { null }
    }.getOrNull()

    private fun JSONObject.toSessionTokens() = SessionTokens(
        accessToken = getString("access_token"),
        refreshToken = getString("refresh_token"),
        expiresInSeconds = getLong("expires_in"),
    )

    private companion object {
        const val OTP_REQUEST_PATH = "/auth/otp/request"
        const val OTP_VERIFY_PATH = "/auth/otp/verify"
        const val REFRESH_PATH = "/auth/session/refresh"
        const val LOGOUT_PATH = "/auth/session/logout"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
