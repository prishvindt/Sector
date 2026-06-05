package com.prishvindt.sector.domain.backup

import com.prishvindt.sector.data.SectorObjectEntity
import com.prishvindt.sector.domain.objects.EncryptionState
import com.prishvindt.sector.domain.objects.ObjectVisibility
import com.prishvindt.sector.domain.objects.OwnerKind
import com.prishvindt.sector.domain.objects.SectorJson
import com.prishvindt.sector.domain.objects.SectorJsonValue
import com.prishvindt.sector.domain.objects.SectorObjectPayloadJson
import com.prishvindt.sector.domain.objects.SourceKind
import com.prishvindt.sector.domain.objects.SyncState
import com.prishvindt.sector.domain.objects.asArrayOrNull
import com.prishvindt.sector.domain.objects.asObjectOrNull
import com.prishvindt.sector.domain.objects.optionalBoolean
import com.prishvindt.sector.domain.objects.optionalDouble
import com.prishvindt.sector.domain.objects.optionalLong
import com.prishvindt.sector.domain.objects.optionalString
import com.prishvindt.sector.domain.objects.requiredLong
import com.prishvindt.sector.domain.objects.requiredString

internal object BackupJson {
    fun formatManifest(manifest: BackupManifest): String =
        SectorJson.stringify(
            SectorJson.obj(
                "format" to SectorJson.string(manifest.format),
                "version" to SectorJson.number(manifest.version),
                "createdAt" to SectorJson.number(manifest.createdAt),
                "sections" to selectionToJson(manifest.sections),
                "mediaIncluded" to SectorJson.bool(manifest.mediaIncluded),
                "objectCount" to SectorJson.number(manifest.objectCount),
                "mediaCount" to SectorJson.number(manifest.media.size),
                "media" to SectorJson.array(manifest.media.map(::mediaReferenceToJson))
            )
        )

    fun parseManifest(text: String): BackupManifest {
        val fields = SectorJson.parse(text).getOrThrow().asObjectOrNull()
            ?: throw UnsupportedBackupException("Manifest root must be an object")
        val format = fields.requiredString("format")
        if (format != SECTOR_BACKUP_FORMAT) {
            throw UnsupportedBackupException("Unsupported backup format $format")
        }
        val version = fields.requiredSupportedVersion()
        val media = fields["media"]
            ?.asArrayOrNull()
            ?.mapNotNull { value -> runCatching { parseMediaReference(value) }.getOrNull() }
            .orEmpty()
        fields.optionalSupportedInt("mediaCount", default = media.size)
        return BackupManifest(
            format = format,
            version = version,
            createdAt = fields.requiredLong("createdAt"),
            sections = parseSelection(fields["sections"]),
            mediaIncluded = fields.optionalBoolean("mediaIncluded") ?: media.isNotEmpty(),
            objectCount = fields.optionalSupportedInt("objectCount", default = 0),
            media = media
        )
    }

    fun formatObjects(objects: List<SectorObjectEntity>): String =
        SectorJson.stringify(
            SectorJson.obj(
                "objects" to SectorJson.array(objects.map(::objectToJson))
            )
        )

    fun parseObjects(text: String): ParsedBackupObjects {
        val fields = SectorJson.parse(text).getOrThrow().asObjectOrNull()
            ?: throw IllegalArgumentException("Objects root must be an object")
        val parsed = mutableListOf<SectorObjectEntity>()
        var skipped = 0
        fields["objects"]
            ?.asArrayOrNull()
            .orEmpty()
            .forEach { value ->
                runCatching { parseObject(value) }
                    .onSuccess { parsed += it }
                    .onFailure { skipped += 1 }
            }
        return ParsedBackupObjects(objects = parsed, skippedObjects = skipped)
    }

    fun formatSettings(settings: BackupSettings): String =
        SectorJson.stringify(settings.toJson())

    fun parseSettings(text: String): BackupSettings {
        val fields = SectorJson.parse(text).getOrThrow().asObjectOrNull()
            ?: throw IllegalArgumentException("Settings root must be an object")
        return BackupSettings(
            ownPointColor = fields.optionalString("ownPointColor"),
            gpsPointScale = fields.optionalDouble("gpsPointScale")?.toFloat(),
            destinationMarkerType = fields.optionalString("destinationMarkerType"),
            gpsMode = fields.optionalString("gpsMode"),
            accuracyWarningMeters = fields.optionalDouble("accuracyWarningMeters"),
            showSelfCallsign = fields.optionalBoolean("showSelfCallsign"),
            showImportedCallsigns = fields.optionalBoolean("showImportedCallsigns"),
            callsignBehavior = fields.optionalString("callsignBehavior"),
            routeMode = fields.optionalString("routeMode"),
            routeType = fields.optionalString("routeType"),
            showMapNotes = fields.optionalBoolean("showMapNotes"),
            showMapNoteTitles = fields.optionalBoolean("showMapNoteTitles")
        )
    }

