package com.sikorokoro44.messengerpro

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sikorokoro44.messengerpro.ui.auth.Countries
import com.sikorokoro44.messengerpro.ui.auth.OtpEntryScreen
import com.sikorokoro44.messengerpro.ui.auth.PhoneEntryScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IdentityScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun phoneEntryShowsValidationAndBackendErrors() {
        composeRule.setContent {
            MaterialTheme {
                PhoneEntryScreen(
                    phoneInput = "123",
                    onPhoneInputChange = {},
                    selectedCountry = Countries.default,
                    onCountrySelected = {},
                    validationMessage = "That phone number is too short.",
                    errorMessage = "NOT_CONFIGURED",
                    isBusy = false,
                    onSubmit = {},
                )
            }
        }
        composeRule.onNodeWithText("Sign in with your phone number").assertIsDisplayed()
        composeRule.onNodeWithText("That phone number is too short.").assertIsDisplayed()
        composeRule.onNodeWithText("NOT_CONFIGURED").assertIsDisplayed()
        composeRule.onNodeWithText("Send verification code").assertIsDisplayed()
    }

    @Test
    fun otpEntryShowsRedactedNumberAndCooldown() {
        composeRule.setContent {
            MaterialTheme {
                OtpEntryScreen(
                    redactedPhone = "+•••••••3456",
                    codeInput = "123456",
                    onCodeInputChange = {},
                    retryAfterSeconds = 30L,
                    errorMessage = null,
                    isBusy = false,
                    onVerify = {},
                    onResend = {},
                )
            }
        }
        composeRule.onNodeWithText("Enter the 6-digit code").assertIsDisplayed()
        composeRule.onNodeWithText("Sent to +•••••••3456").assertIsDisplayed()
        composeRule.onNodeWithText("Resend available in 30s").assertIsDisplayed()
        composeRule.onNodeWithText("Verify code").assertIsDisplayed()
    }
}
