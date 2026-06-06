package com.prishvindt.sector.domain.locations

import com.prishvindt.sector.data.ImportedLocation
import com.prishvindt.sector.data.LocalSharedLocationInput
import com.prishvindt.sector.data.SectorObjectRepository
import com.prishvindt.sector.data.toImportedLocationOrNull
import com.prishvindt.sector.domain.GeoPoint
import com.prishvindt.sector.domain.LocationExchangeFormat
import java.time.Clock

class LocationShareManager(
    private val repository: SectorObjectRepository,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    suspend fun formatCurrentLocation(input: CurrentLocationShareInput): Result<String> {
        val objectEntity = repository.createLocalSharedLocation(
            LocalSharedLocationInput(
                point = input.point,
                callsign = input.callsign.trim(),
                accuracyMeters = input.accuracyMeters?.toDouble(),
                bearing = null,
                timestampEpochSeconds = clock.instant().epochSecond
            )
        )
        return repository.exportObjects(
            objects = listOf(objectEntity),
            callsign = input.callsign.trim()
        )
    }

    suspend fun importLocation(text: String): Result<ImportedLocation> {
        val payload = LocationExchangeFormat.parse(text)
            .getOrElse { return Result.failure(it) }
        val location = repository.importSharedLocationFromLegacy(payload).toImportedLocationOrNull()
            ?: return Result.failure(IllegalStateException("РќРµ СѓРґР°Р»РѕСЃСЊ РёРјРїРѕСЂС‚РёСЂРѕРІР°С‚СЊ GPS"))
        return Result.success(location)
    }
}

data class CurrentLocationShareInput(
    val point: GeoPoint,
    val callsign: String,
    val accuracyMeters: Float?
)
