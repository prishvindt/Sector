package com.prishvindt.sector.ui.common

import com.prishvindt.sector.MapKitState
import com.prishvindt.sector.data.AppSettings
import com.prishvindt.sector.data.Measurement
import com.prishvindt.sector.domain.GeoPoint
import com.prishvindt.sector.domain.RouteTarget
import com.prishvindt.sector.location.LocationState
import com.prishvindt.sector.updates.UpdateStatus

enum class DrawerItem(val title: String) {
    CALLSIGN("Позывной"),
    INPUT("Ввод данных"),
    EXPORT("Экспорт"),
    IMPORT("Импорт"),
    MEASUREMENTS("Замеры"),
    SETTINGS("Настройки"),
    ABOUT("О приложении")
}

data class MapDisplaySettings(
    val ownPointColor: Int,
    val showSelfCallsign: Boolean,
    val showImportedCallsigns: Boolean,
    val callsign: String
)

data class MainUiState(
    val settings: AppSettings = AppSettings(),
    val measurements: List<Measurement> = emptyList(),
    val locationState: LocationState = LocationState(),
    val mapKitState: MapKitState = MapKitState(),
    val updateStatus: UpdateStatus = UpdateStatus(),
    val intersection: RouteTarget? = null,
    val destination: GeoPoint? = null,
    val selectedTarget: RouteTarget? = null,
    val routePolyline: List<GeoPoint> = emptyList(),
    val cameraFocus: GeoPoint? = null,
    val cameraFocusNonce: Long = 0L,
    val cameraFocusPreserveZoom: Boolean = false,
    val showFirstStartDialog: Boolean = false,
    val showExportWarning: Boolean = false,
    val showBackgroundRationale: Boolean = false,
    val callsignPromptForExport: Boolean = false
) {
    val mapDisplaySettings: MapDisplaySettings
        get() = MapDisplaySettings(
            ownPointColor = settings.ownPointColor.colorArgb,
            showSelfCallsign = settings.showSelfCallsign,
            showImportedCallsigns = settings.showImportedCallsigns,
            callsign = settings.callsign
        )
}

sealed interface UiEvent {
    data class ShowMessage(val message: String) : UiEvent
    data class ShareText(val text: String) : UiEvent
    data class CopyText(val label: String, val text: String) : UiEvent
    data class OpenUrl(val url: String) : UiEvent
    data class OpenExternalRoute(val appUri: String, val webUri: String) : UiEvent
    data object ShowUpdateBanner : UiEvent
    data object RequestBackgroundLocationPermission : UiEvent
    data object RequestNotificationPermission : UiEvent
}
