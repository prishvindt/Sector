package com.prishvindt.sector.ui.notes

import android.net.Uri
import com.prishvindt.sector.domain.GeoPoint
import com.prishvindt.sector.domain.notes.MAP_NOTE_MAX_ATTACHMENTS
import com.prishvindt.sector.domain.notes.MAP_NOTE_MAX_AUDIO
import com.prishvindt.sector.domain.notes.MAP_NOTE_MAX_PHOTOS
import com.prishvindt.sector.domain.notes.MapNote
import com.prishvindt.sector.domain.notes.NoteDraft
import com.prishvindt.sector.domain.notes.NoteDraftAttachment
import com.prishvindt.sector.domain.notes.NoteManager
import com.prishvindt.sector.domain.notes.NoteSaveResult
import com.prishvindt.sector.domain.notes.PendingCameraCapture
import com.prishvindt.sector.domain.objects.MapNoteAttachmentType
import com.prishvindt.sector.media.notes.NoteMediaManager
import com.prishvindt.sector.media.notes.RecordedNoteAudio
import com.prishvindt.sector.ui.common.MainUiState
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class NoteUiCoordinator(
    private val noteManager: NoteManager,
    private val noteMediaManager: NoteMediaManager,
    private val scope: CoroutineScope,
    private val currentState: () -> MainUiState,
    private val updateState: ((MainUiState) -> MainUiState) -> Unit,
    private val showMessage: (String) -> Unit
) {
    private var pendingCameraCapture: PendingCameraCapture? = null

    fun observeNotes(): Flow<List<MapNote>> = noteManager.observeNotes()

    fun openNew(point: GeoPoint) {
        scope.launch {
            clearPendingCameraCapture()
            val draft = noteManager.newDraft(point)
            updateState { state ->
                state.copy(
                    noteDraft = draft,
                    selectedTarget = null,
                    destination = null,
                    routePolyline = emptyList(),
                    activeRouteBuilt = false,
                    routeFocusPolyline = emptyList()
                )
            }
        }
    }

    fun openExisting(objectId: String) {
        val note = currentState().mapNotes.firstOrNull { it.objectId == objectId }
        if (note == null) {
            showMessage("Заметка не найдена")
            return
        }
        clearPendingCameraCapture()
        updateState { state ->
            state.copy(
                noteDraft = noteManager.editDraft(note),
                selectedTarget = null
            )
        }
    }

    fun updateTitle(value: String) {
        updateState { state ->
            state.copy(noteDraft = state.noteDraft?.copy(title = value))
        }
    }

    fun updateText(value: String) {
        updateState { state ->
            state.copy(noteDraft = state.noteDraft?.copy(text = value))
        }
    }

    fun addPhoto(uri: Uri) {
        val draft = currentState().noteDraft ?: return
        if (!draft.canAddPhoto()) {
            showMessage("Лимит: максимум 2 фото и 1 аудио")
            return
        }
        val uriString = uri.toString()
        appendAttachment(
            noteManager.pickedPhotoAttachment(
                uriString = uriString,
                mimeType = noteMediaManager.mimeType(uriString),
                sizeBytes = noteMediaManager.sizeBytes(uriString)
            )
        )
    }

    fun prepareCameraCapture(): Uri? {
        val draft = currentState().noteDraft ?: return null
        if (!draft.canAddPhoto()) {
            showMessage("Лимит: максимум 2 фото и 1 аудио")
            return null
        }
        val capture = noteMediaManager.prepareCameraCapture()
        pendingCameraCapture = capture
        return Uri.parse(capture.uriString)
    }

    fun onCameraCaptureResult(success: Boolean) {
        val capture = pendingCameraCapture ?: return
        pendingCameraCapture = null
        if (!success) {
            deleteFile(capture.filePath)
            return
        }
        val draft = currentState().noteDraft
        if (draft == null || !draft.canAddPhoto()) {
            deleteFile(capture.filePath)
            showMessage("Лимит: максимум 2 фото и 1 аудио")
            return
        }
        appendAttachment(
            noteManager.capturedPhotoAttachment(
                capture = capture,
                sizeBytes = File(capture.filePath).length()
            )
        )
    }

    fun addAudio(recording: RecordedNoteAudio) {
        val draft = currentState().noteDraft ?: return
        if (!draft.canAddAudio()) {
            deleteFile(recording.filePath)
            showMessage("Лимит: максимум 1 аудио на заметку")
            return
        }
        appendAttachment(
            noteManager.recordedAudioAttachment(
                filePath = recording.filePath,
                sizeBytes = recording.sizeBytes,
                durationMs = recording.durationMs
            )
        )
    }

    fun removeAttachment(attachmentId: String) {
        val draft = currentState().noteDraft ?: return
        val attachment = draft.attachments.firstOrNull { it.attachmentId == attachmentId }
        if (attachment?.isPending == true) {
            noteMediaManager.deletePendingDraftFile(attachment)
        }
        updateState { state ->
            state.copy(
                noteDraft = state.noteDraft?.copy(
                    attachments = state.noteDraft.attachments.filterNot { it.attachmentId == attachmentId }
                )
            )
        }
    }

    fun saveOpen() {
        val draft = currentState().noteDraft ?: return
        scope.launch {
            noteManager.save(draft)
                .onSuccess { result ->
                    pendingCameraCapture = null
                    updateState { it.copy(noteDraft = null) }
                    when (result) {
                        NoteSaveResult.EmptySkipped -> showMessage("Пустая заметка не сохранена")
                        is NoteSaveResult.Saved -> showMessage("Заметка сохранена")
                    }
                }
                .onFailure {
                    showMessage(it.message ?: "Ошибка сохранения заметки")
                }
        }
    }

    fun dismissOpen() {
        currentState().noteDraft?.let(noteManager::cleanupPending)
        clearPendingCameraCapture()
        updateState { it.copy(noteDraft = null) }
    }

    fun deleteOpen() {
        val draft = currentState().noteDraft ?: return
        val objectId = draft.objectId
        if (objectId == null) {
            dismissOpen()
            return
        }
        val note = currentState().mapNotes.firstOrNull { it.objectId == objectId }
        if (note == null) {
            dismissOpen()
            return
        }
        scope.launch {
            noteManager.delete(note)
            pendingCameraCapture = null
            updateState { it.copy(noteDraft = null) }
            showMessage("Заметка удалена")
        }
    }

    fun cleanupOpenDraft() {
        currentState().noteDraft?.let(noteManager::cleanupPending)
        clearPendingCameraCapture()
    }

    private fun appendAttachment(attachment: NoteDraftAttachment) {
        updateState { state ->
            state.copy(
                noteDraft = state.noteDraft?.copy(
                    attachments = state.noteDraft.attachments + attachment
                )
            )
        }
    }

    private fun clearPendingCameraCapture() {
        pendingCameraCapture?.let { deleteFile(it.filePath) }
        pendingCameraCapture = null
    }

    private fun deleteFile(path: String) {
        runCatching { File(path).delete() }
    }
}

private fun NoteDraft.canAddPhoto(): Boolean =
    attachments.count { it.type == MapNoteAttachmentType.PHOTO } < MAP_NOTE_MAX_PHOTOS &&
        attachments.size < MAP_NOTE_MAX_ATTACHMENTS

private fun NoteDraft.canAddAudio(): Boolean =
    attachments.count { it.type == MapNoteAttachmentType.AUDIO } < MAP_NOTE_MAX_AUDIO &&
        attachments.size < MAP_NOTE_MAX_ATTACHMENTS
