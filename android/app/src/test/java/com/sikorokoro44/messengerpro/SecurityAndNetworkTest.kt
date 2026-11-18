package com.sikorokoro44.messengerpro

import com.sikorokoro44.messengerpro.data.network.ApiResult
import com.sikorokoro44.messengerpro.data.network.AuthError
import com.sikorokoro44.messengerpro.data.network.OtpRequest
import com.sikorokoro44.messengerpro.data.network.OtpVerifyRequest
import com.sikorokoro44.messengerpro.data.network.RefreshRequest
import com.sikorokoro44.messengerpro.data.network.RetryAfterParser
import com.sikorokoro44.messengerpro.data.network.UnconfiguredAuthApi
import com.sikorokoro44.messengerpro.security.DiagnosticsRedactor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityAndNetworkTest {
    @Test
    fun phoneRedactionHidesAllButLastFourDigits() {
        val redacted = DiagnosticsRedactor.phone("+26876123456")
        assertEquals("+•••••••3456", redacted)
        assertFalse(redacted.contains("26876123456"))
        assertFalse(redacted.contains("761234"))
    }

    @Test
    fun codeAndTokenRedactionNeverExposeValues() {
        assertEquals("••••••", DiagnosticsRedactor.code("482913"))
        val token = DiagnosticsRedactor.token("secret-access-token")
        assertFalse(token.contains("secret"))
        assertTrue(token.contains("redacted"))
        assertEquals("NOT_CONFIGURED", DiagnosticsRedactor.error(AuthError.NotConfigured()))
    }

    @Test
    fun retryAfterParsingIsBounded() {
        assertNull(RetryAfterParser.parse(null))
        assertNull(RetryAfterParser.parse("soon"))
        assertEquals(0L, RetryAfterParser.parse("-5"))
        assertEquals(90L, RetryAfterParser.parse(" 90 "))
        assertEquals(3_600L, RetryAfterParser.parse("999999"))
    }

    @Test
    fun unconfiguredBackendNeverProducesSuccessOrSession() = runBlocking {
        val api = UnconfiguredAuthApi()
        val request = OtpRequest("+26876123456", "ss-ZA")
        assertTrue(api.requestOtp(request) is ApiResult.Failure)

        val verify = api.verifyOtp(OtpVerifyRequest("+26876123456", "req-1", "482913"))
        assertTrue(verify is ApiResult.Failure)
        assertEquals("NOT_CONFIGURED", (verify as ApiResult.Failure).error.code)

        assertTrue(api.refresh(RefreshRequest("refresh")) is ApiResult.Failure)
        assertTrue(api.logout(com.sikorokoro44.messengerpro.data.network.LogoutRequest("refresh")) is ApiResult.Failure)
    }
}
