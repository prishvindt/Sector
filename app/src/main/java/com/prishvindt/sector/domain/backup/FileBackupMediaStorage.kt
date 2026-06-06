package com.prishvindt.sector.domain.backup

import com.prishvindt.sector.domain.objects.MapNoteAttachmentPayloadV1
import com.prishvindt.sector.domain.objects.MapNoteAttachmentType
import com.prishvindt.sector.media.notes.hasExistingLocalNoteMedia
import java.io.File

class FileBackupMediaStorage(
    private val filesDir: File
) : BackupMediaStorage {
    override fun backupFileFor(attachment: MapNoteAttachmentPayloadV1): File? {
        if (!hasExistingLocalNoteMedia(filesDir, attachment.mediaIncluded, attachment.localPath)) {
            return null
        }
        return insideFilesDir(attachment.localPath)?.takeIf(File::isFile)
    }

    override fun createImportedFile(
        objectId: String,
        attachment: MapNoteAttachmentPayloadV1,
        zipPath: String
    ): File {
        val noteDir = File(filesDir, "notes/${objectId.safePathSegment()}").insideRoot()
        noteDir.mkdirs()
        val sourceName = zipPath.substringAfterLast('/').ifBlank { attachment.attachmentId }
        val extension = sourceName.substringAfterLast('.', missingDelimiterValue = "")
            .safeExtension()
            .ifBlank { attachment.type.defaultExtension() }
        val fileName = "${attachment.attachmentId.safePathSegment()}.$extension"
        return File(noteDir, fileName).insideRoot()
    }

    override fun relativePath(file: File): String {
        val root = filesDir.canonicalFile
        val canonical = file.canonicalFile
        require(canonical.path.startsWith(root.path + File.separator)) {
            "Backup media file must be inside filesDir"
        }
        return canonical.relativeTo(root).path.replace(File.separatorChar, '/')
    }

    private fun insideFilesDir(relativePath: String): File? =
        runCatching { File(filesDir, relativePath).insideRoot() }.getOrNull()

    private fun File.insideRoot(): File {
        val root = filesDir.canonicalFile
        val canonical = canonicalFile
        require(canonical.path == root.path || canonical.path.startsWith(root.path + File.separator)) {
            "Path escapes filesDir"
        }
        return canonical
    }

    private fun String.safePathSegment(): String =
        replace(Regex("""[^A-Za-z0-9._-]"""), "_")
            .take(96)
            .ifBlank { "item" }

    private fun String.safeExtension(): String =
        replace(Regex("""[^A-Za-z0-9]"""), "")
            .take(12)

    private fun MapNoteAttachmentType.defaultExtension(): String =
        when (this) {
            MapNoteAttachmentType.PHOTO -> "jpg"
            MapNoteAttachmentType.AUDIO -> "m4a"
        }
}
