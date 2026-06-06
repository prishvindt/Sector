package com.prishvindt.sector.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteRequestGateTest {
    @Test
    fun invalidateMakesExistingRequestStale() {
        val gate = RouteRequestGate()
        val requestId = gate.next()

        gate.invalidate()

        assertFalse(gate.isCurrent(requestId))
    }

    @Test
    fun newerRequestSupersedesOlderRequest() {
        val gate = RouteRequestGate()
        val firstRequestId = gate.next()
        val secondRequestId = gate.next()

        assertFalse(gate.isCurrent(firstRequestId))
        assertTrue(gate.isCurrent(secondRequestId))
    }

    @Test
    fun invalidatedRouteRequestStaysStaleUntilEndpointSelectionBuildsNewRoute() {
        val gate = RouteRequestGate()
        val inFlightRequestId = gate.next()

        gate.invalidate()
        val selectedEndpointRequestId = gate.next()

        assertFalse(gate.isCurrent(inFlightRequestId))
        assertTrue(gate.isCurrent(selectedEndpointRequestId))
    }
}
