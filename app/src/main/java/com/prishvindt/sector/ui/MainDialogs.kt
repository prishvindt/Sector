package com.prishvindt.sector.ui

import android.net.Uri
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
import com.prishvindt.sector.domain.GeoPoint
import com.prishvindt.sector.domain.RouteTarget
import com.prishvindt.sector.domain.RouteTargetType
import com.prishvindt.sector.media.notes.RecordedNoteAudio
import com.prishvindt.sector.ui.about.AboutScreen
import com.prishvindt.sector.ui.callsign.CallsignDialog
import com.prishvindt.sector.ui.common.BackgroundLocationRationaleDialog
import com.prishvindt.sector.ui.common.BackupCategorySelectionDialog
import com.prishvindt.sector.ui.common.DestinationTargetBottomSheet
import com.prishvindt.sector.ui.common.DrawerItem
import com.prishvindt.sector.ui.common.ExportMeasurementSelectionDialog
import com.prishvindt.sector.ui.common.ExportWarningDialog
import com.prishvindt.sector.ui.common.ImportBackupCategorySelectionDialog
import com.prishvindt.sector.ui.common.MainUiState
import com.prishvindt.sector.ui.common.TargetMenuDialog
import com.prishvindt.sector.ui.firststart.FirstStartDialog
import com.prishvindt.sector.ui.importdata.ImportDialog
import com.prishvindt.sector.ui.input.MeasurementInputDialog
import com.prishvindt.sector.ui.measurements.MeasurementsScreen
import com.prishvindt.sector.ui.notes.NoteDialog

