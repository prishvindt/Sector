package com.prishvindt.sector.domain.measurements

import com.prishvindt.sector.data.Measurement
import com.prishvindt.sector.data.MeasurementDao
import com.prishvindt.sector.data.MeasurementRepository
import com.prishvindt.sector.data.MeasurementSource
import com.prishvindt.sector.domain.ExportFormat
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

class MeasurementManagerTest {
    private val fixedClock = Clock.fixed(
        Instant.parse("2026-05-23T17:15:00Z"),
        ZoneOffset.ofHours(3)
    )

    @Test
    fun saveSelfMeasurementCreatesMeasurementFromInput() = runTest {
        val dao = FakeMeasurementDao()
        val manager = MeasurementManager(
            repository = MeasurementRepository(dao),
            clock = fixedClock,
            idFactory = { "550e8400-e29b-41d4-a716-446655440000" }
        )

        val result = manager.saveSelfMeasurement(
            input(
                accuracyMeters = 12f,
                accuracyWarningMeters = 10.0,
                signalText = "-61"
            )
        ).getOrThrow()

        val saved = dao.snapshot().single()
        assertTrue(result.showAccuracyWarning)
        assertEquals("550e8400-e29b-41d4-a716-446655440000", saved.measurementId)
        assertEquals("NIK", saved.callsign)
        assertEquals(59.437123, saved.latitude, 0.0)
        assertEquals(24.753456, saved.longitude, 0.0)
        assertEquals(12.0, saved.accuracyM ?: 0.0, 0.0)
        assertEquals(11, saved.satelliteCount)
        assertEquals(283.0, saved.azimuthDeg, 0.0)
        assertEquals(15.0, saved.azimuthErrorDeg, 0.0)
        assertEquals(-61, saved.signalDbm)
        assertEquals(MeasurementSource.SELF, saved.source)
        assertEquals("2026-05-23T20:15:00+03:00", saved.timestamp)
    }

