package com.prishvindt.sector.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupJsonTest {
    @Test
    fun parseManifestAcceptsVersionOne() {
        val parsed = BackupJson.parseManifest(manifest(version = 1L))

        assertEquals(SECTOR_BACKUP_FORMAT, parsed.format)
        assertEquals(SECTOR_BACKUP_VERSION, parsed.version)
    }

    @Test
    fun parseManifestRejectsUnsupportedFormat() {
        val result = runCatching {
            BackupJson.parseManifest(manifest(format = "OTHER_BACKUP_V1"))
        }

        assertTrue(result.exceptionOrNull() is UnsupportedBackupException)
    }

    @Test
    fun parseManifestRejectsUnsupportedVersionTwo() {
        val result = runCatching {
            BackupJson.parseManifest(manifest(version = 2L))
        }

        assertTrue(result.exceptionOrNull() is UnsupportedBackupException)
    }

    @Test
    fun parseManifestRejectsUnsupportedVersionBeforeIntConversion() {
        val result = runCatching {
            BackupJson.parseManifest(manifest(version = 4_294_967_297L))
        }

        assertTrue(result.exceptionOrNull() is UnsupportedBackupException)
    }

    @Test
    fun parseManifestRejectsObjectCountOverflow() {
        val result = runCatching {
            BackupJson.parseManifest(manifest(objectCount = 4_294_967_297L))
        }

        assertTrue(result.exceptionOrNull() is UnsupportedBackupException)
    }

    @Test
    fun parseManifestRejectsMediaCountOverflow() {
        val result = runCatching {
            BackupJson.parseManifest(manifest(mediaCount = 4_294_967_297L))
        }

        assertTrue(result.exceptionOrNull() is UnsupportedBackupException)
    }

    @Test
    fun parseManifestPreservesLargeMediaSizeAsLong() {
        val parsed = BackupJson.parseManifest(
            manifest(
                mediaCount = 1L,
                mediaJson = """
                    [
                      {
                        "objectId": "550e8400-e29b-41d4-a716-446655440001",
                        "attachmentId": "photo-1",
                        "path": "media/notes/550e8400-e29b-41d4-a716-446655440001/photo-1.jpg",
                        "mimeType": "image/jpeg",
                        "sizeBytes": 4294967297
                      }
                    ]
                """.trimIndent()
            )
        )

        assertEquals(4_294_967_297L, parsed.media.single().sizeBytes)
    }

    private fun manifest(
        format: String = "SECTOR_BACKUP_V1",
        version: Long = 1L,
        objectCount: Long = 0L,
        mediaCount: Long = 0L,
        mediaJson: String = "[]"
    ): String =
        """
            {
              "format": "$format",
              "version": $version,
              "createdAt": 1779556500000,
              "sections": {
                "azimuthRays": false,
                "mapNotes": false,
                "noteMedia": false,
                "settings": false
              },
              "mediaIncluded": false,
              "objectCount": $objectCount,
              "mediaCount": $mediaCount,
              "media": $mediaJson
            }
        """.trimIndent()
}
