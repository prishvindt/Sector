package com.prishvindt.sector.domain

import com.prishvindt.sector.data.Measurement
import com.prishvindt.sector.data.MeasurementSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationExchangeFormatTest {
    @Test
    fun formatCurrentLocationPayloadUsesLocationMarkerAndNoBearingFields() {
        val text = LocationExchangeFormat.format(
            LocationSharePayload(
                callsign = "NIK",
                latitude = 55.123456,
                longitude = 37.123456,
                accuracyMeters = 8.0,
                timestampEpochSeconds = 1_710_000_000L
            )
        )

        assertTrue(text.startsWith("SECTOR_LOCATION_V1"))
        assertTrue(text.contains("callsign=NIK"))
        assertTrue(text.contains("latitude=55.123456"))
        assertTrue(text.contains("longitude=37.123456"))
        assertTrue(text.contains("accuracyMeters=8.0"))
        assertTrue(text.contains("timestamp=1710000000"))
        assertFalse(text.contains("azimuth"))
        assertFalse(text.contains("signal_dbm"))
    }

    @Test
    fun parseLocationOnlyDoesNotRequireMeasurementFields() {
        val parsed = LocationExchangeFormat.parse(
            """
            SECTOR_LOCATION_V1
            callsign=NIK
            latitude=55.123456
            longitude=37.123456
            timestamp=1710000000
            """.trimIndent()
        ).getOrThrow()

        assertEquals("NIK", parsed.callsign)
        assertEquals(55.123456, parsed.latitude, 0.0)
        assertEquals(37.123456, parsed.longitude, 0.0)
        assertEquals(null, parsed.accuracyMeters)
        assertEquals(1_710_000_000L, parsed.timestampEpochSeconds)
    }

    @Test
    fun parseBrokenLocationReportsError() {
        val result = LocationExchangeFormat.parse(
            """
            SECTOR_LOCATION_V1
            callsign=NIK
            longitude=37.123456
            timestamp=1710000000
            """.trimIndent()
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message!!.contains("latitude"))
    }

    @Test
    fun oldMeasurementFormatStillParses() {
        val measurement = Measurement(
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

        val parsed = ExportFormat.parse(ExportFormat.format(measurement)).getOrThrow()

        assertEquals(measurement.measurementId, parsed.measurementId)
        assertEquals(MeasurementSource.IMPORTED, parsed.source)
    }
}
