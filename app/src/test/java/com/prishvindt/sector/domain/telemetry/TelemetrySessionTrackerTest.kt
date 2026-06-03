package com.prishvindt.sector.domain.telemetry

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TelemetrySessionTrackerTest {
    @Test
    fun foregroundStartsSessionHeartbeatAndBackgroundDuration() = runTest {
        val recorder = FakeRecorder()
        var nowMillis = 0L
        val tracker = TelemetrySessionTracker(
            recorder = recorder,
            scope = this,
            heartbeatIntervalMillis = 1_000L,
            nowMillis = { nowMillis },
            sessionIdFactory = { "session-1" }
        )

        tracker.onForeground()
        runCurrent()
        assertEquals(listOf(record(TelemetryEventType.APP_START, "session-1")), recorder.records)

        advanceTimeBy(999L)
        runCurrent()
        assertEquals(1, recorder.records.size)

        advanceTimeBy(1L)
        runCurrent()
        assertEquals(
            listOf(
                record(TelemetryEventType.APP_START, "session-1"),
                record(TelemetryEventType.HEARTBEAT, "session-1")
            ),
            recorder.records
        )

        nowMillis = 2_500L
        tracker.onBackground()
        runCurrent()
        assertEquals(
            listOf(
                record(TelemetryEventType.APP_START, "session-1"),
                record(TelemetryEventType.HEARTBEAT, "session-1"),
                record(TelemetryEventType.APP_BACKGROUND, "session-1", 2L)
            ),
            recorder.records
        )

        advanceTimeBy(2_000L)
        runCurrent()
        assertEquals(3, recorder.records.size)
    }

    @Test
    fun eachForegroundSessionGetsFreshSessionId() = runTest {
        val recorder = FakeRecorder()
        var nowMillis = 0L
        var nextId = 0
        val tracker = TelemetrySessionTracker(
            recorder = recorder,
            scope = this,
            heartbeatIntervalMillis = 60_000L,
            nowMillis = { nowMillis },
            sessionIdFactory = { "session-${++nextId}" }
        )

        tracker.onForeground()
        runCurrent()
        tracker.onBackground()
        runCurrent()
        nowMillis = 1_000L
        tracker.onForeground()
        runCurrent()

        assertEquals(
            listOf(
                record(TelemetryEventType.APP_START, "session-1"),
                record(TelemetryEventType.APP_BACKGROUND, "session-1", 0L),
                record(TelemetryEventType.APP_START, "session-2")
            ),
            recorder.records
        )

        tracker.onBackground()
        runCurrent()
    }

    private fun record(
        eventType: TelemetryEventType,
        sessionId: String,
        durationSeconds: Long? = null
    ) = RecordedEvent(eventType, sessionId, durationSeconds)

    private data class RecordedEvent(
        val eventType: TelemetryEventType,
        val sessionId: String,
        val durationSeconds: Long?
    )

    private class FakeRecorder : TelemetryRecorder {
        val records = mutableListOf<RecordedEvent>()

        override suspend fun record(
            eventType: TelemetryEventType,
            sessionId: String,
            sessionDurationSeconds: Long?
        ) {
            records += RecordedEvent(eventType, sessionId, sessionDurationSeconds)
        }
    }
}
