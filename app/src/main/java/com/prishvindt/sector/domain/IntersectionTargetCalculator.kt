package com.prishvindt.sector.domain

import com.prishvindt.sector.data.Measurement

object IntersectionTargetCalculator {
    fun calculate(
        measurements: List<Measurement>,
        currentPoint: GeoPoint?
    ): RouteTarget? {
        val active = measurements.filter { it.active }
        for (i in active.indices) {
            for (j in i + 1 until active.size) {
                val first = active[i]
                val second = active[j]
                val point = BearingIntersection.intersectionOfBearings(
                    GeoPoint(first.latitude, first.longitude),
                    first.azimuthDeg,
                    GeoPoint(second.latitude, second.longitude),
                    second.azimuthDeg
                ) ?: continue
                val fromMe = currentPoint?.let {
                    "До пересечения: ${GeoMath.formatDistance(GeoMath.distanceMeters(it, point))}"
                }
                val fromSecond = "От ${second.callsign.ifBlank { "замера" }}: " +
                    GeoMath.formatDistance(GeoMath.distanceMeters(GeoPoint(second.latitude, second.longitude), point))
                return RouteTarget(
                    type = RouteTargetType.INTERSECTION,
                    point = point,
                    title = "Пересечение",
                    subtitle = listOfNotNull(fromMe, fromSecond).joinToString("\n")
                )
            }
        }
        return null
    }
}
