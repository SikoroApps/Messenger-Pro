package com.sikorokoro44.messengerpro

import com.sikorokoro44.messengerpro.data.auth.SmsOtpExtractor
import com.sikorokoro44.messengerpro.data.auth.VerificationLocale
import com.sikorokoro44.messengerpro.data.auth.VerificationMessageTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsOtpExtractorTest {
    private val appHash = "ABC123DEF45"

    @Test
    fun retrieverMessageWithMatchingHashYieldsTheCode() {
        val message = "482913 is your Messenger Pro verification code. $appHash"
        assertEquals("482913", SmsOtpExtractor.extract(message, appHash))
    }

    @Test
    fun mismatchedOrMissingRetrieverHashIsRejected() {
        val message = "482913 is your Messenger Pro verification code. OTHERHASH12"
        assertNull(SmsOtpExtractor.extract(message, appHash))
        assertNull(SmsOtpExtractor.extract("482913 is your verification code.", appHash))
    }

    @Test
    fun consentMessageWithoutAppHashYieldsTheCode() {
        assertEquals(
            "719204",
            SmsOtpExtractor.extract("Your Messenger Pro login code is 719204. Do not share it."),
        )
    }

    @Test
    fun ambiguousOrNonVerificationMessagesAreRejected() {
        assertNull(SmsOtpExtractor.extract("123456 and 654321 are your code."))
        assertNull(SmsOtpExtractor.extract("Your order number is 482913."))
        assertNull(SmsOtpExtractor.extract("Verification code: 12345."))
        assertNull(SmsOtpExtractor.extract(null, appHash))
    }

    @Test
    fun localizedTemplatesRoundTripThroughTheExtractor() {
        for (locale in VerificationLocale.entries) {
            val message = VerificationMessageTemplate.render(locale, "482913", appHash)
            assertEquals("482913", SmsOtpExtractor.extract(message, appHash))
        }
    }

    @Test
    fun languageTagFallsBackToEnglishForUnsupportedLocales() {
        assertEquals(VerificationLocale.ENGLISH, VerificationLocale.fromLanguageTag("en-US"))
        assertEquals(VerificationLocale.SWATI, VerificationLocale.fromLanguageTag("ss-ZA"))
        assertEquals(VerificationLocale.ENGLISH, VerificationLocale.fromLanguageTag("xx-ZZ"))
        assertEquals(VerificationLocale.ENGLISH, VerificationLocale.fromLanguageTag(null))
    }

    @Test
    fun templateKeepsOneIsolatedSixDigitCodeAndRejectsInvalidCodes() {
        val message = VerificationMessageTemplate.render(VerificationLocale.SWATI, "719204")
        assertEquals("719204", SmsOtpExtractor.extract(message))
        assertTrue(message.indexOf("719204") == message.lastIndexOf("719204"))
        assertTrue(runCatching { VerificationMessageTemplate.render(VerificationLocale.ENGLISH, "12345") }.isFailure)
        assertTrue(runCatching { VerificationMessageTemplate.render(VerificationLocale.ENGLISH, "12a456") }.isFailure)
    }

    @Test
    fun customKeywordListsUseUnicodeSafeBoundaries() {
        val message = VerificationMessageTemplate.render(VerificationLocale.SWATI, "482913")
        assertNull(SmsOtpExtractor.extract(message, keywords = listOf("unrelated")))
        assertEquals(
            "482913",
            SmsOtpExtractor.extract(message, keywords = listOf("ikhodi yokuqinisekisa")),
        )
        assertTrue(runCatching { SmsOtpExtractor.extract(message, keywords = emptyList()) }.isFailure)
    }
}
