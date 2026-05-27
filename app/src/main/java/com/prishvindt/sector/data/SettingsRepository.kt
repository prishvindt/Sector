package com.prishvindt.sector.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sectorDataStore by preferencesDataStore(name = "sector_settings")

enum class OwnPointColor(val label: String, val colorArgb: Int) {
    BLUE("Синий", 0xFF2F80ED.toInt()),
    GREEN("Зелёный", 0xFF27AE60.toInt()),
    RED("Красный", 0xFFEB5757.toInt()),
    YELLOW("Жёлтый", 0xFFF2C94C.toInt()),
    PURPLE("Фиолетовый", 0xFF9B51E0.toInt())
}

enum class GpsMode(val label: String, val intervalMillis: Long) {
    ECONOMY("Экономный — 10 секунд", 10_000L),
    NORMAL("Обычный — 5 секунд", 5_000L),
    PRECISE("Точный — 2 секунды", 2_000L)
}

enum class CallsignBehavior(val label: String) {
    ALWAYS("Показывать всегда"),
    ON_TAP("Показывать только при нажатии")
}

enum class RouteMode(val label: String) {
    IN_APP("Внутри приложения"),
    YANDEX_MAPS("Открывать в Яндекс.Картах")
}

enum class RouteType(val label: String) {
    CAR("Автомобиль"),
    WALK("Пешком")
}

data class AppSettings(
    val callsign: String = "",
    val firstStartAccepted: Boolean = false,
    val exportWarningAccepted: Boolean = false,
    val ownPointColor: OwnPointColor = OwnPointColor.BLUE,
    val gpsMode: GpsMode = GpsMode.NORMAL,
    val activeSearchEnabled: Boolean = false,
    val accuracyWarningMeters: Double = 30.0,
    val showSelfCallsign: Boolean = true,
    val showImportedCallsigns: Boolean = true,
    val callsignBehavior: CallsignBehavior = CallsignBehavior.ALWAYS,
    val routeMode: RouteMode = RouteMode.IN_APP,
    val routeType: RouteType = RouteType.CAR,
    val updateChecksEnabled: Boolean = true
)

class SettingsRepository(
    private val context: Context
) {
    val settings: Flow<AppSettings> = context.sectorDataStore.data.map { prefs ->
        AppSettings(
            callsign = prefs[Keys.CALLSIGN].orEmpty(),
            firstStartAccepted = prefs[Keys.FIRST_START_ACCEPTED] ?: false,
            exportWarningAccepted = prefs[Keys.EXPORT_WARNING_ACCEPTED] ?: false,
            ownPointColor = prefs[Keys.OWN_POINT_COLOR].toEnum(OwnPointColor.BLUE),
            gpsMode = prefs[Keys.GPS_MODE].toEnum(GpsMode.NORMAL),
            activeSearchEnabled = prefs[Keys.ACTIVE_SEARCH_ENABLED] ?: false,
            accuracyWarningMeters = prefs[Keys.ACCURACY_WARNING_METERS] ?: 30.0,
            showSelfCallsign = prefs[Keys.SHOW_SELF_CALLSIGN] ?: true,
            showImportedCallsigns = prefs[Keys.SHOW_IMPORTED_CALLSIGNS] ?: true,
            callsignBehavior = prefs[Keys.CALLSIGN_BEHAVIOR].toEnum(CallsignBehavior.ALWAYS),
            routeMode = prefs[Keys.ROUTE_MODE].toEnum(RouteMode.IN_APP),
            routeType = prefs[Keys.ROUTE_TYPE].toEnum(RouteType.CAR),
            updateChecksEnabled = prefs[Keys.UPDATE_CHECKS_ENABLED] ?: true
        )
    }

    suspend fun setCallsign(value: String) = put(Keys.CALLSIGN, value.trim())
    suspend fun acceptFirstStart() = put(Keys.FIRST_START_ACCEPTED, true)
    suspend fun acceptExportWarning() = put(Keys.EXPORT_WARNING_ACCEPTED, true)
    suspend fun setOwnPointColor(value: OwnPointColor) = put(Keys.OWN_POINT_COLOR, value.name)
    suspend fun setGpsMode(value: GpsMode) = put(Keys.GPS_MODE, value.name)
    suspend fun setActiveSearchEnabled(value: Boolean) = put(Keys.ACTIVE_SEARCH_ENABLED, value)
    suspend fun setAccuracyWarningMeters(value: Double) = put(Keys.ACCURACY_WARNING_METERS, value)
    suspend fun setShowSelfCallsign(value: Boolean) = put(Keys.SHOW_SELF_CALLSIGN, value)
    suspend fun setShowImportedCallsigns(value: Boolean) = put(Keys.SHOW_IMPORTED_CALLSIGNS, value)
    suspend fun setCallsignBehavior(value: CallsignBehavior) = put(Keys.CALLSIGN_BEHAVIOR, value.name)
    suspend fun setRouteMode(value: RouteMode) = put(Keys.ROUTE_MODE, value.name)
    suspend fun setRouteType(value: RouteType) = put(Keys.ROUTE_TYPE, value.name)
    suspend fun setUpdateChecksEnabled(value: Boolean) = put(Keys.UPDATE_CHECKS_ENABLED, value)

    private suspend fun <T> put(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) {
        context.sectorDataStore.edit { it[key] = value }
    }

    private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T {
        return this?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: default
    }

    private object Keys {
        val CALLSIGN = stringPreferencesKey("callsign")
        val FIRST_START_ACCEPTED = booleanPreferencesKey("first_start_accepted")
        val EXPORT_WARNING_ACCEPTED = booleanPreferencesKey("export_warning_accepted")
        val OWN_POINT_COLOR = stringPreferencesKey("own_point_color")
        val GPS_MODE = stringPreferencesKey("gps_mode")
        val ACTIVE_SEARCH_ENABLED = booleanPreferencesKey("active_search_enabled")
        val ACCURACY_WARNING_METERS = doublePreferencesKey("accuracy_warning_meters")
        val SHOW_SELF_CALLSIGN = booleanPreferencesKey("show_self_callsign")
        val SHOW_IMPORTED_CALLSIGNS = booleanPreferencesKey("show_imported_callsigns")
        val CALLSIGN_BEHAVIOR = stringPreferencesKey("callsign_behavior")
        val ROUTE_MODE = stringPreferencesKey("route_mode")
        val ROUTE_TYPE = stringPreferencesKey("route_type")
        val UPDATE_CHECKS_ENABLED = booleanPreferencesKey("update_checks_enabled")
    }
}
