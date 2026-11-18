package com.sikorokoro44.messengerpro.server

/**
 * A caller identity derived only from a verified, unrevoked server-side session. The account id
 * is never trusted from client input for authorization decisions: it comes from the session row
 * that a token was resolved against.
 */
data class CallerPrincipal(val accountId: String, val sessionId: String)

enum class AccountAuthorizationFailure {
    UNAUTHENTICATED,
    CROSS_ACCOUNT_DENIED,
    ACCOUNT_NOT_FOUND,
    INVALID_DISPLAY_NAME,
    ;

    /**
     * Constant user-facing text. It deliberately does not reveal whether a target row exists or
     * which authorization rule rejected the call, preventing account enumeration.
     */
    val publicMessage: String
        get() = "Account data is not available."
}

class AccountAuthorizationException(val reason: AccountAuthorizationFailure) :
    Exception(reason.name)

/** Bounds a user-chosen display name before it is stored on an account row. */
object AccountDisplayNamePolicy {
    const val MAX_LENGTH = 50

    fun sanitize(raw: String): String? {
        val cleaned = raw.trim()
            .map { if (it.isISOControl() || it == '<' || it == '>') ' ' else it }
            .joinToString("")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_LENGTH)
        return cleaned.takeIf { it.isNotBlank() }
    }
}

/**
 * Reference-core model of the row-ownership check a deployed Supabase RLS policy must enforce.
 *
 * This class is NOT a database security boundary. A real deployment must implement the same
 * ownership rule in SQL RLS and expose it through a service that cannot bypass it. It exists so
 * the cross-account contract can be tested deterministically without credentials or a hosted
 * backend. The ownership check runs before the row lookup, so a denied cross-account request
 * cannot reveal whether the target row exists.
 */
class AuthorizedAccountAccess(
    private val accounts: AccountRepository,
    private val sessions: SessionRepository,
    private val clock: Clock,
) {
    fun read(caller: CallerPrincipal?, accountId: String): Account {
        val principal = authenticate(caller)
        requireOwner(principal, accountId)
        return accounts.findById(accountId)
            ?: throw AccountAuthorizationException(AccountAuthorizationFailure.ACCOUNT_NOT_FOUND)
    }

    fun updateDisplayName(
        caller: CallerPrincipal?,
        accountId: String,
        rawDisplayName: String,
    ): Account {
        val principal = authenticate(caller)
        requireOwner(principal, accountId)
        val displayName = AccountDisplayNamePolicy.sanitize(rawDisplayName)
            ?: throw AccountAuthorizationException(AccountAuthorizationFailure.INVALID_DISPLAY_NAME)
        return accounts.setDisplayName(accountId, displayName)
            ?: throw AccountAuthorizationException(AccountAuthorizationFailure.ACCOUNT_NOT_FOUND)
    }

    private fun authenticate(caller: CallerPrincipal?): CallerPrincipal {
        if (caller == null || caller.accountId.isBlank() || caller.sessionId.isBlank()) {
            throw AccountAuthorizationException(AccountAuthorizationFailure.UNAUTHENTICATED)
        }
        // Re-derive ownership from the live session row so a hand-built principal that pairs a
        // victim account id with an attacker's session id cannot pass the ownership check.
        val session = sessions.findById(caller.sessionId)
            ?: throw AccountAuthorizationException(AccountAuthorizationFailure.UNAUTHENTICATED)
        if (session.revokedAt != null || session.expiresAt <= clock.nowMillis() ||
            session.accountId != caller.accountId
        ) {
            throw AccountAuthorizationException(AccountAuthorizationFailure.UNAUTHENTICATED)
        }
        return caller
    }

    private fun requireOwner(caller: CallerPrincipal, accountId: String) {
        if (caller.accountId != accountId) {
            throw AccountAuthorizationException(AccountAuthorizationFailure.CROSS_ACCOUNT_DENIED)
        }
    }
}
