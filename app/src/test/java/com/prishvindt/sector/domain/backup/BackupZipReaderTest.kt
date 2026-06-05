package com.prishvindt.sector.domain.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupZipReaderTest {
    private val reader = BackupZipReader()

    @Test
    fun readArchiveRejectsMissingObjectsWhenObjectCountPositive() {
        val result = runCatching {
            reader.readArchive(
                input = ByteArrayInputStream(
                    zipBytes(
                        manifest = manifest(objectCount = 1)
                    )
                ),
                includeMedia = false
            )
        }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun readManifestRejectsMissingObjectsWhenMapNotesAdvertised() {
        val result = runCatching {
            reader.readManifest(
                ByteArrayInputStream(
                    zipBytes(
                        manifest = manifest(mapNotes = true)
                    )
                )
            )
        }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun readArchiveAllowsSettingsOnlyBackupWithoutObjects() {
        val archive = reader.readArchive(
            input = ByteArrayInputStream(
                zipBytes(
                    manifest = manifest(settings = true),
                    settings = """{"showMapNotes":false}"""
                )
            ),
            includeMedia = false
        )

        assertEquals(0, archive.objects.size)
        assertEquals(false, archive.settings?.showMapNotes)
    }

    @Test
    fun readArchiveRejectsMissingSettingsWhenSettingsAdvertised() {
        val result = runCatching {
            reader.readArchive(
                input = ByteArrayInputStream(
                    zipBytes(
                        manifest = manifest(settings = true)
                    )
                ),
                includeMedia = false
            )
        }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun readArchiveReadsValidBackup() {
        val archive = reader.readArchive(
            input = ByteArrayInputStream(
                zipBytes(
                    manifest = manifest(azimuthRays = true, objectCount = 1),
                    objects = validObjectsJson()
                )
            ),
            includeMedia = false
        )

        assertEquals(1, archive.objects.size)
        assertEquals("550e8400-e29b-41d4-a716-446655440000", archive.objects.single().objectId)
    }

    private fun zipBytes(
        manifest: String,
        objects: String? = null,
        settings: String? = null
    ): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putText("manifest.json", manifest)
            if (objects != null) {
                zip.putText("objects.json", objects)
            }
            if (settings != null) {
                zip.putText("settings.json", settings)
            }
        }
        return output.toByteArray()
    }

    private fun ZipOutputStream.putText(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(StandardCharsets.UTF_8))
        closeEntry()
    }

    private fun manifest(
        azimuthRays: Boolean = false,
        mapNotes: Boolean = false,
        settings: Boolean = false,
        objectCount: Int = 0
    ): String =
        """
            {
              "format": "SECTOR_BACKUP_V1",
              "version": 1,
              "createdAt": 1779556500000,
              "sections": {
                "azimuthRays": $azimuthRays,
                "mapNotes": $mapNotes,
                "noteMedia": false,
                "settings": $settings
              },
              "mediaIncluded": false,
              "objectCount": $objectCount,
              "mediaCount": 0,
              "media": []
            }
        """.trimIndent()

    private fun validObjectsJson(): String =
        """
            {
              "objects": [
                {
                  "objectId": "550e8400-e29b-41d4-a716-446655440000",
                  "objectType": "AZIMUTH_RAY",
                  "ownerKind": "ME",
                  "ownerId": null,
                  "deviceId": null,
                  "sourceKind": "LOCAL",
                  "createdAt": 1779556500000,
                  "updatedAt": 1779556500000,
                  "deletedAt": null,
                  "syncState": "LOCAL_ONLY",
                  "visibility": "SHAREABLE",
                  "encryptionState": "PLAIN_LOCAL",
                  "payloadVersion": 1,
                  "payload": {
                    "latitude": 55.0,
                    "longitude": 37.0,
                    "azimuth": 123.0,
                    "error": 5.0,
                    "signal": -80,
                    "callsign": null
                  }
                }
              ]
            }
        """.trimIndent()
}
