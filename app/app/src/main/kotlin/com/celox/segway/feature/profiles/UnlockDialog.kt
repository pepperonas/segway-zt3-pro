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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.celox.segway.R

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
        title = { Text(stringResource(R.string.unlock_dialog_title, targetSpeedKmh)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.unlock_dialog_body),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (pinRequired) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter { c -> c.isDigit() }.take(8) },
                        label = { Text(stringResource(R.string.unlock_dialog_pin_field)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = showError,
                        supportingText = if (showError) { {
                            Text(
                                stringResource(R.string.unlock_failed),
                                color = MaterialTheme.colorScheme.error,
                            )
                        } } else null
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(if (pinRequired) pin else null) }) {
                Text(stringResource(R.string.action_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
