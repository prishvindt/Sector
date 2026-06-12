package com.prishvindt.sector.map

import com.prishvindt.sector.domain.AzimuthDistance
import com.prishvindt.sector.domain.GeoMath
import com.prishvindt.sector.domain.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AzimuthRayGeometryTest {
    @Test
    fun distanceLabelUsesRussianCommaAndNoTrailingZeroForIntegers() {
        assertEquals("10 км", AzimuthRayGeometry.distanceLabel(10.0))
        assertEquals("10,5 км", AzimuthRayGeometry.distanceLabel(10.5))
    }

    @Test
    fun rayLineLengthIsTwentyPercentLongerThanDistance() {
        assertEquals(12.0, AzimuthRayGeometry.rayLineLengthKm(10.0), 0.0)
        assertEquals(18.0, AzimuthRayGeometry.rayLineLengthKm(15.0), 0.0)
    }

    @Test
    fun distancePointIsPlacedAtDistanceKm() {
        val origin = GeoPoint(55.0, 37.0)
        val point = AzimuthRayGeometry.distancePoint(
            origin = origin,
            azimuthDeg = 90.0,
            distanceKm = 10.0
        )

        assertEquals(10.0, GeoMath.distanceMeters(origin, point) / 1000.0, 0.02)
    }

    @Test
    fun fillFadeStartsBeforeDistancePointAndEndsAtRayLineLength() {
        val segments = AzimuthRayGeometry.fillSegments(distanceKm = 10.0, baseAlpha = 44)

        assertEquals(0.0, segments.first().startKm, 0.0)
        assertEquals(9.0, segments.first().endKm, 0.0)
        assertEquals(12.0, segments.last().endKm, 0.0001)
        assertEquals(AzimuthDistance.rayLineLengthKm(10.0), segments.last().endKm, 0.0001)
        assertTrue(segments.last().alpha > 0)
        assertTrue(segments.last().alpha < segments.first().alpha)
    }

    @Test
    fun dottedLineSegmentsStayWithinRayLineLength() {
        val segments = AzimuthRayGeometry.dottedLineSegments(lineLengthKm = 12.0)

        assertTrue(segments.isNotEmpty())
        assertEquals(0.0, segments.first().startKm, 0.0)
        assertTrue(segments.size <= 96)
        assertTrue(segments.all { it.endKm <= 12.0 })
        assertTrue(segments.all { it.endKm > it.startKm })
        assertTrue(segments.all { it.endKm - it.startKm <= 0.08 })
    }
}
