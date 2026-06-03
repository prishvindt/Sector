package com.prishvindt.sector.domain.telemetry

object TelemetrySettingsResolver {
    fun resolve(
        configAvailable: Boolean,
        storedEnabled: Boolean?
    ): TelemetrySettings {
        return TelemetrySettings(
            available = configAvailable,
            enabled = configAvailable && (storedEnabled ?: configAvailable)
        )
    }
}
