package com.prishvindt.sector.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.prishvindt.sector.data.Measurement
import com.prishvindt.sector.data.MeasurementColor
import com.prishvindt.sector.domain.GeoMath
import com.prishvindt.sector.domain.GeoPoint
import com.prishvindt.sector.domain.RouteTarget
import com.prishvindt.sector.domain.RouteTargetType
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ExportWarningDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Экспорт содержит координаты") },
        text = {
            Text("Экспорт содержит ваши координаты, азимут и время замера. Отправляйте эти данные только доверенным людям.")
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Понятно") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отменить") } }
    )
}

@Composable
fun ExportMeasurementSelectionDialog(
    measurements: List<Measurement>,
    ownColorArgb: Int,
    onDismiss: () -> Unit,
    onSendAll: () -> Unit,
    onSendSelected: (Set<String>) -> Unit
) {
    val exportableMeasurements = measurements.filter { it.active }
    val measurementIds = exportableMeasurements.map { it.measurementId }
    var selectedIds by remember(measurementIds) { mutableStateOf(emptySet<String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Экспорт лучей") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = exportableMeasurements.isNotEmpty(),
                    onClick = onSendAll
                ) {
                    Text("Отправить все")
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                ) {
                    items(exportableMeasurements, key = { it.measurementId }) { measurement ->
                        val colorArgb = MeasurementColor.resolve(
                            measurement = measurement,
                            ownColorArgb = ownColorArgb
                        )
                        ExportMeasurementSelectionRow(
                            measurement = measurement,
                            colorArgb = colorArgb,
                            selected = measurement.measurementId in selectedIds,
                            onSelectedChange = { selected ->
                                selectedIds = if (selected) {
                                    selectedIds + measurement.measurementId
                                } else {
                                    selectedIds - measurement.measurementId
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = selectedIds.isNotEmpty(),
                onClick = { onSendSelected(selectedIds) }
            ) {
                Text("Отправить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
        containerColor = DialogGraphite,
        titleContentColor = DialogPrimaryText,
        textContentColor = DialogPrimaryText
    )
}

@Composable
private fun ExportMeasurementSelectionRow(
    measurement: Measurement,
    colorArgb: Int,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit
) {
    val rayColor = Color(colorArgb)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = onSelectedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = rayColor,
                uncheckedColor = DialogSecondaryText,
                checkmarkColor = DialogGraphite
            )
        )
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(rayColor, CircleShape)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = measurement.exportTitle(),
                style = MaterialTheme.typography.bodyMedium,
                color = rayColor
            )
            Text(
                text = "${measurement.latitude.formatCoord()}, ${measurement.longitude.formatCoord()}",
                style = MaterialTheme.typography.bodySmall,
                color = DialogSecondaryText
            )
        }
    }
}

@Composable
fun BackgroundLocationRationaleDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Активный поиск") },
        text = {
            Text("Для активного поиска нужно разрешение геолокации 'Всегда'. Без него GPS может остановиться после сворачивания приложения.")
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Продолжить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отменить") } }
    )
}

@Composable
fun TargetMenuDialog(
    target: RouteTarget,
    onDismiss: () -> Unit,
    onInAppRoute: () -> Unit,
    onExternalRoute: () -> Unit,
    onCopyCoordinates: () -> Unit,
    onDeleteDestination: (() -> Unit)?
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(target.title) },
        text = {
            Column {
                Text(target.subtitle ?: "${target.point.latitude}, ${target.point.longitude}")
                TextButton(modifier = androidx.compose.ui.Modifier.fillMaxWidth(), onClick = onInAppRoute) {
                    Text("Маршрут внутри приложения")
                }
                TextButton(modifier = androidx.compose.ui.Modifier.fillMaxWidth(), onClick = onExternalRoute) {
                    Text("Открыть в Яндекс.Картах")
                }
                TextButton(modifier = androidx.compose.ui.Modifier.fillMaxWidth(), onClick = onCopyCoordinates) {
                    Text("Скопировать координаты")
                }
                if (target.type == RouteTargetType.DESTINATION && onDeleteDestination != null) {
                    TextButton(modifier = androidx.compose.ui.Modifier.fillMaxWidth(), onClick = onDeleteDestination) {
                        Text("Удалить точку")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DestinationTargetBottomSheet(
    target: RouteTarget,
    currentPosition: GeoPoint?,
    onDismiss: () -> Unit,
    onInAppRoute: () -> Unit,
    onExternalRoute: () -> Unit,
    onSetAzimuth: () -> Unit,
    onCopyCoordinates: () -> Unit,
    onDeleteDestination: () -> Unit
) {
    val coordinates = target.point.formatCoordinates()
    val distance = currentPosition?.let {
        GeoMath.formatDistance(GeoMath.distanceMeters(it, target.point))
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Координаты: $coordinates", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = distance?.let { "Расстояние от GPS: $it" } ?: "Расстояние от GPS: GPS ещё не найден",
                style = MaterialTheme.typography.bodyMedium
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onInAppRoute
            ) {
                Text("Построить маршрут внутри приложения")
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onExternalRoute
            ) {
                Text("Открыть в Яндекс.Картах")
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onSetAzimuth
            ) {
                Text("Установить азимут")
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onCopyCoordinates
            ) {
                Text("Скопировать координаты")
            }
            TextButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onDeleteDestination
            ) {
                Text("Удалить точку")
            }
        }
    }
}

private fun GeoPoint.formatCoordinates(): String =
    String.format(java.util.Locale.US, "%.6f, %.6f", latitude, longitude)

private fun Measurement.exportTitle(): String {
    val callsign = callsign.ifBlank { "Без позывного" }
    val signal = signalDbm?.let { " · $it dBm" }.orEmpty()
    return "Азимут ${azimuthDeg.formatDegrees()}° · ±${azimuthErrorDeg.formatDegrees()}° · $callsign$signal"
}

private fun Double.formatCoord(): String = String.format(Locale.US, "%.6f", this)

private fun Double.formatDegrees(): String =
    if (this == roundToInt().toDouble()) {
        roundToInt().toString()
    } else {
        String.format(Locale.US, "%.1f", this).trimEnd('0').trimEnd('.')
    }

private val DialogGraphite = Color(0xFF101418)
private val DialogPrimaryText = Color(0xFFE8ECEA)
private val DialogSecondaryText = Color(0xFFB7C1BC)
