package com.prishvindt.sector.domain.backup

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

internal class BackupZipReader {
    fun readManifest(input: InputStream): BackupManifest {
        var manifest: BackupManifest? = null
        var hasObjectsEntry = false
        var hasSettingsEntry = false

        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.normalizedZipPath()
                if (!name.isSafeZipPath()) {
                    throw UnsupportedBackupException("Unsafe backup zip entry")
                }
                when (name) {
                    MANIFEST_ENTRY -> {
                        manifest = zip.readEntryText(MAX_TEXT_ENTRY_BYTES).let(BackupJson::parseManifest)
                        if (manifest.requiredTextEntriesFound(hasObjectsEntry, hasSettingsEntry)) {
                            return manifest
                        }
                    }
                    OBJECTS_ENTRY -> {
                        zip.readEntryText(MAX_TEXT_ENTRY_BYTES)
                        hasObjectsEntry = true
                    }
                    SETTINGS_ENTRY -> {
                        zip.readEntryText(MAX_TEXT_ENTRY_BYTES)
                        hasSettingsEntry = true
                    }
                    else -> {
                        val parsedManifest = manifest
                        if (parsedManifest != null && name.startsWith(MEDIA_PREFIX)) {
                            if (parsedManifest.requiredTextEntriesFound(hasObjectsEntry, hasSettingsEntry)) {
                                return parsedManifest
                            }
                            throw IllegalArgumentException("Backup media appears before required text entries")
                        }
                        zip.drainEntryBytes(MAX_TEXT_ENTRY_BYTES)
                    }
                }
                val parsedManifest = manifest
                if (parsedManifest != null && parsedManifest.requiredTextEntriesFound(hasObjectsEntry, hasSettingsEntry)) {
                    return parsedManifest
                }
            }
        }
        val parsedManifest = manifest ?: throw UnsupportedBackupException("Backup manifest is missing")
        validateRequiredEntries(
            manifest = parsedManifest,
            hasObjectsEntry = hasObjectsEntry,
            hasSettingsEntry = hasSettingsEntry
        )
        return parsedManifest
    }

    fun readArchive(input: InputStream, includeMedia: Boolean): BackupArchive {
        var manifest: BackupManifest? = null
        var objectsText: String? = null
        var settingsText: String? = null
        val media = mutableMapOf<String, ByteArray>()
        var totalMediaBytes = 0L

        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.normalizedZipPath()
                if (!name.isSafeZipPath()) {
                    throw UnsupportedBackupException("Unsafe backup zip entry")
                }
                when {
                    name == MANIFEST_ENTRY -> {
                        manifest = zip.readEntryText(MAX_TEXT_ENTRY_BYTES).let(BackupJson::parseManifest)
                    }
                    name == OBJECTS_ENTRY -> {
                        objectsText = zip.readEntryText(MAX_TEXT_ENTRY_BYTES)
                    }
                    name == SETTINGS_ENTRY -> {
                        settingsText = zip.readEntryText(MAX_TEXT_ENTRY_BYTES)
                    }
                    name.startsWith(MEDIA_PREFIX) -> {
                        val parsedManifest = manifest
                        if (parsedManifest != null &&
                            !parsedManifest.requiredTextEntriesFound(
                                hasObjectsEntry = objectsText != null,
                                hasSettingsEntry = settingsText != null
                            )
                        ) {
                            throw IllegalArgumentException("Backup media appears before required text entries")
                        }
                        if (includeMedia) {
                            val bytes = zip.readEntryBytes(MAX_MEDIA_ENTRY_BYTES)
                            totalMediaBytes += bytes.size
                            if (totalMediaBytes <= MAX_TOTAL_MEDIA_BYTES) {
                                media[name] = bytes
                            } else {
                                throw UnsupportedBackupException("Backup media is too large")
                            }
                        } else {
                            val readyManifest = parsedManifest
                            if (readyManifest != null) {
                                return buildArchive(
                                    manifest = readyManifest,
                                    objectsText = objectsText,
                                    settingsText = settingsText,
                                    media = media
                                )
                            }
                            zip.drainEntryBytes(MAX_TEXT_ENTRY_BYTES)
                        }
                    }
                    else -> zip.drainEntryBytes(MAX_TEXT_ENTRY_BYTES)
                }
            }
        }

        return buildArchive(
            manifest = manifest ?: throw UnsupportedBackupException("Backup manifest is missing"),
            objectsText = objectsText,
            settingsText = settingsText,
            media = media
        )
    }

    private fun buildArchive(
        manifest: BackupManifest,
        objectsText: String?,
        settingsText: String?,
        media: Map<String, ByteArray>
    ): BackupArchive {
        validateRequiredEntries(
            manifest = manifest,
            hasObjectsEntry = objectsText != null,
            hasSettingsEntry = settingsText != null
        )
        val parsedObjects = objectsText
            ?.let(BackupJson::parseObjects)
            ?: ParsedBackupObjects(objects = emptyList(), skippedObjects = 0)
        val settings = settingsText?.let(BackupJson::parseSettings)
        return BackupArchive(
            manifest = manifest,
            objects = parsedObjects.objects,
            skippedBrokenObjects = parsedObjects.skippedObjects,
            settings = settings,
            mediaBytes = media.filterKeys { path ->
                manifest.media.any { it.path == path }
            }
        )
    }

    private fun ZipInputStream.readEntryText(limit: Long): String =
        readEntryBytes(limit).toString(StandardCharsets.UTF_8)

    private fun ZipInputStream.drainEntryBytes(limit: Long) {
        readEntryBytes(limit)
    }

    private fun ZipInputStream.readEntryBytes(limit: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) {
                throw UnsupportedBackupException("Backup entry is too large")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun validateRequiredEntries(
        manifest: BackupManifest,
        hasObjectsEntry: Boolean,
        hasSettingsEntry: Boolean
    ) {
        if (manifest.requiresObjectsEntry() && !hasObjectsEntry) {
            throw IllegalArgumentException("Backup objects.json is missing")
        }
        if (manifest.sections.settings && !hasSettingsEntry) {
            throw IllegalArgumentException("Backup settings.json is missing")
        }
    }

    private fun BackupManifest.requiresObjectsEntry(): Boolean =
        sections.azimuthRays || sections.mapNotes || objectCount > 0

    private fun BackupManifest.requiredTextEntriesFound(
        hasObjectsEntry: Boolean,
        hasSettingsEntry: Boolean
    ): Boolean =
        (!requiresObjectsEntry() || hasObjectsEntry) &&
            (!sections.settings || hasSettingsEntry)

    private fun String.normalizedZipPath(): String =
        replace('\\', '/')

    private fun String.isSafeZipPath(): Boolean =
        isNotBlank() &&
            !startsWith("/") &&
            !contains("/../") &&
            !startsWith("../") &&
            !endsWith("/..")

    private companion object {
        const val MEDIA_PREFIX = "media/notes/"
        const val MAX_TEXT_ENTRY_BYTES = 5L * 1024L * 1024L
        const val MAX_MEDIA_ENTRY_BYTES = 50L * 1024L * 1024L
        const val MAX_TOTAL_MEDIA_BYTES = 200L * 1024L * 1024L
    }
}

internal data class BackupArchive(
    val manifest: BackupManifest,
    val objects: List<com.prishvindt.sector.data.SectorObjectEntity>,
    val skippedBrokenObjects: Int,
    val settings: BackupSettings?,
    val mediaBytes: Map<String, ByteArray>
)
