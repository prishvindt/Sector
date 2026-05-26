package com.prishvindt.sector.domain

object SectorCalculator {
    fun centralLine(
        origin: GeoPoint,
        azimuthDeg: Double,
        rangeKm: Double
    ): List<GeoPoint> {
        return listOf(
            origin,
            GeoMath.destinationPoint(origin, azimuthDeg, rangeKm * 1000.0)
        )
    }

    fun sectorPolygon(
        origin: GeoPoint,
        azimuthDeg: Double,
        errorDeg: Double,
        rangeKm: Double,
        arcSteps: Int = 36
    ): List<GeoPoint> {
        val safeSteps = arcSteps.coerceAtLeast(4)
        val start = azimuthDeg - errorDeg
        val end = azimuthDeg + errorDeg
        val arc = (0..safeSteps).map { index ->
            val bearing = start + (end - start) * index / safeSteps
            GeoMath.destinationPoint(origin, bearing, rangeKm * 1000.0)
        }
        return buildList {
            add(origin)
            addAll(arc)
        }
    }
}
