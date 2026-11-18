package com.sikorokoro44.messengerpro.server

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

enum class SupabaseFailure {
    INVALID_CONFIGURATION,
    UNAUTHORIZED,
    BAD_REQUEST,
    NOT_FOUND,
    CONFLICT,
    SERVER_ERROR,
    INVALID_RESPONSE,
}

/** Generic only: status-derived reason, never a response body or key. */
class SupabaseException(val failure: SupabaseFailure) : Exception(failure.name)

/**
 * Minimal PostgREST adapter using the service-role secret on server/CI only. The key is sent in
 * the `apikey` header; legacy JWT keys additionally need bearer authorization.
 */
class SupabaseRestClient(
    baseUrl: String,
    private val serviceKey: String,
    private val transport: ProviderHttpTransport,
) {
    private val projectUrl: String = run {
        val parsed = URL(baseUrl)
        require(parsed.protocol.equals("https", ignoreCase = true)) { "HTTPS is required" }
        require(parsed.host.endsWith(".supabase.co")) { "Unexpected Supabase host" }
        require(parsed.port == -1 || parsed.port == 443) { "Unexpected Supabase port" }
        baseUrl.trimEnd('/')
    }

    fun execute(
        path: String,
        method: String = "GET",
        body: JSONObject? = null,
        prefer: String? = null,
    ): List<JSONObject> {
        require(path.startsWith("/rest/v1/")) { "Unexpected Supabase REST path" }
        val headers = mutableMapOf(
            "apikey" to serviceKey,
            "Accept" to "application/json",
        )
        if (serviceKey.startsWith("eyJ")) {
            headers["Authorization"] = "Bearer $serviceKey"
        }
        if (body != null) headers["Content-Type"] = "application/json"
        if (prefer != null) headers["Prefer"] = prefer

        val response = transport.send(
            ProviderHttpRequest(
                method = method,
                url = "$projectUrl$path",
                headers = headers,
                body = body?.toString() ?: "",
            ),
        )
        if (response.statusCode !in 200..299) {
            throw SupabaseException(mapFailure(response.statusCode))
        }
        if (response.body.isBlank()) return emptyList()
        return try {
            val trimmed = response.body.trim()
            if (trimmed.startsWith("[")) {
                val array = JSONArray(trimmed)
                List(array.length()) { array.getJSONObject(it) }
            } else {
                listOf(JSONObject(trimmed))
            }
        } catch (_: Exception) {
            throw SupabaseException(SupabaseFailure.INVALID_RESPONSE)
        }
    }

    private fun mapFailure(statusCode: Int): SupabaseFailure = when (statusCode) {
        401, 403 -> SupabaseFailure.UNAUTHORIZED
        400, 422 -> SupabaseFailure.BAD_REQUEST
        404 -> SupabaseFailure.NOT_FOUND
        409 -> SupabaseFailure.CONFLICT
        in 500..599 -> SupabaseFailure.SERVER_ERROR
        else -> SupabaseFailure.INVALID_RESPONSE
    }

    internal fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}

/** Server-side account persistence. All access uses the service role, never the APK. */
class SupabaseAccountRepository(private val client: SupabaseRestClient) : AccountRepository {
    override fun findByPhone(phone: String): Account? =
        single("/rest/v1/accounts?phone_e164=eq.${client.encode(phone)}&select=*&limit=1")
            ?.let(::toAccount)

    override fun findById(id: String): Account? =
        single("/rest/v1/accounts?id=eq.${client.encode(id)}&select=*&limit=1")?.let(::toAccount)

    /**
     * Uses the atomic SQL function in the migration so a duplicate verification cannot replace
     * an existing account id.
     */
    override fun upsertByPhone(phone: String, now: Long): Account {
        val rows = client.execute(
            path = "/rest/v1/rpc/mp_upsert_account",
            method = "POST",
            body = JSONObject().put("p_phone", phone).put("p_now", now),
            prefer = "return=representation",
        )
        return rows.firstOrNull()?.let(::toAccount)
            ?: throw SupabaseException(SupabaseFailure.INVALID_RESPONSE)
    }

    override fun setDisplayName(id: String, displayName: String): Account? = client.execute(
        path = "/rest/v1/accounts?id=eq.${client.encode(id)}&select=*",
        method = "PATCH",
        body = JSONObject().put("display_name", displayName),
        prefer = "return=representation",
    ).firstOrNull()?.let(::toAccount)

    override fun count(): Int = client.execute("/rest/v1/accounts?select=id").size

    private fun single(path: String): JSONObject? = client.execute(path).firstOrNull()

    private fun toAccount(row: JSONObject): Account = Account(
        id = row.getString("id"),
        phone = row.getString("phone_e164"),
        state = row.getString("state"),
        createdAt = row.getLong("created_at"),
        displayName = row.optString("display_name").takeIf { it.isNotBlank() },
    )
}

/** Durable, server-only state for provider-managed OTP verification ids. */
class SupabaseProviderOtpRepository(private val client: SupabaseRestClient) : ProviderOtpRepository {
    override fun save(record: ProviderOtpRecord) {
        client.execute(
            path = "/rest/v1/provider_otp_requests?select=id",
            method = "POST",
            body = JSONObject()
                .put("id", record.id)
                .put("phone_e164", record.phone)
                .put("verification_id", record.verificationId)
                .put("expires_at", record.expiresAt)
                .put("created_at", record.createdAt)
                .put("attempts", record.attempts),
            prefer = "return=representation",
        )
    }

