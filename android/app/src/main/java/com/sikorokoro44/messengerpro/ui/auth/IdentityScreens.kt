package com.sikorokoro44.messengerpro.ui.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

data class Country(val name: String, val dialCode: String)

object Countries {
    val all = listOf(
        Country("Eswatini", "268"),
        Country("South Africa", "27"),
        Country("Mozambique", "258"),
        Country("Zimbabwe", "263"),
        Country("Botswana", "267"),
        Country("Lesotho", "266"),
        Country("Namibia", "264"),
        Country("United Kingdom", "44"),
        Country("United States", "1"),
    )
    val default = all.first()
}

@Composable
fun CountrySelector(
    selected: Country,
    countries: List<Country> = Countries.all,
    onSelected: (Country) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        TextButton(onClick = { expanded = true }, modifier = Modifier.testTag("country_selector")) {
            Text("${selected.name} (+${selected.dialCode})")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            countries.forEach { country ->
                DropdownMenuItem(
                    text = { Text("${country.name} (+${country.dialCode})") },
                    onClick = {
                        onSelected(country)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun PhoneEntryScreen(
    phoneInput: String,
    onPhoneInputChange: (String) -> Unit,
    selectedCountry: Country,
    onCountrySelected: (Country) -> Unit,
    validationMessage: String?,
    errorMessage: String?,
    isBusy: Boolean,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Text("Sign in with your phone number", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        CountrySelector(selected = selectedCountry, onSelected = onCountrySelected)
        OutlinedTextField(
            value = phoneInput,
            onValueChange = onPhoneInputChange,
            label = { Text("Phone number") },
            singleLine = true,
            enabled = !isBusy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth().testTag("phone_input"),
        )
        validationMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        errorMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onSubmit, enabled = !isBusy && phoneInput.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text(if (isBusy) "Requesting code…" else "Send verification code")
        }
    }
}

@Composable
fun OtpEntryScreen(
    redactedPhone: String,
    codeInput: String,
    onCodeInputChange: (String) -> Unit,
    retryAfterSeconds: Long,
    errorMessage: String?,
    isBusy: Boolean,
    onVerify: () -> Unit,
    onResend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Text("Enter the 6-digit code", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text("Sent to $redactedPhone", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = codeInput,
            onValueChange = onCodeInputChange,
            label = { Text("Verification code") },
            singleLine = true,
            enabled = !isBusy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag("code_input"),
        )
        errorMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onVerify, enabled = !isBusy && codeInput.length == 6, modifier = Modifier.fillMaxWidth()) {
            Text(if (isBusy) "Verifying…" else "Verify code")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onResend, enabled = !isBusy && retryAfterSeconds == 0L) {
            Text(if (retryAfterSeconds > 0) "Resend available in ${retryAfterSeconds}s" else "Resend code")
        }
    }
}
