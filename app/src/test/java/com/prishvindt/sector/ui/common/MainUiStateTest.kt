package com.prishvindt.sector.ui.common

import com.prishvindt.sector.data.RouteType
import com.prishvindt.sector.domain.GeoPoint
import com.prishvindt.sector.domain.RouteTarget
import com.prishvindt.sector.domain.RouteTargetType
import com.prishvindt.sector.domain.routes.ActiveRoute
import com.prishvindt.sector.domain.routes.RouteMapState
import com.prishvindt.sector.domain.routes.RouteOrigin
import com.prishvindt.sector.domain.routes.RouteTargetManager
import com.prishvindt.sector.location.LocationState
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
        assertEquals(destination, state.candidateActionPoint)
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
        assertEquals(destination, dismissedState.candidateActionPoint)
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
        assertNull(deletedState.candidateActionPoint)
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
    fun activeRouteFromMyLocationDoesNotExposeDestinationMarkerAfterActivation() {
        val activeRoute = ActiveRoute.fromMyLocation(
            start = gps,
            end = routeEnd,
            polyline = listOf(gps, routeEnd),
            yandexRouteBuilt = true
        )
        val state = MainUiState(
            routeMapState = RouteMapState().activate(activeRoute)
        )

        assertNull(state.selectedDestinationPoint)
        assertNull(state.candidateActionPoint)
        assertNull(state.destination)
        assertNull(state.routeStartMarker)
        assertEquals(routeEnd, state.activeRouteEndPoint)
        assertEquals(activeRoute.polyline, state.routePolyline)
    }

    @Test
    fun activeRouteFromMapPointDoesNotExposeStartOrEndMarkersAfterActivation() {
        val activeRoute = ActiveRoute.fromMapPoint(
            start = destination,
            end = routeEnd,
            polyline = listOf(destination, routeEnd),
            yandexRouteBuilt = true
        )
        val state = MainUiState(
            destinationPoint = destination,
            selectedTarget = RouteTargetManager.destination(destination)
        ).activateRoute(activeRoute)

        assertNull(state.selectedDestinationPoint)
        assertNull(state.candidateActionPoint)
        assertNull(state.destination)
        assertNull(state.selectedTarget)
        assertNull(state.routeStartMarker)
        assertEquals(routeEnd, state.activeRouteEndPoint)
        assertEquals(activeRoute.polyline, state.routePolyline)
    }

    @Test
    fun fallbackRouteActivationKeepsCandidatePointUntilPlannerResult() {
        val fallbackRoute = ActiveRoute.fromMyLocation(
            start = gps,
            end = destination,
            polyline = listOf(gps, destination),
            yandexRouteBuilt = false
        )
        val state = MainUiState(
            destinationPoint = destination,
            selectedTarget = RouteTargetManager.destination(destination)
        )

        val fallbackState = state.activateFallbackRoute(fallbackRoute)

        assertEquals(destination, fallbackState.candidateActionPoint)
        assertEquals(destination, fallbackState.selectedDestinationPoint)
        assertEquals(destination, fallbackState.selectedTargetPoint)
        assertEquals(fallbackRoute, fallbackState.routeMapState.activeRoute)
        assertEquals(fallbackRoute.polyline, fallbackState.routePolyline)
    }

    @Test
    fun fallbackRouteStateKeepsExternalRouteActionAvailableAfterPlannerFailure() {
        val fallbackRoute = ActiveRoute.fromMyLocation(
            start = gps,
            end = destination,
            polyline = listOf(gps, destination),
            yandexRouteBuilt = false
        )
        val state = MainUiState(
            locationState = LocationState(point = gps),
            destinationPoint = destination,
            selectedTarget = RouteTargetManager.destination(destination)
        )

        val fallbackState = state.activateFallbackRoute(fallbackRoute)
        val endpoints = fallbackState.externalRouteEndpointsForSelectedTarget().getOrThrow()
        val links = RouteTargetManager.externalRouteLinks(
            start = endpoints.start,
            target = endpoints.target,
            routeType = RouteType.CAR
        )

        assertEquals(gps, endpoints.start)
        assertEquals(destination, endpoints.target.point)
        assertNull(fallbackState.fallbackExternalRoute)
        assertTrue(links.appUri.contains("${destination.latitude},${destination.longitude}"))
    }

    @Test
    fun successfulRouteActivationClearsCandidatePointAfterPlannerResult() {
        val fallbackRoute = ActiveRoute.fromMyLocation(
            start = gps,
            end = destination,
            polyline = listOf(gps, destination),
            yandexRouteBuilt = false
        )
        val successfulRoute = ActiveRoute.fromMyLocation(
            start = gps,
            end = destination,
            polyline = listOf(gps, routeEnd, destination),
            yandexRouteBuilt = true
        )
        val state = MainUiState(
            destinationPoint = destination,
            selectedTarget = RouteTargetManager.destination(destination)
        ).activateFallbackRoute(fallbackRoute)

        val routedState = state.activateRoute(successfulRoute)

        assertNull(routedState.candidateActionPoint)
        assertNull(routedState.selectedDestinationPoint)
        assertNull(routedState.selectedTarget)
        assertNull(routedState.fallbackExternalRoute)
        assertEquals(successfulRoute, routedState.routeMapState.activeRoute)
        assertTrue(routedState.activeRouteBuilt)
    }

    @Test
    fun mapPointFallbackRouteStoresExternalRouteStartAndEnd() {
        val fallbackRoute = ActiveRoute.fromMapPoint(
            start = destination,
            end = routeEnd,
            polyline = listOf(destination, routeEnd),
            yandexRouteBuilt = false
        )
        val selectingState = MainUiState(
            locationState = LocationState(point = gps),
            destinationPoint = destination
        ).beginSelectingRouteEnd(destination)

        val fallbackState = selectingState.activateFallbackRoute(
            route = fallbackRoute,
            actionPoint = routeEnd
        )
        val endpoints = fallbackState.externalRouteEndpointsForSelectedTarget().getOrThrow()

        assertEquals(routeEnd, fallbackState.candidateActionPoint)
        assertEquals(routeEnd, fallbackState.selectedDestinationPoint)
        assertEquals(routeEnd, fallbackState.selectedTargetPoint)
        assertFalse(fallbackState.isSelectingRouteEndPoint)
        assertNull(fallbackState.routeStartMarker)
        assertEquals(fallbackRoute, fallbackState.routeMapState.activeRoute)
        assertEquals(FallbackExternalRoute(RouteOrigin.MAP_POINT, destination, routeEnd), fallbackState.fallbackExternalRoute)
        assertEquals(destination, endpoints.start)
        assertEquals(routeEnd, endpoints.target.point)
    }

    @Test
    fun mapPointFallbackExternalRouteWorksWithoutGps() {
        val fallbackRoute = ActiveRoute.fromMapPoint(
            start = destination,
            end = routeEnd,
            polyline = listOf(destination, routeEnd),
            yandexRouteBuilt = false
        )
        val fallbackState = MainUiState(
            locationState = LocationState(point = null)
        ).activateFallbackRoute(
            route = fallbackRoute,
            actionPoint = routeEnd
        )

        val endpoints = fallbackState.externalRouteEndpointsForSelectedTarget().getOrThrow()

        assertEquals(destination, endpoints.start)
        assertEquals(routeEnd, endpoints.target.point)
    }

    @Test
    fun deletingActiveFallbackRouteClearsRetainedEndpointCandidate() {
        val fallbackRoute = ActiveRoute.fromMyLocation(
            start = gps,
            end = destination,
            polyline = listOf(gps, destination),
            yandexRouteBuilt = false
        )
        val fallbackState = MainUiState(
            destinationPoint = destination,
            selectedTarget = RouteTargetManager.destination(destination)
        ).activateFallbackRoute(fallbackRoute)

        val deletedState = fallbackState.deleteActiveRoute()

        assertNull(deletedState.routeMapState.activeRoute)
        assertNull(deletedState.candidateActionPoint)
        assertNull(deletedState.selectedTarget)
        assertTrue(deletedState.routePolyline.isEmpty())
    }

    @Test
    fun deletingActiveMapPointFallbackRouteClearsSavedExternalStartAndEnd() {
        val fallbackRoute = ActiveRoute.fromMapPoint(
            start = destination,
            end = routeEnd,
            polyline = listOf(destination, routeEnd),
            yandexRouteBuilt = false
        )
        val fallbackState = MainUiState().activateFallbackRoute(
            route = fallbackRoute,
            actionPoint = routeEnd
        )

        val deletedState = fallbackState.deleteActiveRoute()

        assertNull(deletedState.routeMapState.activeRoute)
        assertNull(deletedState.candidateActionPoint)
        assertNull(deletedState.selectedTarget)
        assertNull(deletedState.fallbackExternalRoute)
    }

    @Test
    fun deletingActiveRouteDoesNotClearUnrelatedCandidatePoint() {
        val fallbackRoute = ActiveRoute.fromMyLocation(
            start = gps,
            end = routeEnd,
            polyline = listOf(gps, routeEnd),
            yandexRouteBuilt = false
        )
        val state = MainUiState(
            destinationPoint = destination,
            selectedTarget = RouteTargetManager.destination(destination),
            routeMapState = RouteMapState().activate(fallbackRoute)
        )

        val deletedState = state.deleteActiveRoute()

        assertNull(deletedState.routeMapState.activeRoute)
        assertEquals(destination, deletedState.candidateActionPoint)
        assertEquals(destination, deletedState.selectedTargetPoint)
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
        assertEquals(destination, selectedState.candidateActionPoint)
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
    fun beginSelectingRouteEndKeepsActiveRouteAndShowsPendingStart() {
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
        assertEquals(activeRoute, selectedState.routeMapState.activeRoute)
        assertEquals(destination, selectedState.routeMapState.pendingStartPoint)
        assertEquals(destination, selectedState.routeStartMarker)
        assertTrue(selectedState.isSelectingRouteEndPoint)
        assertTrue(selectedState.routePanelVisible)
        assertTrue(selectedState.drawGpsRouteArrow)
        assertEquals(activeRoute.polyline, selectedState.routePolyline)
        assertTrue(selectedState.routeFocusPolyline.isEmpty())
    }

    @Test
    fun cancelRoutePointSelectionPreservesActiveRoute() {
        val activeRoute = ActiveRoute.fromMyLocation(
            start = gps,
            end = routeEnd,
            polyline = listOf(gps, routeEnd),
            yandexRouteBuilt = true
        )
        val selectingState = MainUiState(
            routeMapState = RouteMapState().activate(activeRoute)
        ).beginSelectingRouteEnd(destination)

        val canceledState = selectingState.copy(
            routeMapState = selectingState.routeMapState.cancelPointSelection()
        )

        assertEquals(activeRoute, canceledState.routeMapState.activeRoute)
        assertNull(canceledState.routeMapState.pendingStartPoint)
        assertNull(canceledState.routeStartMarker)
        assertFalse(canceledState.isSelectingRouteEndPoint)
        assertTrue(canceledState.routePanelVisible)
        assertTrue(canceledState.drawGpsRouteArrow)
        assertEquals(activeRoute.polyline, canceledState.routePolyline)
    }

    @Test
    fun beginAndCancelRoutePointSelectionWithoutActiveRouteLeavesNoRoute() {
        val selectingState = MainUiState().beginSelectingRouteEnd(destination)

        val canceledState = selectingState.copy(
            routeMapState = selectingState.routeMapState.cancelPointSelection()
        )

        assertNull(canceledState.routeMapState.activeRoute)
        assertNull(canceledState.routeMapState.pendingStartPoint)
        assertNull(canceledState.routeStartMarker)
        assertFalse(canceledState.routePanelVisible)
        assertFalse(canceledState.drawGpsRouteArrow)
        assertTrue(canceledState.routePolyline.isEmpty())
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
    fun selectingEndLongTapReplacesOldRouteWhenNewRouteActivates() {
        val oldRoute = ActiveRoute.fromMyLocation(
            start = gps,
            end = destination,
            polyline = listOf(gps, destination),
            yandexRouteBuilt = true
        )
        val selectingState = MainUiState(
            routeMapState = RouteMapState().activate(oldRoute)
        ).beginSelectingRouteEnd(destination)

        val action = selectingState.mapLongTapAction(routeEnd) as MapLongTapAction.BuildRouteFromMapPoint
        val newRoute = ActiveRoute.fromMapPoint(
            start = action.start,
            end = action.end,
            polyline = listOf(action.start, action.end),
            yandexRouteBuilt = true
        )
        val routedState = selectingState.activateRoute(newRoute)

        assertEquals(newRoute, routedState.routeMapState.activeRoute)
        assertNull(routedState.routeMapState.pendingStartPoint)
        assertNull(routedState.routeStartMarker)
        assertEquals(routeEnd, routedState.activeRouteEndPoint)
        assertEquals(newRoute.polyline, routedState.routePolyline)
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
    fun selectingEndTargetTapReplacesOldRouteWhenNewRouteActivates() {
        val oldRoute = ActiveRoute.fromMyLocation(
            start = gps,
            end = destination,
            polyline = listOf(gps, destination),
            yandexRouteBuilt = true
        )
        val selectingState = MainUiState(
            routeMapState = RouteMapState().activate(oldRoute)
        ).beginSelectingRouteEnd(destination)

        val action = selectingState.mapTargetTapAction(importedTarget) as MapTargetTapAction.BuildRouteFromMapPoint
        val newRoute = ActiveRoute.fromMapPoint(
            start = action.start,
            end = action.end,
            polyline = listOf(action.start, action.end),
            yandexRouteBuilt = true
        )
        val routedState = selectingState.activateRoute(newRoute)

        assertEquals(newRoute, routedState.routeMapState.activeRoute)
        assertNull(routedState.routeMapState.pendingStartPoint)
        assertNull(routedState.routeStartMarker)
        assertEquals(routeEnd, routedState.activeRouteEndPoint)
        assertEquals(newRoute.polyline, routedState.routePolyline)
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
