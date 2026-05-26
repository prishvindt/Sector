package com.prishvindt.sector.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoMathTest {
    @Test
    fun normalizeBearingWrapsBothDirections() {
        assertEquals(350.0, GeoMath.normalizeBearing(-10.0), 0.0001)
        assertEquals(10.0, GeoMath.normalizeBearing(370.0), 0.0001)
    }

    @Test
    fun distanceMetersMatchesOneDegreeAtEquator() {
        val distance = GeoMath.distanceMeters(0.0, 0.0, 0.0, 1.0)
        assertTrue(distance in 111_000.0..111_400.0)
    }

    @Test
    fun destinationPointMovesNorth() {
        val point = GeoMath.destinationPoint(0.0, 0.0, 0.0, 1000.0)
        assertTrue(point.latitude in 0.0089..0.0091)
        assertEquals(0.0, point.longitude, 0.0001)
    }
}
