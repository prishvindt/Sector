package com.prishvindt.sector.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.prishvindt.sector.SectorApplication
import com.prishvindt.sector.data.CallsignBehavior
import com.prishvindt.sector.data.GpsMode
import com.prishvindt.sector.data.Measurement
import com.prishvindt.sector.data.MeasurementRepository
import com.prishvindt.sector.data.OwnPointColor
import com.prishvindt.sector.data.RouteMode
import com.prishvindt.sector.data.RouteType
import com.prishvindt.sector.data.SettingsRepository
import com.prishvindt.sector.domain.GeoPoint
import com.prishvindt.sector.domain.IntersectionTargetCalculator
import com.prishvindt.sector.domain.RouteTarget
import com.prishvindt.sector.domain.measurements.MeasurementManager
import com.prishvindt.sector.domain.measurements.SelfMeasurementInput
import com.prishvindt.sector.domain.routes.RouteTargetManager
import com.prishvindt.sector.location.ActiveSearchService
import com.prishvindt.sector.location.LocationTracker
import com.prishvindt.sector.map.RoutePlanner
import com.prishvindt.sector.ui.common.MainUiState
import com.prishvindt.sector.ui.common.UiEvent
import com.prishvindt.sector.updates.UpdateChecker
import com.prishvindt.sector.updates.UpdateStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(
    application: Application,
    private val measurementRepository: MeasurementRepository,
    private val measurementManager: MeasurementManager,
    private val settingsRepository: SettingsRepository,
    private val locationTracker: LocationTracker,
    private val routePlanner: RoutePlanner,
    private val updateChecker: UpdateChecker
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var updateCheckedOnce = false
    private var lastGpsMode: GpsMode? = null
    private var pendingExport = false

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update {
                    it.copy(
                        settings = settings,
                        showFirstStartDialog = !settings.firstStartAccepted,
                        intersection = IntersectionTargetCalculator.calculate(it.measurements, it.locationState.point)
                    )
                }
                if (lastGpsMode != settings.gpsMode) {
                    lastGpsMode = settings.gpsMode
                    locationTracker.start(settings.gpsMode)
                }
                if (settings.updateChecksEnabled && !updateCheckedOnce) {
                    updateCheckedOnce = true
                    checkUpdates(silent = true)
                }
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

    fun saveCallsign(value: String, continueExport: Boolean = false) {
        viewModelScope.launch {
            settingsRepository.setCallsign(value)
            _uiState.update { it.copy(callsignPromptForExport = false) }
            if (continueExport || pendingExport) {
                pendingExport = false
                exportLatestSelf()
            }
        }
    }

    fun dismissCallsignPrompt() {
        pendingExport = false
        _uiState.update { it.copy(callsignPromptForExport = false) }
    }

    fun saveMeasurement(azimuthText: String, errorText: String, signalText: String) {
        viewModelScope.launch {
            val location = _uiState.value.locationState
            val point = location.point
            if (point == null) {
                showMessage("GPS ещё не найден")
                return@launch
            }
            val settings = _uiState.value.settings
            measurementManager.saveSelfMeasurement(
                SelfMeasurementInput(
                    point = point,
                    accuracyMeters = location.accuracyMeters,
                    satelliteCount = location.satelliteCount,
                    callsign = settings.callsign,
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
            measurementManager.importMeasurement(text)
                .onSuccess { showMessage("Замер импортирован") }
                .onFailure { showMessage(it.message ?: "Ошибка импорта") }
        }
    }

    fun requestExport() {
        viewModelScope.launch {
            val latest = measurementManager.latestSelf()
            when {
                latest == null -> showMessage("Нет моего замера для экспорта")
                _uiState.value.settings.callsign.isBlank() -> {
                    pendingExport = true
                    _uiState.update { it.copy(callsignPromptForExport = true) }
                }
                !_uiState.value.settings.exportWarningAccepted -> {
                    pendingExport = true
                    _uiState.update { it.copy(showExportWarning = true) }
                }
                else -> exportLatestSelf()
            }
        }
    }

    fun confirmExportWarning() {
        viewModelScope.launch {
            settingsRepository.acceptExportWarning()
            _uiState.update { it.copy(showExportWarning = false) }
            if (pendingExport) {
                pendingExport = false
                exportLatestSelf()
            }
        }
    }

    fun dismissExportWarning() {
        pendingExport = false
        _uiState.update { it.copy(showExportWarning = false) }
    }

    private suspend fun exportLatestSelf() {
        val callsign = _uiState.value.settings.callsign
        if (callsign.isBlank()) {
            pendingExport = true
            _uiState.update { it.copy(callsignPromptForExport = true) }
            return
        }
        measurementManager.exportLatestSelf(callsign)
            .onSuccess { _events.send(UiEvent.ShareText(it)) }
            .onFailure { showMessage(it.message ?: "Ошибка экспорта") }
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
                routePolyline = emptyList()
            )
        }
    }

    fun deleteDestination() {
        _uiState.update { it.copy(destination = null, selectedTarget = null, routePolyline = emptyList()) }
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
            routePlanner.buildRoute(endpoints.start, endpoints.target.point, _uiState.value.settings.routeType)
                .onSuccess { route ->
                    _uiState.update {
                        it.copy(
                            routePolyline = route,
                            selectedTarget = null
                        )
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            routePolyline = listOf(endpoints.start, endpoints.target.point)
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
            routeType = _uiState.value.settings.routeType
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
        viewModelScope.launch {
            _uiState.update { it.copy(updateStatus = it.updateStatus.copy(isChecking = true, lastError = null)) }
            updateChecker.check()
                .onSuccess { info ->
                    _uiState.update {
                        it.copy(
                            updateStatus = UpdateStatus(
                                isChecking = false,
                                updateInfo = info?.takeUnless { update -> update.versionCode <= it.settings.hiddenUpdateVersionCode }
                            )
                        )
                    }
                    if (!silent && info == null) showMessage("Новых обновлений нет")
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(updateStatus = it.updateStatus.copy(isChecking = false, lastError = error.message))
                    }
                    if (!silent) showMessage("Не удалось проверить обновления")
                }
        }
    }

    fun toggleUpdateBanner() {
        _uiState.update {
            it.copy(updateStatus = it.updateStatus.copy(expanded = !it.updateStatus.expanded))
        }
    }

    fun hideUpdateBanner() {
        viewModelScope.launch {
            _uiState.value.updateStatus.updateInfo?.let { settingsRepository.hideUpdate(it.versionCode) }
            _uiState.update { it.copy(updateStatus = it.updateStatus.copy(updateInfo = null, expanded = false)) }
        }
    }

    fun copyUpdateApkUrl() {
        _uiState.value.updateStatus.updateInfo?.apkUrl?.let { url ->
            viewModelScope.launch { _events.send(UiEvent.CopyText("APK", url)) }
        }
    }

    fun focusPoint(point: GeoPoint) {
        _uiState.update {
            it.copy(
                cameraFocus = point,
                cameraFocusNonce = it.cameraFocusNonce + 1,
                selectedTarget = null
            )
        }
    }

    private fun copyCoordinates(point: GeoPoint) {
        val text = String.format(java.util.Locale.US, "%.6f, %.6f", point.latitude, point.longitude)
        viewModelScope.launch { _events.send(UiEvent.CopyText("Координаты", text)) }
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
                        measurementManager = container.measurementManager,
                        settingsRepository = container.settingsRepository,
                        locationTracker = container.locationTracker,
                        routePlanner = container.routePlanner,
                        updateChecker = container.updateChecker
                    ) as T
                }
            }
        }
    }
}
