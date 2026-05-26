package com.prishvindt.sector.domain.measurements

import com.prishvindt.sector.data.Measurement
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
        val imported = ExportFormat.parse(text)
            .getOrElse { return Result.failure(it) }
            .copy(source = MeasurementSource.IMPORTED, active = true)

        repository.upsert(imported)
        return Result.success(imported)
    }

    suspend fun exportLatestSelf(callsign: String): Result<String> {
        val latest = latestSelf()
            ?: return Result.failure(NoSelfMeasurementException())

        return Result.success(ExportFormat.format(latest.copy(callsign = callsign)))
    }

    suspend fun delete(measurement: Measurement) {
        repository.delete(measurement)
    }

    suspend fun clear() {
        repository.clear()
    }

    private fun createSelfMeasurement(input: SelfMeasurementInput): Result<Measurement> {
        val azimuth = input.azimuthText.toDoubleOrNull()
        val error = input.errorText.toDoubleOrNull()
        val signal = input.signalText.takeIf { it.isNotBlank() }?.toIntOrNull()

        return when {
            azimuth == null || azimuth !in 0.0..359.999 ->
                Result.failure(MeasurementValidationException("Азимут должен быть от 0 до 359.999"))

            error == null || error <= 0.0 ->
                Result.failure(MeasurementValidationException("Погрешность должна быть больше 0"))

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

class MeasurementValidationException(message: String) : IllegalArgumentException(message)

class NoSelfMeasurementException : IllegalStateException("Нет моего замера для экспорта")
