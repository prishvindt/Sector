package com.prishvindt.sector.ui.common

import com.prishvindt.sector.domain.GeoPoint
import com.prishvindt.sector.domain.routes.ActiveRoute
import com.prishvindt.sector.domain.routes.RouteMapState
import com.prishvindt.sector.domain.routes.RouteTargetManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainUiStateTest {
    private val gps = GeoPoint(55.751244, 37.618423)
    private val destination = GeoPoint(55.760000, 37.620000)
    private val routeEnd = GeoPoint(55.770000, 37.640000)

    @Test
    fun longTapStateKeepsDestinationPointForMap() {
        val state = MainUiState(
            destinationPoint = destination,
            selectedTarget = RouteTargetManager.destination(destination)
        )

        assertEquals(destination, state.selectedDestinationPoint)
        assertEquals(destination, state.selectedTargetPoint)
        assertEquals(destination, state.destination)
    }

    @Test
    fun dismissingDestinationSheetDoesNotRemoveDestinationPoint() {
        val openedState = MainUiState(
            destinationPoint = destination,
            selectedTarget = RouteTargetManager.destination(destination)
        )

        val dismissedState = openedState.copy(selectedTarget = null)

        assertEquals(destination, dismissedState.selectedDestinationPoint)
        assertNull(dismissedState.selectedTargetPoint)
        assertEquals(destination, dismissedState.destination)
    }

    @Test
    fun deletingDestinationClearsSavedPoint() {
        val openedState = MainUiState(
            destinationPoint = destination,
            selectedTarget = RouteTargetManager.destination(destination)
        )

        val deletedState = openedState.copy(
            destinationPoint = null,
            selectedTarget = null
        )

        assertNull(deletedState.selectedDestinationPoint)
        assertNull(deletedState.selectedTargetPoint)
        assertNull(deletedState.destination)
    }

    @Test
    fun activeRouteEndStaysAvailableAfterDestinationSheetDismiss() {
        val activeRoute = ActiveRoute.fromMyLocation(
            start = gps,
            end = routeEnd,
            polyline = listOf(gps, routeEnd),
            yandexRouteBuilt = true
        )
        val openedState = MainUiState(
            destinationPoint = destination,
            selectedTarget = RouteTargetManager.destination(destination),
            routeMapState = RouteMapState().activate(activeRoute)
        )

        val dismissedState = openedState.copy(selectedTarget = null)

        assertEquals(destination, dismissedState.selectedDestinationPoint)
        assertEquals(routeEnd, dismissedState.activeRouteEndPoint)
        assertEquals(activeRoute.polyline, dismissedState.routePolyline)
    }
}
