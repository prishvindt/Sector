package com.prishvindt.sector.domain.measurements

import com.prishvindt.sector.data.FakeSectorObjectDao
import com.prishvindt.sector.data.Measurement
import com.prishvindt.sector.data.MeasurementSource
import com.prishvindt.sector.data.SectorObjectRepository
import com.prishvindt.sector.domain.ExportFormat
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

class MeasurementManagerTest {
    private val fixedClock = Clock.fixed(
        Instant.parse("2026-05-23T17:15:00Z"),
        ZoneOffset.ofHours(3)
    )

    @Test
    fun saveSelfMeasurementCreatesAzimuthRaySectorObject() = runTest {
        val dao = FakeSectorObjectDao()
        val manager = manager(
            dao = dao,
            ids = listOf("550e8400-e29b-41d4-a716-446655440000")
        )

        val result = manager.saveSelfMeasurement(
            input(
                accuracyMeters = 12f,
                accuracyWarningMeters = 10.0,
                signalText = "-61"
            )
        ).getOrThrow()

        val saved = dao.snapshot().single()
        val payload = SectorObjectPayloadJson.decodeAzimuthRay(saved.payloadJson).getOrThrow()
        assertTrue(result.showAccuracyWarning)
        assertEquals("550e8400-e29b-41d4-a716-446655440000", saved.objectId)
        assertEquals(SectorObjectType.AZIMUTH_RAY.wireName, saved.objectType)
        assertEquals(OwnerKind.ME.wireName, saved.ownerKind)
        assertEquals(SourceKind.LOCAL.wireName, saved.sourceKind)
        assertEquals("NIK", payload.callsign)
        assertEquals(59.437123, payload.latitude, 0.0)
        assertEquals(24.753456, payload.longitude, 0.0)
        assertEquals(283.0, payload.azimuth, 0.0)
        assertEquals(15.0, payload.error, 0.0)
        assertEquals(-61, payload.signal)
        assertEquals(MeasurementSource.SELF, result.measurement.source)
    }

    @Test
    fun saveSelfMeasurementRejectsInvalidSignal() = runTest {
        val dao = FakeSectorObjectDao()
        val manager = manager(dao)

        val result = manager.saveSelfMeasurement(input(signalText = "loud"))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message!!.contains("РњРѕС‰РЅРѕСЃС‚СЊ"))
        assertTrue(dao.snapshot().isEmpty())
    }

    @Test
    fun saveSelfMeasurementStoresEmptyErrorAsZero() = runTest {
        val dao = FakeSectorObjectDao()
        val manager = manager(
            dao = dao,
            ids = listOf("550e8400-e29b-41d4-a716-446655440000")
        )

        manager.saveSelfMeasurement(input(errorText = "")).getOrThrow()

        val payload = SectorObjectPayloadJson.decodeAzimuthRay(dao.snapshot().single().payloadJson).getOrThrow()
        assertEquals(0.0, payload.error, 0.0)
    }

    @Test
    fun importMeasurementStoresImportedAzimuthRayFromLegacyFormat() = runTest {
        val dao = FakeSectorObjectDao()
        val manager = manager(dao)

        val imported = manager.importMeasurement(ExportFormat.format(sample(source = MeasurementSource.SELF)))
            .getOrThrow()

        val saved = dao.snapshot().single()
        assertEquals(imported.measurementId, saved.objectId)
        assertEquals(SectorObjectType.AZIMUTH_RAY.wireName, saved.objectType)
        assertEquals(OwnerKind.CONTACT.wireName, saved.ownerKind)
        assertEquals(SourceKind.IMPORTED_MESSAGE.wireName, saved.sourceKind)
        assertEquals(MeasurementSource.IMPORTED, imported.source)
        assertTrue(imported.active)
    }

    @Test
    fun importMeasurementWithNonUuidLegacyIdGeneratesSectorObjectId() = runTest {
        val dao = FakeSectorObjectDao()
        val generatedObjectId = "550e8400-e29b-41d4-a716-446655440010"
        val manager = manager(
            dao = dao,
            ids = listOf(generatedObjectId)
        )
        val text = ExportFormat.format(
            sample(
                source = MeasurementSource.SELF,
                id = "legacy-measurement-id"
            )
        )

        val imported = manager.importMeasurement(text).getOrThrow()

        val saved = dao.snapshot().single()
        assertEquals(generatedObjectId, imported.measurementId)
        assertEquals(generatedObjectId, saved.objectId)
        assertEquals(SectorObjectType.AZIMUTH_RAY.wireName, saved.objectType)
    }

    @Test
    fun importMeasurementsStoresValidBlocksAndReportsSkippedBlocks() = runTest {
        val dao = FakeSectorObjectDao()
        val manager = manager(dao)
        val first = ExportFormat.format(
            sample(
                source = MeasurementSource.SELF,
                id = "550e8400-e29b-41d4-a716-446655440000"
            )
        )
        val broken = brokenBlock("550e8400-e29b-41d4-a716-446655440001")

        val result = manager.importMeasurements("$first\n\n$broken").getOrThrow()

        assertEquals(1, result.imported.size)
        assertEquals(1, result.skippedBlocks)
        assertEquals(1, dao.snapshot().size)
    }

    @Test
    fun exportMeasurementsUsesSectorBundleV1() = runTest {
        val dao = FakeSectorObjectDao()
        val manager = manager(
            dao = dao,
            ids = listOf("550e8400-e29b-41d4-a716-446655440000")
        )
        val saved = manager.saveSelfMeasurement(input()).getOrThrow().measurement

        val text = manager.exportMeasurements(
            measurements = listOf(saved),
            callsign = "NIK-LOCAL",
            ownColorArgb = 0xFF2F80ED.toInt()
        ).getOrThrow()
        val parsed = SectorBundleFormat.parse(text).getOrThrow()

        assertTrue(text.contains("\"format\":\"SECTOR_BUNDLE_V1\""))
        assertEquals(1, parsed.objects.size)
        assertEquals("NIK-LOCAL", parsed.sender.callsign)
    }

    private fun manager(
        dao: FakeSectorObjectDao,
        ids: List<String> = emptyList()
    ): MeasurementManager {
        val iterator = ids.iterator()
        val repository = SectorObjectRepository(
            dao = dao,
            clock = fixedClock,
            idFactory = {
                if (iterator.hasNext()) iterator.next() else "550e8400-e29b-41d4-a716-446655440099"
            }
        )
        return MeasurementManager(repository = repository, clock = fixedClock)
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
        id: String = "550e8400-e29b-41d4-a716-446655440000"
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
        source = source
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
}
