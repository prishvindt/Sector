package com.prishvindt.sector.domain

import com.prishvindt.sector.data.Measurement
import com.prishvindt.sector.data.MeasurementSource

object ExportFormat {
    const val MARKER = "SECTOR_MEASUREMENT_V1"
    private const val COLOR_FIELD = "colorArgb"

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

    fun hasMeasurementText(text: String): Boolean =
        text.lineSequence().any { it.trim() == MARKER }

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
            measurement.colorArgb?.let { appendLine("$COLOR_FIELD=$it") }
        }.trimEnd()
    }

    fun formatMany(measurements: List<Measurement>): String =
        measurements.joinToString(separator = "\n\n") { format(it) }

    fun parse(text: String): Result<Measurement> {
        val block = measurementBlocks(text).firstOrNull()
            ?: return Result.failure(ImportException("Ошибка импорта: не найден маркер $MARKER"))

        return parseBlock(block)
    }

    fun parseMany(text: String): Result<ParsedMeasurements> {
        val blocks = measurementBlocks(text)
        if (blocks.isEmpty()) {
            return Result.failure(ImportException("Ошибка импорта: не найден маркер $MARKER"))
        }

        val measurements = mutableListOf<Measurement>()
        var skippedBlocks = 0
        var firstFailure: Throwable? = null
        blocks.forEach { block ->
            parseBlock(block)
                .onSuccess { measurements += it }
                .onFailure { cause ->
                    skippedBlocks += 1
                    if (firstFailure == null) {
                        firstFailure = cause
                    }
                }
        }

        if (measurements.isEmpty()) {
            return Result.failure(
                ImportException("Ошибка импорта: не удалось импортировать ни один луч", firstFailure)
            )
        }

        return Result.success(
            ParsedMeasurements(
                measurements = measurements,
                skippedBlocks = skippedBlocks
            )
        )
    }

    private fun parseBlock(text: String): Result<Measurement> {
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
            if (!fields.containsKey(field) || (field != "callsign" && fields[field].isNullOrBlank())) {
                return Result.failure(ImportException("Ошибка импорта: не найдено поле $field"))
            }
        }

        return runCatching {
            Measurement(
                measurementId = fields.required("measurement_id"),
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
                note = null,
                colorArgb = fields.optionalColorArgb(COLOR_FIELD)
            ).also { measurement ->
                require(measurement.latitude in -90.0..90.0) { "lat вне диапазона" }
                require(measurement.longitude in -180.0..180.0) { "lon вне диапазона" }
                require(measurement.azimuthDeg in 0.0..359.999) { "azimuth_deg вне диапазона" }
                require(measurement.azimuthErrorDeg >= 0.0) { "azimuth_error_deg должен быть 0 или больше" }
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

    private fun Map<String, String>.optionalColorArgb(field: String): Int? =
        this[field]?.trim()?.takeIf { it.isNotBlank() }?.let(::parseColorArgb)

    private fun parseColorArgb(raw: String): Int? {
        raw.toIntOrNull()?.let { return it }
        val hex = raw
            .removePrefix("#")
            .removePrefix("0x")
            .removePrefix("0X")
            .takeIf { it.length == 6 || it.length == 8 }
            ?.takeIf { value -> value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } }
            ?: return null
        val value = hex.toLongOrNull(16) ?: return null
        return (if (hex.length == 6) value or 0xFF000000 else value).toInt()
    }

    private fun measurementBlocks(text: String): List<String> {
        val blocks = mutableListOf<String>()
        var currentBlock: MutableList<String>? = null

        fun finishCurrentBlock() {
            currentBlock?.let { lines ->
                if (lines.any { it.trim().isNotBlank() }) {
                    blocks += lines.joinToString("\n")
                }
            }
        }

        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line == MARKER -> {
                    finishCurrentBlock()
                    currentBlock = mutableListOf(rawLine)
                }
                line.isSectorMarker() -> {
                    finishCurrentBlock()
                    currentBlock = null
                }
                currentBlock != null -> currentBlock?.add(rawLine)
            }
        }
        finishCurrentBlock()
        return blocks
    }

    private fun String.isSectorMarker(): Boolean =
        startsWith("SECTOR_") && !contains("=")
}

data class ParsedMeasurements(
    val measurements: List<Measurement>,
    val skippedBlocks: Int
)

class ImportException(message: String, cause: Throwable? = null) : Exception(message, cause)
