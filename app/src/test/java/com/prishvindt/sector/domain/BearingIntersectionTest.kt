package com.prishvindt.sector.domain

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BearingIntersectionTest {
    @Test
    fun findsForwardIntersection() {
        val intersection = BearingIntersection.intersectionOfBearings(
            pointA = GeoPoint(0.0, 0.0),
            bearingA = 90.0,
            pointB = GeoPoint(-1.0, 1.0),
            bearingB = 0.0
        )
        assertNotNull(intersection)
        assertTrue(intersection!!.latitude in -0.01..0.01)
        assertTrue(intersection.longitude in 0.99..1.01)
    }

    @Test
    fun returnsNullWhenIntersectionIsBehindRay() {
        val intersection = BearingIntersection.intersectionOfBearings(
            pointA = GeoPoint(0.0, 0.0),
            bearingA = 270.0,
            pointB = GeoPoint(-1.0, 1.0),
            bearingB = 0.0
        )
        assertNull(intersection)
    }
}
