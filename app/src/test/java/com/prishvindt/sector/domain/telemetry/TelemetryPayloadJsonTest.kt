package com.prishvindt.sector.domain.telemetry

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryPayloadJsonTest {
    @Test
    fun appStartPayloadContainsOnlyAllowedTelemetryFields() {
        val json = TelemetryPayloadJson.encode(
            TelemetryPayload(
                installId = "install-1",
                eventType = TelemetryEventType.APP_START,
                appVersion = "0.1.7",
                versionCode = 8,
                manufacturer = "samsung",
                model = "SM-G991B",
                androidSdk = 35,
                sessionId = "session-1"
            )
        )

        assertTrue(json.contains("\"installId\":\"install-1\""))
        assertTrue(json.contains("\"eventType\":\"app_start\""))
        assertTrue(json.contains("\"appVersion\":\"0.1.7\""))
        assertTrue(json.contains("\"versionCode\":8"))
        assertTrue(json.contains("\"manufacturer\":\"samsung\""))
        assertTrue(json.contains("\"model\":\"SM-G991B\""))
        assertTrue(json.contains("\"androidSdk\":35"))
        assertTrue(json.contains("\"sessionId\":\"session-1\""))
        assertFalse(json.contains("latitude"))
        assertFalse(json.contains("longitude"))
        assertFalse(json.contains("coordinate"))
        assertFalse(json.contains("azimuth"))
        assertFalse(json.contains("callsign"))
        assertFalse(json.contains("contact"))
        assertFalse(json.contains("androidId"))
        assertFalse(json.contains("imei"))
        assertFalse(json.contains("phone"))
        assertFalse(json.contains("sim"))
    }

    @Test
    fun backgroundPayloadIncludesDuration() {
        val json = TelemetryPayloadJson.encode(
            TelemetryPayload(
                installId = "install-1",
                eventType = TelemetryEventType.APP_BACKGROUND,
                appVersion = "0.1.7",
                versionCode = 8,
                manufacturer = "samsung",
                model = "SM-G991B",
                androidSdk = 35,
                sessionId = "session-1",
                sessionDurationSeconds = 5400
            )
        )

        assertTrue(json.contains("\"eventType\":\"app_background\""))
        assertTrue(json.contains("\"sessionDurationSeconds\":5400"))
    }

    @Test
    fun stringValuesAreJsonEscaped() {
        val json = TelemetryPayloadJson.encode(
            TelemetryPayload(
                installId = "install\"1",
                eventType = TelemetryEventType.HEARTBEAT,
                appVersion = "0.1.7",
                versionCode = 8,
                manufacturer = "ACME\\Corp",
                model = "Line\nBreak",
                androidSdk = 35,
                sessionId = "session-1"
            )
        )

        assertTrue(json.contains("\"installId\":\"install\\\"1\""))
        assertTrue(json.contains("\"manufacturer\":\"ACME\\\\Corp\""))
        assertTrue(json.contains("\"model\":\"Line\\nBreak\""))
    }
}
