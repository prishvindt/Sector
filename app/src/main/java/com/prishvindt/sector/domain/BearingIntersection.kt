package com.prishvindt.sector.domain

import kotlin.math.*

object BearingIntersection {
    private const val EPS = 1e-10

    fun intersectionOfBearings(
        pointA: GeoPoint,
        bearingA: Double,
        pointB: GeoPoint,
        bearingB: Double
    ): GeoPoint? {
        if (GeoMath.distanceMeters(pointA, pointB) < 0.5) return null

        val phi1 = pointA.latitude.toRadians()
        val lambda1 = pointA.longitude.toRadians()
        val phi2 = pointB.latitude.toRadians()
        val lambda2 = pointB.longitude.toRadians()
        val theta13 = GeoMath.normalizeBearing(bearingA).toRadians()
        val theta23 = GeoMath.normalizeBearing(bearingB).toRadians()
        val deltaPhi = phi2 - phi1
        val deltaLambda = lambda2 - lambda1

        val delta12 = 2 * asin(
            sqrt(
                sin(deltaPhi / 2).pow(2) +
                    cos(phi1) * cos(phi2) * sin(deltaLambda / 2).pow(2)
            ).coerceIn(0.0, 1.0)
        )
        if (delta12.absoluteValue < EPS) return null

        val thetaA = acos(
            ((sin(phi2) - sin(phi1) * cos(delta12)) / (sin(delta12) * cos(phi1)))
                .coerceIn(-1.0, 1.0)
        )
        val thetaB = acos(
            ((sin(phi1) - sin(phi2) * cos(delta12)) / (sin(delta12) * cos(phi2)))
                .coerceIn(-1.0, 1.0)
        )

        val (theta12, theta21) = if (sin(deltaLambda) > 0) {
            thetaA to (2 * PI - thetaB)
        } else {
            (2 * PI - thetaA) to thetaB
        }

        val alpha1 = signedAngle(theta13 - theta12)
        val alpha2 = signedAngle(theta21 - theta23)

        if (sin(alpha1).absoluteValue < EPS && sin(alpha2).absoluteValue < EPS) return null
        if (sin(alpha1) * sin(alpha2) < 0) return null

        val alpha3 = acos(
            (-cos(alpha1) * cos(alpha2) + sin(alpha1) * sin(alpha2) * cos(delta12))
                .coerceIn(-1.0, 1.0)
        )
        val delta13 = atan2(
            sin(delta12) * sin(alpha1) * sin(alpha2),
            cos(alpha2) + cos(alpha1) * cos(alpha3)
        )
        if (!delta13.isFinite() || delta13 < 0) return null

        val phi3 = asin(
            (sin(phi1) * cos(delta13) + cos(phi1) * sin(delta13) * cos(theta13))
                .coerceIn(-1.0, 1.0)
        )
        val deltaLambda13 = atan2(
            sin(theta13) * sin(delta13) * cos(phi1),
            cos(delta13) - sin(phi1) * sin(phi3)
        )
        val lambda3 = lambda1 + deltaLambda13
        val intersection = GeoPoint(
            latitude = phi3.toDegrees(),
            longitude = ((lambda3.toDegrees() + 540.0) % 360.0) - 180.0
        )

        return intersection.takeIf {
            isAhead(pointA, bearingA, it) && isAhead(pointB, bearingB, it)
        }
    }

    private fun isAhead(origin: GeoPoint, bearing: Double, target: GeoPoint): Boolean {
        if (GeoMath.distanceMeters(origin, target) < 1.0) return true
        val diff = signedAngle(
            GeoMath.initialBearing(origin, target).toRadians() -
                GeoMath.normalizeBearing(bearing).toRadians()
        ).absoluteValue.toDegrees()
        return diff <= 90.0
    }

    private fun signedAngle(rad: Double): Double =
        atan2(sin(rad), cos(rad))

    private fun Double.toRadians(): Double = Math.toRadians(this)
    private fun Double.toDegrees(): Double = Math.toDegrees(this)
}
