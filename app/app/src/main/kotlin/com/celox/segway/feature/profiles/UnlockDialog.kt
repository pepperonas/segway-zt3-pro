package com.celox.segway.feature.profiles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun UnlockDialog(
    targetSpeedKmh: Int,
    pinRequired: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (pin: String?) -> Unit,
    showError: Boolean = false,
) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unlock $targetSpeedKmh km/h") },
        text = {
            Column {
                Text(
                    "Switching to the unlock profile. Make sure you're on private property.",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (pinRequired) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter { c -> c.isDigit() }.take(8) },
                        label = { Text("PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = showError,
                        supportingText = if (showError) { { Text("Wrong PIN", color = MaterialTheme.colorScheme.error) } } else null
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(if (pinRequired) pin else null) }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
