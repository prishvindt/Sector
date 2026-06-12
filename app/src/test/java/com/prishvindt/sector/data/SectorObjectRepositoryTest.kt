package com.prishvindt.sector.data

import com.prishvindt.sector.domain.GeoPoint
import com.prishvindt.sector.domain.LocationSharePayload
import com.prishvindt.sector.domain.objects.EncryptionState
import com.prishvindt.sector.domain.objects.MapNoteAttachmentPayloadV1
import com.prishvindt.sector.domain.objects.MapNoteAttachmentType
import com.prishvindt.sector.domain.objects.ObjectVisibility
import com.prishvindt.sector.domain.objects.OwnerKind
import com.prishvindt.sector.domain.objects.SectorBundleFormat
import com.prishvindt.sector.domain.objects.SectorJson
import com.prishvindt.sector.domain.objects.SectorObjectPayloadJson
import com.prishvindt.sector.domain.objects.SectorObjectType
import com.prishvindt.sector.domain.objects.SourceKind
import com.prishvindt.sector.domain.objects.SyncState
import com.prishvindt.sector.domain.objects.asArrayOrNull
import com.prishvindt.sector.domain.objects.asObjectOrNull
import com.prishvindt.sector.domain.objects.requiredString
import com.prishvindt.sector.map.MapObjectVisibilityPolicy
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SectorObjectRepositoryTest {
    private val fixedClock = Clock.fixed(
        Instant.parse("2026-05-23T17:15:00Z"),
        ZoneOffset.UTC
    )

    @Test
    fun createLocalAzimuthRayCreatesSectorObject() = runTest {
        val dao = FakeSectorObjectDao()
        val repository = repository(
            dao = dao,
            ids = listOf("550e8400-e29b-41d4-a716-446655440000")
        )

        val entity = repository.createLocalAzimuthRay(
            LocalAzimuthRayInput(
                point = GeoPoint(55.123456, 37.123456),
                callsign = "R2ABC",
                azimuth = 123.0,
                error = 15.0,
                distanceKm = 10.5
            )
        )

        val payload = SectorObjectPayloadJson.decodeAzimuthRay(entity.payloadJson).getOrThrow()
        assertEquals("550e8400-e29b-41d4-a716-446655440000", entity.objectId)
        assertEquals(SectorObjectType.AZIMUTH_RAY.wireName, entity.objectType)
        assertEquals(OwnerKind.ME.wireName, entity.ownerKind)
        assertEquals(SourceKind.LOCAL.wireName, entity.sourceKind)
        assertEquals(SyncState.LOCAL_ONLY.wireName, entity.syncState)
        assertEquals(ObjectVisibility.SHAREABLE.wireName, entity.visibility)
        assertEquals(EncryptionState.PLAIN_LOCAL.wireName, entity.encryptionState)
        assertEquals("R2ABC", payload.callsign)
        assertEquals(123.0, payload.azimuth, 0.0)
        assertEquals(10.5, payload.distanceKm, 0.0)
        assertFalse(entity.payloadJson.contains("signal"))
    }

    @Test
    fun importSharedLocationFromLegacyCreatesSharedLocationSectorObject() = runTest {
        val dao = FakeSectorObjectDao()
        val repository = repository(
            dao = dao,
            ids = listOf("550e8400-e29b-41d4-a716-446655440000")
        )

        val entity = repository.importSharedLocationFromLegacy(
            LocationSharePayload(
                callsign = "R2ABC",
                latitude = 55.0,
                longitude = 37.0,
                accuracyMeters = 8.0,
                timestampEpochSeconds = 1_710_000_000L
            )
        )

        val payload = SectorObjectPayloadJson.decodeSharedLocation(entity.payloadJson).getOrThrow()
        assertEquals(SectorObjectType.SHARED_LOCATION.wireName, entity.objectType)
        assertEquals(OwnerKind.CONTACT.wireName, entity.ownerKind)
        assertEquals(SourceKind.IMPORTED_MESSAGE.wireName, entity.sourceKind)
        assertEquals("r2abc", entity.ownerId)
        assertEquals("R2ABC", payload.callsign)
        assertEquals(8.0, payload.accuracyMeters ?: 0.0, 0.0)
    }

    @Test
    fun exportObjectsWritesSectorBundleV1() = runTest {
        val dao = FakeSectorObjectDao()
        val repository = repository(
            dao = dao,
            ids = listOf("550e8400-e29b-41d4-a716-446655440000")
        )
        val entity = repository.createLocalAzimuthRay(sampleAzimuthInput())

        val text = repository.exportObjects(listOf(entity), callsign = "R2ABC").getOrThrow()
        val root = SectorJson.parse(text).getOrThrow().asObjectOrNull()!!
        val parsed = SectorBundleFormat.parse(text).getOrThrow()
        val payloadJson = SectorObjectPayloadJson.stringifyPayload(parsed.objects.single().payload)

        assertEquals("SECTOR_BUNDLE_V1", root.requiredString("format"))
        assertEquals(1, parsed.objects.size)
        assertEquals("R2ABC", parsed.sender.callsign)
        assertEquals(SectorObjectType.AZIMUTH_RAY.wireName, parsed.objects.single().objectType)
        assertTrue(payloadJson.contains("\"distanceKm\":15.0"))
        assertFalse(payloadJson.contains("signal"))
    }

    @Test
    fun importBundleWithOneObjectStoresObject() = runTest {
        val sourceDao = FakeSectorObjectDao()
        val sourceRepository = repository(
            dao = sourceDao,
            ids = listOf("550e8400-e29b-41d4-a716-446655440000")
        )
        val bundle = sourceRepository.exportObjects(
            objects = listOf(sourceRepository.createLocalAzimuthRay(sampleAzimuthInput())),
            callsign = "R2ABC"
        ).getOrThrow()
        val targetDao = FakeSectorObjectDao()
        val targetRepository = repository(targetDao)

        val result = targetRepository.importObjectsFromBundle(bundle).getOrThrow()

        val saved = targetDao.snapshot().single()
        assertEquals(1, result.imported.size)
        assertEquals(0, result.skippedObjects)
        assertEquals(SectorObjectType.AZIMUTH_RAY.wireName, saved.objectType)
        assertEquals(OwnerKind.CONTACT.wireName, saved.ownerKind)
        assertEquals("r2abc", saved.ownerId)
        assertEquals(SourceKind.IMPORTED_MESSAGE.wireName, saved.sourceKind)
    }

    @Test
    fun importOwnExportedBundleSkipsExistingLocalSelfObject() = runTest {
        val dao = FakeSectorObjectDao()
        val repository = repository(
            dao = dao,
            ids = listOf("550e8400-e29b-41d4-a716-446655440000")
        )
        val local = repository.createLocalAzimuthRay(sampleAzimuthInput())
        val bundle = repository.exportObjects(listOf(local), callsign = "R2ABC-EXPORT").getOrThrow()

        val result = repository.importObjectsFromBundle(bundle).getOrThrow()

        val saved = dao.snapshot().single()
        val latestSelf = repository.latestSelfAzimuthRay()
        assertEquals(0, result.imported.size)
        assertEquals(1, result.skippedObjects)
        assertEquals(local.objectId, saved.objectId)
        assertEquals(OwnerKind.ME.wireName, saved.ownerKind)
        assertEquals(SourceKind.LOCAL.wireName, saved.sourceKind)
        assertEquals(local.objectId, latestSelf?.measurementId)
    }

    @Test
    fun importBundleDoesNotUseSenderDeviceIdAsOwnerId() = runTest {
        val dao = FakeSectorObjectDao()
        val repository = repository(dao)
        val bundle = """
            {
              "format": "SECTOR_BUNDLE_V1",
              "version": 1,
              "createdAt": 1779556500000,
              "sender": {"callsign": null, "deviceId": "sender-device-1"},
              "objects": [
                {
                  "objectId": "550e8400-e29b-41d4-a716-446655440000",
                  "objectType": "AZIMUTH_RAY",
                  "ownerKind": "ME",
                  "sourceKind": "LOCAL",
                  "createdAt": 1779556500000,
                  "updatedAt": 1779556500000,
                  "payloadVersion": 1,
                  "payload": {
                    "latitude": 55.123456,
                    "longitude": 37.123456,
                    "azimuth": 123.0,
                    "error": 15.0,
                    "signal": -80,
                    "callsign": "R2ABC"
                  }
                }
              ]
            }
        """.trimIndent()

        repository.importObjectsFromBundle(bundle).getOrThrow()

        val saved = dao.snapshot().single()
        val payload = SectorObjectPayloadJson.decodeAzimuthRay(saved.payloadJson).getOrThrow()
        assertEquals(null, saved.ownerId)
        assertEquals("sender-device-1", saved.deviceId)
        assertEquals(15.0, payload.distanceKm, 0.0)
        assertFalse(saved.payloadJson.contains("signal"))
    }

    @Test
    fun importBundleWithMultipleObjectsStoresObjects() = runTest {
        val sourceDao = FakeSectorObjectDao()
        val sourceRepository = repository(
            dao = sourceDao,
            ids = listOf(
                "550e8400-e29b-41d4-a716-446655440000",
                "550e8400-e29b-41d4-a716-446655440001"
            )
        )
        val first = sourceRepository.createLocalAzimuthRay(sampleAzimuthInput())
        val second = sourceRepository.createLocalSharedLocation(
            LocalSharedLocationInput(
                point = GeoPoint(56.0, 38.0),
                callsign = "R2ABC",
                accuracyMeters = 7.0,
                bearing = null,
                timestampEpochSeconds = 1_710_000_001L
            )
        )
        val bundle = sourceRepository.exportObjects(listOf(first, second), callsign = "R2ABC").getOrThrow()
        val targetDao = FakeSectorObjectDao()
        val targetRepository = repository(targetDao)

        val result = targetRepository.importObjectsFromBundle(bundle).getOrThrow()

        assertEquals(2, result.imported.size)
        assertEquals(
            listOf(SectorObjectType.AZIMUTH_RAY.wireName, SectorObjectType.SHARED_LOCATION.wireName),
            targetDao.snapshot().map { it.objectType }.sorted()
        )
    }

    @Test
    fun exportObjectsWritesMapNoteWithoutMediaBytes() = runTest {
        val dao = FakeSectorObjectDao()
        val repository = repository(dao)
        val note = repository.createOrUpdateLocalMapNote(
            LocalMapNoteInput(
                objectId = "550e8400-e29b-41d4-a716-446655440000",
                point = GeoPoint(55.0, 37.0),
                title = "Заметка 1",
                text = "Текст",
                attachments = listOf(
                    MapNoteAttachmentPayloadV1(
                        attachmentId = "photo-1",
                        type = MapNoteAttachmentType.PHOTO,
                        localPath = "notes/550e8400-e29b-41d4-a716-446655440000/photo_1.jpg",
                        mimeType = "image/jpeg",
                        sizeBytes = 100L,
                        durationMs = null,
                        createdAt = fixedClock.millis()
                    )
                )
            )
        )

        val text = repository.exportObjects(listOf(note), callsign = "R2ABC").getOrThrow()
        val parsed = SectorBundleFormat.parse(text).getOrThrow()
        val payload = parsed.objects.single().payload.asObjectOrNull()!!
        val attachments = payload["attachments"]!!.asArrayOrNull()!!
        val exportedAttachment = attachments.single().asObjectOrNull()!!

        assertEquals(SectorObjectType.MAP_NOTE.wireName, parsed.objects.single().objectType)
        assertEquals("", exportedAttachment.requiredString("localPath"))
        assertTrue(text.contains("\"mediaIncluded\":false"))
        assertFalse(text.contains("base64"))
    }

    @Test
    fun importBundleWithMapNoteStoresNote() = runTest {
        val dao = FakeSectorObjectDao()
        val repository = repository(dao)

        val result = repository.importObjectsFromBundle(mapNoteBundle()).getOrThrow()

        val saved = dao.snapshot().single()
        val payload = SectorObjectPayloadJson.decodeMapNote(saved.payloadJson).getOrThrow()
        assertEquals(1, result.imported.size)
        assertEquals(SectorObjectType.MAP_NOTE.wireName, saved.objectType)
        assertEquals("Заметка импорт", payload.title)
        assertEquals("Текст импорт", payload.text)
    }

    @Test
    fun importBundleWithMapNoteMissingMediaDoesNotCrash() = runTest {
        val dao = FakeSectorObjectDao()
        val repository = repository(dao)

        repository.importObjectsFromBundle(mapNoteBundle(includeMissingMedia = true)).getOrThrow()

        val payload = SectorObjectPayloadJson.decodeMapNote(dao.snapshot().single().payloadJson).getOrThrow()
        assertEquals(1, payload.attachments.size)
        assertEquals("", payload.attachments.single().localPath)
        assertFalse(payload.attachments.single().mediaIncluded)
    }

    @Test
    fun updateImportedMapNotePreservesOwnershipAndSource() = runTest {
        val dao = FakeSectorObjectDao()
        val repository = repository(dao)
        repository.importObjectsFromBundle(mapNoteBundle()).getOrThrow()
        val imported = dao.snapshot().single()

        repository.createOrUpdateLocalMapNote(
            LocalMapNoteInput(
                objectId = imported.objectId,
                point = GeoPoint(56.0, 38.0),
                title = "Заметка обновлена",
                text = "Новый текст",
                attachments = emptyList()
            )
        )

        val saved = dao.snapshot().single()
        val payload = SectorObjectPayloadJson.decodeMapNote(saved.payloadJson).getOrThrow()
        assertEquals(SectorObjectType.MAP_NOTE.wireName, saved.objectType)
        assertEquals(OwnerKind.CONTACT.wireName, saved.ownerKind)
        assertEquals(SourceKind.IMPORTED_MESSAGE.wireName, saved.sourceKind)
        assertEquals("r2abc", saved.ownerId)
        assertEquals("sender-device-1", saved.deviceId)
        assertEquals(imported.syncState, saved.syncState)
        assertEquals(imported.visibility, saved.visibility)
        assertEquals(imported.createdAt, saved.createdAt)
        assertEquals("Заметка обновлена", payload.title)
        assertEquals("Новый текст", payload.text)
        assertEquals(56.0, payload.latitude, 0.0)
        assertEquals(38.0, payload.longitude, 0.0)
    }

    @Test
    fun importBundleSharedLocationReplacesPreviousActiveLocationForSameContact() = runTest {
        val dao = FakeSectorObjectDao()
        val repository = repository(dao)
        val firstId = "550e8400-e29b-41d4-a716-446655440000"
        val secondId = "550e8400-e29b-41d4-a716-446655440001"

        repository.importObjectsFromBundle(
            sharedLocationBundle(
                objectId = firstId,
                latitude = 55.0,
                timestamp = 1_710_000_000L
            )
        ).getOrThrow()
        repository.importObjectsFromBundle(
            sharedLocationBundle(
                objectId = secondId,
                latitude = 56.0,
                timestamp = 1_710_000_100L
            )
        ).getOrThrow()

        val saved = dao.snapshot()
        val active = repository.activeObjects(SectorObjectType.SHARED_LOCATION)
        assertEquals(2, saved.size)
        assertTrue(saved.single { it.objectId == firstId }.deletedAt != null)
        assertEquals(listOf(secondId), active.map { it.objectId })
        assertEquals("r2abc", active.single().ownerId)
    }

    @Test
    fun softDeleteDoesNotReturnObjectInActiveList() = runTest {
        val dao = FakeSectorObjectDao()
        val repository = repository(
            dao = dao,
            ids = listOf("550e8400-e29b-41d4-a716-446655440000")
        )
        val entity = repository.createLocalAzimuthRay(sampleAzimuthInput())

        repository.softDeleteObject(entity.objectId)

        assertTrue(repository.activeObjects().isEmpty())
        assertTrue(repository.observeActiveObjects().first().isEmpty())
        assertTrue(dao.snapshot().single().deletedAt != null)
    }

    @Test
    fun unknownObjectTypeImportsButMapPolicyDoesNotShowIt() = runTest {
        val dao = FakeSectorObjectDao()
        val repository = repository(dao)
        val bundle = """
            {
              "format": "SECTOR_BUNDLE_V1",
              "version": 1,
              "createdAt": 1779556500000,
              "sender": {"callsign": "R2ABC", "deviceId": "device-1"},
              "objects": [
                {
                  "objectId": "550e8400-e29b-41d4-a716-446655440000",
                  "objectType": "FUTURE_OBJECT",
                  "ownerKind": "ME",
                  "sourceKind": "LOCAL",
                  "createdAt": 1779556500000,
                  "updatedAt": 1779556500000,
                  "payloadVersion": 99,
                  "payload": {"hello": "world"}
                }
              ]
            }
        """.trimIndent()

        val result = repository.importObjectsFromBundle(bundle).getOrThrow()

        assertEquals(1, result.imported.size)
        assertEquals("FUTURE_OBJECT", dao.snapshot().single().objectType)
        assertFalse(MapObjectVisibilityPolicy.shouldShowObject(SectorObjectType.UNKNOWN))
    }

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

    private fun sampleAzimuthInput(): LocalAzimuthRayInput =
        LocalAzimuthRayInput(
            point = GeoPoint(55.123456, 37.123456),
            callsign = "R2ABC",
            azimuth = 123.0,
            error = 15.0,
            distanceKm = 15.0
        )

    private fun sharedLocationBundle(
        objectId: String,
        latitude: Double,
        timestamp: Long
    ): String = """
        {
          "format": "SECTOR_BUNDLE_V1",
          "version": 1,
          "createdAt": 1779556500000,
          "sender": {"callsign": "R2ABC", "deviceId": "sender-device-1"},
          "objects": [
            {
              "objectId": "$objectId",
              "objectType": "SHARED_LOCATION",
              "ownerKind": "ME",
              "sourceKind": "LOCAL",
              "createdAt": 1779556500000,
              "updatedAt": 1779556500000,
              "payloadVersion": 1,
              "payload": {
                "latitude": $latitude,
                "longitude": 37.0,
                "accuracyMeters": 8.0,
                "bearing": null,
                "timestamp": $timestamp,
                "callsign": "R2ABC"
              }
            }
          ]
        }
    """.trimIndent()

    private fun mapNoteBundle(includeMissingMedia: Boolean = false): String {
        val attachment = if (includeMissingMedia) {
            """
                "attachments": [
                  {
                    "attachmentId": "photo-1",
                    "type": "PHOTO",
                    "localPath": "notes/remote/photo_1.jpg",
                    "mimeType": "image/jpeg",
                    "sizeBytes": 100,
                    "durationMs": null,
                    "createdAt": 1779556500000
                  }
                ],
            """.trimIndent()
        } else {
            "\"attachments\": [],"
        }
        return """
            {
              "format": "SECTOR_BUNDLE_V1",
              "version": 1,
              "createdAt": 1779556500000,
              "sender": {"callsign": "R2ABC", "deviceId": "sender-device-1"},
              "objects": [
                {
                  "objectId": "550e8400-e29b-41d4-a716-446655440000",
                  "objectType": "MAP_NOTE",
                  "ownerKind": "ME",
                  "sourceKind": "LOCAL",
                  "createdAt": 1779556500000,
                  "updatedAt": 1779556500000,
                  "payloadVersion": 1,
                  "payload": {
                    "latitude": 55.0,
                    "longitude": 37.0,
                    "title": "Заметка импорт",
                    "text": "Текст импорт",
                    $attachment
                    "createdAt": 1779556500000,
                    "updatedAt": 1779556500000
                  }
                }
              ]
            }
        """.trimIndent()
    }
}
