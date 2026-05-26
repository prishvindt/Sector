package com.prishvindt.sector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.prishvindt.sector.domain.GeoPoint
import com.prishvindt.sector.domain.RouteTargetType
import com.prishvindt.sector.map.YandexMapComposable
import com.prishvindt.sector.ui.about.AboutScreen
import com.prishvindt.sector.ui.callsign.CallsignDialog
import com.prishvindt.sector.ui.common.BackgroundLocationRationaleDialog
import com.prishvindt.sector.ui.common.DestinationTargetBottomSheet
import com.prishvindt.sector.ui.common.DrawerItem
import com.prishvindt.sector.ui.common.ExportWarningDialog
import com.prishvindt.sector.ui.common.TargetMenuDialog
import com.prishvindt.sector.ui.common.UiEvent
import com.prishvindt.sector.ui.drawer.SectorDrawer
import com.prishvindt.sector.ui.firststart.FirstStartDialog
import com.prishvindt.sector.ui.importdata.ImportDialog
import com.prishvindt.sector.ui.input.MeasurementInputDialog
import com.prishvindt.sector.ui.measurements.MeasurementsScreen
import com.prishvindt.sector.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onShareText: (String) -> Unit,
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

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                is UiEvent.ShareText -> onShareText(event.text)
                is UiEvent.CopyText -> {
                    onCopyText(event.label, event.text)
                    snackbarHostState.showSnackbar(
                        if (event.label == "Координаты") "Координаты скопированы" else "Скопировано"
                    )
                }
                is UiEvent.OpenExternalRoute -> onOpenExternalRoute(event.appUri, event.webUri)
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
                scope.launch { drawerState.close() }
                when (item) {
                    DrawerItem.EXPORT -> viewModel.requestExport()
                    else -> activeDialog = item
                }
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent
        ) { padding ->
            Box(Modifier.fillMaxSize()) {
                YandexMapComposable(
                    mapKitState = state.mapKitState,
                    locationState = state.locationState,
                    measurements = state.measurements,
                    intersection = state.intersection,
                    destination = state.destination,
                    routePolyline = state.routePolyline,
                    cameraFocus = state.cameraFocus,
                    cameraFocusNonce = state.cameraFocusNonce,
                    displaySettings = state.mapDisplaySettings,
                    onLongTap = viewModel::setDestination,
                    onTargetTap = viewModel::selectTarget,
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                )

                Surface(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(start = 12.dp, top = 12.dp)
                        .size(48.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    tonalElevation = 2.dp
                ) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                if (drawerState.isOpen) {
                                    drawerState.close()
                                } else {
                                    drawerState.open()
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Menu, contentDescription = "Меню")
                    }
                }

                UpdateBanner(
                    expanded = state.updateStatus.expanded,
                    latestVersion = state.updateStatus.updateInfo?.latestVersion,
                    changelog = state.updateStatus.updateInfo?.changelog.orEmpty(),
                    apkUrl = state.updateStatus.updateInfo?.apkUrl,
                    onToggle = viewModel::toggleUpdateBanner,
                    onDownload = { state.updateStatus.updateInfo?.apkUrl?.let(onOpenUrl) },
                    onHide = viewModel::hideUpdateBanner
                )

                GpsStatusPanel(
                    location = state.locationState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                )
            }
        }
    }

    when (activeDialog) {
        DrawerItem.CALLSIGN -> CallsignDialog(
            initialValue = state.settings.callsign,
            onDismiss = { activeDialog = null },
            onSave = {
                viewModel.saveCallsign(it)
                activeDialog = null
            }
        )
        DrawerItem.INPUT -> MeasurementInputDialog(
            onDismiss = { activeDialog = null },
            onSave = { azimuth, error, signal ->
                viewModel.saveMeasurement(azimuth, error, signal)
                activeDialog = null
            }
        )
        DrawerItem.IMPORT -> ImportDialog(
            onDismiss = { activeDialog = null },
            onSave = {
                viewModel.importMeasurement(it)
                activeDialog = null
            }
        )
        DrawerItem.MEASUREMENTS -> MeasurementsScreen(
            measurements = state.measurements,
            currentPosition = state.locationState.point,
            onDismiss = { activeDialog = null },
            onDelete = viewModel::deleteMeasurement,
            onClearAll = viewModel::clearMeasurements,
            onCopyCoordinates = viewModel::copyMeasurementCoordinates,
            onCenter = {
                viewModel.focusMeasurement(it)
                activeDialog = null
            }
        )
        DrawerItem.SETTINGS -> SettingsScreen(
            settings = state.settings,
            onDismiss = { activeDialog = null },
            onOwnPointColor = viewModel::setOwnPointColor,
            onGpsMode = viewModel::setGpsMode,
            onActiveSearch = viewModel::requestActiveSearch,
            onAccuracyWarning = viewModel::setAccuracyWarningMeters,
            onShowSelfCallsign = viewModel::setShowSelfCallsign,
            onShowImportedCallsigns = viewModel::setShowImportedCallsigns,
            onCallsignBehavior = viewModel::setCallsignBehavior,
            onRouteMode = viewModel::setRouteMode,
            onRouteType = viewModel::setRouteType,
            onUpdateChecks = viewModel::setUpdateChecksEnabled,
            onCheckUpdates = { viewModel.checkUpdates(silent = false) }
        )
        DrawerItem.ABOUT -> AboutScreen(onDismiss = { activeDialog = null })
        DrawerItem.EXPORT, null -> Unit
    }

    if (state.showFirstStartDialog) {
        FirstStartDialog(onConfirm = viewModel::acceptFirstStart)
    }
    if (state.showExportWarning) {
        ExportWarningDialog(
            onConfirm = viewModel::confirmExportWarning,
            onDismiss = viewModel::dismissExportWarning
        )
    }
    if (state.showBackgroundRationale) {
        BackgroundLocationRationaleDialog(
            onConfirm = viewModel::confirmBackgroundRationale,
            onDismiss = viewModel::dismissBackgroundRationale
        )
    }
    if (state.callsignPromptForExport) {
        CallsignDialog(
            initialValue = state.settings.callsign,
            title = "Введите позывной",
            onDismiss = viewModel::dismissCallsignPrompt,
            onSave = { viewModel.saveCallsign(it, continueExport = true) }
        )
    }
    state.selectedTarget?.let { target ->
        if (target.type == RouteTargetType.DESTINATION) {
            DestinationTargetBottomSheet(
                target = target,
                currentPosition = state.locationState.point,
                onDismiss = { viewModel.selectTarget(null) },
                onInAppRoute = viewModel::buildInAppRouteToSelectedTarget,
                onExternalRoute = viewModel::openExternalRouteToSelectedTarget,
                onCopyCoordinates = viewModel::copySelectedTargetCoordinates,
                onDeleteDestination = viewModel::deleteDestination
            )
        } else {
            TargetMenuDialog(
                target = target,
                onDismiss = { viewModel.selectTarget(null) },
                onInAppRoute = viewModel::buildInAppRouteToSelectedTarget,
                onExternalRoute = viewModel::openExternalRouteToSelectedTarget,
                onCopyCoordinates = {
                    viewModel.copySelectedTargetCoordinates()
                    viewModel.selectTarget(null)
                },
                onDeleteDestination = null
            )
        }
    }
}

