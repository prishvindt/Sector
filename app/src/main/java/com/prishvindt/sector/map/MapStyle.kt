package com.prishvindt.sector.map

import android.graphics.Color
import com.prishvindt.sector.data.MeasurementColor

object MapStyle {
    const val IMPORTED_COLOR: Int = MeasurementColor.DEFAULT_IMPORTED_ARGB
    const val REMOTE_LOCATION_COLOR: Int = 0xFF00A8A8.toInt()
    const val INTERSECTION_COLOR: Int = 0xFFFF5A3D.toInt()
    const val DESTINATION_COLOR: Int = 0xFF9B51E0.toInt()
    const val ROUTE_COLOR: Int = 0xFF111827.toInt()

    fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
}