    private fun selectionToJson(selection: BackupSelection): SectorJsonValue =
        SectorJson.obj(
            "azimuthRays" to SectorJson.bool(selection.azimuthRays),
            "mapNotes" to SectorJson.bool(selection.mapNotes),
            "noteMedia" to SectorJson.bool(selection.noteMedia),
            "settings" to SectorJson.bool(selection.settings)
        )

    private fun parseSelection(value: SectorJsonValue?): BackupSelection {
        val fields = value?.asObjectOrNull().orEmpty()
        return BackupSelection(
            azimuthRays = fields.optionalBoolean("azimuthRays") ?: false,
            mapNotes = fields.optionalBoolean("mapNotes") ?: false,
            noteMedia = fields.optionalBoolean("noteMedia") ?: false,
            settings = fields.optionalBoolean("settings") ?: false
        ).normalized()
    }

    private fun mediaReferenceToJson(reference: BackupMediaReference): SectorJsonValue =
        SectorJson.obj(
            "objectId" to SectorJson.string(reference.objectId),
            "attachmentId" to SectorJson.string(reference.attachmentId),
            "path" to SectorJson.string(reference.path),
            "mimeType" to SectorJson.string(reference.mimeType),
            "sizeBytes" to SectorJson.number(reference.sizeBytes)
        )

    private fun parseMediaReference(value: SectorJsonValue): BackupMediaReference {
        val fields = value.asObjectOrNull()
            ?: throw IllegalArgumentException("Media reference must be an object")
        val sizeBytes = fields.optionalLong("sizeBytes") ?: 0L
        require(sizeBytes >= 0L) { "Media reference sizeBytes must not be negative" }
        return BackupMediaReference(
            objectId = fields.requiredString("objectId"),
            attachmentId = fields.requiredString("attachmentId"),
            path = fields.requiredString("path"),
            mimeType = fields.optionalString("mimeType").orEmpty(),
            sizeBytes = sizeBytes
        )
    }

    private fun Map<String, SectorJsonValue>.requiredSupportedVersion(): Int {
        val value = requiredLong("version")
        if (value != SECTOR_BACKUP_VERSION.toLong()) {
            throw UnsupportedBackupException("Unsupported backup version $value")
        }
        return value.toInt()
    }

    private fun Map<String, SectorJsonValue>.optionalSupportedInt(name: String, default: Int): Int {
        val value = optionalLong(name) ?: return default
        if (value < 0L || value > Int.MAX_VALUE.toLong()) {
            throw UnsupportedBackupException("Unsupported backup $name $value")
        }
        return value.toInt()
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

    private fun parseObject(value: SectorJsonValue): SectorObjectEntity {
        val fields = value.asObjectOrNull()
            ?: throw IllegalArgumentException("Backup object must be an object")
        val payload = fields["payload"]
            ?: throw IllegalArgumentException("Backup object payload is missing")
        return SectorObjectEntity(
            objectId = fields.requiredString("objectId"),
            objectType = fields.requiredString("objectType"),
            ownerKind = fields.optionalString("ownerKind") ?: OwnerKind.UNKNOWN.wireName,
            ownerId = fields.optionalString("ownerId"),
            deviceId = fields.optionalString("deviceId"),
            sourceKind = fields.optionalString("sourceKind") ?: SourceKind.LOCAL.wireName,
            createdAt = fields.requiredLong("createdAt"),
            updatedAt = fields.requiredLong("updatedAt"),
            deletedAt = fields.optionalLong("deletedAt"),
            syncState = fields.optionalString("syncState") ?: SyncState.LOCAL_ONLY.wireName,
            visibility = fields.optionalString("visibility") ?: ObjectVisibility.SHAREABLE.wireName,
            encryptionState = fields.optionalString("encryptionState") ?: EncryptionState.PLAIN_LOCAL.wireName,
            payloadVersion = fields.requiredLong("payloadVersion").toInt(),
            payloadJson = SectorObjectPayloadJson.stringifyPayload(payload)
        )
    }

    private fun BackupSettings.toJson(): SectorJsonValue =
        SectorJson.obj(
            "ownPointColor" to SectorJson.nullableString(ownPointColor),
            "gpsPointScale" to SectorJson.nullableNumber(gpsPointScale?.toDouble()),
            "destinationMarkerType" to SectorJson.nullableString(destinationMarkerType),
            "gpsMode" to SectorJson.nullableString(gpsMode),
            "accuracyWarningMeters" to SectorJson.nullableNumber(accuracyWarningMeters),
            "showSelfCallsign" to nullableBoolean(showSelfCallsign),
            "showImportedCallsigns" to nullableBoolean(showImportedCallsigns),
            "callsignBehavior" to SectorJson.nullableString(callsignBehavior),
            "routeMode" to SectorJson.nullableString(routeMode),
            "routeType" to SectorJson.nullableString(routeType),
            "showMapNotes" to nullableBoolean(showMapNotes),
            "showMapNoteTitles" to nullableBoolean(showMapNoteTitles)
        )

    private fun nullableBoolean(value: Boolean?): SectorJsonValue =
        value?.let(SectorJson::bool) ?: SectorJson.Null
}

internal data class ParsedBackupObjects(
    val objects: List<SectorObjectEntity>,
    val skippedObjects: Int
)
