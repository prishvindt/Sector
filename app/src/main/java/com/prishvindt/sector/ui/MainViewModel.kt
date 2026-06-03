package com.prishvindt.sector.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.prishvindt.sector.BuildConfig
import com.prishvindt.sector.SectorApplication
import com.prishvindt.sector.data.CallsignBehavior
import com.prishvindt.sector.data.DestinationMarkerType
import com.prishvindt.sector.data.GpsMode
import com.prishvindt.sector.data.ImportedLocationRepository
import com.prishvindt.sector.data.Measurement
import com.prishvindt.sector.data.MeasurementRepository
import com.prishvindt.sector.data.MeasurementSource
import com.prishvindt.sector.data.OwnPointColor
import com.prishvindt.sector.data.RouteMode
import com.prishvindt.sector.data.RouteType
import com.prishvindt.sector.data.SettingsRepository
import com.prishvindt.sector.domain.GeoPoint
import com.prishvindt.sector.domain.IntersectionTargetCalculator
import com.prishvindt.sector.domain.ExportFormat
import com.prishvindt.sector.domain.LocationExchangeFormat
import com.prishvindt.sector.domain.RouteTarget
import com.prishvindt.sector.domain.locations.CurrentLocationShareInput
import com.prishvindt.sector.domain.locations.LocationShareManager
import com.prishvindt.sector.domain.measurements.MeasurementImportResult
import com.prishvindt.sector.domain.measurements.MeasurementManager
import com.prishvindt.sector.domain.measurements.SelfMeasurementInput
import com.prishvindt.sector.domain.routes.RouteTargetManager
import com.prishvindt.sector.location.ActiveSearchService
import com.prishvindt.sector.location.LocationTracker
import com.prishvindt.sector.map.RoutePlanner
import com.prishvindt.sector.ui.common.MainUiState
import com.prishvindt.sector.ui.common.UiEvent
import com.prishvindt.sector.updates.UpdateCoordinator
import com.prishvindt.sector.updates.UpdateCoordinatorEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(
    application: Application,
    private val measurementRepository: MeasurementRepository,
    private val importedLocationRepository: ImportedLocationRepository,
    private val measurementManager: MeasurementManager,
    private val locationShareManager: LocationShareManager,
    private val settingsRepository: SettingsRepository,
    private val locationTracker: LocationTracker,
    private val routePlanner: RoutePlanner,
    private val updateCoordinator: UpdateCoordinator
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var lastGpsMode: GpsMode? = null
    private var pendingExport = false

    init {
        viewModelScope.launch {
            updateCoordinator.status.collect { updateStatus ->
                _uiState.update { it.copy(updateStatus = updateStatus) }
            }
        }
        viewModelScope.launch {
            updateCoordinator.events.collect { event ->
                when (event) {
                    is UpdateCoordinatorEvent.ShowMessage -> _events.send(UiEvent.ShowMessage(event.message))
                    is UpdateCoordinatorEvent.CopyText -> _events.send(UiEvent.CopyText(event.label, event.text))
                    is UpdateCoordinatorEvent.OpenUrl -> _events.send(UiEvent.OpenUrl(event.url))
                    UpdateCoordinatorEvent.ShowUpdateBanner -> _events.send(UiEvent.ShowUpdateBanner)
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update {
                    it.copy(
                        settings = settings,
                        showFirstStartDialog = !settings.firstStartAccepted,
                        showChangelogDialog = it.showChangelogDialog ||
                            BuildConfig.VERSION_CODE > settings.lastSeenChangelogVersionCode,
                        intersection = IntersectionTargetCalculator.calculate(it.measurements, it.locationState.point)
                    )
                }
                if (lastGpsMode != settings.gpsMode) {
                    lastGpsMode = settings.gpsMode
                    locationTracker.start(settings.gpsMode)
                }
                viewModelScope.launch { updateCoordinator.checkOnceIfEnabled(settings.updateChecksEnabled) }
            }
        }
        viewModelScope.launch {
            measurementRepository.observeAll().collect { measurements ->
                _uiState.update {
                    it.copy(
                        measurements = measurements,
                        intersection = IntersectionTargetCalculator.calculate(measurements, it.locationState.point)
                    )
                }
            }
        }
        viewModelScope.launch {
            importedLocationRepository.observeAll().collect { locations ->
                _uiState.update { it.copy(importedLocations = locations) }
            }
        }
        viewModelScope.launch {
            locationTracker.state.collect { location ->
                _uiState.update {
                    it.copy(
                        locationState = location,
                        intersection = IntersectionTargetCalculator.calculate(it.measurements, location.point)
                    )
                }
            }
        }
        viewModelScope.launch {
            SectorApplication.mapKitState.collect { mapKit ->
                _uiState.update { it.copy(mapKitState = mapKit) }
            }
        }
    }

    override fun onCleared() {
        locationTracker.stop()
        super.onCleared()
    }

    fun refreshLocationTracking() {
        locationTracker.start(_uiState.value.settings.gpsMode)
    }

    fun acceptFirstStart() {
        viewModelScope.launch { settingsRepository.acceptFirstStart() }
    }

    fun showChangelog() {
        _uiState.update { it.copy(showChangelogDialog = true) }
    }

    fun dismissChangelog() {
        viewModelScope.launch {
            settingsRepository.setLastSeenChangelogVersionCode(BuildConfig.VERSION_CODE)
            _uiState.update { it.copy(showChangelogDialog = false) }
        }
    }

    fun saveCallsign(value: String, continueExport: Boolean = false) {
        viewModelScope.launch {
            val trimmed = value.trim()
            settingsRepository.setCallsign(trimmed)
            _uiState.update {
                it.copy(
                    settings = it.settings.copy(callsign = trimmed),
                    callsignPromptForExport = false
                )
            }
            if (continueExport || pendingExport) {
                pendingExport = false
                proceedWithExportRequest()
            }
        }
    }

    fun dismissCallsignPrompt() {
        pendingExport = false
        _uiState.update { it.copy(callsignPromptForExport = false) }
    }

    fun saveMeasurement(
        callsignText: String,
        azimuthText: String,
        errorText: String,
        signalText: String,
        sourcePoint: GeoPoint? = null
    ) {
        viewModelScope.launch {
            val location = _uiState.value.locationState
            val point = sourcePoint ?: location.point
            if (point == null) {
                showMessage("GPS ещё не найден")
                return@launch
            }
            val useCurrentGpsMetadata = sourcePoint == null
            val settings = _uiState.value.settings
            measurementManager.saveSelfMeasurement(
                SelfMeasurementInput(
                    point = point,
                    accuracyMeters = if (useCurrentGpsMetadata) location.accuracyMeters else null,
                    satelliteCount = if (useCurrentGpsMetadata) location.satelliteCount else null,
                    callsign = callsignText.trim(),
                    azimuthText = azimuthText,
                    errorText = errorText,
                    signalText = signalText,
                    accuracyWarningMeters = settings.accuracyWarningMeters
                )
            ).onSuccess { result ->
                if (result.showAccuracyWarning) {
                    showMessage("Точность хуже ${settings.accuracyWarningMeters.toInt()} м")
                }
            }.onFailure {
                showMessage(it.message ?: "Ошибка сохранения замера")
            }
        }
    }

    fun importMeasurement(text: String) {
        viewModelScope.launch {
            val hasMeasurements = ExportFormat.hasMeasurementText(text)
            val hasLocation = LocationExchangeFormat.containsLocationText(text)
            if (!hasMeasurements && !hasLocation) {
                measurementManager.importMeasurements(text)
                    .onSuccess { showMessage(it.importSummary()) }
                    .onFailure { showMessage(it.message ?: "Ошибка импорта") }
                return@launch
            }

            val messages = mutableListOf<String>()
            if (hasMeasurements) {
                measurementManager.importMeasurements(text)
                    .onSuccess { messages += it.importSummary() }
                    .onFailure { messages += (it.message ?: "Ошибка импорта лучей") }
            }
            if (hasLocation) {
                locationShareManager.importLocation(text)
                    .onSuccess { messages += "GPS-точка импортирована" }
                    .onFailure { messages += (it.message ?: "Ошибка импорта GPS") }
            }
            showMessage(messages.joinToString("; "))
        }
    }

    fun shareCurrentLocation() {
        val state = _uiState.value
        val point = state.locationState.point
        if (point == null) {
            showMessage("gps-точка ещё не найдена")
            return
        }
        locationShareManager.formatCurrentLocation(
            CurrentLocationShareInput(
                point = point,
                callsign = state.settings.callsign,
                accuracyMeters = state.locationState.accuracyMeters
            )
        ).onSuccess { text ->
            viewModelScope.launch {
                _events.send(
                    UiEvent.ShareText(
                        text = text,
                        chooserTitle = "Поделиться GPS",
                        clipLabel = "GPS Сектор"
                    )
                )
            }
        }.onFailure {
            showMessage(it.message ?: "Ошибка экспорта GPS")
        }
    }

    fun requestExport() {
        viewModelScope.launch {
            proceedWithExportRequest()
        }
    }

    fun confirmExportWarning() {
        viewModelScope.launch {
            settingsRepository.acceptExportWarning()
            _uiState.update {
                it.copy(
                    settings = it.settings.copy(exportWarningAccepted = true),
                    showExportWarning = false
                )
            }
            if (pendingExport) {
                pendingExport = false
                proceedWithExportRequest()
            }
        }
    }

    fun dismissExportWarning() {
        pendingExport = false
        _uiState.update { it.copy(showExportWarning = false) }
    }

    fun dismissExportMeasurementSelection() {
        _uiState.update { it.copy(showExportMeasurementSelection = false) }
    }

    fun sendAllExportMeasurements() {
        viewModelScope.launch {
            shareExportMeasurements(_uiState.value.exportableMeasurements)
        }
    }

    fun sendSelectedExportMeasurements(ids: Set<String>) {
        viewModelScope.launch {
            val selected = _uiState.value.exportableMeasurements
                .filter { it.measurementId in ids }
            shareExportMeasurements(selected)
        }
    }

    private suspend fun proceedWithExportRequest() {
        val state = _uiState.value
        val exportable = state.exportableMeasurements
        when {
            exportable.isEmpty() -> showMessage("Нет азимутных лучей для экспорта")
            exportable.any { it.source == MeasurementSource.SELF } && state.settings.callsign.isBlank() -> {
                pendingExport = true
                _uiState.update { it.copy(callsignPromptForExport = true) }
            }
            !state.settings.exportWarningAccepted -> {
                pendingExport = true
                _uiState.update { it.copy(showExportWarning = true) }
            }
            exportable.size == 1 -> shareExportMeasurements(exportable)
            else -> _uiState.update { it.copy(showExportMeasurementSelection = true) }
        }
    }

    private suspend fun shareExportMeasurements(measurements: List<Measurement>) {
        val state = _uiState.value
        measurementManager.exportMeasurements(
            measurements = measurements,
            callsign = state.settings.callsign,
            ownColorArgb = state.settings.ownPointColor.colorArgb
        )
            .onSuccess { text ->
                _uiState.update { it.copy(showExportMeasurementSelection = false) }
                _events.send(UiEvent.ShareText(text))
            }
            .onFailure {
                showMessage(it.message ?: "Ошибка экспорта")
            }
    }

    fun deleteMeasurement(measurement: Measurement) {
        viewModelScope.launch { measurementManager.delete(measurement) }
    }

    fun clearMeasurements() {
        viewModelScope.launch { measurementManager.clear() }
    }

    fun copyMeasurementCoordinates(measurement: Measurement) {
        copyCoordinates(GeoPoint(measurement.latitude, measurement.longitude))
    }

    fun focusMeasurement(measurement: Measurement) {
        focusPoint(GeoPoint(measurement.latitude, measurement.longitude))
    }

    fun setDestination(point: GeoPoint) {
        _uiState.update {
            it.copy(
                destination = point,
                selectedTarget = RouteTargetManager.destination(point),
                routePolyline = emptyList(),
                activeRouteBuilt = false
            )
        }
    }

    fun deleteDestination() {
        _uiState.update {
            it.copy(
                destination = null,
                selectedTarget = null,
                routePolyline = emptyList(),
                activeRouteBuilt = false,
                routeFocusPolyline = emptyList()
            )
        }
    }

    fun selectTarget(target: RouteTarget?) {
        _uiState.update { it.copy(selectedTarget = target) }
    }

    fun copySelectedTargetCoordinates() {
        _uiState.value.selectedTarget?.let { copyCoordinates(it.point) }
    }

    fun buildInAppRouteToSelectedTarget() {
        val endpoints = RouteTargetManager.routeEndpoints(
            start = _uiState.value.locationState.point,
            target = _uiState.value.selectedTarget
        ).getOrElse {
            showMessage(it.message ?: "GPS ещё не найден")
            return
        }
        viewModelScope.launch {
            routePlanner.buildRoute(endpoints.start, endpoints.target.point, RouteType.CAR)
                .onSuccess { route ->
                    _uiState.update {
                        it.copy(
                            routePolyline = route,
                            activeRouteBuilt = true,
                            selectedTarget = null
                        )
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            routePolyline = listOf(endpoints.start, endpoints.target.point),
                            activeRouteBuilt = false
                        )
                    }
                    showMessage("Маршрут не построился. Показан ориентир, можно открыть Яндекс.Карты.")
                }
        }
    }

    fun openExternalRouteToSelectedTarget() {
        val endpoints = RouteTargetManager.routeEndpoints(
            start = _uiState.value.locationState.point,
            target = _uiState.value.selectedTarget
        ).getOrElse {
            showMessage(it.message ?: "GPS ещё не найден")
            return
        }
        val links = RouteTargetManager.externalRouteLinks(
            start = endpoints.start,
            target = endpoints.target,
            routeType = RouteType.CAR
        )
        _uiState.update { it.copy(selectedTarget = null) }
        viewModelScope.launch {
            _events.send(
                UiEvent.OpenExternalRoute(
                    appUri = links.appUri,
                    webUri = links.webUri
                )
            )
        }
    }

    fun requestActiveSearch(enabled: Boolean) {
        if (!enabled) {
            setActiveSearchEnabled(false)
            return
        }
        _uiState.update { it.copy(showBackgroundRationale = true) }
    }

    fun confirmBackgroundRationale() {
        _uiState.update { it.copy(showBackgroundRationale = false) }
        viewModelScope.launch { _events.send(UiEvent.RequestBackgroundLocationPermission) }
    }

    fun dismissBackgroundRationale() {
        _uiState.update { it.copy(showBackgroundRationale = false) }
    }

    fun onBackgroundPermissionResult(granted: Boolean) {
        if (granted) {
            setActiveSearchEnabled(true)
        } else {
            showMessage("Активный поиск требует разрешение геолокации 'Всегда'")
        }
    }

    private fun setActiveSearchEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setActiveSearchEnabled(enabled)
            if (enabled) {
                ActiveSearchService.start(getApplication(), _uiState.value.settings.gpsMode)
            } else {
                ActiveSearchService.stop(getApplication())
            }
        }
    }

    fun setOwnPointColor(value: OwnPointColor) = viewModelScope.launch { settingsRepository.setOwnPointColor(value) }
    fun setGpsPointScale(value: Float) = viewModelScope.launch { settingsRepository.setGpsPointScale(value) }
    fun setDestinationMarkerType(value: DestinationMarkerType) =
        viewModelScope.launch { settingsRepository.setDestinationMarkerType(value) }
    fun setGpsMode(value: GpsMode) = viewModelScope.launch { settingsRepository.setGpsMode(value) }
    fun setAccuracyWarningMeters(value: String) = viewModelScope.launch {
        value.toDoubleOrNull()?.let { settingsRepository.setAccuracyWarningMeters(it) }
    }
    fun setShowSelfCallsign(value: Boolean) = viewModelScope.launch { settingsRepository.setShowSelfCallsign(value) }
    fun setShowImportedCallsigns(value: Boolean) = viewModelScope.launch { settingsRepository.setShowImportedCallsigns(value) }
    fun setCallsignBehavior(value: CallsignBehavior) = viewModelScope.launch { settingsRepository.setCallsignBehavior(value) }
    fun setRouteMode(value: RouteMode) = viewModelScope.launch { settingsRepository.setRouteMode(value) }
    fun setRouteType(value: RouteType) = viewModelScope.launch { settingsRepository.setRouteType(value) }
    fun setUpdateChecksEnabled(value: Boolean) = viewModelScope.launch { settingsRepository.setUpdateChecksEnabled(value) }

    fun checkUpdates(silent: Boolean = false) {
        viewModelScope.launch { updateCoordinator.checkUpdates(silent) }
    }

    fun toggleUpdateBanner() {
        updateCoordinator.toggleBanner()
    }

    fun hideUpdateBanner() {
        updateCoordinator.hideBanner()
    }

    fun installUpdate() {
        viewModelScope.launch { updateCoordinator.installUpdate() }
    }

    fun copyUpdateApkUrl() {
        viewModelScope.launch { updateCoordinator.copyUpdateApkUrl() }
    }

    fun openUpdateApkUrl() {
        viewModelScope.launch { updateCoordinator.openUpdateApkUrl() }
    }

    fun focusPoint(point: GeoPoint) {
        _uiState.update {
            it.copy(
                cameraFocus = point,
                cameraFocusNonce = it.cameraFocusNonce + 1,
                cameraFocusPreserveZoom = false,
                selectedTarget = null
            )
        }
    }

    fun focusCurrentLocation() {
        val point = _uiState.value.locationState.point
        if (point == null) {
            showMessage("gps-точка ещё не найдена")
            return
        }
        _uiState.update {
            it.copy(
                cameraFocus = point,
                cameraFocusNonce = it.cameraFocusNonce + 1,
                cameraFocusPreserveZoom = true,
                selectedTarget = null
            )
        }
    }

    fun focusActiveRoute() {
        val state = _uiState.value
        val currentPoint = state.locationState.point
        val destination = state.destination
        if (currentPoint == null) {
            showMessage("gps-точка ещё не найдена")
            return
        }
        if (destination == null || state.routePolyline.size < 2) {
            focusPoint(destination ?: currentPoint)
            return
        }
        val remainingRoute = remainingRoutePolyline(
            currentPoint = currentPoint,
            destination = destination,
            routePolyline = state.routePolyline
        )
        _uiState.update {
            it.copy(
                routeFocusPolyline = remainingRoute,
                routeFocusNonce = it.routeFocusNonce + 1,
                selectedTarget = null
            )
        }
    }

    private fun copyCoordinates(point: GeoPoint) {
        val text = String.format(java.util.Locale.US, "%.6f, %.6f", point.latitude, point.longitude)
        viewModelScope.launch { _events.send(UiEvent.CopyText("Координаты", text)) }
    }

    private fun remainingRoutePolyline(
        currentPoint: GeoPoint,
        destination: GeoPoint,
        routePolyline: List<GeoPoint>
    ): List<GeoPoint> {
        if (routePolyline.size < 2) return listOf(currentPoint, destination)
        val segmentIndex = nearestSegmentIndex(currentPoint, routePolyline)
        val routeTail = routePolyline.drop(segmentIndex + 1)
        return (listOf(currentPoint) + routeTail + destination)
            .distinctAdjacent()
            .takeIf { it.size >= 2 }
            ?: listOf(currentPoint, destination)
    }

    private fun nearestSegmentIndex(point: GeoPoint, polyline: List<GeoPoint>): Int {
        var bestIndex = 0
        var bestDistance = Double.MAX_VALUE
        for (index in 0 until polyline.lastIndex) {
            val distance = distanceToSegmentSquared(point, polyline[index], polyline[index + 1])
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = index
            }
        }
        return bestIndex
    }

    private fun distanceToSegmentSquared(point: GeoPoint, start: GeoPoint, end: GeoPoint): Double {
        val x = point.longitude
        val y = point.latitude
        val x1 = start.longitude
        val y1 = start.latitude
        val x2 = end.longitude
        val y2 = end.latitude
        val dx = x2 - x1
        val dy = y2 - y1
        if (dx == 0.0 && dy == 0.0) {
            val px = x - x1
            val py = y - y1
            return px * px + py * py
        }
        val t = (((x - x1) * dx + (y - y1) * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
        val projectedX = x1 + t * dx
        val projectedY = y1 + t * dy
        val px = x - projectedX
        val py = y - projectedY
        return px * px + py * py
    }

    private fun List<GeoPoint>.distinctAdjacent(): List<GeoPoint> {
        val result = mutableListOf<GeoPoint>()
        forEach { point ->
            if (result.lastOrNull() != point) {
                result += point
            }
        }
        return result
    }

    private fun showMessage(message: String) {
        viewModelScope.launch { _events.send(UiEvent.ShowMessage(message)) }
    }

    companion object {
        fun factory(application: SectorApplication): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val container = application.appContainer
                    return MainViewModel(
                        application = application,
                        measurementRepository = container.measurementRepository,
                        importedLocationRepository = container.importedLocationRepository,
                        measurementManager = container.measurementManager,
                        locationShareManager = container.locationShareManager,
                        settingsRepository = container.settingsRepository,
                        locationTracker = container.locationTracker,
                        routePlanner = container.routePlanner,
                        updateCoordinator = UpdateCoordinator(
                            updateChecker = container.updateChecker,
                            updateInstaller = container.updateInstaller
                        )
                    ) as T
                }
            }
        }
    }
}

private fun MeasurementImportResult.importSummary(): String {
    val base = if (imported.size == 1 && skippedBlocks == 0) {
        "Замер импортирован"
    } else {
        "Импортировано лучей: ${imported.size}"
    }
    return if (skippedBlocks > 0) {
        "$base, пропущено блоков: $skippedBlocks"
    } else {
        base
    }
}
