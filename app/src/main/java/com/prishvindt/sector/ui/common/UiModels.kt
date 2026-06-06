package com.prishvindt.sector.ui.common

import com.prishvindt.sector.MapKitState
import com.prishvindt.sector.data.AppSettings
import com.prishvindt.sector.data.DestinationMarkerType
import com.prishvindt.sector.data.ImportedLocation
import com.prishvindt.sector.data.Measurement
import com.prishvindt.sector.domain.GeoPoint
import com.prishvindt.sector.domain.RouteTarget
import com.prishvindt.sector.domain.RouteTargetType
import com.prishvindt.sector.domain.backup.BackupSelection
import com.prishvindt.sector.domain.notes.MapNote
import com.prishvindt.sector.domain.notes.NoteDraft
import com.prishvindt.sector.domain.routes.ActiveRoute
import com.prishvindt.sector.domain.routes.RouteMapState
import com.prishvindt.sector.domain.routes.RoutePointSelectionState
import com.prishvindt.sector.domain.routes.RouteTargetManager
import com.prishvindt.sector.location.LocationState
import com.prishvindt.sector.updates.UpdateStatus

enum class DrawerItem(val title: String) {
    CALLSIGN("Позывной"),
    INPUT("Ввод данных"),
    SHARE_GPS("Поделиться GPS"),
    EXPORT("Экспорт"),
    IMPORT("Импорт"),
    MEASUREMENTS("Замеры"),
    SETTINGS("Настройки"),
    ABOUT("О приложении")
}

data class MapDisplaySettings(
    val ownPointColor: Int,
    val gpsPointScale: Float,
    val destinationMarkerType: DestinationMarkerType,
    val showSelfCallsign: Boolean,
    val showImportedCallsigns: Boolean,
    val showMapNotes: Boolean,
    val showMapNoteTitles: Boolean,
    val callsign: String
)

