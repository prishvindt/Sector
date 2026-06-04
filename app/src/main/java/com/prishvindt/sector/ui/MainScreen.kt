package com.prishvindt.sector.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.prishvindt.sector.domain.GeoPoint
import com.prishvindt.sector.map.YandexMapComposable
import com.prishvindt.sector.ui.common.DrawerItem
import com.prishvindt.sector.ui.common.UiEvent
import com.prishvindt.sector.ui.drawer.SectorDrawer
import com.prishvindt.sector.ui.map.MapOverlays
import com.prishvindt.sector.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onShareText: (text: String, chooserTitle: String, clipLabel: String) -> Unit,
    onCopyText: (label: String, text: String) -> Unit,
    onOpenExternalRoute: (appUri: String, webUri: String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onRequestBackgroundLocation: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var activeDialog by remember { mutableStateOf<DrawerItem?>(null) }
    var measurementInputPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var settingsVisible by remember { mutableStateOf(false) }

    BackHandler(enabled = settingsVisible) {
        settingsVisible = false
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                is UiEvent.ShareText -> onShareText(event.text, event.chooserTitle, event.clipLabel)
                is UiEvent.CopyText -> {
                    onCopyText(event.label, event.text)
                    snackbarHostState.showSnackbar(
                        if (event.label == "Координаты") "Координаты скопированы" else "Скопировано"
                    )
                }
                is UiEvent.OpenUrl -> onOpenUrl(event.url)
                is UiEvent.OpenExternalRoute -> onOpenExternalRoute(event.appUri, event.webUri)
                UiEvent.ShowUpdateBanner -> settingsVisible = false
                UiEvent.RequestBackgroundLocationPermission -> onRequestBackgroundLocation()
                UiEvent.RequestNotificationPermission -> Unit
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            SectorDrawer(
                onClose = { scope.launch { drawerState.close() } }
            ) { item ->
                scope.launch {
                    drawerState.close()
                    when (item) {
                        DrawerItem.SHARE_GPS -> {
                            activeDialog = null
                            measurementInputPoint = null
                            settingsVisible = false
                            viewModel.shareCurrentLocation()
                        }
                        DrawerItem.EXPORT -> {
                            measurementInputPoint = null
                            viewModel.requestExport()
                        }
                        DrawerItem.SETTINGS -> {
                            activeDialog = null
                            measurementInputPoint = null
                            settingsVisible = true
                        }
                        DrawerItem.INPUT -> {
                            settingsVisible = false
                            measurementInputPoint = null
                            activeDialog = item
                        }
                        else -> {
                            settingsVisible = false
                            measurementInputPoint = null
                            activeDialog = item
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent
        ) {
            Box(Modifier.fillMaxSize()) {
                if (settingsVisible) {
                    SettingsScreen(
                        settings = state.settings,
                        onDismiss = { settingsVisible = false },
                        onOwnPointColor = viewModel::setOwnPointColor,
                        onGpsPointScale = viewModel::setGpsPointScale,
                        onDestinationMarkerType = viewModel::setDestinationMarkerType,
                        onGpsMode = viewModel::setGpsMode,
                        onActiveSearch = viewModel::requestActiveSearch,
                        onAccuracyWarning = viewModel::setAccuracyWarningMeters,
                        onShowSelfCallsign = viewModel::setShowSelfCallsign,
                        onShowImportedCallsigns = viewModel::setShowImportedCallsigns,
                        onShowMapNotes = viewModel::setShowMapNotes,
                        onShowMapNoteTitles = viewModel::setShowMapNoteTitles,
                        onCallsignBehavior = viewModel::setCallsignBehavior,
                        onUpdateChecks = viewModel::setUpdateChecksEnabled,
                        onTelemetryEnabled = viewModel::setTelemetryEnabled,
                        onResetTelemetryInstallId = viewModel::resetTelemetryInstallId,
                        onCheckUpdates = { viewModel.checkUpdates(silent = false) }
                    )
                } else {
                    YandexMapComposable(
                        mapKitState = state.mapKitState,
                        locationState = state.locationState,
                        measurements = state.measurements,
                        importedLocations = state.importedLocations,
                        mapNotes = state.mapNotes,
                        intersection = state.intersection,
                        destination = state.destination,
                        routePolyline = state.routePolyline,
                        activeRouteBuilt = state.activeRouteBuilt,
                        routeFocusPolyline = state.routeFocusPolyline,
                        routeFocusNonce = state.routeFocusNonce,
                        cameraFocus = state.cameraFocus,
                        cameraFocusNonce = state.cameraFocusNonce,
                        cameraFocusPreserveZoom = state.cameraFocusPreserveZoom,
                        displaySettings = state.mapDisplaySettings,
                        onLongTap = viewModel::setDestination,
                        onTargetTap = viewModel::onMapTargetTap,
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                    )

                    MapOverlays(
                        location = state.locationState,
                        updateStatus = state.updateStatus,
                        onMenuClick = {
                            scope.launch {
                                if (drawerState.isOpen) {
                                    drawerState.close()
                                } else {
                                    drawerState.open()
                                }
                            }
                        },
                        onGpsClick = viewModel::focusCurrentLocation,
                        showRoutePanel = state.routePanelVisible,
                        onRouteGpsClick = viewModel::focusCurrentLocation,
                        onRouteFitClick = viewModel::focusActiveRoute,
                        onRouteShareClick = viewModel::shareCurrentLocation,
                        onRouteDeleteClick = viewModel::deleteDestination,
                        onUpdateToggle = viewModel::toggleUpdateBanner,
                        onInstallUpdate = viewModel::installUpdate,
                        onOpenUpdateLink = viewModel::openUpdateApkUrl,
                        onHideUpdate = viewModel::hideUpdateBanner
                    )
                }
            }
        }
    }

    MainDialogHost(
        activeDialog = activeDialog,
        state = state,
        measurementInputPoint = measurementInputPoint,
        onDismissActiveDialog = {
            activeDialog = null
            measurementInputPoint = null
        },
        onSaveCallsign = { viewModel.saveCallsign(it) },
        onSaveMeasurement = viewModel::saveMeasurement,
        onImportMeasurement = viewModel::importMeasurement,
        onDeleteMeasurement = viewModel::deleteMeasurement,
        onClearMeasurements = viewModel::clearMeasurements,
        onCopyMeasurementCoordinates = viewModel::copyMeasurementCoordinates,
        onFocusMeasurement = viewModel::focusMeasurement,
        onAcceptFirstStart = viewModel::acceptFirstStart,
        onConfirmExportWarning = viewModel::confirmExportWarning,
        onDismissExportWarning = viewModel::dismissExportWarning,
        onDismissExportMeasurementSelection = viewModel::dismissExportMeasurementSelection,
        onSendAllExportMeasurements = viewModel::sendAllExportMeasurements,
        onSendSelectedExportMeasurements = viewModel::sendSelectedExportMeasurements,
        onConfirmBackgroundRationale = viewModel::confirmBackgroundRationale,
        onDismissBackgroundRationale = viewModel::dismissBackgroundRationale,
        onDismissCallsignPrompt = viewModel::dismissCallsignPrompt,
        onSaveCallsignForExport = { viewModel.saveCallsign(it, continueExport = true) },
        onShowChangelog = viewModel::showChangelog,
        onDismissChangelog = viewModel::dismissChangelog,
        onSelectTarget = viewModel::selectTarget,
        onBuildInAppRouteToSelectedTarget = viewModel::buildInAppRouteToSelectedTarget,
        onOpenExternalRouteToSelectedTarget = viewModel::openExternalRouteToSelectedTarget,
        onAddNoteForSelectedTarget = {
            state.selectedTarget?.point?.let(viewModel::openNewNote)
        },
        onSetAzimuthForSelectedTarget = {
            state.selectedTarget?.point?.let { point ->
                measurementInputPoint = point
                viewModel.selectTarget(null)
                activeDialog = DrawerItem.INPUT
            }
        },
        onCopySelectedTargetCoordinates = viewModel::copySelectedTargetCoordinates,
        onDeleteDestination = viewModel::deleteDestination,
        onNoteTitleChange = viewModel::updateNoteTitle,
        onNoteTextChange = viewModel::updateNoteText,
        onNotePhotoPicked = viewModel::addNotePhoto,
        onPrepareNoteCameraCapture = viewModel::prepareNoteCameraCapture,
        onNoteCameraCaptureResult = viewModel::onNoteCameraCaptureResult,
        onNoteAudioRecorded = viewModel::addNoteAudio,
        onRemoveNoteAttachment = viewModel::removeNoteAttachment,
        onSaveNote = viewModel::saveOpenNote,
        onDismissNote = viewModel::dismissOpenNote,
        onDeleteNote = viewModel::deleteOpenNote,
        onShowNoteMessage = { message ->
            scope.launch { snackbarHostState.showSnackbar(message) }
        }
    )
}
