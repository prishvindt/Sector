package com.prishvindt.sector.domain.telemetry

data class TelemetryPayload(
    val installId: String,
    val eventType: TelemetryEventType,
    val appVersion: String,
    val versionCode: Int,
    val manufacturer: String,
    val model: String,
    val androidSdk: Int,
    val sessionId: String,
    val sessionDurationSeconds: Long? = null
)
