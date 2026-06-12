package com.prishvindt.sector.domain.objects

import com.prishvindt.sector.data.SectorObjectEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SectorBundleFormatTest {
    @Test
    fun formatWritesStableSectorBundleV1Json() {
        val text = SectorBundleFormat.format(
            objects = listOf(sampleObject()),
            sender = SectorBundleSender(callsign = "R2ABC", deviceId = "device-1"),
            createdAt = 1_779_556_500_000L
        )

        assertTrue(text.contains("\"format\":\"SECTOR_BUNDLE_V1\""))
        assertTrue(text.contains("\"version\":1"))
        assertTrue(text.contains("\"objects\":["))
        assertTrue(text.contains("\"payload\":{\"latitude\":55.123456"))
        assertTrue(text.contains("\"encryptionState\":\"PLAIN_LOCAL\""))
    }

    @Test
    fun parseSkipsBrokenObjectsAndKeepsValidOnes() {
        val text = """
            {
              "format": "SECTOR_BUNDLE_V1",
              "version": 1,
              "createdAt": 1779556500000,
              "sender": {"callsign": "R2ABC", "deviceId": "device-1"},
              "objects": [
                {
                  "objectId": "550e8400-e29b-41d4-a716-446655440000",
                  "objectType": "AZIMUTH_RAY",
                  "ownerKind": "ME",
                  "payloadVersion": 1,
                  "payload": {
                    "latitude": 55.123456,
                    "longitude": 37.123456,
                    "azimuth": 123.0,
                    "error": 15.0,
                    "distanceKm": 15.0,
                    "callsign": "R2ABC"
                  }
                },
                {"objectType": "BROKEN", "payloadVersion": 1, "payload": {}}
              ]
            }
        """.trimIndent()

        val parsed = SectorBundleFormat.parse(text).getOrThrow()

        assertEquals(1, parsed.objects.size)
        assertEquals(1, parsed.skippedObjects)
        assertEquals("AZIMUTH_RAY", parsed.objects.single().objectType)
    }

    private fun sampleObject(): SectorObjectEntity =
        SectorObjectEntity(
            objectId = "550e8400-e29b-41d4-a716-446655440000",
            objectType = SectorObjectType.AZIMUTH_RAY.wireName,
            ownerKind = OwnerKind.ME.wireName,
            ownerId = null,
            deviceId = "device-1",
            sourceKind = SourceKind.LOCAL.wireName,
            createdAt = 1_779_556_500_000L,
            updatedAt = 1_779_556_500_000L,
            deletedAt = null,
            syncState = SyncState.LOCAL_ONLY.wireName,
            visibility = ObjectVisibility.SHAREABLE.wireName,
            encryptionState = EncryptionState.PLAIN_LOCAL.wireName,
            payloadVersion = 1,
            payloadJson = SectorObjectPayloadJson.encode(
                AzimuthRayPayloadV1(
                    latitude = 55.123456,
                    longitude = 37.123456,
                    azimuth = 123.0,
                    error = 15.0,
                    distanceKm = 15.0,
                    callsign = "R2ABC"
                )
            )
        )
}
