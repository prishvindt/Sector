package com.prishvindt.sector.domain.telemetry

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

data class TelemetrySettings(
    val available: Boolean,
    val enabled: Boolean
)

interface TelemetrySettingsSource {
    val telemetrySettings: Flow<TelemetrySettings>
    suspend fun getOrCreateTelemetryInstallId(): String
}

interface TelemetryRecorder {
    suspend fun record(
        eventType: TelemetryEventType,
        sessionId: String,
        sessionDurationSeconds: Long? = null
    )
}

class TelemetryRepository(
    private val config: TelemetryConfig,
    private val settingsSource: TelemetrySettingsSource,
    private val client: TelemetryClient,
    private val payloadFactory: TelemetryPayloadFactory
) : TelemetryRecorder {
    override suspend fun record(
        eventType: TelemetryEventType,
        sessionId: String,
        sessionDurationSeconds: Long?
    ) {
        runCatching {
            if (!config.isAvailable) return
            val settings = settingsSource.telemetrySettings.first()
            if (!settings.available || !settings.enabled) return

            val installId = settingsSource.getOrCreateTelemetryInstallId()
            val payload = payloadFactory.create(
                installId = installId,
                eventType = eventType,
                sessionId = sessionId,
                sessionDurationSeconds = sessionDurationSeconds
                    .takeIf { eventType == TelemetryEventType.APP_BACKGROUND }
            )
            client.send(payload)
        }
    }
}
