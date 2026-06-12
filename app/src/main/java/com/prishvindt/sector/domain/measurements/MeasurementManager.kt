package com.prishvindt.sector.domain.measurements

import com.prishvindt.sector.data.LocalAzimuthRayInput
import com.prishvindt.sector.data.Measurement
import com.prishvindt.sector.data.MeasurementColor
import com.prishvindt.sector.data.MeasurementSource
import com.prishvindt.sector.data.SectorObjectRepository
import com.prishvindt.sector.data.toMeasurementOrNull
import com.prishvindt.sector.domain.AzimuthDistance
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
            ?: return Result.failure(MeasurementValidationException("Не удалось создать луч"))
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
        val azimuth = AzimuthDistance.parseInput(input.azimuthText)
        val errorText = input.errorText.trim()
        val error = if (errorText.isBlank()) 0.0 else AzimuthDistance.parseInput(errorText)
        val distanceKm = AzimuthDistance.parseInput(input.distanceText)

        return when {
            azimuth == null || azimuth !in 0.0..359.999 ->
                Result.failure(MeasurementValidationException("Азимут должен быть от 0 до 359.999"))

            error == null || error < 0.0 ->
                Result.failure(MeasurementValidationException("Погрешность должна быть 0 или больше"))

            distanceKm == null || !AzimuthDistance.isValid(distanceKm) ->
                Result.failure(
                    MeasurementValidationException(
                        "Расстояние должно быть от ${AzimuthDistance.MIN_KM} до " +
                            "${AzimuthDistance.MAX_KM.toInt()} км"
                    )
                )

            else -> Result.success(
                LocalAzimuthRayInput(
                    point = input.point,
                    callsign = input.callsign,
                    azimuth = azimuth,
                    error = error,
                    distanceKm = distanceKm
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
    val distanceText: String,
    val accuracyWarningMeters: Double
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

class NoSelfMeasurementException : IllegalStateException("Нет моего замера для экспорта")

class NoMeasurementsForExportException : IllegalStateException("Нет азимутных лучей для экспорта")

class NoMeasurementImportedException : IllegalStateException("Не импортировано ни одного луча")
