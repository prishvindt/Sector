package com.prishvindt.sector.updates

import com.prishvindt.sector.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class UpdateRepository(
    private val updateInfoUrl: String = BuildConfig.UPDATE_INFO_URL
) {
    suspend fun checkForUpdate(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(updateInfoUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6_000
                readTimeout = 6_000
                useCaches = false
            }
            try {
                if (connection.responseCode !in 200..299) return@runCatching null
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val info = UpdateInfo(
                    latestVersion = json.getString("latestVersion"),
                    versionCode = json.getInt("versionCode"),
                    apkUrl = json.getString("apkUrl"),
                    changelog = json.optJSONArray("changelog")?.let { array ->
                        (0 until array.length()).map { index -> array.getString(index) }
                    } ?: emptyList(),
                    mandatory = json.optBoolean("mandatory", false)
                )
                info.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
            } finally {
                connection.disconnect()
            }
        }
    }
}
