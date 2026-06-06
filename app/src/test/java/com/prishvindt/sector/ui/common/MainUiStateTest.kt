package com.prishvindt.sector.ui.common

import com.prishvindt.sector.domain.GeoPoint
import com.prishvindt.sector.domain.RouteTarget
import com.prishvindt.sector.domain.RouteTargetType
import com.prishvindt.sector.domain.routes.ActiveRoute
import com.prishvindt.sector.domain.routes.RouteMapState
import com.prishvindt.sector.domain.routes.RouteTargetManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainUiStateTest {
    private val gps = GeoPoint(55.751244, 37.618423)
    private val destination = GeoPoint(55.760000, 37.620000)
    private val routeEnd = GeoPoint(55.770000, 37.640000)
    private val newDestination = GeoPoint(55.780000, 37.650000)
    private val importedTarget = RouteTarget(
        type = RouteTargetType.IMPORTED,
        point = routeEnd,
        title = "Imported target"
    )
    private val noteTarget = RouteTarget(
        type = RouteTargetType.MAP_NOTE,
        point = routeEnd,
        title = "Map note",
        objectId = "note-1"
    )

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

    @Test
    fun candidatePointIsSeparateFromActiveRouteEndMarker() {
        val activeRoute = ActiveRoute.fromMyLocation(
            start = gps,
            end = routeEnd,
            polyline = listOf(gps, routeEnd),
            yandexRouteBuilt = true
        )
        val state = MainUiState(
            routeMapState = RouteMapState().activate(activeRoute)
        )

        val selectedState = state.selectDestination(destination)

        assertEquals(destination, selectedState.selectedDestinationPoint)
        assertEquals(routeEnd, selectedState.activeRouteEndPoint)
        assertEquals(destination, selectedState.destination)
        assertEquals(activeRoute.polyline, selectedState.routePolyline)
    }

    @Test
    fun selectingNewDestinationKeepsActiveRouteAndFocus() {
        val activeRoute = ActiveRoute.fromMyLocation(
            start = gps,
            end = routeEnd,
            polyline = listOf(gps, destination, routeEnd),
            yandexRouteBuilt = true
        )
        val state = MainUiState(
            destinationPoint = destination,
            selectedTarget = RouteTargetManager.destination(destination),
            routeMapState = RouteMapState().activate(activeRoute),
            routeFocusPolyline = activeRoute.polyline
        )

        val selectedState = state.selectDestination(newDestination)

        assertEquals(newDestination, selectedState.destinationPoint)
        assertEquals(newDestination, selectedState.selectedTargetPoint)
        assertEquals(activeRoute, selectedState.routeMapState.activeRoute)
        assertEquals(routeEnd, selectedState.activeRouteEndPoint)
        assertTrue(selectedState.routePanelVisible)
        assertTrue(selectedState.drawGpsRouteArrow)
        assertEquals(activeRoute.polyline, selectedState.routePolyline)
        assertEquals(activeRoute.polyline, selectedState.routeFocusPolyline)
    }

    @Test
    fun beginSelectingRouteEndClearsActiveRouteAndKeepsPendingStartVisible() {
        val activeRoute = ActiveRoute.fromMyLocation(
            start = gps,
            end = routeEnd,
            polyline = listOf(gps, routeEnd),
            yandexRouteBuilt = true
        )
        val state = MainUiState(
            selectedTarget = RouteTargetManager.destination(destination),
            routeMapState = RouteMapState().activate(activeRoute),
            routeFocusPolyline = activeRoute.polyline
        )

        val selectedState = state.beginSelectingRouteEnd(destination)

        assertNull(selectedState.selectedTarget)
        assertNull(selectedState.routeMapState.activeRoute)
        assertEquals(destination, selectedState.routeMapState.pendingStartPoint)
        assertEquals(destination, selectedState.routeStartMarker)
        assertTrue(selectedState.isSelectingRouteEndPoint)
        assertFalse(selectedState.routePanelVisible)
        assertFalse(selectedState.drawGpsRouteArrow)
        assertTrue(selectedState.routePolyline.isEmpty())
        assertTrue(selectedState.routeFocusPolyline.isEmpty())
    }

    @Test
    fun idleLongTapSelectsNewDestination() {
        val state = MainUiState()

        val action = state.mapLongTapAction(newDestination)

        assertEquals(MapLongTapAction.SelectDestination(newDestination), action)
    }

    @Test
    fun selectingEndLongTapBuildsRouteToTappedPoint() {
        val state = MainUiState(
            routeMapState = RouteMapState().beginSelectingEnd(destination)
        )

        val action = state.mapLongTapAction(routeEnd)

        assertEquals(
            MapLongTapAction.BuildRouteFromMapPoint(start = destination, end = routeEnd),
            action
        )
    }

    @Test
    fun selectingEndTargetTapBuildsRouteToTargetPoint() {
        val state = MainUiState(
            routeMapState = RouteMapState().beginSelectingEnd(destination)
        )

        val action = state.mapTargetTapAction(importedTarget)

        assertTrue(action is MapTargetTapAction.BuildRouteFromMapPoint)
        action as MapTargetTapAction.BuildRouteFromMapPoint
        assertEquals(destination, action.start)
        assertEquals(routeEnd, action.end)
    }

    @Test
    fun selectingEndTargetTapDoesNotOpenBottomSheet() {
        val state = MainUiState(
            routeMapState = RouteMapState().beginSelectingEnd(destination)
        )

        val action = state.mapTargetTapAction(RouteTargetManager.destination(routeEnd))

        assertFalse(action is MapTargetTapAction.OpenTargetMenu)
    }

    @Test
    fun selectingEndTargetTapClearsPendingSelectionWhenRouteActivates() {
        val state = MainUiState(
            routeMapState = RouteMapState().beginSelectingEnd(destination)
        )
        val action = state.mapTargetTapAction(importedTarget) as MapTargetTapAction.BuildRouteFromMapPoint
        val route = ActiveRoute.fromMapPoint(
            start = action.start,
            end = action.end,
            polyline = listOf(action.start, action.end),
            yandexRouteBuilt = false
        )

        val routedState = state.copy(routeMapState = state.routeMapState.activate(route))

        assertNull(routedState.routeMapState.pendingStartPoint)
        assertNull(routedState.routeStartMarker)
        assertEquals(routeEnd, routedState.activeRouteEndPoint)
    }

    @Test
    fun selectingEndMapNoteTapCanBeRouteEndpoint() {
        val state = MainUiState(
            routeMapState = RouteMapState().beginSelectingEnd(destination)
        )

        val action = state.mapTargetTapAction(noteTarget)

        assertEquals(
            MapTargetTapAction.BuildRouteFromMapPoint(start = destination, end = routeEnd),
            action
        )
    }

    @Test
    fun idleTargetTapOpensOrdinaryMenuAsBefore() {
        val state = MainUiState()

        val action = state.mapTargetTapAction(importedTarget)

        assertEquals(MapTargetTapAction.OpenTargetMenu(importedTarget), action)
    }

    @Test
    fun idleMapNoteTapOpensNoteAsBefore() {
        val state = MainUiState()

        val action = state.mapTargetTapAction(noteTarget)

        assertEquals(MapTargetTapAction.OpenMapNote("note-1"), action)
    }
}
