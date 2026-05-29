package com.prishvindt.sector.domain.locations

import com.prishvindt.sector.data.ImportedLocation
import com.prishvindt.sector.data.ImportedLocationRepository
import com.prishvindt.sector.domain.GeoPoint
import com.prishvindt.sector.domain.LocationExchangeFormat
import com.prishvindt.sector.domain.LocationSharePayload
import java.time.Clock
import java.util.Locale

class LocationShareManager(
    private val repository: ImportedLocationRepository,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    fun formatCurrentLocation(input: CurrentLocationShareInput): Result<String> {
        return runCatching {
            LocationExchangeFormat.format(
                LocationSharePayload(
                    callsign = input.callsign.trim(),
                    latitude = input.point.latitude,
                    longitude = input.point.longitude,
                    accuracyMeters = input.accuracyMeters?.toDouble(),
                    timestampEpochSeconds = clock.instant().epochSecond
                )
            )
        }
    }

    suspend fun importLocation(text: String): Result<ImportedLocation> {
        val payload = LocationExchangeFormat.parse(text)
            .getOrElse { return Result.failure(it) }
        val location = payload.toImportedLocation(clock.millis())
        repository.upsert(location)
        return Result.success(location)
    }

    private fun LocationSharePayload.toImportedLocation(receivedAtEpochMillis: Long): ImportedLocation =
        ImportedLocation(
            locationKey = locationKey(
                callsign = callsign,
                latitude = latitude,
                longitude = longitude,
                timestampEpochSeconds = timestampEpochSeconds
            ),
            callsign = callsign.trim(),
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters,
            timestampEpochSeconds = timestampEpochSeconds,
            receivedAtEpochMillis = receivedAtEpochMillis
        )

    private fun locationKey(
        callsign: String,
        latitude: Double,
        longitude: Double,
        timestampEpochSeconds: Long
    ): String {
        val normalizedCallsign = callsign.trim().lowercase(Locale.ROOT)
        return if (normalizedCallsign.isNotBlank()) {
            "callsign:$normalizedCallsign"
        } else {
            val lat = String.format(Locale.US, "%.6f", latitude)
            val lon = String.format(Locale.US, "%.6f", longitude)
            "anonymous:$timestampEpochSeconds:$lat:$lon"
        }
    }
}

data class CurrentLocationShareInput(
    val point: GeoPoint,
    val callsign: String,
    val accuracyMeters: Float?
)
