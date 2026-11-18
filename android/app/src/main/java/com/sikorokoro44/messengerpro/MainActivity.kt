package com.sikorokoro44.messengerpro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sikorokoro44.messengerpro.data.auth.InvalidPhoneReason
import com.sikorokoro44.messengerpro.data.auth.PhoneNormalizer
import com.sikorokoro44.messengerpro.data.auth.PhoneValidationResult
import com.sikorokoro44.messengerpro.data.network.ApiResult
import com.sikorokoro44.messengerpro.data.network.AuthError
import com.sikorokoro44.messengerpro.data.network.OtpRequest
import com.sikorokoro44.messengerpro.data.network.OtpVerifyRequest
import com.sikorokoro44.messengerpro.data.network.UnconfiguredAuthApi
import com.sikorokoro44.messengerpro.domain.auth.AuthEvent
import com.sikorokoro44.messengerpro.domain.auth.AuthState
import com.sikorokoro44.messengerpro.domain.auth.AuthStateMachine
import com.sikorokoro44.messengerpro.security.DiagnosticsRedactor
import com.sikorokoro44.messengerpro.ui.auth.Countries
import com.sikorokoro44.messengerpro.ui.auth.Country
import com.sikorokoro44.messengerpro.ui.auth.OtpEntryScreen
import com.sikorokoro44.messengerpro.ui.auth.PhoneEntryScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MessengerProApp() }
    }
}

private fun invalidPhoneMessage(reason: InvalidPhoneReason): String = when (reason) {
    InvalidPhoneReason.EMPTY -> "Enter a phone number."
    InvalidPhoneReason.INVALID_CHARACTERS -> "Use digits only, with an optional country code."
    InvalidPhoneReason.TOO_SHORT -> "That phone number is too short."
    InvalidPhoneReason.TOO_LONG -> "That phone number is too long."
}

private fun otpContext(state: AuthState): Pair<String, String>? = when (state) {
    is AuthState.OtpEntry -> state.phoneE164 to state.requestId
    is AuthState.VerifyingCode -> state.phoneE164 to state.requestId
    is AuthState.Failed -> otpContext(state.previous)
    else -> null
}

@Composable
private fun MessengerProApp() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
            ) {
                Text("Messenger Pro", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Phone number sign-in · OTP required",
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                IdentityFlow()
            }
        }
    }
}

@Composable
private fun IdentityFlow() {
    // No endpoint or credential is constructed here. Until a backend is deployed this reports
    // NOT_CONFIGURED; it never fabricates a code, session or successful authentication.
    val authApi = remember { UnconfiguredAuthApi() }
    val machine = remember { AuthStateMachine() }
    var authState by remember { mutableStateOf<AuthState>(AuthState.PhoneEntry) }
    var phoneInput by remember { mutableStateOf("") }
    var country by remember { mutableStateOf<Country>(Countries.default) }
    var codeInput by remember { mutableStateOf("") }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun dispatch(event: AuthEvent) {
        authState = machine.send(event)
    }

    fun reportFailure(error: AuthError) {
        errorMessage = DiagnosticsRedactor.error(error)
        dispatch(AuthEvent.OtpRequestFailed(error))
    }

    val submitPhone: () -> Unit = {
        when (val result = PhoneNormalizer.normalize(phoneInput, country.dialCode)) {
            is PhoneValidationResult.Invalid -> {
                validationMessage = invalidPhoneMessage(result.reason)
                errorMessage = null
            }
            is PhoneValidationResult.Valid -> {
                validationMessage = null
                errorMessage = null
                dispatch(AuthEvent.PhoneSubmitted(result.phone))
                scope.launch {
                    when (val response = authApi.requestOtp(OtpRequest(result.phone.e164, locale = "ss-ZA"))) {
                        is ApiResult.Success -> dispatch(AuthEvent.OtpRequestSucceeded(response.data))
                        is ApiResult.Failure -> reportFailure(response.error)
                    }
                }
            }
        }
    }

    val verifyCode: () -> Unit = {
        val context = otpContext(authState)
        if (context == null) {
            errorMessage = "Request a code first."
        } else if (codeInput.length != 6 || !codeInput.all(Char::isDigit)) {
            errorMessage = "Enter the 6-digit code."
        } else {
            errorMessage = null
            dispatch(AuthEvent.CodeSubmitted(codeInput))
            scope.launch {
                when (
                    val response = authApi.verifyOtp(
                        OtpVerifyRequest(context.first, context.second, codeInput),
                    )
                ) {
                    is ApiResult.Success -> dispatch(AuthEvent.VerificationSucceeded(response.data))
                    is ApiResult.Failure -> {
                        errorMessage = DiagnosticsRedactor.error(response.error)
                        dispatch(AuthEvent.VerificationFailed(response.error))
                    }
                }
            }
        }
    }

    val resendCode: () -> Unit = {
        otpContext(authState)?.let { context ->
            errorMessage = null
            dispatch(AuthEvent.ResendRequested)
            scope.launch {
                when (val response = authApi.requestOtp(OtpRequest(context.first, locale = "ss-ZA"))) {
                    is ApiResult.Success -> dispatch(AuthEvent.OtpRequestSucceeded(response.data))
                    is ApiResult.Failure -> reportFailure(response.error)
                }
            }
        }
    }

    when (val state = authState) {
        is AuthState.Authenticated -> Text(
            "Session established. Messaging is not connected yet.",
            modifier = Modifier.padding(top = 24.dp),
            style = MaterialTheme.typography.bodyLarge,
        )

        is AuthState.OtpEntry, is AuthState.VerifyingCode, is AuthState.Failed -> {
            val context = otpContext(state)
            if (context != null) {
                val retrySeconds = if (state is AuthState.OtpEntry) state.retryAfterSeconds else 0L
                OtpEntryScreen(
                    redactedPhone = DiagnosticsRedactor.phone(context.first),
                    codeInput = codeInput,
                    onCodeInputChange = { codeInput = it },
                    retryAfterSeconds = retrySeconds,
                    errorMessage = errorMessage,
                    isBusy = state is AuthState.VerifyingCode,
                    onVerify = verifyCode,
                    onResend = resendCode,
                )
            } else {
                PhoneEntryScreen(
                    phoneInput = phoneInput,
                    onPhoneInputChange = { phoneInput = it },
                    selectedCountry = country,
                    onCountrySelected = { country = it },
                    validationMessage = validationMessage,
                    errorMessage = errorMessage,
                    isBusy = state is AuthState.RequestingOtp,
                    onSubmit = submitPhone,
                )
            }
        }

        else -> PhoneEntryScreen(
            phoneInput = phoneInput,
            onPhoneInputChange = { phoneInput = it },
            selectedCountry = country,
            onCountrySelected = { country = it },
            validationMessage = validationMessage,
            errorMessage = errorMessage,
            isBusy = state is AuthState.RequestingOtp,
            onSubmit = submitPhone,
        )
    }
}
