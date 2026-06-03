package com.prishvindt.sector.domain.telemetry

import android.os.Build

data class TelemetryDeviceInfo(
    val manufacturer: String,
    val model: String,
    val androidSdk: Int
)

class TelemetryPayloadFactory(
    private val appVersion: String,
    private val versionCode: Int,
    private val deviceInfoProvider: () -> TelemetryDeviceInfo = {
        TelemetryDeviceInfo(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            androidSdk = Build.VERSION.SDK_INT
        )
    }
) {
    fun create(
        installId: String,
        eventType: TelemetryEventType,
        sessionId: String,
        sessionDurationSeconds: Long? = null
    ): TelemetryPayload {
        val deviceInfo = deviceInfoProvider()
        return TelemetryPayload(
            installId = installId,
            eventType = eventType,
            appVersion = appVersion,
            versionCode = versionCode,
            manufacturer = deviceInfo.manufacturer,
            model = deviceInfo.model,
            androidSdk = deviceInfo.androidSdk,
            sessionId = sessionId,
            sessionDurationSeconds = sessionDurationSeconds
        )
    }
}
