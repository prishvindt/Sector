package com.prishvindt.sector.domain

import com.prishvindt.sector.data.Measurement
import com.prishvindt.sector.data.MeasurementSource
import org.junit.Assert.assertEquals
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
            signalDbm = -61,
            rangeKm = 15.0,
            timestamp = "2026-05-23T20:15:00+03:00",
            source = MeasurementSource.SELF
        )

        val parsed = ExportFormat.parse(ExportFormat.format(source)).getOrThrow()

        assertEquals(source.measurementId, parsed.measurementId)
        assertEquals(source.callsign, parsed.callsign)
        assertEquals(source.latitude, parsed.latitude, 0.0)
        assertEquals(source.azimuthDeg, parsed.azimuthDeg, 0.0)
        assertEquals(MeasurementSource.IMPORTED, parsed.source)
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
            range_km=15
            timestamp=2026-05-23T20:15:00+03:00
            """.trimIndent()
        )

        assertTrue(result.exceptionOrNull()?.message!!.contains("azimuth_deg"))
    }
}
