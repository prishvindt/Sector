package com.prishvindt.sector.map

import android.graphics.Color
import com.prishvindt.sector.data.MeasurementColor

object MapStyle {
    const val IMPORTED_COLOR: Int = MeasurementColor.DEFAULT_IMPORTED_ARGB
    const val REMOTE_LOCATION_COLOR: Int = 0xFF00A8A8.toInt()
    const val MAP_NOTE_COLOR: Int = 0xFFFFC857.toInt()
    const val INTERSECTION_COLOR: Int = 0xFFFF5A3D.toInt()
    const val DESTINATION_COLOR: Int = 0xFF9B51E0.toInt()
    const val ROUTE_START_COLOR: Int = 0xFF1D8F63.toInt()
    const val ROUTE_COLOR: Int = 0xFF39FF14.toInt()
    const val FALLBACK_ROUTE_COLOR: Int = 0xFF111827.toInt()
    const val ROUTE_STROKE_WIDTH: Float = 4.3f
    const val FALLBACK_ROUTE_STROKE_WIDTH: Float = 2.3f
    const val ROUTE_LAYER_Z_INDEX: Float = 10f
    const val GPS_LAYER_Z_INDEX: Float = 20f
    const val AZIMUTH_LAYER_Z_INDEX: Float = 30f
    const val IMPORTED_LOCATION_LAYER_Z_INDEX: Float = 40f
    const val MAP_NOTE_LAYER_Z_INDEX: Float = 50f
    const val TARGET_LAYER_Z_INDEX: Float = 60f
    const val AZIMUTH_RAY_DOT_SIZE_PX: Float = 2f
    const val AZIMUTH_RAY_DOT_SPACING_PX: Float = 6f
    const val AZIMUTH_RAY_DOT_GAP_PX: Float = 4f

    fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
}
