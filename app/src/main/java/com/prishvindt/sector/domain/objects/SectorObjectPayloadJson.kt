package com.prishvindt.sector.domain.objects

import com.prishvindt.sector.domain.AzimuthDistance

object SectorObjectPayloadJson {
    fun encode(payload: AzimuthRayPayloadV1): String =
        SectorJson.stringify(payload.toJson())

    fun encode(payload: SharedLocationPayloadV1): String =
        SectorJson.stringify(payload.toJson())

    fun encode(payload: MapNotePayloadV1): String =
        SectorJson.stringify(payload.toJson())

    fun encode(payload: LiveLocationPayloadV1): String =
        SectorJson.stringify(payload.toJson())

    fun decodeAzimuthRay(json: String): Result<AzimuthRayPayloadV1> =
        runCatching {
            val fields = parseObject(json)
            AzimuthRayPayloadV1(
                latitude = fields.requiredDouble("latitude"),
                longitude = fields.requiredDouble("longitude"),
                azimuth = fields.requiredDouble("azimuth"),
                error = fields.requiredDouble("error"),
                distanceKm = fields.optionalDouble("distanceKm") ?: AzimuthDistance.DEFAULT_KM,
                callsign = fields.optionalString("callsign")
            ).also { it.validate() }
        }

    fun decodeSharedLocation(json: String): Result<SharedLocationPayloadV1> =
        runCatching {
            val fields = parseObject(json)
            SharedLocationPayloadV1(
                latitude = fields.requiredDouble("latitude"),
                longitude = fields.requiredDouble("longitude"),
                accuracyMeters = fields.optionalDouble("accuracyMeters"),
                bearing = fields.optionalDouble("bearing"),
                timestamp = fields.requiredLong("timestamp"),
                callsign = fields.optionalString("callsign")
            ).also { it.validate() }
        }

    fun decodeMapNote(json: String): Result<MapNotePayloadV1> =
        runCatching {
            val fields = parseObject(json)
            MapNotePayloadV1(
                latitude = fields.requiredDouble("latitude"),
                longitude = fields.requiredDouble("longitude"),
                title = fields.optionalString("title").orEmpty(),
                text = fields.requiredString("text"),
                createdAt = fields.requiredLong("createdAt"),
                updatedAt = fields.requiredLong("updatedAt"),
                attachments = fields["attachments"]
                    ?.asArrayOrNull()
                    ?.mapNotNull { value -> runCatching { decodeMapNoteAttachment(value) }.getOrNull() }
                    .orEmpty()
            ).also { it.validate() }
        }

    fun decodeLiveLocation(json: String): Result<LiveLocationPayloadV1> =
        runCatching {
            val fields = parseObject(json)
            LiveLocationPayloadV1(
                latitude = fields.requiredDouble("latitude"),
                longitude = fields.requiredDouble("longitude"),
                accuracyMeters = fields.optionalDouble("accuracyMeters"),
                bearing = fields.optionalDouble("bearing"),
                speed = fields.optionalDouble("speed"),
                timestamp = fields.requiredLong("timestamp"),
                sessionId = fields.optionalString("sessionId"),
                callsign = fields.optionalString("callsign")
            ).also { it.validate() }
        }

    fun parsePayloadJson(json: String): Result<SectorJsonValue> =
        SectorJson.parse(json)

    fun stringifyPayload(value: SectorJsonValue): String =
        SectorJson.stringify(value)

    fun AzimuthRayPayloadV1.toJson(): SectorJsonValue =
        SectorJson.obj(
            "latitude" to SectorJson.number(latitude),
            "longitude" to SectorJson.number(longitude),
            "azimuth" to SectorJson.number(azimuth),
            "error" to SectorJson.number(error),
            "distanceKm" to SectorJson.number(distanceKm),
            "callsign" to SectorJson.nullableString(callsign)
        )

    fun SharedLocationPayloadV1.toJson(): SectorJsonValue =
        SectorJson.obj(
            "latitude" to SectorJson.number(latitude),
            "longitude" to SectorJson.number(longitude),
            "accuracyMeters" to SectorJson.nullableNumber(accuracyMeters),
            "bearing" to SectorJson.nullableNumber(bearing),
            "timestamp" to SectorJson.number(timestamp),
            "callsign" to SectorJson.nullableString(callsign)
        )

    fun MapNotePayloadV1.toJson(): SectorJsonValue =
        SectorJson.obj(
            "latitude" to SectorJson.number(latitude),
            "longitude" to SectorJson.number(longitude),
            "title" to SectorJson.string(title),
            "text" to SectorJson.string(text),
            "createdAt" to SectorJson.number(createdAt),
            "updatedAt" to SectorJson.number(updatedAt),
            "attachments" to SectorJson.array(attachments.map { it.toJson() })
        )

