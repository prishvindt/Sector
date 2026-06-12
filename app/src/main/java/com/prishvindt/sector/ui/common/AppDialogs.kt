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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.prishvindt.sector.domain.AzimuthDistance
import com.prishvindt.sector.domain.GeoMath
import com.prishvindt.sector.domain.GeoPoint
import com.prishvindt.sector.domain.RouteTarget
import com.prishvindt.sector.domain.RouteTargetType
import com.prishvindt.sector.domain.backup.BackupSelection
import com.prishvindt.sector.domain.notes.MapNote
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
    mapNotes: List<MapNote>,
    ownColorArgb: Int,
    onDismiss: () -> Unit,
    onSaveData: () -> Unit,
    onSendAll: () -> Unit,
    onSendSelected: (Set<String>, Set<String>) -> Unit
) {
    val exportableMeasurements = measurements.filter { it.active }
    val measurementIds = exportableMeasurements.map { it.measurementId }
    val noteIds = mapNotes.map { it.objectId }
    var selectedTab by remember { mutableStateOf(0) }
    var selectedMeasurementIds by remember(measurementIds) { mutableStateOf(emptySet<String>()) }
    var selectedNoteIds by remember(noteIds) { mutableStateOf(emptySet<String>()) }
    val hasExportableObjects = exportableMeasurements.isNotEmpty() || mapNotes.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Экспорт") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = hasExportableObjects,
                    onClick = onSendAll
                ) {
                    Text("Отправить все")
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onSaveData
                ) {
                    Text(EXPORT_BACKUP_BUTTON_LABEL)
                }
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = ExportTabsContainerColor,
                    contentColor = DialogPrimaryText
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        selectedContentColor = DialogPrimaryText,
                        unselectedContentColor = DialogPrimaryText,
                        text = { Text("Лучи") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        selectedContentColor = DialogPrimaryText,
                        unselectedContentColor = DialogPrimaryText,
                        text = { Text("Заметки") }
                    )
                }
                when (selectedTab) {
                    0 -> ExportMeasurementsTab(
                        measurements = exportableMeasurements,
                        ownColorArgb = ownColorArgb,
                        selectedIds = selectedMeasurementIds,
                        onSelectedIdsChange = { selectedMeasurementIds = it }
                    )
                    else -> ExportNotesTab(
                        notes = mapNotes,
                        selectedIds = selectedNoteIds,
                        onSelectedIdsChange = { selectedNoteIds = it }
                    )
                }
                Text(
                    text = "Выбрано: ${selectedMeasurementIds.size + selectedNoteIds.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = DialogSecondaryText
                )
            }
        },
        confirmButton = {
            Button(
                enabled = selectedMeasurementIds.isNotEmpty() || selectedNoteIds.isNotEmpty(),
                onClick = { onSendSelected(selectedMeasurementIds, selectedNoteIds) }
            ) {
                Text("Отправить выбранное")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
        containerColor = DialogContainer,
        titleContentColor = DialogPrimaryText,
        textContentColor = DialogPrimaryText
    )
}

