package com.prishvindt.sector.domain

import java.math.BigDecimal
import java.math.RoundingMode

object AzimuthDistance {
    const val DEFAULT_KM: Double = 15.0
    const val MIN_KM: Double = 0.1
    const val MAX_KM: Double = 50.0
    const val RAY_LINE_LENGTH_MULTIPLIER: Double = 1.2
    const val FILL_FADE_START_MULTIPLIER: Double = 0.9
    const val FILL_FINAL_ALPHA_RATIO: Double = 0.1

    fun parseInput(text: String): Double? =
        text.trim()
            .replace(',', '.')
            .takeIf { it.isNotBlank() }
            ?.toDoubleOrNull()

    fun isValid(distanceKm: Double): Boolean =
        distanceKm.isFinite() && distanceKm in MIN_KM..MAX_KM

    fun rayLineLengthKm(distanceKm: Double): Double =
        distanceKm * RAY_LINE_LENGTH_MULTIPLIER

    fun fillFadeStartKm(distanceKm: Double): Double =
        distanceKm * FILL_FADE_START_MULTIPLIER

    fun formatLabel(distanceKm: Double): String {
        val value = BigDecimal.valueOf(distanceKm)
            .setScale(2, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
            .replace('.', ',')
        return "$value км"
    }
}
