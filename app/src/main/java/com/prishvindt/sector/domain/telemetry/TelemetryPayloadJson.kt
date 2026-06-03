package com.prishvindt.sector.domain.telemetry

object TelemetryPayloadJson {
    fun encode(payload: TelemetryPayload): String = buildString {
        append('{')
        appendString("installId", payload.installId)
        append(',')
        appendString("eventType", payload.eventType.wireName)
        append(',')
        appendString("appVersion", payload.appVersion)
        append(',')
        appendNumber("versionCode", payload.versionCode)
        append(',')
        appendString("manufacturer", payload.manufacturer)
        append(',')
        appendString("model", payload.model)
        append(',')
        appendNumber("androidSdk", payload.androidSdk)
        append(',')
        appendString("sessionId", payload.sessionId)
        payload.sessionDurationSeconds?.let { duration ->
            append(',')
            appendNumber("sessionDurationSeconds", duration)
        }
        append('}')
    }

    private fun StringBuilder.appendString(name: String, value: String) {
        append('"').append(name).append('"').append(':')
        append('"').append(value.jsonEscaped()).append('"')
    }

    private fun StringBuilder.appendNumber(name: String, value: Number) {
        append('"').append(name).append('"').append(':').append(value)
    }

    private fun String.jsonEscaped(): String = buildString {
        for (char in this@jsonEscaped) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\t' -> append("\\t")
                '\n' -> append("\\n")
                '\u000C' -> append("\\f")
                '\r' -> append("\\r")
                else -> {
                    if (char.code < 0x20) {
                        append("\\u")
                        append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        append(char)
                    }
                }
            }
        }
    }
}
