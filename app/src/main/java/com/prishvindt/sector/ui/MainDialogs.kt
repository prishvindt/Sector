package com.prishvindt.sector.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.prishvindt.sector.BuildConfig
import com.prishvindt.sector.data.Measurement
import com.prishvindt.sector.domain.RouteTarget
import com.prishvindt.sector.domain.RouteTargetType
import com.prishvindt.sector.ui.about.AboutScreen
import com.prishvindt.sector.ui.callsign.CallsignDialog
import com.prishvindt.sector.ui.common.BackgroundLocationRationaleDialog
import com.prishvindt.sector.ui.common.DestinationTargetBottomSheet
import com.prishvindt.sector.ui.common.DrawerItem
import com.prishvindt.sector.ui.common.ExportWarningDialog
import com.prishvindt.sector.ui.common.MainUiState
import com.prishvindt.sector.ui.common.TargetMenuDialog
import com.prishvindt.sector.ui.firststart.FirstStartDialog
import com.prishvindt.sector.ui.importdata.ImportDialog
import com.prishvindt.sector.ui.input.MeasurementInputDialog
import com.prishvindt.sector.ui.measurements.MeasurementsScreen

@Composable
fun MainDialogHost(
    activeDialog: DrawerItem?,
    state: MainUiState,
    onDismissActiveDialog: () -> Unit,
    onSaveCallsign: (String) -> Unit,
    onSaveMeasurement: (String, String, String) -> Unit,
    onImportMeasurement: (String) -> Unit,
    onDeleteMeasurement: (Measurement) -> Unit,
    onClearMeasurements: () -> Unit,
    onCopyMeasurementCoordinates: (Measurement) -> Unit,
    onFocusMeasurement: (Measurement) -> Unit,
    onAcceptFirstStart: () -> Unit,
    onConfirmExportWarning: () -> Unit,
    onDismissExportWarning: () -> Unit,
    onConfirmBackgroundRationale: () -> Unit,
    onDismissBackgroundRationale: () -> Unit,
    onDismissCallsignPrompt: () -> Unit,
    onSaveCallsignForExport: (String) -> Unit,
    onShowChangelog: () -> Unit,
    onDismissChangelog: () -> Unit,
    onSelectTarget: (RouteTarget?) -> Unit,
    onBuildInAppRouteToSelectedTarget: () -> Unit,
    onOpenExternalRouteToSelectedTarget: () -> Unit,
    onCopySelectedTargetCoordinates: () -> Unit,
    onDeleteDestination: () -> Unit
) {
    when (activeDialog) {
        DrawerItem.CALLSIGN -> CallsignDialog(
            initialValue = state.settings.callsign,
            onDismiss = onDismissActiveDialog,
            onSave = {
                onSaveCallsign(it)
                onDismissActiveDialog()
            }
        )
        DrawerItem.INPUT -> MeasurementInputDialog(
            onDismiss = onDismissActiveDialog,
            onSave = { azimuth, error, signal ->
                onSaveMeasurement(azimuth, error, signal)
                onDismissActiveDialog()
            }
        )
        DrawerItem.IMPORT -> ImportDialog(
            onDismiss = onDismissActiveDialog,
            onSave = {
                onImportMeasurement(it)
                onDismissActiveDialog()
            }
        )
        DrawerItem.MEASUREMENTS -> MeasurementsScreen(
            measurements = state.measurements,
            currentPosition = state.locationState.point,
            onDismiss = onDismissActiveDialog,
            onDelete = onDeleteMeasurement,
            onClearAll = onClearMeasurements,
            onCopyCoordinates = onCopyMeasurementCoordinates,
            onCenter = {
                onFocusMeasurement(it)
                onDismissActiveDialog()
            }
        )
        DrawerItem.ABOUT -> AboutScreen(
            onDismiss = onDismissActiveDialog,
            onShowChangelog = {
                onDismissActiveDialog()
                onShowChangelog()
            }
        )
        DrawerItem.EXPORT, DrawerItem.SETTINGS, null -> Unit
    }

    if (state.showFirstStartDialog) {
        FirstStartDialog(onConfirm = onAcceptFirstStart)
    }
    if (state.showExportWarning) {
        ExportWarningDialog(
            onConfirm = onConfirmExportWarning,
            onDismiss = onDismissExportWarning
        )
    }
    if (state.showBackgroundRationale) {
        BackgroundLocationRationaleDialog(
            onConfirm = onConfirmBackgroundRationale,
            onDismiss = onDismissBackgroundRationale
        )
    }
    if (state.callsignPromptForExport) {
        CallsignDialog(
            initialValue = state.settings.callsign,
            title = "Введите позывной",
            onDismiss = onDismissCallsignPrompt,
            onSave = onSaveCallsignForExport
        )
    }
    if (
        state.showChangelogDialog &&
        activeDialog == null &&
        !state.showFirstStartDialog &&
        !state.showExportWarning &&
        !state.showBackgroundRationale &&
        !state.callsignPromptForExport &&
        state.selectedTarget == null
    ) {
        ChangelogDialog(onDismiss = onDismissChangelog)
    }

    state.selectedTarget?.let { target ->
        if (target.type == RouteTargetType.DESTINATION) {
            DestinationTargetBottomSheet(
                target = target,
                currentPosition = state.locationState.point,
                onDismiss = { onSelectTarget(null) },
                onInAppRoute = onBuildInAppRouteToSelectedTarget,
                onExternalRoute = onOpenExternalRouteToSelectedTarget,
                onCopyCoordinates = onCopySelectedTargetCoordinates,
                onDeleteDestination = onDeleteDestination
            )
        } else {
            TargetMenuDialog(
                target = target,
                onDismiss = { onSelectTarget(null) },
                onInAppRoute = onBuildInAppRouteToSelectedTarget,
                onExternalRoute = onOpenExternalRouteToSelectedTarget,
                onCopyCoordinates = {
                    onCopySelectedTargetCoordinates()
                    onSelectTarget(null)
                },
                onDeleteDestination = null
            )
        }
    }
}

@Composable
private fun ChangelogDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Что нового в ${BuildConfig.VERSION_NAME}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChangelogBullet("Убрано мерцание объектов карты при изменении GPS/спутников.")
                ChangelogBullet("Улучшена стабильность отрисовки маршрута, азимута и сектора.")
                ChangelogBullet("Внутренняя логика обновлений вынесена из главного экрана.")
                ChangelogBullet("Главный экран стал легче и стабильнее.")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Понятно")
            }
        },
        containerColor = ChangelogContainer,
        titleContentColor = ChangelogPrimaryText,
        textContentColor = ChangelogPrimaryText
    )
}

@Composable
private fun ChangelogBullet(text: String) {
    Text(
        text = "• $text",
        style = MaterialTheme.typography.bodyMedium
    )
}

private val ChangelogContainer = Color(0xFF101418)
private val ChangelogPrimaryText = Color(0xFFE8ECEA)
