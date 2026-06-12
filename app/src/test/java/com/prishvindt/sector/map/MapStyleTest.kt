package com.prishvindt.sector.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapStyleTest {
    @Test
    fun normalRouteUsesBrightGreenStyle() {
        assertEquals(0xFF39FF14.toInt(), MapStyle.ROUTE_COLOR)
        assertEquals(4.3f, MapStyle.ROUTE_STROKE_WIDTH, 0.001f)
        assertTrue(MapStyle.ROUTE_STROKE_WIDTH > MapStyle.FALLBACK_ROUTE_STROKE_WIDTH)
    }

    @Test
    fun fallbackRouteIsVisuallyDifferentFromNormalRoute() {
        assertEquals(0xFF111827.toInt(), MapStyle.FALLBACK_ROUTE_COLOR)
        assertEquals(2.3f, MapStyle.FALLBACK_ROUTE_STROKE_WIDTH, 0.001f)
        assertTrue(MapStyle.FALLBACK_ROUTE_STROKE_WIDTH < MapStyle.ROUTE_STROKE_WIDTH)
    }

    @Test
    fun azimuthLayerDrawsAboveRouteLayer() {
        assertTrue(MapStyle.AZIMUTH_LAYER_Z_INDEX > MapStyle.ROUTE_LAYER_Z_INDEX)
        assertTrue(MapStyle.MAP_NOTE_LAYER_Z_INDEX > MapStyle.ROUTE_LAYER_Z_INDEX)
        assertTrue(MapStyle.TARGET_LAYER_Z_INDEX > MapStyle.ROUTE_LAYER_Z_INDEX)
    }

    @Test
    fun azimuthRayUsesTinyFrequentDotStyle() {
        assertEquals(2f, MapStyle.AZIMUTH_RAY_DOT_SIZE_PX, 0.001f)
        assertEquals(6f, MapStyle.AZIMUTH_RAY_DOT_SPACING_PX, 0.001f)
        assertEquals(4f, MapStyle.AZIMUTH_RAY_DOT_GAP_PX, 0.001f)
        assertEquals(
            MapStyle.AZIMUTH_RAY_DOT_SPACING_PX,
            MapStyle.AZIMUTH_RAY_DOT_SIZE_PX + MapStyle.AZIMUTH_RAY_DOT_GAP_PX,
            0.001f
        )
        assertTrue(MapStyle.AZIMUTH_RAY_DOT_SIZE_PX < MapStyle.FALLBACK_ROUTE_STROKE_WIDTH)
    }
}
