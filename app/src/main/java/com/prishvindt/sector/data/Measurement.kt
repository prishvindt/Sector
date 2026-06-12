package com.prishvindt.sector.data

import com.prishvindt.sector.domain.AzimuthDistance

enum class MeasurementSource {
    SELF,
    IMPORTED
}

data class Measurement(
    val measurementId: String,
    val sessionMarker: String = "SECTOR_MEASUREMENT_V1",
    val callsign: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Double?,
    val satelliteCount: Int?,
    val azimuthDeg: Double,
    val azimuthErrorDeg: Double,
    val distanceKm: Double = AzimuthDistance.DEFAULT_KM,
    val timestamp: String,
    val source: MeasurementSource,
    val active: Boolean = true,
    val note: String? = null,
    val colorArgb: Int? = null
)
