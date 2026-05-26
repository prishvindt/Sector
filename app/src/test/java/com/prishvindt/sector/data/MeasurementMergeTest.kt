package com.prishvindt.sector.data

import com.prishvindt.sector.domain.MeasurementMerge
import org.junit.Assert.assertEquals
import org.junit.Test

class MeasurementMergeTest {
    @Test
    fun upsertReplacesMeasurementWithSameId() {
        val first = sample("id", 10.0)
        val second = sample("id", 25.0)

        val result = MeasurementMerge.upsert(listOf(first), second)

        assertEquals(1, result.size)
        assertEquals(25.0, result.single().azimuthDeg, 0.0)
    }

    private fun sample(id: String, azimuth: Double) = Measurement(
        measurementId = id,
        callsign = "NIK",
        latitude = 0.0,
        longitude = 0.0,
        accuracyM = null,
        satelliteCount = null,
        azimuthDeg = azimuth,
        azimuthErrorDeg = 15.0,
        signalDbm = null,
        rangeKm = 15.0,
        timestamp = "2026-05-23T20:15:00+03:00",
        source = MeasurementSource.IMPORTED
    )
}
