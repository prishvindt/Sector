package com.prishvindt.sector.map

import com.prishvindt.sector.domain.AzimuthDistance
import com.prishvindt.sector.domain.GeoMath
import com.prishvindt.sector.domain.GeoPoint
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object AzimuthRayGeometry {
    fun rayLineLengthKm(distanceKm: Double): Double =
        AzimuthDistance.rayLineLengthKm(distanceKm)

    fun distancePoint(
        origin: GeoPoint,
        azimuthDeg: Double,
        distanceKm: Double
    ): GeoPoint =
        GeoMath.destinationPoint(origin, azimuthDeg, distanceKm * 1000.0)

    fun distanceLabel(distanceKm: Double): String =
        AzimuthDistance.formatLabel(distanceKm)

    fun fillSegments(
        distanceKm: Double,
        baseAlpha: Int,
        fadeSegmentCount: Int = 6
    ): List<AzimuthFillSegment> {
        val safeBaseAlpha = baseAlpha.coerceIn(0, 255)
        val finalAlpha = max(1, (safeBaseAlpha * AzimuthDistance.FILL_FINAL_ALPHA_RATIO).roundToInt())
        val fadeStartKm = AzimuthDistance.fillFadeStartKm(distanceKm)
        val endKm = rayLineLengthKm(distanceKm)
        val segmentCount = fadeSegmentCount.coerceAtLeast(1)
        val fadeLengthKm = endKm - fadeStartKm
        return buildList {
            add(AzimuthFillSegment(startKm = 0.0, endKm = fadeStartKm, alpha = safeBaseAlpha))
            repeat(segmentCount) { index ->
                val start = fadeStartKm + fadeLengthKm * index / segmentCount
                val end = fadeStartKm + fadeLengthKm * (index + 1) / segmentCount
                val progress = (index + 1).toDouble() / segmentCount
                val alpha = (safeBaseAlpha + (finalAlpha - safeBaseAlpha) * progress)
                    .roundToInt()
                    .coerceIn(finalAlpha, safeBaseAlpha)
                add(AzimuthFillSegment(startKm = start, endKm = end, alpha = alpha))
            }
        }
    }

    fun dottedLineSegments(
        lineLengthKm: Double,
        maxSegments: Int = 96
    ): List<AzimuthLineSegment> {
        if (lineLengthKm <= 0.0) return emptyList()
        val stepKm = max(MinDotStepKm, lineLengthKm / maxSegments.coerceAtLeast(1))
        val dotLengthKm = min(MaxDotLengthKm, stepKm * DotLengthRatio)
            .coerceAtLeast(MinDotLengthKm)
        val segments = mutableListOf<AzimuthLineSegment>()
        var startKm = 0.0
        while (startKm < lineLengthKm && segments.size < maxSegments) {
            val endKm = min(startKm + dotLengthKm, lineLengthKm)
            if (endKm > startKm) {
                segments += AzimuthLineSegment(startKm = startKm, endKm = endKm)
            }
            startKm += stepKm
        }
        return segments
    }

    private const val MinDotStepKm = 0.2
    private const val DotLengthRatio = 0.22
    private const val MinDotLengthKm = 0.025
    private const val MaxDotLengthKm = 0.08
}

data class AzimuthFillSegment(
    val startKm: Double,
    val endKm: Double,
    val alpha: Int
)

data class AzimuthLineSegment(
    val startKm: Double,
    val endKm: Double
)
