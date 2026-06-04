package com.prishvindt.sector.data

data class ImportedLocation(
    val locationKey: String,
    val callsign: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double?,
    val timestampEpochSeconds: Long,
    val receivedAtEpochMillis: Long
)
