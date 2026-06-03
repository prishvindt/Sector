package com.prishvindt.sector.domain.telemetry

enum class TelemetryEventType(val wireName: String) {
    APP_START("app_start"),
    HEARTBEAT("heartbeat"),
    APP_BACKGROUND("app_background")
}
