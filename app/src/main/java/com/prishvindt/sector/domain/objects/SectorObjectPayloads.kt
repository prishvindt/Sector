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
    val title: String,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long,
    val attachments: List<MapNoteAttachmentPayloadV1> = emptyList()
)

data class MapNoteAttachmentPayloadV1(
    val attachmentId: String,
    val type: MapNoteAttachmentType,
    val localPath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long?,
    val createdAt: Long,
    val mediaIncluded: Boolean = true
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

enum class MapNoteAttachmentType(val wireName: String) {
    PHOTO("PHOTO"),
    AUDIO("AUDIO");

    companion object {
        fun fromWireName(value: String?): MapNoteAttachmentType? =
            entries.firstOrNull { it.wireName == value }
    }
}
