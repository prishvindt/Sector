package com.prishvindt.sector.domain

object SectorCalculator {
    fun centralLine(
        origin: GeoPoint,
        azimuthDeg: Double,
        lengthKm: Double
    ): List<GeoPoint> {
        return listOf(
            origin,
            GeoMath.destinationPoint(origin, azimuthDeg, lengthKm * 1000.0)
        )
    }

    fun sectorPolygon(
        origin: GeoPoint,
        azimuthDeg: Double,
        errorDeg: Double,
        outerDistanceKm: Double,
        arcSteps: Int = 36
    ): List<GeoPoint> {
        val safeSteps = arcSteps.coerceAtLeast(4)
        val start = azimuthDeg - errorDeg
        val end = azimuthDeg + errorDeg
        val arc = (0..safeSteps).map { index ->
            val bearing = start + (end - start) * index / safeSteps
            GeoMath.destinationPoint(origin, bearing, outerDistanceKm * 1000.0)
        }
        return buildList {
            add(origin)
            addAll(arc)
        }
    }

    fun sectorBandPolygon(
        origin: GeoPoint,
        azimuthDeg: Double,
        errorDeg: Double,
        innerDistanceKm: Double,
        outerDistanceKm: Double,
        arcSteps: Int = 36
    ): List<GeoPoint> {
        require(outerDistanceKm > innerDistanceKm) { "outerDistanceKm must be greater than innerDistanceKm" }
        if (innerDistanceKm <= 0.0) {
            return sectorPolygon(
                origin = origin,
                azimuthDeg = azimuthDeg,
                errorDeg = errorDeg,
                outerDistanceKm = outerDistanceKm,
                arcSteps = arcSteps
            )
        }
        val safeSteps = arcSteps.coerceAtLeast(4)
        val start = azimuthDeg - errorDeg
        val end = azimuthDeg + errorDeg
        val outerArc = (0..safeSteps).map { index ->
            val bearing = start + (end - start) * index / safeSteps
            GeoMath.destinationPoint(origin, bearing, outerDistanceKm * 1000.0)
        }
        val innerArc = (safeSteps downTo 0).map { index ->
            val bearing = start + (end - start) * index / safeSteps
            GeoMath.destinationPoint(origin, bearing, innerDistanceKm * 1000.0)
        }
        return outerArc + innerArc
    }
}
