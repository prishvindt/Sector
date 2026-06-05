package com.prishvindt.sector.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.prishvindt.sector.MapKitState
import com.prishvindt.sector.data.ImportedLocation
import com.prishvindt.sector.data.Measurement
import com.prishvindt.sector.domain.GeoPoint
import com.prishvindt.sector.domain.RouteTarget
import com.prishvindt.sector.domain.notes.MapNote
import com.prishvindt.sector.location.LocationState
import com.prishvindt.sector.ui.common.MapDisplaySettings
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.mapview.MapView

@Composable
fun YandexMapComposable(
    mapKitState: MapKitState,
    locationState: LocationState,
    measurements: List<Measurement>,
    importedLocations: List<ImportedLocation>,
    mapNotes: List<MapNote>,
    intersection: RouteTarget?,
    activeRouteEnd: GeoPoint?,
    selectedDestination: GeoPoint?,
    routeStartMarker: GeoPoint?,
    routePolyline: List<GeoPoint>,
    drawGpsRouteArrow: Boolean,
    routeFocusPolyline: List<GeoPoint>,
    routeFocusNonce: Long,
    cameraFocus: GeoPoint?,
    cameraFocusNonce: Long,
    cameraFocusPreserveZoom: Boolean,
    displaySettings: MapDisplaySettings,
    onLongTap: (GeoPoint) -> Unit,
    onTargetTap: (RouteTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!mapKitState.isReady) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = mapKitState.message ?: "MapKit не готов",
                modifier = Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val context = LocalContext.current
    val mapView = remember { MapView(context) }
    val currentOnLongTap by rememberUpdatedState(onLongTap)
    val currentOnTargetTap by rememberUpdatedState(onTargetTap)
    val inputListener = remember {
        MapTapHandler { point -> currentOnLongTap(point) }
    }
    var controller by remember { mutableStateOf<MapObjectsController?>(null) }

    DisposableEffect(mapView) {
        val map = mapView.mapWindow.map
        MapKitFactory.getInstance().onStart()
        mapView.onStart()
        map.addInputListener(inputListener)
        controller = MapObjectsController(context, mapView.mapWindow) { target ->
            currentOnTargetTap(target)
        }
        onDispose {
            runCatching { map.removeInputListener(inputListener) }
            controller = null
            mapView.onStop()
            MapKitFactory.getInstance().onStop()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { mapView },
        update = {
            controller?.update(
                locationState = locationState,
                measurements = measurements,
                importedLocations = importedLocations,
                mapNotes = mapNotes,
                intersection = intersection,
                activeRouteEnd = activeRouteEnd,
                selectedDestination = selectedDestination,
                routeStartMarker = routeStartMarker,
                routePolyline = routePolyline,
                drawGpsRouteArrow = drawGpsRouteArrow,
                routeFocusPolyline = routeFocusPolyline,
                routeFocusNonce = routeFocusNonce,
                cameraFocus = cameraFocus,
                cameraFocusNonce = cameraFocusNonce,
                cameraFocusPreserveZoom = cameraFocusPreserveZoom,
                displaySettings = displaySettings
            )
        }
    )
}
