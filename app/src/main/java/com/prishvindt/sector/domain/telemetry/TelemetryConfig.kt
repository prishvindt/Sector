package com.prishvindt.sector.domain.telemetry

data class TelemetryConfig(
    val baseUrl: String,
    val appToken: String
) {
    val isAvailable: Boolean
        get() = baseUrl.isNotBlank() && appToken.isNotBlank()

    val eventsEndpoint: String
        get() = "${baseUrl.trim().trimEnd('/')}/api/v1/events"
}
