package com.prishvindt.sector.domain.notes

import com.prishvindt.sector.domain.GeoPoint
import com.prishvindt.sector.domain.objects.MapNoteAttachmentPayloadV1
import com.prishvindt.sector.domain.objects.MapNoteAttachmentType

const val MAP_NOTE_DEFAULT_TITLE_PREFIX = "Заметка"
const val MAP_NOTE_MAX_PHOTOS = 2
const val MAP_NOTE_MAX_AUDIO = 1
const val MAP_NOTE_MAX_ATTACHMENTS = 3

data class MapNote(
    val objectId: String,
    val point: GeoPoint,
    val title: String,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long,
    val attachments: List<MapNoteAttachmentPayloadV1>
)

data class NoteDraft(
    val objectId: String?,
    val point: GeoPoint,
    val title: String,
    val defaultTitle: String,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long,
    val attachments: List<NoteDraftAttachment>
) {
    val isNew: Boolean
        get() = objectId == null

    val isDefaultTitle: Boolean
        get() = title.trim() == defaultTitle
}

data class NoteDraftAttachment(
    val attachmentId: String,
    val type: MapNoteAttachmentType,
    val localPath: String?,
    val sourceUri: String?,
    val sourcePath: String?,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long?,
    val createdAt: Long,
    val mediaIncluded: Boolean = true
) {
    val isPending: Boolean
        get() = sourceUri != null || sourcePath != null
}

data class PendingCameraCapture(
    val uriString: String,
    val filePath: String
)

sealed interface NoteSaveResult {
    data class Saved(val note: MapNote) : NoteSaveResult
    data object EmptySkipped : NoteSaveResult
}

interface NoteNumberStore {
    suspend fun reserveNextNoteNumber(): Int
}

interface NoteAttachmentStorage {
    suspend fun persistAttachments(
        objectId: String,
        draftAttachments: List<NoteDraftAttachment>,
        previousAttachments: List<MapNoteAttachmentPayloadV1>
    ): PersistedNoteAttachments

    suspend fun deleteNoteFiles(objectId: String)

    fun deletePendingDraftFiles(draft: NoteDraft)

    fun deletePendingDraftFile(attachment: NoteDraftAttachment)
}

data class PersistedNoteAttachments(
    val attachments: List<MapNoteAttachmentPayloadV1>,
    val removedAttachments: List<MapNoteAttachmentPayloadV1>
)
