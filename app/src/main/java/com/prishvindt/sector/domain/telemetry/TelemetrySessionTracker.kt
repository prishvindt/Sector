package com.prishvindt.sector.domain.telemetry

import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TelemetrySessionTracker(
    private val recorder: TelemetryRecorder,
    private val scope: CoroutineScope,
    private val heartbeatIntervalMillis: Long = HEARTBEAT_INTERVAL_MILLIS,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val sessionIdFactory: () -> String = { UUID.randomUUID().toString() }
) {
    private var activeSession: TelemetrySession? = null
    private var heartbeatJob: Job? = null

    fun onForeground() {
        if (activeSession != null) return

        val session = TelemetrySession(
            id = sessionIdFactory(),
            startedAtMillis = nowMillis()
        )
        activeSession = session
        scope.launch {
            recorder.record(TelemetryEventType.APP_START, session.id)
        }
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(heartbeatIntervalMillis)
                recorder.record(TelemetryEventType.HEARTBEAT, session.id)
            }
        }
    }

    fun onBackground() {
        val session = activeSession ?: return
        activeSession = null
        heartbeatJob?.cancel()
        heartbeatJob = null

        val durationSeconds = ((nowMillis() - session.startedAtMillis) / 1_000L).coerceAtLeast(0L)
        scope.launch {
            recorder.record(
                eventType = TelemetryEventType.APP_BACKGROUND,
                sessionId = session.id,
                sessionDurationSeconds = durationSeconds
            )
        }
    }

    private data class TelemetrySession(
        val id: String,
        val startedAtMillis: Long
    )

    companion object {
        const val HEARTBEAT_INTERVAL_MILLIS = 15L * 60L * 1_000L
    }
}
