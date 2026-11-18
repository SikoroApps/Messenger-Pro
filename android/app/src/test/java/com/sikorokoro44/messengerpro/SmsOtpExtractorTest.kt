package com.sikorokoro44.messengerpro

import com.sikorokoro44.messengerpro.data.auth.SmsOtpExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
