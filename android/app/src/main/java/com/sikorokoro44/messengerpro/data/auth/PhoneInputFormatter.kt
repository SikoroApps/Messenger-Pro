package com.sikorokoro44.messengerpro.data.auth

/** Presentation-only grouping. The canonical value still comes from [PhoneNormalizer]. */
object PhoneInputFormatter {
    private const val maxNationalDigits = 9

    fun format(raw: String, dialCode: String): String {
        val digits = raw.filter(Char::isDigit)
        if (digits.isEmpty()) return ""
        val national = when {
            digits.startsWith("00$dialCode") -> digits.drop(2 + dialCode.length)
            digits.startsWith(dialCode) && digits.length > dialCode.length + 4 -> digits.drop(dialCode.length)
            digits.startsWith('0') -> digits.drop(1)
            else -> digits
        }.take(maxNationalDigits)
        if (national.isEmpty()) return ""
        return "+$dialCode ${group(national)}"
    }

    private fun group(digits: String): String {
        val groups = mutableListOf<String>()
        var index = 0
        for (size in listOf(2, 3, 3)) {
            if (index >= digits.length) break
            groups += digits.substring(index, minOf(index + size, digits.length))
            index += size
        }
        if (index < digits.length) groups += digits.substring(index)
        return groups.joinToString(" ")
    }
}
