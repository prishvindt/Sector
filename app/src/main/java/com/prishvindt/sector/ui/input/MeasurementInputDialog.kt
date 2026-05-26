package com.prishvindt.sector.ui.input

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp

@Composable
fun MeasurementInputDialog(
    onDismiss: () -> Unit,
    onSave: (azimuth: String, error: String, signal: String) -> Unit
) {
    var azimuth by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("15") }
    var signal by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ввод данных") },
        text = {
            Column {
                NumericField(
                    value = azimuth,
                    onValueChange = { azimuth = it },
                    placeholder = "Азимут"
                )
                Spacer(Modifier.height(8.dp))
                NumericField(
                    value = error,
                    onValueChange = { error = it },
                    placeholder = "Погрешность, °"
                )
                Spacer(Modifier.height(8.dp))
                NumericField(
                    value = signal,
                    onValueChange = { signal = it },
                    placeholder = "Мощность dBm"
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(azimuth, error, signal) }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отменить")
            }
        }
    )
}

@Composable
private fun NumericField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = { Text(placeholder) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}
