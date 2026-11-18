package com.sikorokoro44.messengerpro.data.auth

/** Manual entry stays digits-only and bounded; autofill helpers will use the same contract. */
object OtpInputFormatter {
    const val CODE_LENGTH = 6

    fun sanitize(raw: String): String = raw.filter(Char::isDigit).take(CODE_LENGTH)

    fun isComplete(value: String): Boolean = value.length == CODE_LENGTH && value.all(Char::isDigit)
}
