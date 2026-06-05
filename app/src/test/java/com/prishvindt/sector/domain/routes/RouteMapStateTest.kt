package com.prishvindt.sector.domain.routes

import com.prishvindt.sector.domain.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteMapStateTest {
    private val gps = GeoPoint(55.751244, 37.618423)
    private val firstPoint = GeoPoint(55.760000, 37.620000)
    private val secondPoint = GeoPoint(55.770000, 37.640000)
    private val midPoint = GeoPoint(55.765000, 37.630000)

    @Test
    fun myLocationRouteShowsPanelAndGpsArrow() {
        val route = ActiveRoute.fromMyLocation(
            start = gps,
            end = firstPoint,
            polyline = listOf(midPoint),
            yandexRouteBuilt = true
        )
        val state = RouteMapState().activate(route)

        assertEquals(RouteOrigin.MY_LOCATION, state.activeRoute?.origin)
        assertTrue(state.routePanelVisible)
        assertTrue(state.gpsArrowVisible)
        assertNull(state.visibleStartMarker)
        assertEquals(gps, state.routePolyline.first())
        assertEquals(firstPoint, state.routePolyline.last())
    }

    @Test
    fun mapPointRouteShowsStartMarkerAndNoGpsArrow() {
        val route = ActiveRoute.fromMapPoint(
            start = firstPoint,
            end = secondPoint,
            polyline = listOf(midPoint),
            yandexRouteBuilt = true
        )
        val state = RouteMapState().activate(route)

        assertEquals(RouteOrigin.MAP_POINT, state.activeRoute?.origin)
        assertTrue(state.routePanelVisible)
        assertFalse(state.gpsArrowVisible)
        assertEquals(firstPoint, state.visibleStartMarker)
        assertEquals(firstPoint, state.routePolyline.first())
        assertEquals(secondPoint, state.routePolyline.last())
    }

    @Test
    fun beginAndCancelEndSelectionPreservesExistingRoute() {
        val existingRoute = ActiveRoute.fromMapPoint(
            start = firstPoint,
            end = secondPoint,
            polyline = listOf(firstPoint, midPoint, secondPoint),
            yandexRouteBuilt = true
        )
        val selectingState = RouteMapState(activeRoute = existingRoute)
            .beginSelectingEnd(gps)

        assertSame(existingRoute, selectingState.activeRoute)
        assertEquals(gps, selectingState.pendingStartPoint)
        assertEquals(gps, selectingState.visibleStartMarker)

        val canceledState = selectingState.cancelPointSelection()

        assertSame(existingRoute, canceledState.activeRoute)
        assertNull(canceledState.pendingStartPoint)
        assertEquals(firstPoint, canceledState.visibleStartMarker)
    }

    @Test
    fun activatingNewRouteReplacesOldRouteAndClearsSelection() {
        val oldRoute = ActiveRoute.fromMyLocation(
            start = gps,
            end = firstPoint,
            polyline = listOf(gps, firstPoint),
            yandexRouteBuilt = true
        )
        val newRoute = ActiveRoute.fromMapPoint(
            start = firstPoint,
            end = secondPoint,
            polyline = listOf(firstPoint, midPoint, secondPoint),
            yandexRouteBuilt = true
        )

        val state = RouteMapState(activeRoute = oldRoute)
            .beginSelectingEnd(firstPoint)
            .activate(newRoute)

        assertSame(newRoute, state.activeRoute)
        assertNull(state.pendingStartPoint)
        assertEquals(firstPoint, state.visibleStartMarker)
        assertFalse(state.gpsArrowVisible)
    }

    @Test
    fun clearActiveRouteClearsRouteState() {
        val route = ActiveRoute.fromMapPoint(
            start = firstPoint,
            end = secondPoint,
            polyline = listOf(firstPoint, secondPoint),
            yandexRouteBuilt = false
        )

        val state = RouteMapState(activeRoute = route)
            .beginSelectingEnd(gps)
            .clearActiveRoute()

        assertNull(state.activeRoute)
        assertNull(state.pendingStartPoint)
        assertFalse(state.routePanelVisible)
        assertFalse(state.gpsArrowVisible)
        assertTrue(state.routePolyline.isEmpty())
    }
}
