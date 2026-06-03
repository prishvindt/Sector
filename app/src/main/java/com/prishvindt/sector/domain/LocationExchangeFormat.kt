package com.prishvindt.sector.domain

object LocationExchangeFormat {
    const val MARKER = "SECTOR_LOCATION_V1"

    private val requiredFields = listOf("callsign", "latitude", "longitude", "timestamp")

    fun isLocationText(text: String): Boolean =
        text.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() } == MARKER

    fun containsLocationText(text: String): Boolean =
        text.lineSequence().any { it.trim() == MARKER }

    fun format(payload: LocationSharePayload): String {
        return buildString {
            appendLine(MARKER)
            appendLine("callsign=${payload.callsign}")
            appendLine("latitude=${payload.latitude}")
            appendLine("longitude=${payload.longitude}")
            payload.accuracyMeters?.let { appendLine("accuracyMeters=$it") }
            appendLine("timestamp=${payload.timestampEpochSeconds}")
        }.trimEnd()
    }

    fun parse(text: String): Result<LocationSharePayload> {
        val block = locationBlocks(text).firstOrNull()
            ?: return Result.failure(ImportException("Ошибка импорта: не найден маркер $MARKER"))

        val lines = block.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()

        if (lines.firstOrNull() != MARKER) {
            return Result.failure(ImportException("Ошибка импорта: не найден маркер $MARKER"))
        }

        val fields = lines.drop(1)
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator < 0) {
                    null
                } else {
                    line.substring(0, separator).trim() to line.substring(separator + 1).trim()
                }
            }
            .toMap()

        for (field in requiredFields) {
            if (!fields.containsKey(field)) {
                return Result.failure(ImportException("Ошибка импорта: не найдено поле $field"))
            }
        }

        return runCatching {
            LocationSharePayload(
                callsign = fields.required("callsign"),
                latitude = fields.required("latitude").toDouble(),
                longitude = fields.required("longitude").toDouble(),
                accuracyMeters = fields.optionalDouble("accuracyMeters"),
                timestampEpochSeconds = fields.required("timestamp").toLong()
            ).also { payload ->
                require(payload.latitude in -90.0..90.0) { "latitude вне диапазона" }
                require(payload.longitude in -180.0..180.0) { "longitude вне диапазона" }
                require(payload.timestampEpochSeconds > 0L) { "timestamp должен быть больше 0" }
                require(payload.accuracyMeters == null || payload.accuracyMeters >= 0.0) {
                    "accuracyMeters должен быть 0 или больше"
                }
            }
        }.recoverCatching { cause ->
            throw ImportException(
                "Ошибка импорта: ${cause.message ?: "неверный формат"}",
                cause
            )
        }
    }

    private fun Map<String, String>.required(field: String): String =
        this[field] ?: throw ImportException("Ошибка импорта: не найдено поле $field")

    private fun Map<String, String>.optionalDouble(field: String): Double? =
        this[field]?.takeIf { it.isNotBlank() }?.toDouble()

    private fun locationBlocks(text: String): List<String> {
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

data class LocationSharePayload(
    val callsign: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double?,
    val timestampEpochSeconds: Long
)
