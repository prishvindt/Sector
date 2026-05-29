package com.prishvindt.sector.ui.common

import com.prishvindt.sector.MapKitState
import com.prishvindt.sector.data.AppSettings
import com.prishvindt.sector.data.DestinationMarkerType
import com.prishvindt.sector.data.ImportedLocation
import com.prishvindt.sector.data.Measurement
import com.prishvindt.sector.domain.GeoPoint
import com.prishvindt.sector.domain.RouteTarget
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
    val callsign: String
)

data class MainUiState(
    val settings: AppSettings = AppSettings(),
    val measurements: List<Measurement> = emptyList(),
    val importedLocations: List<ImportedLocation> = emptyList(),
    val locationState: LocationState = LocationState(),
    val mapKitState: MapKitState = MapKitState(),
    val updateStatus: UpdateStatus = UpdateStatus(),
    val intersection: RouteTarget? = null,
    val destination: GeoPoint? = null,
    val selectedTarget: RouteTarget? = null,
    val routePolyline: List<GeoPoint> = emptyList(),
    val activeRouteBuilt: Boolean = false,
    val routeFocusPolyline: List<GeoPoint> = emptyList(),
    val routeFocusNonce: Long = 0L,
    val cameraFocus: GeoPoint? = null,
    val cameraFocusNonce: Long = 0L,
    val cameraFocusPreserveZoom: Boolean = false,
    val showFirstStartDialog: Boolean = false,
    val showExportWarning: Boolean = false,
    val showBackgroundRationale: Boolean = false,
    val callsignPromptForExport: Boolean = false,
    val showChangelogDialog: Boolean = false
) {
    val routePanelVisible: Boolean
        get() = activeRouteBuilt &&
            locationState.point != null &&
            destination != null &&
            routePolyline.size >= 2

    val mapDisplaySettings: MapDisplaySettings
        get() = MapDisplaySettings(
            ownPointColor = settings.ownPointColor.colorArgb,
            gpsPointScale = settings.gpsPointScale,
            destinationMarkerType = settings.destinationMarkerType,
            showSelfCallsign = settings.showSelfCallsign,
            showImportedCallsigns = settings.showImportedCallsigns,
            callsign = settings.callsign
        )
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
    data object ShowUpdateBanner : UiEvent
    data object RequestBackgroundLocationPermission : UiEvent
    data object RequestNotificationPermission : UiEvent
}
