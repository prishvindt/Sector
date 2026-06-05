package com.prishvindt.sector.media.notes

import android.content.ContentResolver
import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.prishvindt.sector.domain.notes.NoteAttachmentStorage
import com.prishvindt.sector.domain.notes.NoteDraft
import com.prishvindt.sector.domain.notes.NoteDraftAttachment
import com.prishvindt.sector.domain.notes.PendingCameraCapture
import com.prishvindt.sector.domain.notes.PersistedNoteAttachments
import com.prishvindt.sector.domain.objects.MapNoteAttachmentPayloadV1
import com.prishvindt.sector.domain.objects.MapNoteAttachmentType
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NoteMediaManager(
    private val context: Context
) : NoteAttachmentStorage {
    fun prepareCameraCapture(): PendingCameraCapture {
        val dir = pendingDir().apply { mkdirs() }
        val file = File(dir, "camera_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return PendingCameraCapture(uriString = uri.toString(), filePath = file.absolutePath)
    }

    fun mimeType(uriString: String): String? =
        runCatching { context.contentResolver.getType(Uri.parse(uriString)) }.getOrNull()

    fun sizeBytes(uriString: String): Long =
        runCatching {
            val uri = Uri.parse(uriString)
            querySize(uri) ?: context.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                it.length.takeIf { length -> length >= 0L }
            }
        }.getOrNull() ?: 0L

    override suspend fun persistAttachments(
        objectId: String,
        draftAttachments: List<NoteDraftAttachment>,
        previousAttachments: List<MapNoteAttachmentPayloadV1>
    ): PersistedNoteAttachments = withContext(Dispatchers.IO) {
        val noteDir = noteDir(objectId).apply { mkdirs() }
        val previousById = previousAttachments.associateBy { it.attachmentId }
        val saved = mutableListOf<MapNoteAttachmentPayloadV1>()
        var nextPhotoIndex = nextAttachmentIndex(previousAttachments, MapNoteAttachmentType.PHOTO)

        draftAttachments.forEach { draft ->
            val previous = previousById[draft.attachmentId]
            if (!draft.isPending) {
                val existingLocal = previous
                    ?.takeIf { it.hasExistingLocalMedia() }
                    ?: draft.toExistingLocalPayloadOrNull()
                if (existingLocal != null) {
                    saved += existingLocal
                }
                return@forEach
            }
            when (draft.type) {
                MapNoteAttachmentType.PHOTO -> {
                    val destination = File(noteDir, "photo_${nextPhotoIndex++}.jpg")
                    persistPhoto(draft, destination)
                    saved += draft.toPayload(
                        localPath = destination.relativeToFilesDir(),
                        sizeBytes = destination.length(),
                        mimeType = "image/jpeg",
                        mediaIncluded = true
                    )
                }
                MapNoteAttachmentType.AUDIO -> {
                    val destination = File(noteDir, "audio_1.m4a")
                    persistAudio(draft, destination)
                    saved += draft.toPayload(
                        localPath = destination.relativeToFilesDir(),
                        sizeBytes = destination.length(),
                        mimeType = "audio/mp4",
                        mediaIncluded = true
                    )
                }
            }
            draft.sourcePath?.let(::deleteFileQuietly)
        }

        val keptIds = saved.map { it.attachmentId }.toSet()
        val savedPaths = saved.map { it.localPath }.filter { it.isNotBlank() }.toSet()
        val removed = previousAttachments.filter { it.attachmentId !in keptIds }
        removed
            .filter { it.localPath !in savedPaths }
            .forEach(::deleteStoredAttachment)
        PersistedNoteAttachments(attachments = saved, removedAttachments = emptyList())
    }

    override suspend fun deleteNoteFiles(objectId: String) {
        withContext(Dispatchers.IO) {
            noteDir(objectId).deleteRecursively()
        }
    }

    override fun deletePendingDraftFiles(draft: NoteDraft) {
        draft.attachments
            .filter { it.isPending }
            .forEach(::deletePendingDraftFile)
    }

    override fun deletePendingDraftFile(attachment: NoteDraftAttachment) {
        attachment.sourcePath?.let(::deleteFileQuietly)
    }

    fun fileForLocalPath(localPath: String): File =
        File(context.filesDir, localPath)

    private fun persistPhoto(draft: NoteDraftAttachment, destination: File) {
        val source = when {
            draft.sourceUri != null -> ImageDecoder.createSource(context.contentResolver, Uri.parse(draft.sourceUri))
            draft.sourcePath != null -> ImageDecoder.createSource(File(draft.sourcePath))
            draft.localPath != null -> ImageDecoder.createSource(fileForLocalPath(draft.localPath))
            else -> error("Photo source is missing")
        }
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val width = info.size.width
            val height = info.size.height
            val longest = maxOf(width, height)
            if (longest > MAX_PHOTO_SIDE_PX) {
                val scale = MAX_PHOTO_SIDE_PX.toFloat() / longest.toFloat()
                decoder.setTargetSize(
                    (width * scale).toInt().coerceAtLeast(1),
                    (height * scale).toInt().coerceAtLeast(1)
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        destination.outputStream().use { output ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        }
        bitmap.recycle()
    }

    private fun persistAudio(draft: NoteDraftAttachment, destination: File) {
        val sourcePath = draft.sourcePath
            ?: draft.localPath?.let { fileForLocalPath(it).absolutePath }
            ?: error("Audio source is missing")
        File(sourcePath).copyTo(destination, overwrite = true)
    }

    private fun querySize(uri: Uri): Long? {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            return uri.path?.let(::File)?.length()
        }
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && cursor.moveToFirst()) cursor.getLong(index) else null
            }
    }

    private fun nextAttachmentIndex(
        attachments: List<MapNoteAttachmentPayloadV1>,
        type: MapNoteAttachmentType
    ): Int {
        val maxExisting = attachments
            .filter { it.type == type }
            .mapNotNull { attachment ->
                Regex("""_(\d+)\.""")
                    .find(attachment.localPath)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
            }
            .maxOrNull()
        return (maxExisting ?: 0) + 1
    }

    private fun deleteStoredAttachment(attachment: MapNoteAttachmentPayloadV1) {
        attachment.localPath
            .takeIf { it.isNotBlank() }
            ?.let { File(context.filesDir, it) }
            ?.delete()
    }

    private fun MapNoteAttachmentPayloadV1.hasExistingLocalMedia(): Boolean =
        hasExistingLocalNoteMedia(
            filesDir = context.filesDir,
            mediaIncluded = mediaIncluded,
            localPath = localPath
        )

    private fun NoteDraftAttachment.toExistingLocalPayloadOrNull(): MapNoteAttachmentPayloadV1? {
        val localPath = localPath?.takeIf { it.isNotBlank() } ?: return null
        if (!hasExistingLocalNoteMedia(context.filesDir, mediaIncluded, localPath)) return null
        return toPayload(
            localPath = localPath,
            sizeBytes = fileForLocalPath(localPath).length(),
            mediaIncluded = true
        )
    }

    private fun NoteDraftAttachment.toPayload(
        localPath: String,
        sizeBytes: Long,
        mimeType: String = this.mimeType,
        mediaIncluded: Boolean
    ): MapNoteAttachmentPayloadV1 =
        MapNoteAttachmentPayloadV1(
            attachmentId = attachmentId,
            type = type,
            localPath = localPath,
            mimeType = mimeType.ifBlank { defaultMimeType(type) },
            sizeBytes = sizeBytes.coerceAtLeast(0L),
            durationMs = durationMs,
            createdAt = createdAt,
            mediaIncluded = mediaIncluded
        )

    private fun File.relativeToFilesDir(): String =
        relativeTo(context.filesDir)
            .path
            .replace(File.separatorChar, '/')

    private fun noteDir(objectId: String): File =
        File(context.filesDir, "notes/$objectId")

    private fun pendingDir(): File =
        File(context.filesDir, "notes/_pending")

    private fun deleteFileQuietly(path: String) {
        runCatching {
            val file = File(path)
            if (file.absolutePath.startsWith(context.filesDir.absolutePath)) {
                file.delete()
            }
        }
    }

    private fun defaultMimeType(type: MapNoteAttachmentType): String =
        when (type) {
            MapNoteAttachmentType.PHOTO -> "image/jpeg"
            MapNoteAttachmentType.AUDIO -> "audio/mp4"
        }

    private companion object {
        const val MAX_PHOTO_SIDE_PX = 1600
        const val JPEG_QUALITY = 85
    }
}

internal fun hasExistingLocalNoteMedia(
    filesDir: File,
    mediaIncluded: Boolean,
    localPath: String?
): Boolean {
    if (!mediaIncluded) return false
    val relativePath = localPath?.takeIf { it.isNotBlank() } ?: return false
    return runCatching {
        val root = filesDir.canonicalFile
        val file = File(root, relativePath).canonicalFile
        file.isFile && file.path.startsWith(root.path + File.separator)
    }.getOrDefault(false)
}