    fun LiveLocationPayloadV1.toJson(): SectorJsonValue =
        SectorJson.obj(
            "latitude" to SectorJson.number(latitude),
            "longitude" to SectorJson.number(longitude),
            "accuracyMeters" to SectorJson.nullableNumber(accuracyMeters),
            "bearing" to SectorJson.nullableNumber(bearing),
            "speed" to SectorJson.nullableNumber(speed),
            "timestamp" to SectorJson.number(timestamp),
            "sessionId" to SectorJson.nullableString(sessionId),
            "callsign" to SectorJson.nullableString(callsign)
        )

    private fun parseObject(json: String): Map<String, SectorJsonValue> =
        SectorJson.parse(json).getOrThrow().asObjectOrNull()
            ?: throw IllegalArgumentException("Payload must be a JSON object")

    private fun decodeMapNoteAttachment(value: SectorJsonValue): MapNoteAttachmentPayloadV1 {
        val fields = value.asObjectOrNull()
            ?: throw IllegalArgumentException("Attachment must be a JSON object")
        val type = MapNoteAttachmentType.fromWireName(fields.requiredString("type"))
            ?: throw IllegalArgumentException("Unsupported attachment type")
        return MapNoteAttachmentPayloadV1(
            attachmentId = fields.requiredString("attachmentId"),
            type = type,
            localPath = fields.optionalString("localPath").orEmpty(),
            mimeType = fields.optionalString("mimeType").orEmpty(),
            sizeBytes = fields.optionalLong("sizeBytes") ?: 0L,
            durationMs = fields.optionalLong("durationMs"),
            createdAt = fields.requiredLong("createdAt"),
            mediaIncluded = fields.optionalBoolean("mediaIncluded") ?: true
        ).also { it.validate() }
    }

    private fun MapNoteAttachmentPayloadV1.toJson(): SectorJsonValue =
        SectorJson.obj(
            "attachmentId" to SectorJson.string(attachmentId),
            "type" to SectorJson.string(type.wireName),
            "localPath" to SectorJson.string(localPath),
            "mimeType" to SectorJson.string(mimeType),
            "sizeBytes" to SectorJson.number(sizeBytes),
            "durationMs" to SectorJson.nullableNumber(durationMs),
            "createdAt" to SectorJson.number(createdAt),
            "mediaIncluded" to SectorJson.bool(mediaIncluded)
        )

    private fun AzimuthRayPayloadV1.validate() {
        validateCoordinates(latitude, longitude)
        require(azimuth in 0.0..359.999) { "azimuth is out of range" }
        require(error >= 0.0) { "error must be positive" }
        require(AzimuthDistance.isValid(distanceKm)) {
            "distanceKm must be between ${AzimuthDistance.MIN_KM} and ${AzimuthDistance.MAX_KM}"
        }
    }

    private fun SharedLocationPayloadV1.validate() {
        validateCoordinates(latitude, longitude)
        require(timestamp > 0L) { "timestamp must be positive" }
        require(accuracyMeters == null || accuracyMeters >= 0.0) { "accuracyMeters must be positive" }
    }

    private fun MapNotePayloadV1.validate() {
        validateCoordinates(latitude, longitude)
        require(title.isNotBlank()) { "title must not be blank" }
        require(createdAt > 0L) { "createdAt must be positive" }
        require(updatedAt > 0L) { "updatedAt must be positive" }
        attachments.forEach { it.validate() }
    }

    private fun LiveLocationPayloadV1.validate() {
        validateCoordinates(latitude, longitude)
        require(timestamp > 0L) { "timestamp must be positive" }
        require(accuracyMeters == null || accuracyMeters >= 0.0) { "accuracyMeters must be positive" }
        require(speed == null || speed >= 0.0) { "speed must be positive" }
    }

    private fun validateCoordinates(latitude: Double, longitude: Double) {
        require(latitude in -90.0..90.0) { "latitude is out of range" }
        require(longitude in -180.0..180.0) { "longitude is out of range" }
    }

    private fun MapNoteAttachmentPayloadV1.validate() {
        require(attachmentId.isNotBlank()) { "attachmentId must not be blank" }
        require(sizeBytes >= 0L) { "sizeBytes must not be negative" }
        require(createdAt > 0L) { "attachment createdAt must be positive" }
        require(durationMs == null || durationMs >= 0L) { "durationMs must not be negative" }
        require(localPath.isBlank() || !localPath.isAbsolutePathLike()) {
            "localPath must be relative"
        }
    }

    private fun String.isAbsolutePathLike(): Boolean =
        startsWith("/") || startsWith("\\") || contains(":\\") || contains(":/")
}
