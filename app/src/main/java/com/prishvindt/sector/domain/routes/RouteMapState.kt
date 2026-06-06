package com.prishvindt.sector.domain.routes

import com.prishvindt.sector.domain.GeoPoint

enum class RouteOrigin {
    MY_LOCATION,
    MAP_POINT
}

data class ActiveRoute(
    val origin: RouteOrigin,
    val start: GeoPoint,
    val end: GeoPoint,
    val polyline: List<GeoPoint>,
    val yandexRouteBuilt: Boolean
) {
    val mapStartMarker: GeoPoint?
        get() = start.takeIf { origin == RouteOrigin.MAP_POINT }

    val drawsGpsArrow: Boolean
        get() = origin == RouteOrigin.MY_LOCATION && polyline.size >= 2

    companion object {
        fun fromMyLocation(
            start: GeoPoint,
            end: GeoPoint,
            polyline: List<GeoPoint>,
            yandexRouteBuilt: Boolean
        ): ActiveRoute =
            ActiveRoute(
                origin = RouteOrigin.MY_LOCATION,
                start = start,
                end = end,
                polyline = normalizedPolyline(start, end, polyline),
                yandexRouteBuilt = yandexRouteBuilt
            )

        fun fromMapPoint(
            start: GeoPoint,
            end: GeoPoint,
            polyline: List<GeoPoint>,
            yandexRouteBuilt: Boolean
        ): ActiveRoute =
            ActiveRoute(
                origin = RouteOrigin.MAP_POINT,
                start = start,
                end = end,
                polyline = normalizedPolyline(start, end, polyline),
                yandexRouteBuilt = yandexRouteBuilt
            )
    }
}

sealed interface RoutePointSelectionState {
    data object Idle : RoutePointSelectionState
    data class SelectingEnd(val start: GeoPoint) : RoutePointSelectionState
}

data class RouteMapState(
    val activeRoute: ActiveRoute? = null,
    val pointSelection: RoutePointSelectionState = RoutePointSelectionState.Idle
) {
    val pendingStartPoint: GeoPoint?
        get() = (pointSelection as? RoutePointSelectionState.SelectingEnd)?.start

    val visibleStartMarker: GeoPoint?
        get() = pendingStartPoint

    val routePolyline: List<GeoPoint>
        get() = activeRoute?.polyline.orEmpty()

    val routePanelVisible: Boolean
        get() = activeRoute != null && routePolyline.size >= 2

    val gpsArrowVisible: Boolean
        get() = activeRoute?.drawsGpsArrow == true

    fun beginSelectingEnd(start: GeoPoint): RouteMapState =
        copy(pointSelection = RoutePointSelectionState.SelectingEnd(start))

    fun cancelPointSelection(): RouteMapState =
        copy(pointSelection = RoutePointSelectionState.Idle)

    fun activate(route: ActiveRoute): RouteMapState =
        RouteMapState(activeRoute = route)

    fun clearActiveRoute(): RouteMapState =
        RouteMapState()
}

private fun normalizedPolyline(start: GeoPoint, end: GeoPoint, polyline: List<GeoPoint>): List<GeoPoint> {
    val normalized = (listOf(start) + polyline + end)
        .distinctAdjacent()
        .takeIf { it.size >= 2 }
    return normalized ?: listOf(start, end)
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