    override fun find(id: String): ProviderOtpRecord? = client.execute(
        "/rest/v1/provider_otp_requests?id=eq.${client.encode(id)}&select=*&limit=1",
    ).firstOrNull()?.let(::toRecord)

    override fun invalidateActiveForPhone(phone: String, now: Long) {
        client.execute(
            path = "/rest/v1/provider_otp_requests?phone_e164=eq.${client.encode(phone)}" +
                "&consumed_at=is.null&failed_at=is.null&select=id",
            method = "PATCH",
            body = JSONObject().put("failed_at", now),
            prefer = "return=representation",
        )
    }

    override fun consume(id: String, at: Long): Boolean = client.execute(
        path = "/rest/v1/provider_otp_requests?id=eq.${client.encode(id)}" +
            "&consumed_at=is.null&failed_at=is.null&select=id",
        method = "PATCH",
        body = JSONObject().put("consumed_at", at),
        prefer = "return=representation",
    ).isNotEmpty()

    private fun toRecord(row: JSONObject): ProviderOtpRecord = ProviderOtpRecord(
        id = row.getString("id"),
        phone = row.getString("phone_e164"),
        verificationId = row.getString("verification_id"),
        expiresAt = row.getLong("expires_at"),
        createdAt = row.getLong("created_at"),
        attempts = row.optInt("attempts"),
        consumedAt = nullableLong(row, "consumed_at"),
        failedAt = nullableLong(row, "failed_at"),
    )

    private fun nullableLong(row: JSONObject, key: String): Long? =
        if (row.isNull(key)) null else row.getLong(key)
}

/** Server-side session persistence; only password-equivalent refresh hashes are stored. */
class SupabaseSessionRepository(private val client: SupabaseRestClient) : SessionRepository {
    override fun save(session: Session) {
        client.execute(
            path = "/rest/v1/sessions?select=id",
            method = "POST",
            body = JSONObject()
                .put("id", session.id)
                .put("account_id", session.accountId)
                .put("refresh_hash", session.refreshHash)
                .put("expires_at", session.expiresAt)
                .put("created_at", session.createdAt)
                .put("device_label", session.deviceLabel),
            prefer = "return=representation",
        )
    }

    override fun findById(id: String): Session? =
        single("/rest/v1/sessions?id=eq.${client.encode(id)}&select=*&limit=1")?.let(::toSession)

    override fun findByRefreshHash(hash: String): Session? = single(
        "/rest/v1/sessions?refresh_hash=eq.${client.encode(hash)}&select=*&limit=1",
    )?.let(::toSession)

    override fun isConsumed(hash: String): Boolean = client.execute(
        "/rest/v1/consumed_refresh_tokens?refresh_hash=eq.${client.encode(hash)}&select=refresh_hash&limit=1",
    ).isNotEmpty()

    override fun consumedAccount(hash: String): String? = client.execute(
        "/rest/v1/consumed_refresh_tokens?refresh_hash=eq.${client.encode(hash)}&select=account_id&limit=1",
    ).firstOrNull()?.optString("account_id")?.takeIf { it.isNotBlank() }

    override fun markConsumed(hash: String, accountId: String) {
        client.execute(
            path = "/rest/v1/consumed_refresh_tokens?on_conflict=refresh_hash&select=refresh_hash",
            method = "POST",
            body = JSONObject()
                .put("refresh_hash", hash)
                .put("account_id", accountId)
                .put("consumed_at", System.currentTimeMillis()),
            prefer = "resolution=ignore-duplicates,return=representation",
        )
    }

    override fun revoke(sessionId: String, at: Long): Boolean = client.execute(
        path = "/rest/v1/sessions?id=eq.${client.encode(sessionId)}" +
            "&revoked_at=is.null&select=id",
        method = "PATCH",
        body = JSONObject().put("revoked_at", at),
        prefer = "return=representation",
    ).isNotEmpty()

    override fun revokeAllForAccount(accountId: String, at: Long) {
        client.execute(
            path = "/rest/v1/sessions?account_id=eq.${client.encode(accountId)}" +
                "&revoked_at=is.null&select=id",
            method = "PATCH",
            body = JSONObject().put("revoked_at", at),
            prefer = "return=representation",
        )
    }

    override fun count(): Int = client.execute("/rest/v1/sessions?select=id").size

    private fun single(path: String): JSONObject? = client.execute(path).firstOrNull()

    private fun toSession(row: JSONObject): Session = Session(
        id = row.getString("id"),
        accountId = row.getString("account_id"),
        refreshHash = row.getString("refresh_hash"),
        expiresAt = row.getLong("expires_at"),
        createdAt = row.getLong("created_at"),
        deviceLabel = row.optString("device_label").takeIf { it.isNotBlank() },
        revokedAt = if (row.isNull("revoked_at")) null else row.getLong("revoked_at"),
    )
}

/** HTTPS transport for the configured Supabase project. Redirects are disabled. */
class SupabaseHttpsTransport(
    baseUrl: String,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 15_000,
) : ProviderHttpTransport {
    private val allowedHost: String = URL(baseUrl).host.also {
        require(URL(baseUrl).protocol.equals("https", ignoreCase = true)) { "HTTPS is required" }
        require(it.endsWith(".supabase.co")) { "Unexpected Supabase host" }
    }

    @Throws(IOException::class)
    override fun send(request: ProviderHttpRequest): ProviderHttpResponse {
        val url = URL(request.url)
        require(url.protocol.equals("https", ignoreCase = true)) { "HTTPS is required" }
        require(url.host == allowedHost) { "Unexpected Supabase host" }
        require(url.path.startsWith("/rest/v1/")) { "Unexpected Supabase path" }

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
