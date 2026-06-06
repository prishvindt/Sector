package com.prishvindt.sector.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.prishvindt.sector.BuildConfig
import com.prishvindt.sector.SectorApplication
import com.prishvindt.sector.data.CallsignBehavior
import com.prishvindt.sector.data.DestinationMarkerType
import com.prishvindt.sector.data.GpsMode
import com.prishvindt.sector.data.Measurement
import com.prishvindt.sector.data.MeasurementSource
import com.prishvindt.sector.data.OwnPointColor
import com.prishvindt.sector.data.RouteMode
import com.prishvindt.sector.data.RouteType
import com.prishvindt.sector.data.SectorObjectImportResult
import com.prishvindt.sector.data.SectorObjectRepository
import com.prishvindt.sector.data.SettingsRepository
import com.prishvindt.sector.domain.GeoPoint
import com.prishvindt.sector.domain.IntersectionTargetCalculator
import com.prishvindt.sector.domain.ExportFormat
import com.prishvindt.sector.domain.LocationExchangeFormat
import com.prishvindt.sector.domain.RouteTarget
import com.prishvindt.sector.domain.backup.BackupImportSummary
import com.prishvindt.sector.domain.backup.BackupManager
import com.prishvindt.sector.domain.backup.BackupSelection
import com.prishvindt.sector.domain.backup.EmptyBackupException
import com.prishvindt.sector.domain.backup.UnsupportedBackupException
import com.prishvindt.sector.domain.locations.CurrentLocationShareInput
import com.prishvindt.sector.domain.locations.LocationShareManager
import com.prishvindt.sector.domain.measurements.MeasurementImportResult
import com.prishvindt.sector.domain.measurements.MeasurementManager
import com.prishvindt.sector.domain.measurements.SelfMeasurementInput
import com.prishvindt.sector.domain.notes.NoteManager
import com.prishvindt.sector.domain.objects.SectorBundleFormat
import com.prishvindt.sector.domain.routes.ActiveRoute
import com.prishvindt.sector.domain.routes.RouteOrigin
import com.prishvindt.sector.domain.routes.RouteTargetManager
import com.prishvindt.sector.location.ActiveSearchService
import com.prishvindt.sector.location.LocationTracker
import com.prishvindt.sector.map.RoutePlanner
import com.prishvindt.sector.media.notes.NoteMediaManager
import com.prishvindt.sector.media.notes.RecordedNoteAudio
import com.prishvindt.sector.ui.common.MainUiState
import com.prishvindt.sector.ui.common.MapLongTapAction
import com.prishvindt.sector.ui.common.MapTargetTapAction
import com.prishvindt.sector.ui.common.UiEvent
import com.prishvindt.sector.ui.notes.NoteUiCoordinator
import com.prishvindt.sector.updates.UpdateCoordinator
import com.prishvindt.sector.updates.UpdateCoordinatorEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.zip.ZipException

