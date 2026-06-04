package com.prishvindt.sector.domain.objects

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
                signal = fields.optionalInt("signal"),
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
                title = fields.optionalString("title"),
                text = fields.requiredString("text"),
                createdAt = fields.requiredLong("createdAt"),
                updatedAt = fields.requiredLong("updatedAt")
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
            "signal" to SectorJson.nullableNumber(signal),
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
            "title" to SectorJson.nullableString(title),
            "text" to SectorJson.string(text),
            "createdAt" to SectorJson.number(createdAt),
            "updatedAt" to SectorJson.number(updatedAt)
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

    private fun AzimuthRayPayloadV1.validate() {
        validateCoordinates(latitude, longitude)
        require(azimuth in 0.0..359.999) { "azimuth is out of range" }
        require(error >= 0.0) { "error must be positive" }
    }

    private fun SharedLocationPayloadV1.validate() {
        validateCoordinates(latitude, longitude)
        require(timestamp > 0L) { "timestamp must be positive" }
        require(accuracyMeters == null || accuracyMeters >= 0.0) { "accuracyMeters must be positive" }
    }

    private fun MapNotePayloadV1.validate() {
        validateCoordinates(latitude, longitude)
        require(text.isNotBlank()) { "text must not be blank" }
        require(createdAt > 0L) { "createdAt must be positive" }
        require(updatedAt > 0L) { "updatedAt must be positive" }
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
}