@Composable
fun MainDialogHost(
    activeDialog: DrawerItem?,
    state: MainUiState,
    measurementInputPoint: GeoPoint?,
    onDismissActiveDialog: () -> Unit,
    onSaveCallsign: (String) -> Unit,
    onSaveMeasurement: (String, String, String, String, GeoPoint?) -> Unit,
    onImportMeasurement: (String) -> Unit,
    onDeleteMeasurement: (Measurement) -> Unit,
    onClearMeasurements: () -> Unit,
    onCopyMeasurementCoordinates: (Measurement) -> Unit,
    onFocusMeasurement: (Measurement) -> Unit,
    onAcceptFirstStart: () -> Unit,
    onConfirmExportWarning: () -> Unit,
    onDismissExportWarning: () -> Unit,
    onDismissExportMeasurementSelection: () -> Unit,
    onRequestBackup: () -> Unit,
    onDismissBackupCategorySelection: () -> Unit,
    onConfirmBackupCategories: (com.prishvindt.sector.domain.backup.BackupSelection) -> Unit,
    onSendAllExportMeasurements: () -> Unit,
    onSendSelectedExportMeasurements: (Set<String>, Set<String>) -> Unit,
    onRequestImportBackupZip: () -> Unit,
    onDismissImportBackupCategorySelection: () -> Unit,
    onConfirmImportBackupCategories: (com.prishvindt.sector.domain.backup.BackupSelection) -> Unit,
    onConfirmBackgroundRationale: () -> Unit,
    onDismissBackgroundRationale: () -> Unit,
    onDismissCallsignPrompt: () -> Unit,
    onSaveCallsignForExport: (String) -> Unit,
    onShowChangelog: () -> Unit,
    onDismissChangelog: () -> Unit,
    onSelectTarget: (RouteTarget?) -> Unit,
    onBuildInAppRouteToSelectedTarget: () -> Unit,
    onBeginRouteFromSelectedPoint: () -> Unit,
    onOpenExternalRouteToSelectedTarget: () -> Unit,
    onAddNoteForSelectedTarget: () -> Unit,
    onSetAzimuthForSelectedTarget: () -> Unit,
    onCopySelectedTargetCoordinates: () -> Unit,
    onDeleteSelectedDestination: () -> Unit,
    onNoteTitleChange: (String) -> Unit,
    onNoteTextChange: (String) -> Unit,
    onNotePhotoPicked: (Uri) -> Unit,
    onPrepareNoteCameraCapture: () -> Uri?,
    onNoteCameraCaptureResult: (Boolean) -> Unit,
    onNoteAudioRecorded: (RecordedNoteAudio) -> Unit,
    onRemoveNoteAttachment: (String) -> Unit,
    onSaveNote: () -> Unit,
    onDismissNote: () -> Unit,
    onDeleteNote: () -> Unit,
    onShowNoteMessage: (String) -> Unit
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
            initialCallsign = state.settings.callsign,
            sourcePoint = measurementInputPoint,
            onDismiss = onDismissActiveDialog,
            onSave = { callsign, azimuth, error, signal ->
                onSaveMeasurement(callsign, azimuth, error, signal, measurementInputPoint)
                onDismissActiveDialog()
            }
        )
        DrawerItem.IMPORT -> ImportDialog(
            onDismiss = onDismissActiveDialog,
            onSave = {
                onImportMeasurement(it)
                onDismissActiveDialog()
            },
            onImportZip = {
                onRequestImportBackupZip()
                onDismissActiveDialog()
            }
        )
        DrawerItem.MEASUREMENTS -> MeasurementsScreen(
            measurements = state.measurements,
            currentPosition = state.locationState.point,
            ownColorArgb = state.settings.ownPointColor.colorArgb,
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
        DrawerItem.SHARE_GPS, DrawerItem.EXPORT, DrawerItem.SETTINGS, null -> Unit
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
    if (state.showExportMeasurementSelection) {
        ExportMeasurementSelectionDialog(
            measurements = state.exportableMeasurements,
            mapNotes = state.mapNotes,
            ownColorArgb = state.settings.ownPointColor.colorArgb,
            onDismiss = onDismissExportMeasurementSelection,
            onSaveData = onRequestBackup,
            onSendAll = onSendAllExportMeasurements,
            onSendSelected = onSendSelectedExportMeasurements
        )
    }
    if (state.showBackupCategorySelection) {
        BackupCategorySelectionDialog(
            onDismiss = onDismissBackupCategorySelection,
            onConfirm = onConfirmBackupCategories
        )
    }
    state.importBackupAvailableSections?.let { available ->
        ImportBackupCategorySelectionDialog(
            available = available,
            onDismiss = onDismissImportBackupCategorySelection,
            onConfirm = onConfirmImportBackupCategories
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
        !state.showExportMeasurementSelection &&
        !state.showBackupCategorySelection &&
        state.importBackupAvailableSections == null &&
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
                onRouteFromPoint = onBeginRouteFromSelectedPoint,
                onExternalRoute = onOpenExternalRouteToSelectedTarget,
                onAddNote = onAddNoteForSelectedTarget,
                onSetAzimuth = onSetAzimuthForSelectedTarget,
                onCopyCoordinates = onCopySelectedTargetCoordinates,
                onDeleteDestination = onDeleteSelectedDestination
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

    state.noteDraft?.let { draft ->
        NoteDialog(
            draft = draft,
            onTitleChange = onNoteTitleChange,
            onTextChange = onNoteTextChange,
            onPhotoPicked = onNotePhotoPicked,
            onPrepareCameraCapture = onPrepareNoteCameraCapture,
            onCameraCaptureResult = onNoteCameraCaptureResult,
            onAudioRecorded = onNoteAudioRecorded,
            onRemoveAttachment = onRemoveNoteAttachment,
            onSave = onSaveNote,
            onDismiss = onDismissNote,
            onDelete = if (draft.isNew) null else onDeleteNote,
            onShowMessage = onShowNoteMessage
        )
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
                ChangelogBullet("Добавлены заметки на карте через долгий тап.")
                ChangelogBullet("Заметки поддерживают текст, до двух фото и одну аудиозапись.")
                ChangelogBullet("Фото и аудио хранятся локально во внутренней папке приложения.")
                ChangelogBullet("В настройках появились переключатели видимости заметок и их названий.")
                ChangelogBullet("Экспорт SECTOR_BUNDLE_V1 передает заметки без медиафайлов.")
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

private val ChangelogContainer = Color(0xFFFFFFFF)
private val ChangelogPrimaryText = Color(0xFF15191D)
