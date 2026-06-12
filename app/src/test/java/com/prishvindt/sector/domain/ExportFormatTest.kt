package com.prishvindt.sector.domain

import com.prishvindt.sector.data.Measurement
import com.prishvindt.sector.data.MeasurementSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportFormatTest {
    @Test
    fun exportImportRoundTripKeepsFields() {
        val source = Measurement(
            measurementId = "550e8400-e29b-41d4-a716-446655440000",
            callsign = "NIK",
            latitude = 59.437123,
            longitude = 24.753456,
            accuracyM = 8.0,
            satelliteCount = 12,
            azimuthDeg = 283.0,
            azimuthErrorDeg = 15.0,
            distanceKm = 10.5,
            timestamp = "2026-05-23T20:15:00+03:00",
            source = MeasurementSource.SELF
        )

        val text = ExportFormat.format(source)
        val parsed = ExportFormat.parse(text).getOrThrow()

        assertTrue(text.contains("distance_km=10.5"))
        assertFalse(text.contains("signal_dbm"))
        assertEquals(source.measurementId, parsed.measurementId)
        assertEquals(source.callsign, parsed.callsign)
        assertEquals(source.latitude, parsed.latitude, 0.0)
        assertEquals(source.azimuthDeg, parsed.azimuthDeg, 0.0)
        assertEquals(10.5, parsed.distanceKm, 0.0)
        assertNull(parsed.colorArgb)
        assertEquals(MeasurementSource.IMPORTED, parsed.source)
    }

    @Test
    fun formatManyUsesRepeatedMeasurementBlocks() {
        val first = sample("550e8400-e29b-41d4-a716-446655440000")
        val second = sample("550e8400-e29b-41d4-a716-446655440001", colorArgb = 0xFF27AE60.toInt())

        val text = ExportFormat.formatMany(listOf(first, second))

        assertEquals(2, Regex("^SECTOR_MEASUREMENT_V1", RegexOption.MULTILINE).findAll(text).count())
        assertTrue(text.contains("\n\nSECTOR_MEASUREMENT_V1"))
        assertTrue(text.contains("colorArgb=${0xFF27AE60.toInt()}"))
    }

    @Test
    fun parseManyImportsValidBlocksAndSkipsBrokenBlock() {
        val first = ExportFormat.format(sample("550e8400-e29b-41d4-a716-446655440000"))
        val broken = brokenBlock("550e8400-e29b-41d4-a716-446655440001")

        val parsed = ExportFormat.parseMany("$first\n\n$broken").getOrThrow()

        assertEquals(1, parsed.measurements.size)
        assertEquals(1, parsed.skippedBlocks)
    }

    @Test
    fun parseManyReturnsErrorWhenAllBlocksAreBroken() {
        val first = brokenBlock("550e8400-e29b-41d4-a716-446655440000")
        val second = brokenBlock("550e8400-e29b-41d4-a716-446655440001")

        val result = ExportFormat.parseMany("$first\n\n$second")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message!!.contains("не удалось импортировать ни один луч"))
    }

    @Test
    fun parseManyReturnsErrorForSingleBrokenBlock() {
        val result = ExportFormat.parseMany(brokenBlock("550e8400-e29b-41d4-a716-446655440000"))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message!!.contains("не удалось импортировать ни один луч"))
    }

    @Test
    fun parseOptionalColorField() {
        val parsed = ExportFormat.parse(
            """
            SECTOR_MEASUREMENT_V1
            measurement_id=550e8400-e29b-41d4-a716-446655440000
            callsign=NIK
            lat=59.4
            lon=24.7
            azimuth_deg=123
            azimuth_error_deg=5
            distance_km=15
            timestamp=2026-05-23T20:15:00+03:00
            colorArgb=-123456
            """.trimIndent()
        ).getOrThrow()

        assertEquals(-123456, parsed.colorArgb)
    }

    @Test
    fun invalidColorFieldDoesNotBreakImport() {
        val parsed = ExportFormat.parse(
            """
            SECTOR_MEASUREMENT_V1
            measurement_id=550e8400-e29b-41d4-a716-446655440000
            callsign=NIK
            lat=59.4
            lon=24.7
            azimuth_deg=123
            azimuth_error_deg=5
            distance_km=15
            timestamp=2026-05-23T20:15:00+03:00
            colorArgb=not-a-color
            """.trimIndent()
        ).getOrThrow()

        assertNull(parsed.colorArgb)
    }

    @Test
    fun blankCallsignAndZeroErrorStayImportable() {
        val parsed = ExportFormat.parse(
            """
            SECTOR_MEASUREMENT_V1
            measurement_id=550e8400-e29b-41d4-a716-446655440000
            callsign=
            lat=59.4
            lon=24.7
            azimuth_deg=123
            azimuth_error_deg=0
            distance_km=15
            timestamp=2026-05-23T20:15:00+03:00
            """.trimIndent()
        ).getOrThrow()

        assertEquals("", parsed.callsign)
        assertEquals(0.0, parsed.azimuthErrorDeg, 0.0)
    }

    @Test
    fun oldLegacyRangeFieldStillParsesAsDistance() {
        val parsed = ExportFormat.parse(
            """
            SECTOR_MEASUREMENT_V1
            measurement_id=550e8400-e29b-41d4-a716-446655440000
            callsign=NIK
            lat=59.4
            lon=24.7
            azimuth_deg=123
            azimuth_error_deg=5
            range_km=12
            signal_dbm=-80
            timestamp=2026-05-23T20:15:00+03:00
            """.trimIndent()
        ).getOrThrow()

        assertEquals(12.0, parsed.distanceKm, 0.0)
    }

    @Test
    fun legacyBlockWithoutDistanceUsesDefaultDistance() {
        val parsed = ExportFormat.parse(
            """
            SECTOR_MEASUREMENT_V1
            measurement_id=550e8400-e29b-41d4-a716-446655440000
            callsign=NIK
            lat=59.4
            lon=24.7
            azimuth_deg=123
            azimuth_error_deg=5
            signal_dbm=-80
            timestamp=2026-05-23T20:15:00+03:00
            """.trimIndent()
        ).getOrThrow()

        assertEquals(AzimuthDistance.DEFAULT_KM, parsed.distanceKm, 0.0)
    }

    @Test
    fun parseReportsMissingField() {
        val result = ExportFormat.parse(
            """
            SECTOR_MEASUREMENT_V1
            measurement_id=550e8400-e29b-41d4-a716-446655440000
            callsign=NIK
            lat=59.4
            lon=24.7
            azimuth_error_deg=15
            distance_km=15
            timestamp=2026-05-23T20:15:00+03:00
            """.trimIndent()
        )

        assertTrue(result.exceptionOrNull()?.message!!.contains("azimuth_deg"))
    }

    private fun sample(
        id: String,
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
        distanceKm = 15.0,
        timestamp = "2026-05-23T20:15:00+03:00",
        source = MeasurementSource.SELF,
        colorArgb = colorArgb
    )

    private fun brokenBlock(id: String) = """
        SECTOR_MEASUREMENT_V1
        measurement_id=$id
        callsign=BAD
        lat=59.4
        lon=24.7
        azimuth_error_deg=15
        distance_km=15
        timestamp=2026-05-23T20:15:00+03:00
    """.trimIndent()
}
