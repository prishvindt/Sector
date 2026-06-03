package com.prishvindt.sector.telemetry

import com.prishvindt.sector.domain.telemetry.TelemetryClient
import com.prishvindt.sector.domain.telemetry.TelemetryConfig
import com.prishvindt.sector.domain.telemetry.TelemetryPayload
import com.prishvindt.sector.domain.telemetry.TelemetryPayloadJson
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TelemetryHttpClient(
    private val config: TelemetryConfig
) : TelemetryClient {
    override suspend fun send(payload: TelemetryPayload) {
        withContext(Dispatchers.IO) {
            val body = TelemetryPayloadJson.encode(payload).toByteArray(Charsets.UTF_8)
            val connection = (URL(config.eventsEndpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MILLIS
                readTimeout = TIMEOUT_MILLIS
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("X-App-Token", config.appToken)
                setFixedLengthStreamingMode(body.size)
            }

            try {
                connection.outputStream.use { output ->
                    output.write(body)
                }
                val code = connection.responseCode
                if (code !in 200..299) {
                    throw IOException("Telemetry response code $code")
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000
    }
}
