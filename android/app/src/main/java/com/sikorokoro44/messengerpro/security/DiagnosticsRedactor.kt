package com.sikorokoro44.messengerpro.security

import com.sikorokoro44.messengerpro.data.network.AuthError

/** Redaction helpers for any diagnostic surface. Codes and tokens are never logged at all. */
object DiagnosticsRedactor {
    fun phone(e164: String): String {
        val digits = e164.filter(Char::isDigit)
        if (digits.length <= 4) return "••••"
        val masked = "\u2022".repeat(digits.length - 4)
        return "+$masked${digits.takeLast(4)}"
    }

    fun code(code: String): String = "\u2022".repeat(code.length.coerceAtMost(6))

    fun token(token: String): String = if (token.isEmpty()) "<empty>" else "<redacted:${token.length}>"

    /** Only stable error codes are safe to record; messages may be shown to the user but not logged verbatim. */
    fun error(error: AuthError): String = error.code
}
