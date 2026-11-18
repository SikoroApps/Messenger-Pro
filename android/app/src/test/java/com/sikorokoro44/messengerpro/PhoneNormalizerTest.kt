package com.sikorokoro44.messengerpro

import com.sikorokoro44.messengerpro.data.auth.InvalidPhoneReason
import com.sikorokoro44.messengerpro.data.auth.PhoneNormalizer
import com.sikorokoro44.messengerpro.data.auth.PhoneValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNormalizerTest {
    @Test
    fun normalizesEswatiniInternationalFormat() {
        val result = PhoneNormalizer.normalize("+268 76 123 456")
        assertTrue(result is PhoneValidationResult.Valid)
        assertEquals("+26876123456", (result as PhoneValidationResult.Valid).phone.e164)
        assertEquals("76123456", result.phone.nationalNumber)
    }

    @Test
    fun convertsLocalNationalNumberUsingDefaultDialCode() {
        val result = PhoneNormalizer.normalize("076 123 456", "268")
        assertTrue(result is PhoneValidationResult.Valid)
        assertEquals("+26876123456", (result as PhoneValidationResult.Valid).phone.e164)
    }

    @Test
    fun acceptsPrefixAndForeignNumbersWithoutClaimingLocalCountry() {
        val prefixed = PhoneNormalizer.normalize("0026876123456")
        assertTrue(prefixed is PhoneValidationResult.Valid)
        assertEquals("+26876123456", (prefixed as PhoneValidationResult.Valid).phone.e164)

        val foreign = PhoneNormalizer.normalize("+1 415 555 2671")
        assertTrue(foreign is PhoneValidationResult.Valid)
        assertEquals("+14155552671", (foreign as PhoneValidationResult.Valid).phone.e164)
        assertEquals("", foreign.phone.countryDialCode)
    }

    @Test
    fun rejectsMalformedInput() {
        assertEquals(
            InvalidPhoneReason.EMPTY,
            (PhoneNormalizer.normalize("  ") as PhoneValidationResult.Invalid).reason,
        )
        assertEquals(
            InvalidPhoneReason.INVALID_CHARACTERS,
            (PhoneNormalizer.normalize("+268abc1234") as PhoneValidationResult.Invalid).reason,
        )
        assertEquals(
            InvalidPhoneReason.TOO_SHORT,
            (PhoneNormalizer.normalize("123") as PhoneValidationResult.Invalid).reason,
        )
        assertTrue(PhoneNormalizer.normalize("+1234567890123456") is PhoneValidationResult.Invalid)
    }
}
