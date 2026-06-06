package com.prishvindt.sector.domain.backup

import com.prishvindt.sector.domain.objects.MapNoteAttachmentPayloadV1
import java.io.File

const val SECTOR_BACKUP_FORMAT = "SECTOR_BACKUP_V1"
const val SECTOR_BACKUP_VERSION = 1

data class BackupSelection(
    val azimuthRays: Boolean = false,
    val mapNotes: Boolean = false,
    val noteMedia: Boolean = false,
    val settings: Boolean = false
) {
    fun normalized(): BackupSelection =
        if (noteMedia && !mapNotes) copy(mapNotes = true) else this

    fun anySelected(): Boolean =
        azimuthRays || mapNotes || noteMedia || settings

    fun intersect(available: BackupSelection): BackupSelection {
        val normalized = normalized()
        return BackupSelection(
            azimuthRays = normalized.azimuthRays && available.azimuthRays,
            mapNotes = normalized.mapNotes && available.mapNotes,
            noteMedia = normalized.noteMedia && available.noteMedia && available.mapNotes,
            settings = normalized.settings && available.settings
        ).normalized()
    }
}

data class BackupManifest(
    val format: String,
    val version: Int,
    val createdAt: Long,
    val sections: BackupSelection,
    val mediaIncluded: Boolean,
    val objectCount: Int,
    val media: List<BackupMediaReference>
)

data class BackupMediaReference(
    val objectId: String,
    val attachmentId: String,
    val path: String,
    val mimeType: String,
    val sizeBytes: Long
)

data class BackupSettings(
    val ownPointColor: String? = null,
    val gpsPointScale: Float? = null,
    val destinationMarkerType: String? = null,
    val gpsMode: String? = null,
    val accuracyWarningMeters: Double? = null,
    val showSelfCallsign: Boolean? = null,
    val showImportedCallsigns: Boolean? = null,
    val callsignBehavior: String? = null,
    val routeMode: String? = null,
    val routeType: String? = null,
    val showMapNotes: Boolean? = null,
    val showMapNoteTitles: Boolean? = null
)

data class BackupImportPreview(
    val availableSections: BackupSelection,
    val objectCount: Int,
    val mediaCount: Int
)

data class BackupExportSummary(
    val objectCount: Int,
    val mediaCount: Int,
    val settingsIncluded: Boolean
)

data class BackupImportSummary(
    val importedObjects: Int,
    val skippedObjects: Int,
    val skippedBrokenObjects: Int,
    val restoredMedia: Int,
    val missingMedia: Int,
    val settingsApplied: Boolean
)

interface BackupSettingsStore {
    suspend fun backupSettings(): BackupSettings
    suspend fun applyBackupSettings(settings: BackupSettings)
}

interface BackupMediaStorage {
    fun backupFileFor(attachment: MapNoteAttachmentPayloadV1): File?
    fun createImportedFile(
        objectId: String,
        attachment: MapNoteAttachmentPayloadV1,
        zipPath: String
    ): File

    fun relativePath(file: File): String
}

class EmptyBackupException : IllegalStateException("Backup is empty")

class UnsupportedBackupException(message: String) : IllegalArgumentException(message)
