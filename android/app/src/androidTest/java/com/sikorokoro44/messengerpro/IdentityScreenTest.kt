package com.sikorokoro44.messengerpro

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sikorokoro44.messengerpro.data.auth.OtpInputFormatter
import com.sikorokoro44.messengerpro.data.auth.PhoneInputFormatter
import com.sikorokoro44.messengerpro.data.network.AuthError
import com.sikorokoro44.messengerpro.security.DiagnosticsRedactor
import com.sikorokoro44.messengerpro.ui.auth.Countries
import com.sikorokoro44.messengerpro.ui.auth.Country
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

    @Test
    fun countryPickerChangesTheSelectedDialCode() {
        composeRule.setContent {
            MaterialTheme {
                var selected by remember { mutableStateOf<Country>(Countries.default) }
                PhoneEntryScreen(
                    phoneInput = "",
                    onPhoneInputChange = {},
                    selectedCountry = selected,
                    onCountrySelected = { selected = it },
                    validationMessage = null,
                    errorMessage = null,
                    isBusy = false,
                    onSubmit = {},
                )
            }
        }
        composeRule.onNodeWithTag("country_selector").performClick()
        composeRule.onNodeWithText("South Africa (+27)").performClick()
        composeRule.onNodeWithText("South Africa (+27)").assertIsDisplayed()
    }

    @Test
    fun phoneFieldFormatsNationalInputWithoutChangingNormalizerRules() {
        composeRule.setContent {
            MaterialTheme {
                var input by remember { mutableStateOf("") }
                PhoneEntryScreen(
                    phoneInput = input,
                    onPhoneInputChange = { input = PhoneInputFormatter.format(it, "268") },
                    selectedCountry = Countries.default,
                    onCountrySelected = {},
                    validationMessage = null,
                    errorMessage = null,
                    isBusy = false,
                    onSubmit = {},
                )
            }
        }
        composeRule.onNodeWithTag("phone_input").performTextInput("076123456")
        composeRule.onNodeWithTag("phone_input").assertTextContains("+268 76 123 456")
    }

    @Test
    fun codeFieldAcceptsOnlySixDigitsAndEnablesVerification() {
        composeRule.setContent {
            MaterialTheme {
                var code by remember { mutableStateOf("") }
                OtpEntryScreen(
                    redactedPhone = "+•••••••3456",
                    codeInput = code,
                    onCodeInputChange = { code = OtpInputFormatter.sanitize(it) },
                    retryAfterSeconds = 0L,
                    errorMessage = null,
                    isBusy = false,
                    onVerify = {},
                    onResend = {},
                )
            }
        }
        composeRule.onNodeWithTag("code_input").performTextInput("48a2-9 13")
        composeRule.onNodeWithTag("code_input").assertTextContains("482913")
        composeRule.onNodeWithText("Verify code").assertIsEnabled()
    }

    @Test
    fun phoneOnlyIdentityScreensHaveNoEmailOrPasswordControls() {
        composeRule.setContent {
            MaterialTheme {
                PhoneEntryScreen(
                    phoneInput = "",
                    onPhoneInputChange = {},
                    selectedCountry = Countries.default,
                    onCountrySelected = {},
                    validationMessage = null,
                    errorMessage = null,
                    isBusy = false,
                    onSubmit = {},
                )
            }
        }
        composeRule.onNodeWithTag("phone_input").assertIsDisplayed()
        composeRule.onNodeWithText("email", substring = true, ignoreCase = true).assertDoesNotExist()
        composeRule.onNodeWithText("password", substring = true, ignoreCase = true).assertDoesNotExist()

        composeRule.setContent {
            MaterialTheme {
                OtpEntryScreen(
                    redactedPhone = "+•••••••3456",
                    codeInput = "",
                    onCodeInputChange = {},
                    retryAfterSeconds = 0L,
                    errorMessage = null,
                    isBusy = false,
                    onVerify = {},
                    onResend = {},
                )
            }
        }
        composeRule.onNodeWithTag("code_input").assertIsDisplayed()
        composeRule.onNodeWithText("email", substring = true, ignoreCase = true).assertDoesNotExist()
        composeRule.onNodeWithText("password", substring = true, ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun offlineStateShowsNetworkErrorWithoutDisablingManualEntry() {
        composeRule.setContent {
            MaterialTheme {
                PhoneEntryScreen(
                    phoneInput = "+268 76 123 456",
                    onPhoneInputChange = {},
                    selectedCountry = Countries.default,
                    onCountrySelected = {},
                    validationMessage = null,
                    errorMessage = DiagnosticsRedactor.error(AuthError.Network()),
                    isBusy = false,
                    onSubmit = {},
                )
            }
        }
        composeRule.onNodeWithText(DiagnosticsRedactor.error(AuthError.Network())).assertIsDisplayed()
        composeRule.onNodeWithTag("phone_input").assertIsDisplayed()
        composeRule.onNodeWithText("Send verification code").assertIsEnabled()
    }
}