class MainViewModel(
    application: Application,
    private val sectorObjectRepository: SectorObjectRepository,
    private val backupManager: BackupManager,
    private val measurementManager: MeasurementManager,
    noteManager: NoteManager,
    noteMediaManager: NoteMediaManager,
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
    private var pendingBackupSelection: BackupSelection? = null
    private var pendingImportBackupUri: Uri? = null
    private val routeRequests = RouteRequestGate()
    private val noteCoordinator = NoteUiCoordinator(
        noteManager = noteManager,
        noteMediaManager = noteMediaManager,
        scope = viewModelScope,
        currentState = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        showMessage = ::showMessage
    )

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
            sectorObjectRepository.observeActiveAzimuthRays().collect { measurements ->
                _uiState.update {
                    it.copy(
                        measurements = measurements,
                        intersection = IntersectionTargetCalculator.calculate(measurements, it.locationState.point)
                    )
                }
            }
        }
        viewModelScope.launch {
            sectorObjectRepository.observeImportedSharedLocations().collect { locations ->
                _uiState.update { it.copy(importedLocations = locations) }
            }
        }
        viewModelScope.launch {
            noteCoordinator.observeNotes().collect { notes ->
                _uiState.update { it.copy(mapNotes = notes) }
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
        noteCoordinator.cleanupOpenDraft()
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
            if (SectorBundleFormat.containsBundleText(text)) {
                sectorObjectRepository.importObjectsFromBundle(text)
                    .onSuccess { showMessage(it.importSummary()) }
                    .onFailure { showMessage(it.message ?: "РћС€РёР±РєР° РёРјРїРѕСЂС‚Р° bundle") }
                return@launch
            }

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
        viewModelScope.launch {
            locationShareManager.formatCurrentLocation(
                CurrentLocationShareInput(
                    point = point,
                    callsign = state.settings.callsign,
                    accuracyMeters = state.locationState.accuracyMeters
                )
            ).onSuccess { text ->
                _events.send(
                    UiEvent.ShareText(
                        text = text,
                        chooserTitle = "Поделиться GPS",
                        clipLabel = "GPS Сектор"
                    )
                )
            }.onFailure {
                showMessage(it.message ?: "Ошибка экспорта GPS")
            }
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

    fun requestBackup() {
        _uiState.update {
            it.copy(
                showExportMeasurementSelection = false,
                showBackupCategorySelection = true
            )
        }
    }

    fun dismissBackupCategorySelection() {
        pendingBackupSelection = null
        _uiState.update { it.copy(showBackupCategorySelection = false) }
    }

    fun confirmBackupCategories(selection: BackupSelection) {
        val normalized = selection.normalized()
        if (!normalized.anySelected()) {
            showMessage("Backup пустой")
            return
        }
        pendingBackupSelection = normalized
        _uiState.update { it.copy(showBackupCategorySelection = false) }
        viewModelScope.launch {
            _events.send(UiEvent.CreateBackupZip(backupManager.defaultFileName()))
        }
    }

    fun onBackupDocumentCreated(uri: Uri?) {
        val selection = pendingBackupSelection ?: return
        pendingBackupSelection = null
        if (uri == null) return
        viewModelScope.launch {
            val output = runCatching {
                getApplication<Application>().contentResolver.openOutputStream(uri)
            }.getOrNull()
            if (output == null) {
                showMessage("Ошибка записи backup")
                return@launch
            }
            output.use { stream ->
                backupManager.writeBackup(stream, selection)
            }.onSuccess { summary ->
                showMessage(
                    "Backup создан: объектов ${summary.objectCount}, медиа ${summary.mediaCount}"
                )
            }.onFailure { error ->
                showMessage(error.backupMessage(isRead = false))
            }
        }
    }

    fun requestImportBackupZip() {
        viewModelScope.launch {
            _events.send(UiEvent.OpenBackupZip)
        }
    }

    fun onBackupZipSelected(uri: Uri?) {
        if (uri == null) return
        pendingImportBackupUri = uri
        viewModelScope.launch {
            val input = runCatching {
                getApplication<Application>().contentResolver.openInputStream(uri)
            }.getOrNull()
            if (input == null) {
                pendingImportBackupUri = null
                showMessage("Ошибка чтения backup")
                return@launch
            }
            input.use { stream ->
                backupManager.readImportPreview(stream)
            }.onSuccess { preview ->
                if (!preview.availableSections.anySelected()) {
                    pendingImportBackupUri = null
                    showMessage("Backup пустой")
                } else {
                    _uiState.update {
                        it.copy(importBackupAvailableSections = preview.availableSections)
                    }
                }
            }.onFailure { error ->
                pendingImportBackupUri = null
                showMessage(error.backupMessage(isRead = true))
            }
        }
    }

    fun dismissImportBackupCategorySelection() {
        pendingImportBackupUri = null
        _uiState.update { it.copy(importBackupAvailableSections = null) }
    }

    fun confirmImportBackupCategories(selection: BackupSelection) {
        val uri = pendingImportBackupUri ?: return
        val normalized = selection.normalized()
        if (!normalized.anySelected()) {
            showMessage("Backup пустой")
            return
        }
        pendingImportBackupUri = null
        _uiState.update { it.copy(importBackupAvailableSections = null) }
        viewModelScope.launch {
            val input = runCatching {
                getApplication<Application>().contentResolver.openInputStream(uri)
            }.getOrNull()
            if (input == null) {
                showMessage("Ошибка чтения backup")
                return@launch
            }
            input.use { stream ->
                backupManager.importBackup(stream, normalized)
            }.onSuccess { summary ->
                showMessage(summary.importSummary())
            }.onFailure { error ->
                showMessage(error.backupMessage(isRead = true))
            }
        }
    }

    fun sendAllExportMeasurements() {
        viewModelScope.launch {
            val state = _uiState.value
            shareExportObjectsByIds(
                state.exportableMeasurements.map { it.measurementId } +
                    state.mapNotes.map { it.objectId }
            )
        }
    }

    fun sendSelectedExportMeasurements(
        measurementIds: Set<String>,
        noteIds: Set<String>
    ) {
        viewModelScope.launch {
            val state = _uiState.value
            val selectedMeasurementIds = state.exportableMeasurements
                .filter { it.measurementId in measurementIds }
                .map { it.measurementId }
            val selectedNoteIds = state.mapNotes
                .filter { it.objectId in noteIds }
                .map { it.objectId }
            shareExportObjectsByIds(selectedMeasurementIds + selectedNoteIds)
        }
    }

    private suspend fun proceedWithExportRequest() {
        val state = _uiState.value
        val exportable = state.exportableMeasurements
        val noteIds = state.mapNotes.map { it.objectId }
        when {
            exportable.isEmpty() && noteIds.isEmpty() -> _uiState.update {
                it.copy(showExportMeasurementSelection = true)
            }
            exportable.any { it.source == MeasurementSource.SELF } && state.settings.callsign.isBlank() -> {
                pendingExport = true
                _uiState.update { it.copy(callsignPromptForExport = true) }
            }
            !state.settings.exportWarningAccepted -> {
                pendingExport = true
                _uiState.update { it.copy(showExportWarning = true) }
            }
            else -> _uiState.update { it.copy(showExportMeasurementSelection = true) }
        }
    }

    private suspend fun shareExportObjectsByIds(objectIds: List<String>) {
        if (objectIds.isEmpty()) {
            showMessage("Нет объектов для экспорта")
            return
        }
        val state = _uiState.value
        sectorObjectRepository.exportObjectsByIds(
            objectIds = objectIds,
            callsign = state.settings.callsign
        )
            .onSuccess { text ->
                _uiState.update { it.copy(showExportMeasurementSelection = false) }
                _events.send(
                    UiEvent.ShareText(
                        text = text,
                        chooserTitle = "Экспорт Сектор",
                        clipLabel = "Сектор"
                    )
                )
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

    fun onMapLongTap(point: GeoPoint) {
        when (val action = _uiState.value.mapLongTapAction(point)) {
            is MapLongTapAction.BuildRouteFromMapPoint -> buildInAppRouteFromMapPoint(
                start = action.start,
                end = action.end
            )
            is MapLongTapAction.SelectDestination -> setDestination(action.point)
        }
    }

    fun setDestination(point: GeoPoint) {
        _uiState.update { it.selectDestination(point) }
    }

    fun beginRouteFromSelectedPoint() {
        val start = _uiState.value.selectedTargetPoint ?: return
        routeRequests.invalidate()
        _uiState.update { it.beginSelectingRouteEnd(start) }
    }

    fun cancelRoutePointSelection() {
        _uiState.update {
            it.copy(routeMapState = it.routeMapState.cancelPointSelection())
        }
    }

    fun deleteSelectedDestination() {
        val state = _uiState.value
        val selectedPoint = state.selectedTargetPoint ?: state.destinationPoint
        val activeRoute = state.routeMapState.activeRoute
        val deletesSavedDestination = selectedPoint != null && state.destinationPoint == selectedPoint
        val deletesActiveRoute = selectedPoint != null &&
            (activeRoute?.start == selectedPoint || activeRoute?.end == selectedPoint)
        if (deletesActiveRoute) {
            routeRequests.invalidate()
        }
        _uiState.update {
            it.copy(
                destinationPoint = if (deletesSavedDestination) null else it.destinationPoint,
                selectedTarget = null,
                routeMapState = if (deletesActiveRoute) {
                    it.routeMapState.clearActiveRoute()
                } else {
                    it.routeMapState
                },
                routeFocusPolyline = if (deletesActiveRoute) emptyList() else it.routeFocusPolyline
            )
        }
    }

    fun deleteActiveRoute() {
        routeRequests.invalidate()
        _uiState.update {
            it.copy(
                selectedTarget = null,
                routeMapState = it.routeMapState.clearActiveRoute(),
                routeFocusPolyline = emptyList()
            )
        }
    }

    fun selectTarget(target: RouteTarget?) {
        _uiState.update { it.copy(selectedTarget = target) }
    }

    fun onMapTargetTap(target: RouteTarget) {
        when (val action = _uiState.value.mapTargetTapAction(target)) {
            is MapTargetTapAction.BuildRouteFromMapPoint -> buildInAppRouteFromMapPoint(
                start = action.start,
                end = action.end
            )
            MapTargetTapAction.Ignore -> Unit
            is MapTargetTapAction.OpenMapNote -> openExistingNote(action.objectId)
            is MapTargetTapAction.OpenTargetMenu -> selectTarget(action.target)
        }
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
        buildInAppRoute(
            origin = RouteOrigin.MY_LOCATION,
            start = endpoints.start,
            end = endpoints.target.point
        )
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

    private fun buildInAppRouteFromMapPoint(start: GeoPoint, end: GeoPoint) {
        buildInAppRoute(
            origin = RouteOrigin.MAP_POINT,
            start = start,
            end = end
        )
    }

    private fun buildInAppRoute(origin: RouteOrigin, start: GeoPoint, end: GeoPoint) {
        val requestId = routeRequests.next()
        replaceActiveRoute(activeRoute(origin, start, end, listOf(start, end), yandexRouteBuilt = false))
        viewModelScope.launch {
            routePlanner.buildRoute(start, end, RouteType.CAR)
                .onSuccess { route ->
                    if (!routeRequests.isCurrent(requestId)) return@launch
                    replaceActiveRoute(activeRoute(origin, start, end, route, yandexRouteBuilt = true))
                }
                .onFailure {
                    if (!routeRequests.isCurrent(requestId)) return@launch
                    showMessage("Маршрут не построился. Показан ориентир, можно открыть Яндекс.Карты.")
                }
        }
    }

    private fun replaceActiveRoute(route: ActiveRoute) {
        _uiState.update { it.activateRoute(route) }
    }

    private fun activeRoute(
        origin: RouteOrigin,
        start: GeoPoint,
        end: GeoPoint,
        polyline: List<GeoPoint>,
        yandexRouteBuilt: Boolean
    ): ActiveRoute =
        when (origin) {
            RouteOrigin.MY_LOCATION -> ActiveRoute.fromMyLocation(
                start = start,
                end = end,
                polyline = polyline,
                yandexRouteBuilt = yandexRouteBuilt
            )
            RouteOrigin.MAP_POINT -> ActiveRoute.fromMapPoint(
                start = start,
                end = end,
                polyline = polyline,
                yandexRouteBuilt = yandexRouteBuilt
            )
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
    fun setTelemetryEnabled(value: Boolean) = viewModelScope.launch { settingsRepository.setTelemetryEnabled(value) }
    fun setShowMapNotes(value: Boolean) = viewModelScope.launch { settingsRepository.setShowMapNotes(value) }
    fun setShowMapNoteTitles(value: Boolean) = viewModelScope.launch { settingsRepository.setShowMapNoteTitles(value) }

    fun openNewNote(point: GeoPoint) = noteCoordinator.openNew(point)
    fun openExistingNote(objectId: String) = noteCoordinator.openExisting(objectId)
    fun updateNoteTitle(value: String) = noteCoordinator.updateTitle(value)
    fun updateNoteText(value: String) = noteCoordinator.updateText(value)
    fun addNotePhoto(uri: Uri) = noteCoordinator.addPhoto(uri)
    fun prepareNoteCameraCapture(): Uri? = noteCoordinator.prepareCameraCapture()
    fun onNoteCameraCaptureResult(success: Boolean) = noteCoordinator.onCameraCaptureResult(success)
    fun addNoteAudio(recording: RecordedNoteAudio) = noteCoordinator.addAudio(recording)
    fun removeNoteAttachment(attachmentId: String) = noteCoordinator.removeAttachment(attachmentId)
    fun saveOpenNote() = noteCoordinator.saveOpen()
    fun dismissOpenNote() = noteCoordinator.dismissOpen()
    fun deleteOpenNote() = noteCoordinator.deleteOpen()

    fun resetTelemetryInstallId() {
        viewModelScope.launch {
            settingsRepository.resetTelemetryInstallId()
            showMessage("ID статистики сброшен")
        }
    }

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
        val activeRoute = state.routeMapState.activeRoute
        val currentPoint = state.locationState.point
        if (activeRoute == null || activeRoute.polyline.size < 2) {
            focusPoint(state.destination ?: currentPoint ?: return)
            return
        }
        val routeToFocus = if (activeRoute.origin == RouteOrigin.MY_LOCATION && currentPoint != null) {
            remainingRoutePolyline(
                currentPoint = currentPoint,
                destination = activeRoute.end,
                routePolyline = activeRoute.polyline
            )
        } else {
            activeRoute.polyline
        }
        _uiState.update {
            it.copy(
                routeFocusPolyline = routeToFocus,
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
                        sectorObjectRepository = container.sectorObjectRepository,
                        backupManager = container.backupManager,
                        measurementManager = container.measurementManager,
                        noteManager = container.noteManager,
                        noteMediaManager = container.noteMediaManager,
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

internal class RouteRequestGate {
    private var current = 0L

    fun next(): Long {
        current += 1
        return current
    }

    fun invalidate() {
        current += 1
    }

    fun isCurrent(requestId: Long): Boolean =
        requestId == current
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

private fun SectorObjectImportResult.importSummary(): String {
    val base = if (imported.size == 1 && skippedObjects == 0) {
        "Sector object импортирован"
    } else {
        "Импортировано объектов: ${imported.size}"
    }
    return if (skippedObjects > 0) {
        "$base, пропущено объектов: $skippedObjects"
    } else {
        base
    }
}

private fun BackupImportSummary.importSummary(): String {
    val base = "Импортировано объектов: $importedObjects, пропущено: $skippedObjects"
    val details = buildList {
        if (skippedBrokenObjects > 0) add("битых: $skippedBrokenObjects")
        if (restoredMedia > 0) add("медиа: $restoredMedia")
        if (missingMedia > 0) add("медиа пропущено: $missingMedia")
        if (settingsApplied) add("настройки применены")
    }
    return if (details.isEmpty()) base else "$base; ${details.joinToString(", ")}"
}

private fun Throwable.backupMessage(isRead: Boolean): String =
    when (this) {
        is EmptyBackupException -> "Backup пустой"
        is UnsupportedBackupException -> "Неподдерживаемый файл"
        is ZipException,
        is IllegalArgumentException -> "Файл backup повреждён"
        else -> if (isRead) "Ошибка чтения backup" else "Ошибка записи backup"
    }
