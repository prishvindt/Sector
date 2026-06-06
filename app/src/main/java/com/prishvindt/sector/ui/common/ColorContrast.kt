package com.prishvindt.sector.ui.common

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

private const val LightBackgroundLuminanceThreshold = 0.5
private val DarkContentOnLight = Color(0xFF111827)

internal fun contrastingContentColor(background: Color): Color =
    if (background.relativeLuminance() > LightBackgroundLuminanceThreshold) {
        DarkContentOnLight
    } else {
        Color.White
    }

private fun Color.relativeLuminance(): Double =
    0.2126 * red.linearizedColorChannel() +
        0.7152 * green.linearizedColorChannel() +
        0.0722 * blue.linearizedColorChannel()

private fun Float.linearizedColorChannel(): Double {
    val channel = toDouble()
    return if (channel <= 0.03928) {
        channel / 12.92
    } else {
        ((channel + 0.055) / 1.055).pow(2.4)
    }
}
