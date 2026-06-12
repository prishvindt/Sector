package com.prishvindt.sector.ui.input

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.unit.dp
import com.prishvindt.sector.domain.GeoPoint
import java.util.Locale

@Composable
fun MeasurementInputDialog(
    initialCallsign: String,
    sourcePoint: GeoPoint? = null,
    onDismiss: () -> Unit,
    onSave: (callsign: String, azimuth: String, error: String, distance: String) -> Unit
) {
    var callsign by remember(initialCallsign) { mutableStateOf(initialCallsign) }
    var azimuth by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(DefaultAzimuthErrorText) }
    var distance by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Параметры азимута") },
        text = {
            Column {
                OutlinedTextField(
                    value = callsign,
                    onValueChange = { callsign = it },
                    singleLine = true,
                    label = { Text("Позывной") }
                )
                sourcePoint?.let { point ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Координаты: ${point.formatCoordinates()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                NumericField(
                    value = azimuth,
                    onValueChange = { azimuth = it },
                    label = "Азимут",
                    suffix = "°"
                )
                Spacer(Modifier.height(8.dp))
                NumericField(
                    value = error,
                    onValueChange = { error = it },
                    label = "Погрешность",
                    suffix = "°"
                )
                Spacer(Modifier.height(8.dp))
                NumericField(
                    value = distance,
                    onValueChange = { distance = it },
                    label = "Расстояние, км",
                    suffix = "км"
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(callsign, azimuth, error, distance) }) {
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
    placeholder: String? = null,
    label: String? = null,
    suffix: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = placeholder?.let { { Text(it) } },
        label = label?.let { { Text(it) } },
        suffix = suffix?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

private fun GeoPoint.formatCoordinates(): String =
    String.format(Locale.US, "%.6f, %.6f", latitude, longitude)

private const val DefaultAzimuthErrorText = "5"
