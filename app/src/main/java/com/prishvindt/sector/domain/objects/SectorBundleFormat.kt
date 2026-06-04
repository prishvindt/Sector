package com.prishvindt.sector.domain.objects

import com.prishvindt.sector.data.SectorObjectEntity

object SectorBundleFormat {
    const val FORMAT = "SECTOR_BUNDLE_V1"
    const val VERSION = 1

    fun containsBundleText(text: String): Boolean =
        text.contains(FORMAT) && text.contains("\"format\"")

    fun format(
        objects: List<SectorObjectEntity>,
        sender: SectorBundleSender,
        createdAt: Long
    ): String {
        val root = SectorJson.obj(
            "format" to SectorJson.string(FORMAT),
            "version" to SectorJson.number(VERSION),
            "createdAt" to SectorJson.number(createdAt),
            "sender" to SectorJson.obj(
                "callsign" to SectorJson.nullableString(sender.callsign),
                "deviceId" to SectorJson.nullableString(sender.deviceId)
            ),
            "objects" to SectorJson.array(objects.map(::objectToJson))
        )
        return SectorJson.stringify(root)
    }

    fun parse(text: String): Result<ParsedSectorBundle> =
        runCatching {
            val root = SectorJson.parse(text).getOrThrow().asObjectOrNull()
                ?: throw IllegalArgumentException("Bundle root must be a JSON object")
            val format = root.requiredString("format")
            require(format == FORMAT) { "Unsupported bundle format $format" }
            val version = root.requiredLong("version").toInt()
            require(version == VERSION) { "Unsupported bundle version $version" }
            val sender = parseSender(root["sender"])
            val createdAt = root.requiredLong("createdAt")
            val rawObjects = root["objects"]?.asArrayOrNull()
                ?: throw IllegalArgumentException("Bundle objects must be an array")
            val parsedObjects = mutableListOf<SectorBundleObject>()
            var skippedObjects = 0
            rawObjects.forEach { value ->
                runCatching { parseObject(value) }
                    .onSuccess { parsedObjects += it }
                    .onFailure { skippedObjects += 1 }
            }
            ParsedSectorBundle(
                createdAt = createdAt,
                sender = sender,
                objects = parsedObjects,
                skippedObjects = skippedObjects
            )
        }

    private fun objectToJson(entity: SectorObjectEntity): SectorJsonValue {
        val payload = SectorObjectPayloadJson.parsePayloadJson(entity.payloadJson).getOrThrow()
        return SectorJson.obj(
            "objectId" to SectorJson.string(entity.objectId),
            "objectType" to SectorJson.string(entity.objectType),
            "ownerKind" to SectorJson.string(entity.ownerKind),
            "ownerId" to SectorJson.nullableString(entity.ownerId),
            "deviceId" to SectorJson.nullableString(entity.deviceId),
            "sourceKind" to SectorJson.string(entity.sourceKind),
            "createdAt" to SectorJson.number(entity.createdAt),
            "updatedAt" to SectorJson.number(entity.updatedAt),
            "deletedAt" to SectorJson.nullableNumber(entity.deletedAt),
            "syncState" to SectorJson.string(entity.syncState),
            "visibility" to SectorJson.string(entity.visibility),
            "encryptionState" to SectorJson.string(entity.encryptionState),
            "payloadVersion" to SectorJson.number(entity.payloadVersion),
            "payload" to payload
        )
    }

    private fun parseSender(value: SectorJsonValue?): SectorBundleSender {
        val fields = value?.asObjectOrNull().orEmpty()
        return SectorBundleSender(
            callsign = fields.optionalString("callsign"),
            deviceId = fields.optionalString("deviceId")
        )
    }

    private fun parseObject(value: SectorJsonValue): SectorBundleObject {
        val fields = value.asObjectOrNull()
            ?: throw IllegalArgumentException("Bundle item must be an object")
        val payload = fields["payload"]
            ?: throw IllegalArgumentException("Bundle item payload is missing")
        val payloadVersion = fields["payloadVersion"]?.asLongOrNull()?.toInt()
            ?: throw IllegalArgumentException("Bundle item payloadVersion is missing")
        return SectorBundleObject(
            objectId = fields.requiredString("objectId"),
            objectType = fields.requiredString("objectType"),
            ownerKind = fields.optionalString("ownerKind"),
            ownerId = fields.optionalString("ownerId"),
            deviceId = fields.optionalString("deviceId"),
            sourceKind = fields.optionalString("sourceKind"),
            createdAt = fields.optionalLong("createdAt"),
            updatedAt = fields.optionalLong("updatedAt"),
            deletedAt = fields.optionalLong("deletedAt"),
            syncState = fields.optionalString("syncState"),
            visibility = fields.optionalString("visibility"),
            encryptionState = fields.optionalString("encryptionState"),
            payloadVersion = payloadVersion,
            payload = payload
        )
    }
}

data class SectorBundleSender(
    val callsign: String?,
    val deviceId: String?
)

data class ParsedSectorBundle(
    val createdAt: Long,
    val sender: SectorBundleSender,
    val objects: List<SectorBundleObject>,
    val skippedObjects: Int
)

data class SectorBundleObject(
    val objectId: String,
    val objectType: String,
    val ownerKind: String?,
    val ownerId: String?,
    val deviceId: String?,
    val sourceKind: String?,
    val createdAt: Long?,
    val updatedAt: Long?,
    val deletedAt: Long?,
    val syncState: String?,
    val visibility: String?,
    val encryptionState: String?,
    val payloadVersion: Int,
    val payload: SectorJsonValue
)
