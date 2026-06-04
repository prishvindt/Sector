package com.prishvindt.sector.domain.measurements

import com.prishvindt.sector.data.LocalAzimuthRayInput
import com.prishvindt.sector.data.Measurement
import com.prishvindt.sector.data.MeasurementColor
import com.prishvindt.sector.data.MeasurementSource
import com.prishvindt.sector.data.SectorObjectRepository
import com.prishvindt.sector.data.toMeasurementOrNull
import com.prishvindt.sector.domain.ExportFormat
import com.prishvindt.sector.domain.GeoPoint
import java.time.Clock

class MeasurementManager(
    private val repository: SectorObjectRepository,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    suspend fun latestSelf(): Measurement? = repository.latestSelfAzimuthRay()

    suspend fun saveSelfMeasurement(input: SelfMeasurementInput): Result<SaveSelfMeasurementResult> {
        val localInput = createSelfMeasurementInput(input).getOrElse { return Result.failure(it) }
        val measurement = repository.createLocalAzimuthRay(localInput).toMeasurementOrNull()
            ?: return Result.failure(MeasurementValidationException("РќРµ СѓРґР°Р»РѕСЃСЊ СЃРѕР·РґР°С‚СЊ Р»СѓС‡"))
        return Result.success(
            SaveSelfMeasurementResult(
                measurement = measurement,
                showAccuracyWarning = input.accuracyMeters?.let { it > input.accuracyWarningMeters } == true
            )
        )
    }

    suspend fun importMeasurement(text: String): Result<Measurement> {
        val result = importMeasurements(text)
            .getOrElse { return Result.failure(it) }

        val imported = result.imported.firstOrNull()
            ?: return Result.failure(NoMeasurementImportedException())
        return Result.success(imported)
    }

    suspend fun importMeasurements(text: String): Result<MeasurementImportResult> {
        val parsed = ExportFormat.parseMany(text)
            .getOrElse { return Result.failure(it) }
        val imported = mutableListOf<Measurement>()
        parsed.measurements.forEach {
            val entity = repository.importAzimuthRayFromLegacy(
                it.copy(source = MeasurementSource.IMPORTED, active = true)
            )
            entity.toMeasurementOrNull()?.let(imported::add)
        }
        return Result.success(
            MeasurementImportResult(
                imported = imported,
                skippedBlocks = parsed.skippedBlocks
            )
        )
    }

    suspend fun exportLatestSelf(callsign: String): Result<String> {
        val latest = latestSelf()
            ?: return Result.failure(NoSelfMeasurementException())

        return repository.exportObjectsByIds(listOf(latest.measurementId), callsign)
    }

    suspend fun exportMeasurements(
        measurements: List<Measurement>,
        callsign: String,
        ownColorArgb: Int,
        importedDefaultArgb: Int = MeasurementColor.DEFAULT_IMPORTED_ARGB
    ): Result<String> {
        if (measurements.isEmpty()) {
            return Result.failure(NoMeasurementsForExportException())
        }
        return repository.exportObjectsByIds(measurements.map { it.measurementId }, callsign)
    }

    suspend fun delete(measurement: Measurement) {
        repository.softDeleteObject(measurement.measurementId)
    }

    suspend fun clear() {
        repository.softDeleteAllActiveAzimuthRaysForClearAction()
    }

    private fun createSelfMeasurementInput(input: SelfMeasurementInput): Result<LocalAzimuthRayInput> {
        val azimuth = input.azimuthText.toDoubleOrNull()
        val errorText = input.errorText.trim()
        val error = if (errorText.isBlank()) 0.0 else errorText.toDoubleOrNull()
        val signal = input.signalText.takeIf { it.isNotBlank() }?.toIntOrNull()

        return when {
            azimuth == null || azimuth !in 0.0..359.999 ->
                Result.failure(MeasurementValidationException("РђР·РёРјСѓС‚ РґРѕР»Р¶РµРЅ Р±С‹С‚СЊ РѕС‚ 0 РґРѕ 359.999"))

            error == null || error < 0.0 ->
                Result.failure(MeasurementValidationException("РџРѕРіСЂРµС€РЅРѕСЃС‚СЊ РґРѕР»Р¶РЅР° Р±С‹С‚СЊ 0 РёР»Рё Р±РѕР»СЊС€Рµ"))

            input.signalText.isNotBlank() && signal == null ->
                Result.failure(MeasurementValidationException("РњРѕС‰РЅРѕСЃС‚СЊ dBm РґРѕР»Р¶РЅР° Р±С‹С‚СЊ С‡РёСЃР»РѕРј"))

            else -> Result.success(
                LocalAzimuthRayInput(
                    point = input.point,
                    callsign = input.callsign,
                    azimuth = azimuth,
                    error = error,
                    signal = signal
                )
            )
        }
    }
}

data class SelfMeasurementInput(
    val point: GeoPoint,
    val accuracyMeters: Float?,
    val satelliteCount: Int?,
    val callsign: String,
    val azimuthText: String,
    val errorText: String,
    val signalText: String,
    val accuracyWarningMeters: Double,
    val rangeKm: Double = 15.0
)

data class SaveSelfMeasurementResult(
    val measurement: Measurement,
    val showAccuracyWarning: Boolean
)

data class MeasurementImportResult(
    val imported: List<Measurement>,
    val skippedBlocks: Int
)

class MeasurementValidationException(message: String) : IllegalArgumentException(message)

class NoSelfMeasurementException : IllegalStateException("РќРµС‚ РјРѕРµРіРѕ Р·Р°РјРµСЂР° РґР»СЏ СЌРєСЃРїРѕСЂС‚Р°")

class NoMeasurementsForExportException : IllegalStateException("РќРµС‚ Р°Р·РёРјСѓС‚РЅС‹С… Р»СѓС‡РµР№ РґР»СЏ СЌРєСЃРїРѕСЂС‚Р°")

class NoMeasurementImportedException : IllegalStateException("РќРµ РёРјРїРѕСЂС‚РёСЂРѕРІР°РЅРѕ РЅРё РѕРґРЅРѕРіРѕ Р»СѓС‡Р°")