    @Test
    fun saveSelfMeasurementRejectsInvalidSignal() = runTest {
        val dao = FakeMeasurementDao()
        val manager = MeasurementManager(
            repository = MeasurementRepository(dao),
            clock = fixedClock,
            idFactory = { "550e8400-e29b-41d4-a716-446655440000" }
        )

        val result = manager.saveSelfMeasurement(input(signalText = "loud"))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message!!.contains("Мощность"))
        assertTrue(dao.snapshot().isEmpty())
    }

    @Test
    fun saveSelfMeasurementStoresEmptyErrorAsZero() = runTest {
        val dao = FakeMeasurementDao()
        val manager = MeasurementManager(
            repository = MeasurementRepository(dao),
            clock = fixedClock,
            idFactory = { "550e8400-e29b-41d4-a716-446655440000" }
        )

        manager.saveSelfMeasurement(input(errorText = "")).getOrThrow()

        assertEquals(0.0, dao.snapshot().single().azimuthErrorDeg, 0.0)
    }

    @Test
    fun importMeasurementStoresImportedActiveMeasurement() = runTest {
        val dao = FakeMeasurementDao()
        val manager = MeasurementManager(
            repository = MeasurementRepository(dao),
            clock = fixedClock,
            idFactory = { "unused" }
        )

        val imported = manager.importMeasurement(ExportFormat.format(sample(source = MeasurementSource.SELF)))
            .getOrThrow()

        val saved = dao.snapshot().single()
        assertEquals(imported, saved)
        assertEquals(MeasurementSource.IMPORTED, saved.source)
        assertTrue(saved.active)
    }

    @Test
    fun importMeasurementsStoresValidBlocksAndReportsSkippedBlocks() = runTest {
        val dao = FakeMeasurementDao()
        val manager = MeasurementManager(
            repository = MeasurementRepository(dao),
            clock = fixedClock,
            idFactory = { "unused" }
        )
        val first = ExportFormat.format(
            sample(
                source = MeasurementSource.SELF,
                id = "550e8400-e29b-41d4-a716-446655440000",
                colorArgb = 0xFF2F80ED.toInt()
            )
        )
        val broken = brokenBlock("550e8400-e29b-41d4-a716-446655440001")

        val result = manager.importMeasurements("$first\n\n$broken").getOrThrow()

        assertEquals(1, result.imported.size)
        assertEquals(1, result.skippedBlocks)
        assertEquals(1, dao.snapshot().size)
        assertEquals(0xFF2F80ED.toInt(), dao.snapshot().first().colorArgb)
        assertTrue(dao.snapshot().all { it.source == MeasurementSource.IMPORTED && it.active })
    }

    @Test
    fun importMeasurementsReturnsFailureWhenAllBlocksAreBroken() = runTest {
        val dao = FakeMeasurementDao()
        val manager = MeasurementManager(
            repository = MeasurementRepository(dao),
            clock = fixedClock,
            idFactory = { "unused" }
        )
        val first = brokenBlock("550e8400-e29b-41d4-a716-446655440000")
        val second = brokenBlock("550e8400-e29b-41d4-a716-446655440001")

        val result = manager.importMeasurements("$first\n\n$second")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message!!.contains("не удалось импортировать ни один луч"))
        assertTrue(dao.snapshot().isEmpty())
    }

    @Test
    fun exportMeasurementsFormatsSelectedBlocksWithResolvedColors() {
        val manager = MeasurementManager(
            repository = MeasurementRepository(FakeMeasurementDao()),
            clock = fixedClock,
            idFactory = { "unused" }
        )
        val self = sample(
            source = MeasurementSource.SELF,
            id = "550e8400-e29b-41d4-a716-446655440000"
        )
        val imported = sample(
            source = MeasurementSource.IMPORTED,
            id = "550e8400-e29b-41d4-a716-446655440001",
            colorArgb = 0xFFFF5A3D.toInt()
        ).copy(callsign = "FOX")

        val text = manager.exportMeasurements(
            measurements = listOf(self, imported),
            callsign = "NIK-LOCAL",
            ownColorArgb = 0xFF2F80ED.toInt()
        ).getOrThrow()
        val parsed = ExportFormat.parseMany(text).getOrThrow().measurements

        assertTrue(text.contains("\n\nSECTOR_MEASUREMENT_V1"))
        assertEquals("NIK-LOCAL", parsed[0].callsign)
        assertEquals(0xFF2F80ED.toInt(), parsed[0].colorArgb)
        assertEquals("FOX", parsed[1].callsign)
        assertEquals(0xFFFF5A3D.toInt(), parsed[1].colorArgb)
    }

    private fun input(
        accuracyMeters: Float? = 8f,
        accuracyWarningMeters: Double = 50.0,
        azimuthText: String = "283.0",
        errorText: String = "15.0",
        signalText: String = ""
    ) = SelfMeasurementInput(
        point = GeoPoint(59.437123, 24.753456),
        accuracyMeters = accuracyMeters,
        satelliteCount = 11,
        callsign = "NIK",
        azimuthText = azimuthText,
        errorText = errorText,
        signalText = signalText,
        accuracyWarningMeters = accuracyWarningMeters
    )

    private fun sample(
        source: MeasurementSource,
        id: String = "550e8400-e29b-41d4-a716-446655440000",
        colorArgb: Int? = null
    ) = Measurement(
        measurementId = id,
        callsign = "NIK",
        latitude = 59.437123,
        longitude = 24.753456,
        accuracyM = 8.0,
        satelliteCount = 12,
        azimuthDeg = 283.0,
        azimuthErrorDeg = 15.0,
        signalDbm = -61,
        rangeKm = 15.0,
        timestamp = "2026-05-23T20:15:00+03:00",
        source = source,
        colorArgb = colorArgb
    )

    private fun brokenBlock(id: String) = """
        SECTOR_MEASUREMENT_V1
        measurement_id=$id
        callsign=BAD
        lat=59.4
        lon=24.7
        azimuth_error_deg=15
        range_km=15
        timestamp=2026-05-23T20:15:00+03:00
    """.trimIndent()

    private class FakeMeasurementDao(initial: List<Measurement> = emptyList()) : MeasurementDao {
        private val items = initial.toMutableList()

        fun snapshot(): List<Measurement> = items.toList()

        override fun observeAll(): Flow<List<Measurement>> =
            flowOf(items.sortedByDescending { it.timestamp })

        override fun observeLatestActiveBySource(source: MeasurementSource): Flow<Measurement?> =
            flowOf(latestActiveBySourceNow(source))

        override suspend fun latestActiveBySource(source: MeasurementSource): Measurement? =
            latestActiveBySourceNow(source)

        override fun observeActive(): Flow<List<Measurement>> =
            flowOf(items.filter { it.active }.sortedByDescending { it.timestamp })

        override suspend fun upsert(measurement: Measurement) {
            val index = items.indexOfFirst { it.measurementId == measurement.measurementId }
            if (index < 0) {
                items += measurement
            } else {
                items[index] = measurement
            }
        }

        override suspend fun delete(measurement: Measurement) {
            items.removeIf { it.measurementId == measurement.measurementId }
        }

        override suspend fun clear() {
            items.clear()
        }

        override suspend fun countById(measurementId: String): Int =
            items.count { it.measurementId == measurementId }

        private fun latestActiveBySourceNow(source: MeasurementSource): Measurement? =
            items
                .filter { it.source == source && it.active }
                .maxByOrNull { it.timestamp }
    }
}
