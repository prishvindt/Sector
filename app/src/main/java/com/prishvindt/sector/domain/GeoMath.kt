package com.prishvindt.sector.domain

import kotlin.math.*

data class GeoPoint(
    val latitude: Double,
    val longitude: Double
)

object GeoMath {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun normalizeBearing(deg: Double): Double {
        val normalized = deg % 360.0
        return if (normalized < 0.0) normalized + 360.0 else normalized
    }

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = lat1.toRadians()
        val phi2 = lat2.toRadians()
        val deltaPhi = (lat2 - lat1).toRadians()
        val deltaLambda = (lon2 - lon1).toRadians()

        val a = sin(deltaPhi / 2).pow(2) +
            cos(phi1) * cos(phi2) * sin(deltaLambda / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    fun distanceMeters(a: GeoPoint, b: GeoPoint): Double =
        distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)

    fun destinationPoint(
        lat: Double,
        lon: Double,
        bearingDeg: Double,
        distanceMeters: Double
    ): GeoPoint {
        val angularDistance = distanceMeters / EARTH_RADIUS_METERS
        val bearing = normalizeBearing(bearingDeg).toRadians()
        val phi1 = lat.toRadians()
        val lambda1 = lon.toRadians()

        val sinPhi2 = sin(phi1) * cos(angularDistance) +
            cos(phi1) * sin(angularDistance) * cos(bearing)
        val phi2 = asin(sinPhi2.coerceIn(-1.0, 1.0))
        val y = sin(bearing) * sin(angularDistance) * cos(phi1)
        val x = cos(angularDistance) - sin(phi1) * sin(phi2)
        val lambda2 = lambda1 + atan2(y, x)

        return GeoPoint(
            latitude = phi2.toDegrees(),
            longitude = normalizeLongitude(lambda2.toDegrees())
        )
    }

    fun destinationPoint(point: GeoPoint, bearingDeg: Double, distanceMeters: Double): GeoPoint =
        destinationPoint(point.latitude, point.longitude, bearingDeg, distanceMeters)

    fun initialBearing(from: GeoPoint, to: GeoPoint): Double {
        val phi1 = from.latitude.toRadians()
        val phi2 = to.latitude.toRadians()
        val deltaLambda = (to.longitude - from.longitude).toRadians()
        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        return normalizeBearing(atan2(y, x).toDegrees())
    }

    fun formatDistance(meters: Double): String {
        return if (meters < 1000.0) {
            "${meters.roundToInt()} м"
        } else {
            String.format(java.util.Locale.US, "%.2f км", meters / 1000.0)
        }
    }

    internal fun Double.toRadians(): Double = Math.toRadians(this)
    internal fun Double.toDegrees(): Double = Math.toDegrees(this)

    private fun normalizeLongitude(deg: Double): Double =
        ((deg + 540.0) % 360.0) - 180.0
}
