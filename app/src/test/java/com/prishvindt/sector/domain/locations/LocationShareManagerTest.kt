package com.prishvindt.sector.domain.locations

import com.prishvindt.sector.data.ImportedLocation
import com.prishvindt.sector.data.ImportedLocationDao
import com.prishvindt.sector.data.ImportedLocationRepository
import com.prishvindt.sector.domain.GeoPoint
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
    fun formatCurrentLocationUsesEpochSecondsAndAllowsEmptyCallsign() {
        val manager = LocationShareManager(
            repository = ImportedLocationRepository(FakeImportedLocationDao()),
            clock = fixedClock
        )

        val text = manager.formatCurrentLocation(
            CurrentLocationShareInput(
                point = GeoPoint(55.123456, 37.123456),
                callsign = "",
                accuracyMeters = 8f
            )
        ).getOrThrow()

        assertTrue(text.contains("callsign="))
        assertTrue(text.contains("accuracyMeters=8.0"))
        assertTrue(text.contains("timestamp=1779556500"))
    }

    @Test
    fun importLocationWithSameCallsignReplacesPreviousLocation() = runTest {
        val dao = FakeImportedLocationDao()
        val manager = LocationShareManager(
            repository = ImportedLocationRepository(dao),
            clock = fixedClock
        )

        manager.importLocation(locationText(callsign = "NIK", latitude = 55.0, longitude = 37.0)).getOrThrow()
        manager.importLocation(locationText(callsign = "nik", latitude = 56.0, longitude = 38.0)).getOrThrow()

        val saved = dao.snapshot().single()
        assertEquals("nik", saved.callsign)
        assertEquals(56.0, saved.latitude, 0.0)
        assertEquals(38.0, saved.longitude, 0.0)
    }

    @Test
    fun importLocationWithoutCallsignDoesNotOverwriteOtherAnonymousLocations() = runTest {
        val dao = FakeImportedLocationDao()
        val manager = LocationShareManager(
            repository = ImportedLocationRepository(dao),
            clock = fixedClock
        )

        manager.importLocation(locationText(callsign = "", latitude = 55.0, longitude = 37.0, timestamp = 1L)).getOrThrow()
        manager.importLocation(locationText(callsign = "", latitude = 56.0, longitude = 38.0, timestamp = 2L)).getOrThrow()

        assertEquals(2, dao.snapshot().size)
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

    private class FakeImportedLocationDao : ImportedLocationDao {
        private val items = mutableListOf<ImportedLocation>()

        fun snapshot(): List<ImportedLocation> = items.toList()

        override fun observeAll(): Flow<List<ImportedLocation>> =
            flowOf(items.sortedByDescending { it.receivedAtEpochMillis })

        override suspend fun upsert(location: ImportedLocation) {
            val index = items.indexOfFirst { it.locationKey == location.locationKey }
            if (index < 0) {
                items += location
            } else {
                items[index] = location
            }
        }

        override suspend fun clear() {
            items.clear()
        }
    }
}
