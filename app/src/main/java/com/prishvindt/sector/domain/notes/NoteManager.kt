package com.prishvindt.sector.domain.notes

import com.prishvindt.sector.data.LocalMapNoteInput
import com.prishvindt.sector.data.SectorObjectEntity
import com.prishvindt.sector.data.SectorObjectRepository
import com.prishvindt.sector.data.toMapNoteOrNull
import com.prishvindt.sector.domain.GeoPoint
import com.prishvindt.sector.domain.objects.MapNoteAttachmentPayloadV1
import com.prishvindt.sector.domain.objects.MapNoteAttachmentType
import com.prishvindt.sector.domain.objects.SectorObjectPayloadJson
import com.prishvindt.sector.domain.objects.SectorObjectType
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NoteManager(
    private val repository: SectorObjectRepository,
    private val numberStore: NoteNumberStore,
    private val attachmentStorage: NoteAttachmentStorage,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val attachmentIdFactory: () -> String = { UUID.randomUUID().toString() }
) {
    fun observeNotes(): Flow<List<MapNote>> =
        repository.observeActiveObjects(SectorObjectType.MAP_NOTE)
            .map { objects -> objects.mapNotNull(SectorObjectEntity::toMapNoteOrNull) }

    suspend fun newDraft(point: GeoPoint): NoteDraft {
        val number = numberStore.reserveNextNoteNumber()
        val now = clock.millis()
        val title = "$MAP_NOTE_DEFAULT_TITLE_PREFIX $number"
        return NoteDraft(
            objectId = null,
            point = point,
            title = title,
            defaultTitle = title,
            text = "",
            createdAt = now,
            updatedAt = now,
            attachments = emptyList()
        )
    }

    fun editDraft(note: MapNote): NoteDraft =
        NoteDraft(
            objectId = note.objectId,
            point = note.point,
            title = note.title,
            defaultTitle = note.title,
            text = note.text,
            createdAt = note.createdAt,
            updatedAt = note.updatedAt,
            attachments = note.attachments.map { it.toDraftAttachment() }
        )

    fun pickedPhotoAttachment(uriString: String, mimeType: String?, sizeBytes: Long): NoteDraftAttachment =
        pendingAttachment(
            type = MapNoteAttachmentType.PHOTO,
            sourceUri = uriString,
            sourcePath = null,
            mimeType = mimeType?.takeIf { it.isNotBlank() } ?: "image/jpeg",
            sizeBytes = sizeBytes
        )

    fun capturedPhotoAttachment(capture: PendingCameraCapture, sizeBytes: Long): NoteDraftAttachment =
        pendingAttachment(
            type = MapNoteAttachmentType.PHOTO,
            sourceUri = capture.uriString,
            sourcePath = capture.filePath,
            mimeType = "image/jpeg",
            sizeBytes = sizeBytes
        )

    fun recordedAudioAttachment(filePath: String, sizeBytes: Long, durationMs: Long?): NoteDraftAttachment =
        pendingAttachment(
            type = MapNoteAttachmentType.AUDIO,
            sourceUri = null,
            sourcePath = filePath,
            mimeType = "audio/mp4",
            sizeBytes = sizeBytes,
            durationMs = durationMs
        )

    suspend fun save(draft: NoteDraft): Result<NoteSaveResult> =
        runCatching {
            val normalizedDraft = draft.copy(
                title = draft.title.trim().ifBlank { draft.defaultTitle },
                text = draft.text.trim()
            )
            if (normalizedDraft.isEmptyDefaultNote()) {
                attachmentStorage.deletePendingDraftFiles(normalizedDraft)
                return@runCatching NoteSaveResult.EmptySkipped
            }
            validateAttachmentLimits(normalizedDraft.attachments)

            val objectId = normalizedDraft.objectId ?: repository.newLocalObjectId()
            val previous = normalizedDraft.objectId
                ?.let { repository.objectById(it) }
                ?.takeIf { SectorObjectType.fromWireName(it.objectType) == SectorObjectType.MAP_NOTE }
                ?.payloadJson
                ?.let { SectorObjectPayloadJson.decodeMapNote(it).getOrNull()?.attachments }
                .orEmpty()
            val persisted = attachmentStorage.persistAttachments(
                objectId = objectId,
                draftAttachments = normalizedDraft.attachments,
                previousAttachments = previous
            )
            val entity = repository.createOrUpdateLocalMapNote(
                LocalMapNoteInput(
                    objectId = objectId,
                    point = normalizedDraft.point,
                    title = normalizedDraft.title,
                    text = normalizedDraft.text,
                    attachments = persisted.attachments,
                    createdAt = normalizedDraft.createdAt
                )
            )
            persisted.removedAttachments.forEach { attachment ->
                attachmentStorage.deletePendingDraftFile(attachment.toDraftAttachment())
            }
            NoteSaveResult.Saved(entity.toMapNoteOrNull() ?: error("Saved note payload is invalid"))
        }

    suspend fun delete(note: MapNote) {
        repository.softDeleteObject(note.objectId)
        attachmentStorage.deleteNoteFiles(note.objectId)
    }

    fun cleanupPending(draft: NoteDraft) {
        attachmentStorage.deletePendingDraftFiles(draft)
    }

    private fun pendingAttachment(
        type: MapNoteAttachmentType,
        sourceUri: String?,
        sourcePath: String?,
        mimeType: String,
        sizeBytes: Long,
        durationMs: Long? = null
    ): NoteDraftAttachment =
        NoteDraftAttachment(
            attachmentId = attachmentIdFactory(),
            type = type,
            localPath = null,
            sourceUri = sourceUri,
            sourcePath = sourcePath,
            mimeType = mimeType,
            sizeBytes = sizeBytes.coerceAtLeast(0L),
            durationMs = durationMs,
            createdAt = clock.millis()
        )

    private fun NoteDraft.isEmptyDefaultNote(): Boolean =
        isNew &&
            isDefaultTitle &&
            text.isBlank() &&
            attachments.isEmpty()

    private fun validateAttachmentLimits(attachments: List<NoteDraftAttachment>) {
        val photoCount = attachments.count { it.type == MapNoteAttachmentType.PHOTO }
        val audioCount = attachments.count { it.type == MapNoteAttachmentType.AUDIO }
        require(photoCount <= MAP_NOTE_MAX_PHOTOS) { "Максимум 2 фото на заметку" }
        require(audioCount <= MAP_NOTE_MAX_AUDIO) { "Максимум 1 аудио на заметку" }
        require(attachments.size <= MAP_NOTE_MAX_ATTACHMENTS) { "Максимум 3 вложения на заметку" }
    }

    private fun MapNoteAttachmentPayloadV1.toDraftAttachment(): NoteDraftAttachment =
        NoteDraftAttachment(
            attachmentId = attachmentId,
            type = type,
            localPath = localPath,
            sourceUri = null,
            sourcePath = null,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            durationMs = durationMs,
            createdAt = createdAt,
            mediaIncluded = mediaIncluded
        )
}
