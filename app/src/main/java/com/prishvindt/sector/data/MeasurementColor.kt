package com.prishvindt.sector.data

object MeasurementColor {
    const val DEFAULT_IMPORTED_ARGB: Int = 0xFF27AE60.toInt()

    fun resolve(
        measurement: Measurement,
        ownColorArgb: Int,
        importedDefaultArgb: Int = DEFAULT_IMPORTED_ARGB
    ): Int = when (measurement.source) {
        MeasurementSource.SELF -> ownColorArgb
        MeasurementSource.IMPORTED -> measurement.colorArgb ?: importedDefaultArgb
    }
}
