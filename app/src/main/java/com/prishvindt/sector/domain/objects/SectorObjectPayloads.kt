package com.prishvindt.sector.domain.objects

data class AzimuthRayPayloadV1(
    val latitude: Double,
    val longitude: Double,
    val azimuth: Double,
    val error: Double,
    val signal: Int?,
    val callsign: String?
)

data class SharedLocationPayloadV1(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double?,
    val bearing: Double?,
    val timestamp: Long,
    val callsign: String?
)

data class MapNotePayloadV1(
    val latitude: Double,
    val longitude: Double,
    val title: String?,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long
)

data class LiveLocationPayloadV1(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double?,
    val bearing: Double?,
    val speed: Double?,
    val timestamp: Long,
    val sessionId: String?,
    val callsign: String?
)