data class MainUiState(
    val settings: AppSettings = AppSettings(),
    val measurements: List<Measurement> = emptyList(),
    val importedLocations: List<ImportedLocation> = emptyList(),
    val mapNotes: List<MapNote> = emptyList(),
    val noteDraft: NoteDraft? = null,
    val locationState: LocationState = LocationState(),
    val mapKitState: MapKitState = MapKitState(),
    val updateStatus: UpdateStatus = UpdateStatus(),
    val intersection: RouteTarget? = null,
    val destinationPoint: GeoPoint? = null,
    val selectedTarget: RouteTarget? = null,
    val routeMapState: RouteMapState = RouteMapState(),
    val routeFocusPolyline: List<GeoPoint> = emptyList(),
    val routeFocusNonce: Long = 0L,
    val cameraFocus: GeoPoint? = null,
    val cameraFocusNonce: Long = 0L,
    val cameraFocusPreserveZoom: Boolean = false,
    val showFirstStartDialog: Boolean = false,
    val showExportWarning: Boolean = false,
    val showExportMeasurementSelection: Boolean = false,
    val showBackupCategorySelection: Boolean = false,
    val importBackupAvailableSections: BackupSelection? = null,
    val showBackgroundRationale: Boolean = false,
    val callsignPromptForExport: Boolean = false,
    val showChangelogDialog: Boolean = false
) {
    val exportableMeasurements: List<Measurement>
        get() = measurements.filter { it.active }

    // Long-tap action marker. It is intentionally separate from active route endpoints.
    val candidateActionPoint: GeoPoint?
        get() = destinationPoint

    val selectedDestinationPoint: GeoPoint?
        get() = candidateActionPoint

    val selectedTargetPoint: GeoPoint?
        get() = selectedTarget?.point

    val activeRouteEndPoint: GeoPoint?
        get() = routeMapState.activeRoute?.end

    // Kept for older callers; this is only the candidate/action point, not activeRoute.end.
    val destination: GeoPoint?
        get() = candidateActionPoint

    val routeStartMarker: GeoPoint?
        get() = routeMapState.visibleStartMarker

    val routePolyline: List<GeoPoint>
        get() = routeMapState.routePolyline

    val activeRouteBuilt: Boolean
        get() = routeMapState.activeRoute?.yandexRouteBuilt == true

    val drawGpsRouteArrow: Boolean
        get() = routeMapState.gpsArrowVisible

    val routePointSelectionState: RoutePointSelectionState
        get() = routeMapState.pointSelection

    val isSelectingRouteEndPoint: Boolean
        get() = routePointSelectionState is RoutePointSelectionState.SelectingEnd

    val routePanelVisible: Boolean
        get() = routeMapState.routePanelVisible

    fun selectDestination(point: GeoPoint): MainUiState =
        copy(
            destinationPoint = point,
            selectedTarget = RouteTargetManager.destination(point)
        )

    fun beginSelectingRouteEnd(start: GeoPoint): MainUiState =
        copy(
            selectedTarget = null,
            routeMapState = routeMapState.clearActiveRoute().beginSelectingEnd(start),
            routeFocusPolyline = emptyList()
        )

    fun activateRoute(route: ActiveRoute): MainUiState =
        copy(
            destinationPoint = null,
            selectedTarget = null,
            routeMapState = routeMapState.activate(route),
            routeFocusPolyline = emptyList()
        )

    fun mapLongTapAction(point: GeoPoint): MapLongTapAction =
        when (val selection = routePointSelectionState) {
            RoutePointSelectionState.Idle -> MapLongTapAction.SelectDestination(point)
            is RoutePointSelectionState.SelectingEnd -> {
                MapLongTapAction.BuildRouteFromMapPoint(
                    start = selection.start,
                    end = point
                )
            }
        }

    fun mapTargetTapAction(target: RouteTarget): MapTargetTapAction =
        when (val selection = routePointSelectionState) {
            RoutePointSelectionState.Idle -> {
                if (target.type == RouteTargetType.MAP_NOTE) {
                    target.objectId
                        ?.let(MapTargetTapAction::OpenMapNote)
                        ?: MapTargetTapAction.Ignore
                } else {
                    MapTargetTapAction.OpenTargetMenu(target)
                }
            }
            is RoutePointSelectionState.SelectingEnd -> {
                MapTargetTapAction.BuildRouteFromMapPoint(
                    start = selection.start,
                    end = target.point
                )
            }
        }

    val mapDisplaySettings: MapDisplaySettings
        get() = MapDisplaySettings(
            ownPointColor = settings.ownPointColor.colorArgb,
            gpsPointScale = settings.gpsPointScale,
            destinationMarkerType = settings.destinationMarkerType,
            showSelfCallsign = settings.showSelfCallsign,
            showImportedCallsigns = settings.showImportedCallsigns,
            showMapNotes = settings.showMapNotes,
            showMapNoteTitles = settings.showMapNoteTitles,
            callsign = settings.callsign
        )
}

sealed interface MapLongTapAction {
    data class SelectDestination(val point: GeoPoint) : MapLongTapAction
    data class BuildRouteFromMapPoint(val start: GeoPoint, val end: GeoPoint) : MapLongTapAction
}

sealed interface MapTargetTapAction {
    data class BuildRouteFromMapPoint(val start: GeoPoint, val end: GeoPoint) : MapTargetTapAction
    data class OpenTargetMenu(val target: RouteTarget) : MapTargetTapAction
    data class OpenMapNote(val objectId: String) : MapTargetTapAction
    data object Ignore : MapTargetTapAction
}

sealed interface UiEvent {
    data class ShowMessage(val message: String) : UiEvent
    data class ShareText(
        val text: String,
        val chooserTitle: String = "Экспорт замера",
        val clipLabel: String = "Замер Сектор"
    ) : UiEvent
    data class CopyText(val label: String, val text: String) : UiEvent
    data class OpenUrl(val url: String) : UiEvent
    data class OpenExternalRoute(val appUri: String, val webUri: String) : UiEvent
    data class CreateBackupZip(val defaultFileName: String) : UiEvent
    data object OpenBackupZip : UiEvent
    data object ShowUpdateBanner : UiEvent
    data object RequestBackgroundLocationPermission : UiEvent
    data object RequestNotificationPermission : UiEvent
}
