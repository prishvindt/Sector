package com.prishvindt.sector.domain.telemetry

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryRepositoryTest {
    private val payloadFactory = TelemetryPayloadFactory(
        appVersion = "0.1.7",
        versionCode = 8,
        deviceInfoProvider = {
            TelemetryDeviceInfo(
                manufacturer = "samsung",
                model = "SM-G991B",
                androidSdk = 35
            )
        }
    )

    @Test
    fun emptyUrlDisablesSending() = runTest {
        val settings = FakeSettingsSource(TelemetrySettings(available = true, enabled = true))
        val client = FakeClient()
        val repository = TelemetryRepository(
            config = TelemetryConfig(baseUrl = "", appToken = "token"),
            settingsSource = settings,
            client = client,
            payloadFactory = payloadFactory
        )

        repository.record(TelemetryEventType.APP_START, "session-1")

        assertTrue(client.sent.isEmpty())
        assertEquals(0, settings.installIdRequests)
    }

    @Test
    fun emptyTokenDisablesSending() = runTest {
        val settings = FakeSettingsSource(TelemetrySettings(available = true, enabled = true))
        val client = FakeClient()
        val repository = TelemetryRepository(
            config = TelemetryConfig(baseUrl = "https://telemetry.example", appToken = ""),
            settingsSource = settings,
            client = client,
            payloadFactory = payloadFactory
        )

        repository.record(TelemetryEventType.APP_START, "session-1")

        assertTrue(client.sent.isEmpty())
        assertEquals(0, settings.installIdRequests)
    }

    @Test
    fun disabledSettingSkipsSending() = runTest {
        val settings = FakeSettingsSource(TelemetrySettings(available = true, enabled = false))
        val client = FakeClient()
        val repository = enabledRepository(settings, client)

        repository.record(TelemetryEventType.HEARTBEAT, "session-1")

        assertTrue(client.sent.isEmpty())
        assertEquals(0, settings.installIdRequests)
    }

    @Test
    fun enabledTelemetrySendsPayloadWithStableInstallId() = runTest {
        val settings = FakeSettingsSource(TelemetrySettings(available = true, enabled = true))
        val client = FakeClient()
        val repository = enabledRepository(settings, client)

        repository.record(TelemetryEventType.APP_START, "session-1")
        repository.record(TelemetryEventType.HEARTBEAT, "session-1")

        assertEquals(2, client.sent.size)
        assertEquals("install-1", client.sent[0].installId)
        assertEquals("install-1", client.sent[1].installId)
        assertEquals(2, settings.installIdRequests)
    }

    @Test
    fun networkErrorsDoNotEscapeRepository() = runTest {
        val settings = FakeSettingsSource(TelemetrySettings(available = true, enabled = true))
        val repository = enabledRepository(settings, ThrowingClient())

        repository.record(TelemetryEventType.APP_START, "session-1")

        assertEquals(1, settings.installIdRequests)
    }

    @Test
    fun durationIsOnlyKeptForBackgroundEvent() = runTest {
        val settings = FakeSettingsSource(TelemetrySettings(available = true, enabled = true))
        val client = FakeClient()
        val repository = enabledRepository(settings, client)

        repository.record(TelemetryEventType.HEARTBEAT, "session-1", sessionDurationSeconds = 10L)
        repository.record(TelemetryEventType.APP_BACKGROUND, "session-1", sessionDurationSeconds = 10L)

        assertNull(client.sent[0].sessionDurationSeconds)
        assertEquals(10L, client.sent[1].sessionDurationSeconds)
    }

    private fun enabledRepository(
        settings: FakeSettingsSource,
        client: TelemetryClient
    ) = TelemetryRepository(
        config = TelemetryConfig(baseUrl = "https://telemetry.example", appToken = "token"),
        settingsSource = settings,
        client = client,
        payloadFactory = payloadFactory
    )

    private class FakeSettingsSource(
        initialSettings: TelemetrySettings
    ) : TelemetrySettingsSource {
        override val telemetrySettings = MutableStateFlow(initialSettings)
        var installIdRequests = 0
            private set
        private var installId: String? = null

        override suspend fun getOrCreateTelemetryInstallId(): String {
            installIdRequests += 1
            return installId ?: "install-1".also { installId = it }
        }
    }

    private class FakeClient : TelemetryClient {
        val sent = mutableListOf<TelemetryPayload>()

        override suspend fun send(payload: TelemetryPayload) {
            sent += payload
        }
    }

    private class ThrowingClient : TelemetryClient {
        override suspend fun send(payload: TelemetryPayload) {
            error("network unavailable")
        }
    }
}
