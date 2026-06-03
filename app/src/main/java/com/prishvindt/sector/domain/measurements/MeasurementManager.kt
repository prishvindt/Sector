package com.prishvindt.sector.domain.measurements

import com.prishvindt.sector.data.Measurement
import com.prishvindt.sector.data.MeasurementColor
import com.prishvindt.sector.data.MeasurementRepository
import com.prishvindt.sector.data.MeasurementSource
import com.prishvindt.sector.domain.ExportFormat
import com.prishvindt.sector.domain.GeoPoint
import java.time.Clock
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class MeasurementManager(
    private val repository: MeasurementRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val idFactory: () -> String = { UUID.randomUUID().toString() }
) {
    suspend fun latestSelf(): Measurement? = repository.latestSelf()

    suspend fun saveSelfMeasurement(input: SelfMeasurementInput): Result<SaveSelfMeasurementResult> {
        val measurement = createSelfMeasurement(input).getOrElse { return Result.failure(it) }
        repository.upsert(measurement)
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
        val imported = parsed.measurements.map {
            it.copy(source = MeasurementSource.IMPORTED, active = true)
        }
        imported.forEach { repository.upsert(it) }
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

        return Result.success(ExportFormat.format(latest.copy(callsign = callsign)))
    }

    fun exportMeasurements(
        measurements: List<Measurement>,
        callsign: String,
        ownColorArgb: Int,
        importedDefaultArgb: Int = MeasurementColor.DEFAULT_IMPORTED_ARGB
    ): Result<String> {
        if (measurements.isEmpty()) {
            return Result.failure(NoMeasurementsForExportException())
        }
        return Result.success(
            ExportFormat.formatMany(
                measurements.map {
                    it.prepareForExport(
                        callsign = callsign,
                        ownColorArgb = ownColorArgb,
                        importedDefaultArgb = importedDefaultArgb
                    )
                }
            )
        )
    }

    suspend fun delete(measurement: Measurement) {
        repository.delete(measurement)
    }

    suspend fun clear() {
        repository.clear()
    }

    private fun createSelfMeasurement(input: SelfMeasurementInput): Result<Measurement> {
        val azimuth = input.azimuthText.toDoubleOrNull()
        val errorText = input.errorText.trim()
        val error = if (errorText.isBlank()) 0.0 else errorText.toDoubleOrNull()
        val signal = input.signalText.takeIf { it.isNotBlank() }?.toIntOrNull()

        return when {
            azimuth == null || azimuth !in 0.0..359.999 ->
                Result.failure(MeasurementValidationException("Азимут должен быть от 0 до 359.999"))

            error == null || error < 0.0 ->
                Result.failure(MeasurementValidationException("Погрешность должна быть 0 или больше"))

            input.signalText.isNotBlank() && signal == null ->
                Result.failure(MeasurementValidationException("Мощность dBm должна быть числом"))

            else -> Result.success(
                Measurement(
                    measurementId = idFactory(),
                    callsign = input.callsign,
                    latitude = input.point.latitude,
                    longitude = input.point.longitude,
                    accuracyM = input.accuracyMeters?.toDouble(),
                    satelliteCount = input.satelliteCount,
                    azimuthDeg = azimuth,
                    azimuthErrorDeg = error,
                    signalDbm = signal,
                    rangeKm = input.rangeKm,
                    timestamp = OffsetDateTime.now(clock).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    source = MeasurementSource.SELF
                )
            )
        }
    }

    private fun Measurement.prepareForExport(
        callsign: String,
        ownColorArgb: Int,
        importedDefaultArgb: Int
    ): Measurement {
        val exportColor = MeasurementColor.resolve(
            measurement = this,
            ownColorArgb = ownColorArgb,
            importedDefaultArgb = importedDefaultArgb
        )
        return when (source) {
            MeasurementSource.SELF -> copy(callsign = callsign, colorArgb = exportColor)
            MeasurementSource.IMPORTED -> copy(colorArgb = exportColor)
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

class NoSelfMeasurementException : IllegalStateException("Нет моего замера для экспорта")

class NoMeasurementsForExportException : IllegalStateException("Нет азимутных лучей для экспорта")

class NoMeasurementImportedException : IllegalStateException("Не импортировано ни одного луча")
