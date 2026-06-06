package com.prishvindt.sector.data

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
    val signalDbm: Int?,
    val rangeKm: Double = 15.0,
    val timestamp: String,
    val source: MeasurementSource,
    val active: Boolean = true,
    val note: String? = null,
    val colorArgb: Int? = null
)