@Composable
fun BackupCategorySelectionDialog(
    onDismiss: () -> Unit,
    onConfirm: (BackupSelection) -> Unit
) {
    BackupCategoryDialog(
        title = "Сохранить данные",
        confirmText = "Сохранить",
        available = BackupSelection(
            azimuthRays = true,
            mapNotes = true,
            noteMedia = true,
            settings = true
        ),
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

@Composable
fun ImportBackupCategorySelectionDialog(
    available: BackupSelection,
    onDismiss: () -> Unit,
    onConfirm: (BackupSelection) -> Unit
) {
    BackupCategoryDialog(
        title = "Импорт из ZIP",
        confirmText = "Импортировать",
        available = available,
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

@Composable
private fun BackupCategoryDialog(
    title: String,
    confirmText: String,
    available: BackupSelection,
    onDismiss: () -> Unit,
    onConfirm: (BackupSelection) -> Unit
) {
    var azimuthRays by remember(available) { mutableStateOf(available.azimuthRays) }
    var mapNotes by remember(available) { mutableStateOf(available.mapNotes) }
    var noteMedia by remember(available) { mutableStateOf(available.noteMedia && available.mapNotes) }
    var settings by remember(available) { mutableStateOf(available.settings) }
    val selection = BackupSelection(
        azimuthRays = azimuthRays && available.azimuthRays,
        mapNotes = mapNotes && available.mapNotes,
        noteMedia = noteMedia && available.noteMedia && mapNotes,
        settings = settings && available.settings
    ).normalized()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                BackupCategoryRow(
                    label = "Азимутные лучи",
                    checked = azimuthRays && available.azimuthRays,
                    enabled = available.azimuthRays,
                    onCheckedChange = { azimuthRays = it }
                )
                BackupCategoryRow(
                    label = "Заметки",
                    checked = mapNotes && available.mapNotes,
                    enabled = available.mapNotes,
                    onCheckedChange = {
                        mapNotes = it
                        if (!it) noteMedia = false
                    }
                )
                BackupCategoryRow(
                    label = "Медиа из заметок",
                    checked = noteMedia && available.noteMedia && mapNotes,
                    enabled = available.noteMedia,
                    onCheckedChange = {
                        noteMedia = it
                        if (it) mapNotes = true
                    }
                )
                BackupCategoryRow(
                    label = "Настройки",
                    checked = settings && available.settings,
                    enabled = available.settings,
                    onCheckedChange = { settings = it }
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selection) }) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
        containerColor = DialogContainer,
        titleContentColor = DialogPrimaryText,
        textContentColor = DialogPrimaryText
    )
}

@Composable
private fun BackupCategoryRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = DialogSecondaryText,
                disabledUncheckedColor = DialogSecondaryText.copy(alpha = 0.38f),
                disabledCheckedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
            )
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) DialogPrimaryText else DialogSecondaryText.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun ExportMeasurementsTab(
    measurements: List<Measurement>,
    ownColorArgb: Int,
    selectedIds: Set<String>,
    onSelectedIdsChange: (Set<String>) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
    ) {
        if (measurements.isEmpty()) {
            item {
                Text(
                    text = "Нет лучей для экспорта",
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = DialogSecondaryText
                )
            }
        }
        items(measurements, key = { it.measurementId }) { measurement ->
            val colorArgb = MeasurementColor.resolve(
                measurement = measurement,
                ownColorArgb = ownColorArgb
            )
            ExportMeasurementSelectionRow(
                measurement = measurement,
                colorArgb = colorArgb,
                selected = measurement.measurementId in selectedIds,
                onSelectedChange = { selected ->
                    onSelectedIdsChange(
                        if (selected) {
                            selectedIds + measurement.measurementId
                        } else {
                            selectedIds - measurement.measurementId
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun ExportNotesTab(
    notes: List<MapNote>,
    selectedIds: Set<String>,
    onSelectedIdsChange: (Set<String>) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
    ) {
        if (notes.isEmpty()) {
            item {
                Text(
                    text = "Нет заметок для экспорта",
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = DialogSecondaryText
                )
            }
        }
        items(notes, key = { it.objectId }) { note ->
            ExportNoteSelectionRow(
                note = note,
                selected = note.objectId in selectedIds,
                onSelectedChange = { selected ->
                    onSelectedIdsChange(
                        if (selected) {
                            selectedIds + note.objectId
                        } else {
                            selectedIds - note.objectId
                        }
                    )
                }
            )
        }
    }
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
                checkmarkColor = contrastingContentColor(rayColor)
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
private fun ExportNoteSelectionRow(
    note: MapNote,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit
) {
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
                checkedColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = DialogSecondaryText,
                checkmarkColor = DialogContainer
            )
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = note.title,
                style = MaterialTheme.typography.bodyMedium,
                color = DialogPrimaryText
            )
            Text(
                text = note.exportSubtitle(),
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
                    Text("Маршрут от меня")
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
    onRouteFromPoint: () -> Unit,
    onExternalRoute: () -> Unit,
    onAddNote: () -> Unit,
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
                Text("Маршрут от меня")
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onRouteFromPoint
            ) {
                Text("Маршрут от этой точки")
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onExternalRoute
            ) {
                Text("Открыть в Яндекс.Картах")
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onAddNote
            ) {
                Text("Добавить заметку")
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
    return "Азимут ${azimuthDeg.formatDegrees()}° · ±${azimuthErrorDeg.formatDegrees()}° · " +
        "${AzimuthDistance.formatLabel(distanceKm)} · $callsign"
}

private fun MapNote.exportSubtitle(): String {
    val preview = text.trim().takeIf { it.isNotBlank() }
    val coordinates = "${point.latitude.formatCoord()}, ${point.longitude.formatCoord()}"
    return if (preview == null) {
        coordinates
    } else {
        "$coordinates · ${preview.take(48)}"
    }
}

private fun Double.formatCoord(): String = String.format(Locale.US, "%.6f", this)

private fun Double.formatDegrees(): String =
    if (this == roundToInt().toDouble()) {
        roundToInt().toString()
    } else {
        String.format(Locale.US, "%.1f", this).trimEnd('0').trimEnd('.')
    }

internal const val EXPORT_BACKUP_BUTTON_LABEL = "Сохранить данные в .zip"
internal val ExportDialogContainerColor = Color(0xFFFFFFFF)
internal val ExportTabsContainerColor = ExportDialogContainerColor

private val DialogContainer = ExportDialogContainerColor
private val DialogPrimaryText = Color(0xFF15191D)
private val DialogSecondaryText = Color(0xFF4F5B56)
