package com.prishvindt.sector.domain.telemetry

interface TelemetryClient {
    suspend fun send(payload: TelemetryPayload)
}
