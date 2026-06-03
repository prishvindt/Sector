package com.prishvindt.sector.domain.telemetry

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetrySettingsResolverTest {
    @Test
    fun telemetryIsEnabledByDefaultWhenConfigExists() {
        val settings = TelemetrySettingsResolver.resolve(
            configAvailable = true,
            storedEnabled = null
        )

        assertTrue(settings.available)
        assertTrue(settings.enabled)
    }

    @Test
    fun telemetryIsUnavailableWhenConfigIsMissing() {
        val settings = TelemetrySettingsResolver.resolve(
            configAvailable = false,
            storedEnabled = null
        )

        assertFalse(settings.available)
        assertFalse(settings.enabled)
    }

    @Test
    fun storedOptOutDisablesTelemetry() {
        val settings = TelemetrySettingsResolver.resolve(
            configAvailable = true,
            storedEnabled = false
        )

        assertTrue(settings.available)
        assertFalse(settings.enabled)
    }

    @Test
    fun missingConfigOverridesStoredOptIn() {
        val settings = TelemetrySettingsResolver.resolve(
            configAvailable = false,
            storedEnabled = true
        )

        assertFalse(settings.available)
        assertFalse(settings.enabled)
    }
}
