package com.sikorokoro44.messengerpro.server

import java.nio.charset.StandardCharsets

data class Session(
    val id: String,
    val accountId: String,
    val refreshHash: String,
    val expiresAt: Long,
    val createdAt: Long,
    @Volatile var revokedAt: Long? = null,
)

interface SessionRepository {
    fun save(session: Session)
    fun findByRefreshHash(hash: String): Session?
    fun isConsumed(hash: String): Boolean
    fun consumedAccount(hash: String): String?
    fun markConsumed(hash: String, accountId: String)
    fun revoke(sessionId: String, at: Long): Boolean
    fun revokeAllForAccount(accountId: String, at: Long)
    fun count(): Int
}

/** In-memory reference implementation. The deployed adapter persists hashes only. */
class InMemorySessionRepository : SessionRepository {
    private val sessions = linkedMapOf<String, Session>()
    private val consumed = mutableMapOf<String, String>()

    @Synchronized
    override fun save(session: Session) {
        sessions[session.id] = session
    }

    @Synchronized
    override fun findByRefreshHash(hash: String): Session? =
        sessions.values.firstOrNull { it.refreshHash == hash }

    @Synchronized
    override fun isConsumed(hash: String): Boolean = consumed.containsKey(hash)

    @Synchronized
    override fun consumedAccount(hash: String): String? = consumed[hash]

    @Synchronized
    override fun markConsumed(hash: String, accountId: String) {
        consumed[hash] = accountId
    }

    @Synchronized
    override fun revoke(sessionId: String, at: Long): Boolean {
        val session = sessions[sessionId] ?: return false
        if (session.revokedAt != null) return false
        session.revokedAt = at
        return true
    }

    @Synchronized
    override fun revokeAllForAccount(accountId: String, at: Long) {
        sessions.values.forEach { session ->
            if (session.accountId == accountId && session.revokedAt == null) {
                session.revokedAt = at
            }
        }
    }

    @Synchronized
    override fun count(): Int = sessions.size
}

data class Tokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
    val sessionId: String,
)

/** Issues, rotates and revokes tokens. Rotation detects reuse and revokes the whole family. */
class TokenService(
    private val pepper: ByteArray,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val sessions: SessionRepository,
    private val accessTtlSeconds: Long = 900L,
    private val refreshTtlMs: Long = 30L * 24 * 60 * 60 * 1000,
) {
    fun hashRefresh(refresh: String): String =
        hmacHex(pepper, refresh.toByteArray(StandardCharsets.UTF_8))

    fun issue(accountId: String): Tokens {
        val accessToken = "a.${ids.newId()}"
        val refreshToken = "r.${ids.newId()}"
        val now = clock.nowMillis()
        val session = Session(
            id = ids.newId(),
            accountId = accountId,
            refreshHash = hashRefresh(refreshToken),
            expiresAt = now + refreshTtlMs,
            createdAt = now,
        )
        sessions.save(session)
        return Tokens(accessToken, refreshToken, accessTtlSeconds, session.id)
    }

    fun rotate(refreshToken: String): Tokens {
        val hash = hashRefresh(refreshToken)
        if (sessions.isConsumed(hash)) {
            sessions.consumedAccount(hash)?.let { sessions.revokeAllForAccount(it, clock.nowMillis()) }
            throw AuthException(AuthFailure.SESSION_REVOKED)
        }
        val session = sessions.findByRefreshHash(hash)
            ?: throw AuthException(AuthFailure.SESSION_REVOKED)
        if (session.revokedAt != null || session.expiresAt <= clock.nowMillis()) {
            throw AuthException(AuthFailure.SESSION_REVOKED)
        }
        sessions.markConsumed(hash, session.accountId)
        return issue(session.accountId)
    }

    fun logout(refreshToken: String) {
        val hash = hashRefresh(refreshToken)
        val session = sessions.findByRefreshHash(hash) ?: return
        sessions.revoke(session.id, clock.nowMillis())
        sessions.markConsumed(hash, session.accountId)
    }
}
