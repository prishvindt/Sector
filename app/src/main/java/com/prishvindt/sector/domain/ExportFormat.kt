package com.prishvindt.sector.domain

import com.prishvindt.sector.data.Measurement
import com.prishvindt.sector.data.MeasurementSource
import java.util.UUID

object ExportFormat {
    const val MARKER = "SECTOR_MEASUREMENT_V1"

    private val requiredFields = listOf(
        "measurement_id",
        "callsign",
        "lat",
        "lon",
        "azimuth_deg",
        "azimuth_error_deg",
        "range_km",
        "timestamp"
    )

    fun format(measurement: Measurement): String {
        return buildString {
            appendLine(MARKER)
            appendLine("measurement_id=${measurement.measurementId}")
            appendLine("callsign=${measurement.callsign}")
            appendLine("lat=${measurement.latitude}")
            appendLine("lon=${measurement.longitude}")
            appendLine("accuracy_m=${measurement.accuracyM ?: ""}")
            appendLine("satellites=${measurement.satelliteCount ?: ""}")
            appendLine("azimuth_deg=${measurement.azimuthDeg}")
            appendLine("azimuth_error_deg=${measurement.azimuthErrorDeg}")
            appendLine("signal_dbm=${measurement.signalDbm ?: ""}")
            appendLine("range_km=${measurement.rangeKm}")
            appendLine("timestamp=${measurement.timestamp}")
        }.trimEnd()
    }

    fun parse(text: String): Result<Measurement> {
        val lines = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()

        if (lines.firstOrNull() != MARKER) {
            return Result.failure(ImportException("Ошибка импорта: не найден маркер $MARKER"))
        }

        val fields = lines.drop(1)
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) null else {
                    line.substring(0, separator).trim() to line.substring(separator + 1).trim()
                }
            }
            .toMap()

        for (field in requiredFields) {
            if (fields[field].isNullOrBlank()) {
                return Result.failure(ImportException("Ошибка импорта: не найдено поле $field"))
            }
        }

        return runCatching {
            Measurement(
                measurementId = fields.required("measurement_id").also { UUID.fromString(it) },
                callsign = fields.required("callsign"),
                latitude = fields.required("lat").toDouble(),
                longitude = fields.required("lon").toDouble(),
                accuracyM = fields.optionalDouble("accuracy_m"),
                satelliteCount = fields.optionalInt("satellites"),
                azimuthDeg = fields.required("azimuth_deg").toDouble(),
                azimuthErrorDeg = fields.required("azimuth_error_deg").toDouble(),
                signalDbm = fields.optionalInt("signal_dbm"),
                rangeKm = fields.required("range_km").toDouble(),
                timestamp = fields.required("timestamp"),
                source = MeasurementSource.IMPORTED,
                active = true,
                note = null
            ).also { measurement ->
                require(measurement.callsign.isNotBlank()) { "callsign пустой" }
                require(measurement.latitude in -90.0..90.0) { "lat вне диапазона" }
                require(measurement.longitude in -180.0..180.0) { "lon вне диапазона" }
                require(measurement.azimuthDeg in 0.0..359.999) { "azimuth_deg вне диапазона" }
                require(measurement.azimuthErrorDeg > 0.0) { "azimuth_error_deg должен быть больше 0" }
                require(measurement.rangeKm > 0.0) { "range_km должен быть больше 0" }
            }
        }.recoverCatching { cause ->
            throw ImportException("Ошибка импорта: ${cause.message ?: "неверный формат"}", cause)
        }
    }

    private fun Map<String, String>.required(field: String): String =
        this[field] ?: throw ImportException("Ошибка импорта: не найдено поле $field")

    private fun Map<String, String>.optionalDouble(field: String): Double? =
        this[field]?.takeIf { it.isNotBlank() }?.toDouble()

    private fun Map<String, String>.optionalInt(field: String): Int? =
        this[field]?.takeIf { it.isNotBlank() }?.toInt()
}

class ImportException(message: String, cause: Throwable? = null) : Exception(message, cause)
