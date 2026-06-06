package com.prishvindt.sector.domain.locations

import com.prishvindt.sector.data.FakeSectorObjectDao
import com.prishvindt.sector.data.SectorObjectRepository
import com.prishvindt.sector.domain.GeoPoint
import com.prishvindt.sector.domain.objects.OwnerKind
import com.prishvindt.sector.domain.objects.SectorBundleFormat
import com.prishvindt.sector.domain.objects.SectorObjectPayloadJson
import com.prishvindt.sector.domain.objects.SectorObjectType
import com.prishvindt.sector.domain.objects.SourceKind
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationShareManagerTest {
    private val fixedClock = Clock.fixed(
        Instant.parse("2026-05-23T17:15:00Z"),
        ZoneOffset.UTC
    )

    @Test
    fun formatCurrentLocationUsesSectorBundleV1AndStoresSharedLocation() = runTest {
        val dao = FakeSectorObjectDao()
        val manager = manager(
            dao = dao,
            ids = listOf("550e8400-e29b-41d4-a716-446655440000")
        )

        val text = manager.formatCurrentLocation(
            CurrentLocationShareInput(
                point = GeoPoint(55.123456, 37.123456),
                callsign = "NIK",
                accuracyMeters = 8f
            )
        ).getOrThrow()

        val saved = dao.snapshot().single()
        val payload = SectorObjectPayloadJson.decodeSharedLocation(saved.payloadJson).getOrThrow()
        val bundle = SectorBundleFormat.parse(text).getOrThrow()
        assertTrue(text.contains("\"format\":\"SECTOR_BUNDLE_V1\""))
        assertEquals(SectorObjectType.SHARED_LOCATION.wireName, saved.objectType)
        assertEquals(OwnerKind.ME.wireName, saved.ownerKind)
        assertEquals(SourceKind.LOCAL.wireName, saved.sourceKind)
        assertEquals("NIK", payload.callsign)
        assertEquals(8.0, payload.accuracyMeters ?: 0.0, 0.0)
        assertEquals(1, bundle.objects.size)
    }

    @Test
    fun importLocationWithSameCallsignSoftDeletesPreviousLocation() = runTest {
        val dao = FakeSectorObjectDao()
        val manager = manager(
            dao = dao,
            ids = listOf(
                "550e8400-e29b-41d4-a716-446655440000",
                "550e8400-e29b-41d4-a716-446655440001"
            )
        )

        manager.importLocation(locationText(callsign = "NIK", latitude = 55.0, longitude = 37.0)).getOrThrow()
        manager.importLocation(locationText(callsign = "nik", latitude = 56.0, longitude = 38.0)).getOrThrow()

        val all = dao.snapshot()
        val active = all.filter { it.deletedAt == null }
        val saved = active.single()
        val payload = SectorObjectPayloadJson.decodeSharedLocation(saved.payloadJson).getOrThrow()
        assertEquals(2, all.size)
        assertEquals(1, all.count { it.deletedAt != null })
        assertEquals("nik", payload.callsign)
        assertEquals(56.0, payload.latitude, 0.0)
        assertEquals(38.0, payload.longitude, 0.0)
    }

    @Test
    fun importLocationWithoutCallsignDoesNotOverwriteOtherAnonymousLocations() = runTest {
        val dao = FakeSectorObjectDao()
        val manager = manager(
            dao = dao,
            ids = listOf(
                "550e8400-e29b-41d4-a716-446655440000",
                "550e8400-e29b-41d4-a716-446655440001"
            )
        )

        manager.importLocation(locationText(callsign = "", latitude = 55.0, longitude = 37.0, timestamp = 1L)).getOrThrow()
        manager.importLocation(locationText(callsign = "", latitude = 56.0, longitude = 38.0, timestamp = 2L)).getOrThrow()

        assertEquals(2, dao.snapshot().count { it.deletedAt == null })
    }

    private fun manager(
        dao: FakeSectorObjectDao,
        ids: List<String>
    ): LocationShareManager {
        val iterator = ids.iterator()
        return LocationShareManager(
            repository = SectorObjectRepository(
                dao = dao,
                clock = fixedClock,
                idFactory = { iterator.next() }
            ),
            clock = fixedClock
        )
    }

    private fun locationText(
        callsign: String,
        latitude: Double,
        longitude: Double,
        timestamp: Long = 1_710_000_000L
    ): String =
        """
        SECTOR_LOCATION_V1
        callsign=$callsign
        latitude=$latitude
        longitude=$longitude
        accuracyMeters=8.0
        timestamp=$timestamp
        """.trimIndent()
}
