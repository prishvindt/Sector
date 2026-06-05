package com.prishvindt.sector.domain.backup

import com.prishvindt.sector.data.SectorObjectEntity
import com.prishvindt.sector.data.SectorObjectRepository
import com.prishvindt.sector.domain.objects.EncryptionState
import com.prishvindt.sector.domain.objects.MapNoteAttachmentPayloadV1
import com.prishvindt.sector.domain.objects.MapNoteAttachmentType
import com.prishvindt.sector.domain.objects.SectorObjectPayloadJson
import com.prishvindt.sector.domain.objects.SectorObjectType
import com.prishvindt.sector.domain.objects.SyncState
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackupManager(
    private val objectRepository: SectorObjectRepository,
    private val settingsStore: BackupSettingsStore,
    private val mediaStorage: BackupMediaStorage,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    private val zipWriter = BackupZipWriter()
    private val zipReader = BackupZipReader()

    fun defaultFileName(): String {
        val stamp = Instant.ofEpochMilli(clock.millis())
            .atZone(clock.zone)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm"))
        return "sector-backup-$stamp.zip"
    }

    suspend fun writeBackup(
        output: OutputStream,
        selection: BackupSelection
    ): Result<BackupExportSummary> =
        try {
            Result.success(
                withContext(Dispatchers.IO) {
                    writeBackupInternal(output, selection.normalized())
                }
            )
        } catch (error: Throwable) {
            Result.failure(error)
        }

    suspend fun readImportPreview(input: InputStream): Result<BackupImportPreview> =
        try {
            Result.success(
                withContext(Dispatchers.IO) {
                    val manifest = zipReader.readManifest(input)
                    BackupImportPreview(
                        availableSections = manifest.availableSections(),
                        objectCount = manifest.objectCount,
                        mediaCount = manifest.media.size
                    )
                }
            )
        } catch (error: Throwable) {
            Result.failure(error)
        }

    suspend fun importBackup(
        input: InputStream,
        selection: BackupSelection
    ): Result<BackupImportSummary> =
        try {
            Result.success(
                withContext(Dispatchers.IO) {
                    importBackupInternal(input, selection.normalized())
                }
            )
        } catch (error: Throwable) {
            Result.failure(error)
        }

    private suspend fun writeBackupInternal(
        output: OutputStream,
        selection: BackupSelection
    ): BackupExportSummary {
        if (!selection.anySelected()) {
            throw EmptyBackupException()
        }
        val objects = collectSelectedObjects(selection)
        if (objects.isEmpty() && !selection.settings) {
            throw EmptyBackupException()
        }
        val mediaFiles = if (selection.noteMedia && selection.mapNotes) {
            collectMediaFiles(objects)
        } else {
            emptyList()
        }
        val settings = if (selection.settings) settingsStore.backupSettings() else null
        val storedSections = BackupSelection(
            azimuthRays = selection.azimuthRays && objects.anyType(SectorObjectType.AZIMUTH_RAY),
            mapNotes = selection.mapNotes && objects.anyType(SectorObjectType.MAP_NOTE),
            noteMedia = mediaFiles.isNotEmpty(),
            settings = settings != null
        )
        val sanitizedObjects = objects.map(::sanitizeObjectForBackup)
        val manifest = BackupManifest(
            format = SECTOR_BACKUP_FORMAT,
            version = SECTOR_BACKUP_VERSION,
            createdAt = clock.millis(),
            sections = storedSections,
            mediaIncluded = mediaFiles.isNotEmpty(),
            objectCount = sanitizedObjects.size,
            media = mediaFiles.map { it.reference }
        )
        zipWriter.write(
            output = output,
            manifest = manifest,
            objects = sanitizedObjects,
            settings = settings,
            mediaFiles = mediaFiles
        )
        return BackupExportSummary(
            objectCount = sanitizedObjects.size,
            mediaCount = mediaFiles.size,
            settingsIncluded = settings != null
        )
    }

    private suspend fun importBackupInternal(
        input: InputStream,
        selection: BackupSelection
    ): BackupImportSummary {
        val archive = zipReader.readArchive(input, includeMedia = selection.noteMedia)
        val effectiveSelection = selection.intersect(archive.manifest.availableSections())
        if (!effectiveSelection.anySelected()) {
            throw EmptyBackupException()
        }

        var importedObjects = 0
        var skippedObjects = 0
        var skippedBrokenObjects = archive.skippedBrokenObjects
        val mediaStats = MediaRestoreStats()

        archive.objects
            .filter { it.isSelectedForImport(effectiveSelection) }
            .forEach { entity ->
                runCatching {
                    if (objectRepository.activeObjectById(entity.objectId) != null) {
                        skippedObjects += 1
                        return@forEach
                    }
                    val prepared = prepareObjectForImport(
                        entity = entity,
                        selection = effectiveSelection,
                        archive = archive,
                        mediaStats = mediaStats
                    )
                    objectRepository.upsertObject(prepared)
                    importedObjects += 1
                }.onFailure {
                    skippedBrokenObjects += 1
                }
            }

        val settings = archive.settings
        var settingsApplied = false
        if (effectiveSelection.settings && settings != null) {
            settingsStore.applyBackupSettings(settings)
            settingsApplied = true
        }

        return BackupImportSummary(
            importedObjects = importedObjects,
            skippedObjects = skippedObjects,
            skippedBrokenObjects = skippedBrokenObjects,
            restoredMedia = mediaStats.restored,
            missingMedia = mediaStats.missing,
            settingsApplied = settingsApplied
        )
    }

    private suspend fun collectSelectedObjects(selection: BackupSelection): List<SectorObjectEntity> =
        buildList {
            if (selection.azimuthRays) {
                addAll(objectRepository.activeObjects(SectorObjectType.AZIMUTH_RAY))
            }
            if (selection.mapNotes) {
                addAll(objectRepository.activeObjects(SectorObjectType.MAP_NOTE))
            }
        }

    private fun collectMediaFiles(objects: List<SectorObjectEntity>): List<BackupMediaFile> =
        objects
            .filter { SectorObjectType.fromWireName(it.objectType) == SectorObjectType.MAP_NOTE }
            .flatMap { entity ->
                val note = SectorObjectPayloadJson.decodeMapNote(entity.payloadJson).getOrNull()
                    ?: return@flatMap emptyList()
                note.attachments.mapNotNull { attachment ->
                    val file = mediaStorage.backupFileFor(attachment) ?: return@mapNotNull null
                    val reference = BackupMediaReference(
                        objectId = entity.objectId,
                        attachmentId = attachment.attachmentId,
                        path = "media/notes/${entity.objectId}/${attachment.zipFileName(file.name)}",
                        mimeType = attachment.mimeType,
                        sizeBytes = file.length().coerceAtLeast(0L)
                    )
                    BackupMediaFile(reference = reference, file = file)
                }
            }

    private fun sanitizeObjectForBackup(entity: SectorObjectEntity): SectorObjectEntity =
        entity.copy(
            ownerId = null,
            deviceId = null,
            syncState = SyncState.LOCAL_ONLY.wireName,
            encryptionState = EncryptionState.PLAIN_LOCAL.wireName,
            payloadJson = sanitizePayloadForBackup(entity)
        )

    private fun sanitizePayloadForBackup(entity: SectorObjectEntity): String =
        when (SectorObjectType.fromWireName(entity.objectType)) {
            SectorObjectType.AZIMUTH_RAY -> {
                val payload = SectorObjectPayloadJson.decodeAzimuthRay(entity.payloadJson).getOrThrow()
                SectorObjectPayloadJson.encode(payload.copy(callsign = null))
            }
            SectorObjectType.MAP_NOTE -> {
                val payload = SectorObjectPayloadJson.decodeMapNote(entity.payloadJson).getOrThrow()
                SectorObjectPayloadJson.encode(
                    payload.copy(
                        attachments = payload.attachments.map {
                            it.copy(localPath = "", mediaIncluded = false)
                        }
                    )
                )
            }
            else -> entity.payloadJson
        }

    private fun prepareObjectForImport(
        entity: SectorObjectEntity,
        selection: BackupSelection,
        archive: BackupArchive,
        mediaStats: MediaRestoreStats
    ): SectorObjectEntity {
        val sanitized = entity.copy(
            ownerId = null,
            deviceId = null,
            deletedAt = null,
            syncState = SyncState.LOCAL_ONLY.wireName,
            encryptionState = EncryptionState.PLAIN_LOCAL.wireName
        )
        return when (SectorObjectType.fromWireName(sanitized.objectType)) {
            SectorObjectType.AZIMUTH_RAY -> {
                val payload = SectorObjectPayloadJson.decodeAzimuthRay(sanitized.payloadJson).getOrThrow()
                sanitized.copy(payloadJson = SectorObjectPayloadJson.encode(payload.copy(callsign = null)))
            }
            SectorObjectType.MAP_NOTE -> {
                val payload = SectorObjectPayloadJson.decodeMapNote(sanitized.payloadJson).getOrThrow()
                val attachments = if (selection.noteMedia) {
                    restoreNoteAttachments(
                        objectId = sanitized.objectId,
                        attachments = payload.attachments,
                        archive = archive,
                        mediaStats = mediaStats
                    )
                } else {
                    emptyList()
                }
                sanitized.copy(payloadJson = SectorObjectPayloadJson.encode(payload.copy(attachments = attachments)))
            }
            else -> sanitized
        }
    }

    private fun restoreNoteAttachments(
        objectId: String,
        attachments: List<MapNoteAttachmentPayloadV1>,
        archive: BackupArchive,
        mediaStats: MediaRestoreStats
    ): List<MapNoteAttachmentPayloadV1> {
        val refsByAttachment = archive.manifest.media
            .filter { it.objectId == objectId }
            .associateBy { it.attachmentId }
        return attachments.mapNotNull { attachment ->
            val reference = refsByAttachment[attachment.attachmentId]
            val bytes = reference?.path?.let(archive.mediaBytes::get)
            if (reference == null || bytes == null) {
                mediaStats.missing += 1
                return@mapNotNull null
            }
            val file = mediaStorage.createImportedFile(objectId, attachment, reference.path)
            file.parentFile?.mkdirs()
            file.outputStream().use { output -> output.write(bytes) }
            if (!file.isFile) {
                mediaStats.missing += 1
                return@mapNotNull null
            }
            mediaStats.restored += 1
            attachment.copy(
                localPath = mediaStorage.relativePath(file),
                mimeType = reference.mimeType.ifBlank { attachment.mimeType },
                sizeBytes = file.length().coerceAtLeast(0L),
                mediaIncluded = true
            )
        }
    }

    private fun SectorObjectEntity.isSelectedForImport(selection: BackupSelection): Boolean =
        when (SectorObjectType.fromWireName(objectType)) {
            SectorObjectType.AZIMUTH_RAY -> selection.azimuthRays
            SectorObjectType.MAP_NOTE -> selection.mapNotes
            else -> false
        }

    private fun BackupManifest.availableSections(): BackupSelection =
        sections.copy(noteMedia = mediaIncluded && media.isNotEmpty()).normalized()

    private fun List<SectorObjectEntity>.anyType(type: SectorObjectType): Boolean =
        any { SectorObjectType.fromWireName(it.objectType) == type }

    private fun MapNoteAttachmentPayloadV1.zipFileName(sourceName: String): String {
        val extension = sourceName.substringAfterLast('.', missingDelimiterValue = "")
            .replace(Regex("""[^A-Za-z0-9]"""), "")
            .take(12)
            .ifBlank { type.defaultExtension() }
        return "${attachmentId.safePathSegment()}.$extension"
    }

    private fun String.safePathSegment(): String =
        replace(Regex("""[^A-Za-z0-9._-]"""), "_")
            .take(96)
            .ifBlank { "item" }

    private fun MapNoteAttachmentType.defaultExtension(): String =
        when (this) {
            MapNoteAttachmentType.PHOTO -> "jpg"
            MapNoteAttachmentType.AUDIO -> "m4a"
        }

    private class MediaRestoreStats(
        var restored: Int = 0,
        var missing: Int = 0
    )
}
