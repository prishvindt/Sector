package com.prishvindt.sector.domain.backup

import com.prishvindt.sector.data.FakeSectorObjectDao
import com.prishvindt.sector.data.LocalAzimuthRayInput
import com.prishvindt.sector.data.LocalMapNoteInput
import com.prishvindt.sector.data.SectorObjectRepository
import com.prishvindt.sector.domain.GeoPoint
import com.prishvindt.sector.domain.objects.EncryptionState
import com.prishvindt.sector.domain.objects.MapNoteAttachmentPayloadV1
import com.prishvindt.sector.domain.objects.MapNoteAttachmentType
import com.prishvindt.sector.domain.objects.SectorObjectPayloadJson
import com.prishvindt.sector.domain.objects.SyncState
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupManagerTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val fixedClock = Clock.fixed(
        Instant.parse("2026-06-05T09:30:00Z"),
        ZoneOffset.UTC
    )

    @Test
    fun backupManifestHasSectorBackupV1() = runTest {
        val dao = FakeSectorObjectDao()
        val repository = repository(dao, ids = listOf(RAY_ID))
        repository.createLocalAzimuthRay(sampleAzimuthInput())
        val manager = manager(repository)

        val bytes = writeBackup(
            manager = manager,
            selection = BackupSelection(azimuthRays = true)
        )

        val manifest = zipText(bytes, "manifest.json")
        assertTrue(manifest.contains("\"format\":\"SECTOR_BACKUP_V1\""))
        assertTrue(manifest.contains("\"version\":1"))
    }

    @Test
    fun backupImportPreservesAzimuthDistance() = runTest {
        val sourceDao = FakeSectorObjectDao()
        val sourceRepository = repository(sourceDao, ids = listOf(RAY_ID))
        sourceRepository.createLocalAzimuthRay(sampleAzimuthInput(distanceKm = 12.5))
        val bytes = writeBackup(
            manager = manager(sourceRepository),
            selection = BackupSelection(azimuthRays = true)
        )
        val targetDao = FakeSectorObjectDao()

        manager(repository(targetDao)).importBackup(
            input = ByteArrayInputStream(bytes),
            selection = BackupSelection(azimuthRays = true)
        ).getOrThrow()

        val payload = SectorObjectPayloadJson.decodeAzimuthRay(targetDao.snapshot().single().payloadJson).getOrThrow()
        assertEquals(12.5, payload.distanceKm, 0.0)
    }

    @Test
    fun readImportPreviewRejectsOversizedObjectsEntry() = runTest {
        val bytes = manualZip(
            manifest = objectsManifest(azimuthRays = true, objectCount = 1),
            objects = oversizedTextEntry()
        )

        val result = manager(repository(FakeSectorObjectDao())).readImportPreview(
            ByteArrayInputStream(bytes)
        )

        assertTrue(result.exceptionOrNull() is UnsupportedBackupException)
    }

    @Test
    fun backupWithoutMediaDoesNotIncludeMediaFiles() = runTest {
        val filesDir = temp.newFolder("files")
        val dao = FakeSectorObjectDao()
        val repository = repository(dao)
        createNoteWithPhoto(repository, filesDir)
        val manager = manager(repository, filesDir = filesDir)

        val bytes = writeBackup(
            manager = manager,
            selection = BackupSelection(mapNotes = true, noteMedia = false)
        )

        val names = zipEntryNames(bytes)
        assertFalse(names.any { it.startsWith("media/") })
        assertTrue(zipText(bytes, "objects.json").contains("\"mediaIncluded\":false"))
    }

    @Test
    fun backupWithMediaIncludesMediaFiles() = runTest {
        val filesDir = temp.newFolder("files")
        val dao = FakeSectorObjectDao()
        val repository = repository(dao)
        createNoteWithPhoto(repository, filesDir)
        val manager = manager(repository, filesDir = filesDir)

        val bytes = writeBackup(
            manager = manager,
            selection = BackupSelection(mapNotes = true, noteMedia = true)
        )

        val names = zipEntryNames(bytes)
        assertTrue(names.contains("media/notes/$NOTE_ID/photo-1.jpg"))
    }

    @Test
    fun importSkipsActiveDuplicateObjectId() = runTest {
        val sourceDao = FakeSectorObjectDao()
        val sourceRepository = repository(sourceDao, ids = listOf(RAY_ID))
        val sourceEntity = sourceRepository.createLocalAzimuthRay(sampleAzimuthInput())
        val bytes = writeBackup(
            manager = manager(sourceRepository),
            selection = BackupSelection(azimuthRays = true)
        )
        val targetDao = FakeSectorObjectDao(listOf(sourceEntity))
        val targetRepository = repository(targetDao)

        val result = manager(targetRepository).importBackup(
            input = ByteArrayInputStream(bytes),
            selection = BackupSelection(azimuthRays = true)
        ).getOrThrow()

        assertEquals(0, result.importedObjects)
        assertEquals(1, result.skippedObjects)
        assertEquals(1, targetDao.snapshot().size)
    }

    @Test
    fun importRestoresSoftDeletedObjectWithSameObjectId() = runTest {
        val sourceDao = FakeSectorObjectDao()
        val sourceRepository = repository(sourceDao, ids = listOf(RAY_ID))
        val sourceEntity = sourceRepository.createLocalAzimuthRay(sampleAzimuthInput())
        val bytes = writeBackup(
            manager = manager(sourceRepository),
            selection = BackupSelection(azimuthRays = true)
        )
        val targetDao = FakeSectorObjectDao(listOf(sourceEntity))
        val targetRepository = repository(targetDao)
        targetRepository.softDeleteObject(RAY_ID)

        val result = manager(targetRepository).importBackup(
            input = ByteArrayInputStream(bytes),
            selection = BackupSelection(azimuthRays = true)
        ).getOrThrow()

        val saved = targetDao.snapshot().single()
        assertEquals(1, result.importedObjects)
        assertEquals(0, result.skippedObjects)
        assertEquals(RAY_ID, saved.objectId)
        assertEquals(null, saved.deletedAt)
        assertEquals(SyncState.LOCAL_ONLY.wireName, saved.syncState)
        assertEquals(EncryptionState.PLAIN_LOCAL.wireName, saved.encryptionState)
    }

    @Test
    fun importSettingsAppliesOnlyAllowedFields() = runTest {
        val settingsStore = FakeBackupSettingsStore(
            current = BackupSettings(
                ownPointColor = "BLUE",
                gpsPointScale = 1f,
                showMapNotes = true
            ),
            callsign = "LOCAL"
        )
        val bytes = manualZip(
            manifest = settingsManifest(),
            settings = """
                {
                  "ownPointColor": "GREEN",
                  "gpsPointScale": 3.5,
                  "showMapNotes": false,
                  "callsign": "IMPORTED",
                  "telemetryEnabled": true
                }
            """.trimIndent()
        )

        manager(
            repository = repository(FakeSectorObjectDao()),
            settingsStore = settingsStore
        ).importBackup(
            input = ByteArrayInputStream(bytes),
            selection = BackupSelection(settings = true)
        ).getOrThrow()

        assertEquals("GREEN", settingsStore.current.ownPointColor)
        assertEquals(3.5f, settingsStore.current.gpsPointScale ?: 0f, 0f)
        assertEquals(false, settingsStore.current.showMapNotes)
        assertEquals("LOCAL", settingsStore.callsign)
    }

    @Test
    fun importDoesNotImportCallsign() = runTest {
        val bytes = manualZip(
            manifest = objectsManifest(azimuthRays = true, objectCount = 1),
            objects = """
                {
                  "objects": [
                    {
                      "objectId": "$RAY_ID",
                      "objectType": "AZIMUTH_RAY",
                      "ownerKind": "CONTACT",
                      "ownerId": "r2abc",
                      "deviceId": "device-remote",
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
                        "callsign": "R2ABC"
                      }
                    }
                  ]
                }
            """.trimIndent()
        )
        val dao = FakeSectorObjectDao()

        manager(repository(dao)).importBackup(
            input = ByteArrayInputStream(bytes),
            selection = BackupSelection(azimuthRays = true)
        ).getOrThrow()

        val saved = dao.snapshot().single()
        val payload = SectorObjectPayloadJson.decodeAzimuthRay(saved.payloadJson).getOrThrow()
        assertEquals(null, payload.callsign)
        assertEquals(15.0, payload.distanceKm, 0.0)
        assertEquals(null, saved.ownerId)
        assertEquals(null, saved.deviceId)
        assertFalse(saved.payloadJson.contains("signal"))
    }

    @Test
    fun importedNoteMediaRestoresLocalPathOnlyWhenFileExists() = runTest {
        val filesDir = temp.newFolder("files")
        val bytes = manualZip(
            manifest = """
                {
                  "format": "SECTOR_BACKUP_V1",
                  "version": 1,
                  "createdAt": 1779556500000,
                  "sections": {
                    "azimuthRays": false,
                    "mapNotes": true,
                    "noteMedia": true,
                    "settings": false
                  },
                  "mediaIncluded": true,
                  "objectCount": 1,
                  "mediaCount": 2,
                  "media": [
                    {
                      "objectId": "$NOTE_ID",
                      "attachmentId": "photo-1",
                      "path": "media/notes/$NOTE_ID/photo-1.jpg",
                      "mimeType": "image/jpeg",
                      "sizeBytes": 4
                    },
                    {
                      "objectId": "$NOTE_ID",
                      "attachmentId": "photo-2",
                      "path": "media/notes/$NOTE_ID/photo-2.jpg",
                      "mimeType": "image/jpeg",
                      "sizeBytes": 4
                    }
                  ]
                }
            """.trimIndent(),
            objects = noteObjectsJson(),
            media = mapOf("media/notes/$NOTE_ID/photo-1.jpg" to byteArrayOf(1, 2, 3, 4))
        )
        val dao = FakeSectorObjectDao()

        val result = manager(repository(dao), filesDir = filesDir).importBackup(
            input = ByteArrayInputStream(bytes),
            selection = BackupSelection(mapNotes = true, noteMedia = true)
        ).getOrThrow()

        val payload = SectorObjectPayloadJson.decodeMapNote(dao.snapshot().single().payloadJson).getOrThrow()
        val attachment = payload.attachments.single()
        assertEquals(1, result.restoredMedia)
        assertEquals(1, result.missingMedia)
        assertTrue(attachment.mediaIncluded)
        assertTrue(attachment.localPath.isNotBlank())
        assertTrue(File(filesDir, attachment.localPath).isFile)
    }

    private suspend fun createNoteWithPhoto(
        repository: SectorObjectRepository,
        filesDir: File
    ) {
        val noteDir = File(filesDir, "notes/$NOTE_ID").apply { mkdirs() }
        File(noteDir, "photo_1.jpg").writeBytes(byteArrayOf(1, 2, 3, 4))
        repository.createOrUpdateLocalMapNote(
            LocalMapNoteInput(
                objectId = NOTE_ID,
                point = GeoPoint(55.0, 37.0),
                title = "Заметка",
                text = "Текст",
                attachments = listOf(
                    MapNoteAttachmentPayloadV1(
                        attachmentId = "photo-1",
                        type = MapNoteAttachmentType.PHOTO,
                        localPath = "notes/$NOTE_ID/photo_1.jpg",
                        mimeType = "image/jpeg",
                        sizeBytes = 4L,
                        durationMs = null,
                        createdAt = fixedClock.millis(),
                        mediaIncluded = true
                    )
                ),
                createdAt = fixedClock.millis()
            )
        )
    }

    private fun manager(
        repository: SectorObjectRepository,
        filesDir: File = temp.newFolder("files-${System.nanoTime()}"),
        settingsStore: BackupSettingsStore = FakeBackupSettingsStore()
    ): BackupManager =
        BackupManager(
            objectRepository = repository,
            settingsStore = settingsStore,
            mediaStorage = FileBackupMediaStorage(filesDir),
            clock = fixedClock
        )

    private fun repository(
        dao: FakeSectorObjectDao,
        ids: List<String> = emptyList()
    ): SectorObjectRepository {
        val iterator = ids.iterator()
        return SectorObjectRepository(
            dao = dao,
            clock = fixedClock,
            idFactory = {
                if (iterator.hasNext()) iterator.next() else "550e8400-e29b-41d4-a716-446655440099"
            },
            deviceIdProvider = { "device-local" }
        )
    }

    private fun sampleAzimuthInput(distanceKm: Double = 15.0): LocalAzimuthRayInput =
        LocalAzimuthRayInput(
            point = GeoPoint(55.0, 37.0),
            callsign = "R2ABC",
            azimuth = 123.0,
            error = 5.0,
            distanceKm = distanceKm
        )

    private suspend fun writeBackup(
        manager: BackupManager,
        selection: BackupSelection
    ): ByteArray {
        val output = ByteArrayOutputStream()
        manager.writeBackup(output, selection).getOrThrow()
        return output.toByteArray()
    }

    private fun manualZip(
        manifest: String,
        objects: String = """{"objects": []}""",
        settings: String? = null,
        media: Map<String, ByteArray> = emptyMap()
    ): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putText("manifest.json", manifest)
            zip.putText("objects.json", objects)
            if (settings != null) {
                zip.putText("settings.json", settings)
            }
            media.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun ZipOutputStream.putText(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(StandardCharsets.UTF_8))
        closeEntry()
    }

    private fun oversizedTextEntry(): String =
        " ".repeat(5 * 1024 * 1024 + 1)

    private fun zipText(bytes: ByteArray, name: String): String {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == name) {
                    return zip.readBytes().toString(StandardCharsets.UTF_8)
                }
            }
        }
        error("Missing zip entry $name")
    }

    private fun zipEntryNames(bytes: ByteArray): List<String> {
        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                names += entry.name
            }
        }
        return names
    }

    private fun settingsManifest(): String =
        """
            {
              "format": "SECTOR_BACKUP_V1",
              "version": 1,
              "createdAt": 1779556500000,
              "sections": {
                "azimuthRays": false,
                "mapNotes": false,
                "noteMedia": false,
                "settings": true
              },
              "mediaIncluded": false,
              "objectCount": 0,
              "mediaCount": 0,
              "media": []
            }
        """.trimIndent()

    private fun objectsManifest(
        azimuthRays: Boolean = false,
        mapNotes: Boolean = false,
        noteMedia: Boolean = false,
        objectCount: Int
    ): String =
        """
            {
              "format": "SECTOR_BACKUP_V1",
              "version": 1,
              "createdAt": 1779556500000,
              "sections": {
                "azimuthRays": $azimuthRays,
                "mapNotes": $mapNotes,
                "noteMedia": $noteMedia,
                "settings": false
              },
              "mediaIncluded": $noteMedia,
              "objectCount": $objectCount,
              "mediaCount": 0,
              "media": []
            }
        """.trimIndent()

    private fun noteObjectsJson(): String =
        """
            {
              "objects": [
                {
                  "objectId": "$NOTE_ID",
                  "objectType": "MAP_NOTE",
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
                    "title": "Заметка",
                    "text": "Текст",
                    "createdAt": 1779556500000,
                    "updatedAt": 1779556500000,
                    "attachments": [
                      {
                        "attachmentId": "photo-1",
                        "type": "PHOTO",
                        "localPath": "",
                        "mimeType": "image/jpeg",
                        "sizeBytes": 4,
                        "durationMs": null,
                        "createdAt": 1779556500000,
                        "mediaIncluded": false
                      },
                      {
                        "attachmentId": "photo-2",
                        "type": "PHOTO",
                        "localPath": "",
                        "mimeType": "image/jpeg",
                        "sizeBytes": 4,
                        "durationMs": null,
                        "createdAt": 1779556500000,
                        "mediaIncluded": false
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

    private class FakeBackupSettingsStore(
        var current: BackupSettings = BackupSettings(
            ownPointColor = "BLUE",
            gpsPointScale = 1f
        ),
        var callsign: String = ""
    ) : BackupSettingsStore {
        override suspend fun backupSettings(): BackupSettings = current

        override suspend fun applyBackupSettings(settings: BackupSettings) {
            current = BackupSettings(
                ownPointColor = settings.ownPointColor ?: current.ownPointColor,
                gpsPointScale = settings.gpsPointScale ?: current.gpsPointScale,
                destinationMarkerType = settings.destinationMarkerType ?: current.destinationMarkerType,
                gpsMode = settings.gpsMode ?: current.gpsMode,
                accuracyWarningMeters = settings.accuracyWarningMeters ?: current.accuracyWarningMeters,
                showSelfCallsign = settings.showSelfCallsign ?: current.showSelfCallsign,
                showImportedCallsigns = settings.showImportedCallsigns ?: current.showImportedCallsigns,
                callsignBehavior = settings.callsignBehavior ?: current.callsignBehavior,
                routeMode = settings.routeMode ?: current.routeMode,
                routeType = settings.routeType ?: current.routeType,
                showMapNotes = settings.showMapNotes ?: current.showMapNotes,
                showMapNoteTitles = settings.showMapNoteTitles ?: current.showMapNoteTitles
            )
        }
    }

    private companion object {
        const val RAY_ID = "550e8400-e29b-41d4-a716-446655440000"
        const val NOTE_ID = "550e8400-e29b-41d4-a716-446655440001"
    }
}
