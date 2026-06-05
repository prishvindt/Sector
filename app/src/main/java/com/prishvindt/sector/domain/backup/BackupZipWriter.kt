package com.prishvindt.sector.domain.backup

import com.prishvindt.sector.data.SectorObjectEntity
import java.io.File
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal class BackupZipWriter {
    fun write(
        output: OutputStream,
        manifest: BackupManifest,
        objects: List<SectorObjectEntity>,
        settings: BackupSettings?,
        mediaFiles: List<BackupMediaFile>
    ) {
        ZipOutputStream(output).use { zip ->
            zip.putTextEntry(MANIFEST_ENTRY, BackupJson.formatManifest(manifest))
            zip.putTextEntry(OBJECTS_ENTRY, BackupJson.formatObjects(objects))
            if (settings != null) {
                zip.putTextEntry(SETTINGS_ENTRY, BackupJson.formatSettings(settings))
            }
            mediaFiles.forEach { media ->
                zip.putFileEntry(media.reference.path, media.file)
            }
        }
    }

    private fun ZipOutputStream.putTextEntry(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(StandardCharsets.UTF_8))
        closeEntry()
    }

    private fun ZipOutputStream.putFileEntry(name: String, file: File) {
        putNextEntry(ZipEntry(name))
        file.inputStream().use { input -> input.copyTo(this) }
        closeEntry()
    }
}

internal data class BackupMediaFile(
    val reference: BackupMediaReference,
    val file: File
)

internal const val MANIFEST_ENTRY = "manifest.json"
internal const val OBJECTS_ENTRY = "objects.json"
internal const val SETTINGS_ENTRY = "settings.json"