@Composable
private fun GpsStatusPanel(
    location: com.prishvindt.sector.location.LocationState,
    modifier: Modifier = Modifier
) {
    val point = location.point
    val text = if (point == null) {
        "Поиск GPS...\nКоординаты: —\nТочность: —\nСпутники: н/д"
    } else {
        "GPS активен\n" +
            "${point.latitude.formatCoord()}, ${point.longitude.formatCoord()}\n" +
            "Точность: ±${location.accuracyMeters?.toInt() ?: 0} м\n" +
            "Спутники: ${location.satelliteCount?.toString() ?: "н/д"}"
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFF0B1F16).copy(alpha = 0.78f)
    ) {
        Text(
            text = text,
            color = Color(0xFF39D98A),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun UpdateBanner(
    expanded: Boolean,
    latestVersion: String?,
    changelog: List<String>,
    apkUrl: String?,
    onToggle: () -> Unit,
    onDownload: () -> Unit,
    onHide: () -> Unit
) {
    if (latestVersion == null || apkUrl == null) return
    Surface(
        modifier = Modifier
            .statusBarsPadding()
            .padding(top = 12.dp, start = 76.dp, end = 12.dp)
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
        tonalElevation = 2.dp
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Доступна версия $latestVersion", style = MaterialTheme.typography.titleSmall)
            if (expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    changelog.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    Row {
                        TextButton(onClick = onDownload) { Text("Скачать") }
                        TextButton(onClick = onHide) { Text("Скрыть") }
                    }
                }
            }
        }
    }
}

private fun Double.formatCoord(): String = String.format(java.util.Locale.US, "%.6f", this)
