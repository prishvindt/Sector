package com.prishvindt.sector.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapStyleTest {
    @Test
    fun normalRouteUsesBrightGreenStyle() {
        assertEquals(0xFF39FF14.toInt(), MapStyle.ROUTE_COLOR)
        assertTrue(MapStyle.ROUTE_STROKE_WIDTH > MapStyle.FALLBACK_ROUTE_STROKE_WIDTH)
    }

    @Test
    fun fallbackRouteIsVisuallyDifferentFromNormalRoute() {
        assertEquals(0xFF111827.toInt(), MapStyle.FALLBACK_ROUTE_COLOR)
        assertTrue(MapStyle.FALLBACK_ROUTE_STROKE_WIDTH < MapStyle.ROUTE_STROKE_WIDTH)
    }
}
