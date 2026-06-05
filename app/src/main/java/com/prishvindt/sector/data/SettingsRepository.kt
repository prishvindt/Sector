package com.prishvindt.sector.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.prishvindt.sector.domain.backup.BackupSettings
import com.prishvindt.sector.domain.backup.BackupSettingsStore
import com.prishvindt.sector.domain.notes.NoteNumberStore
import com.prishvindt.sector.domain.telemetry.TelemetrySettings
import com.prishvindt.sector.domain.telemetry.TelemetrySettingsResolver
import com.prishvindt.sector.domain.telemetry.TelemetrySettingsSource
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

enum class DestinationMarkerType(val label: String) {
    POINT("точка"),
    FLAG("флажок"),
    TARGET("цель")
}

data class AppSettings(
    val callsign: String = "",
    val firstStartAccepted: Boolean = false,
    val exportWarningAccepted: Boolean = false,
    val ownPointColor: OwnPointColor = OwnPointColor.BLUE,
    val gpsPointScale: Float = 1f,
    val destinationMarkerType: DestinationMarkerType = DestinationMarkerType.POINT,
    val gpsMode: GpsMode = GpsMode.NORMAL,
    val activeSearchEnabled: Boolean = false,
    val accuracyWarningMeters: Double = 30.0,
    val showSelfCallsign: Boolean = true,
    val showImportedCallsigns: Boolean = true,
    val callsignBehavior: CallsignBehavior = CallsignBehavior.ALWAYS,
    val routeMode: RouteMode = RouteMode.IN_APP,
    val routeType: RouteType = RouteType.CAR,
    val updateChecksEnabled: Boolean = true,
    val telemetryAvailable: Boolean = false,
    val telemetryEnabled: Boolean = false,
    val lastSeenChangelogVersionCode: Int = 0,
    val showMapNotes: Boolean = true,
    val showMapNoteTitles: Boolean = true
)

class SettingsRepository(
    private val context: Context,
    private val telemetryAvailable: Boolean = false,
    private val uuidFactory: () -> String = { UUID.randomUUID().toString() }
) : TelemetrySettingsSource, NoteNumberStore, BackupSettingsStore {
    val settings: Flow<AppSettings> = context.sectorDataStore.data.map { prefs ->
        val telemetrySettings = TelemetrySettingsResolver.resolve(
            configAvailable = telemetryAvailable,
            storedEnabled = prefs[Keys.TELEMETRY_ENABLED]
        )
        AppSettings(
            callsign = prefs[Keys.CALLSIGN].orEmpty(),
            firstStartAccepted = prefs[Keys.FIRST_START_ACCEPTED] ?: false,
            exportWarningAccepted = prefs[Keys.EXPORT_WARNING_ACCEPTED] ?: false,
            ownPointColor = prefs[Keys.OWN_POINT_COLOR].toEnum(OwnPointColor.BLUE),
            gpsPointScale = (prefs[Keys.GPS_POINT_SCALE] ?: 1f).coerceIn(1f, 5f),
            destinationMarkerType = prefs[Keys.DESTINATION_MARKER_TYPE].toEnum(DestinationMarkerType.POINT),
            gpsMode = prefs[Keys.GPS_MODE].toEnum(GpsMode.NORMAL),
            activeSearchEnabled = prefs[Keys.ACTIVE_SEARCH_ENABLED] ?: false,
            accuracyWarningMeters = prefs[Keys.ACCURACY_WARNING_METERS] ?: 30.0,
            showSelfCallsign = prefs[Keys.SHOW_SELF_CALLSIGN] ?: true,
            showImportedCallsigns = prefs[Keys.SHOW_IMPORTED_CALLSIGNS] ?: true,
            callsignBehavior = prefs[Keys.CALLSIGN_BEHAVIOR].toEnum(CallsignBehavior.ALWAYS),
            routeMode = prefs[Keys.ROUTE_MODE].toEnum(RouteMode.IN_APP),
            routeType = prefs[Keys.ROUTE_TYPE].toEnum(RouteType.CAR),
            updateChecksEnabled = prefs[Keys.UPDATE_CHECKS_ENABLED] ?: true,
            telemetryAvailable = telemetrySettings.available,
            telemetryEnabled = telemetrySettings.enabled,
            lastSeenChangelogVersionCode = prefs[Keys.LAST_SEEN_CHANGELOG_VERSION_CODE] ?: 0,
            showMapNotes = prefs[Keys.SHOW_MAP_NOTES] ?: true,
            showMapNoteTitles = prefs[Keys.SHOW_MAP_NOTE_TITLES] ?: true
        )
    }

    override val telemetrySettings: Flow<TelemetrySettings> = settings.map {
        TelemetrySettings(
            available = it.telemetryAvailable,
            enabled = it.telemetryEnabled
        )
    }

    suspend fun setCallsign(value: String) = put(Keys.CALLSIGN, value.trim())
    suspend fun acceptFirstStart() = put(Keys.FIRST_START_ACCEPTED, true)
    suspend fun acceptExportWarning() = put(Keys.EXPORT_WARNING_ACCEPTED, true)
    suspend fun setOwnPointColor(value: OwnPointColor) = put(Keys.OWN_POINT_COLOR, value.name)
    suspend fun setGpsPointScale(value: Float) = put(Keys.GPS_POINT_SCALE, value.coerceIn(1f, 5f))
    suspend fun setDestinationMarkerType(value: DestinationMarkerType) = put(Keys.DESTINATION_MARKER_TYPE, value.name)
    suspend fun setGpsMode(value: GpsMode) = put(Keys.GPS_MODE, value.name)
    suspend fun setActiveSearchEnabled(value: Boolean) = put(Keys.ACTIVE_SEARCH_ENABLED, value)
    suspend fun setAccuracyWarningMeters(value: Double) = put(Keys.ACCURACY_WARNING_METERS, value)
    suspend fun setShowSelfCallsign(value: Boolean) = put(Keys.SHOW_SELF_CALLSIGN, value)
    suspend fun setShowImportedCallsigns(value: Boolean) = put(Keys.SHOW_IMPORTED_CALLSIGNS, value)
    suspend fun setCallsignBehavior(value: CallsignBehavior) = put(Keys.CALLSIGN_BEHAVIOR, value.name)
    suspend fun setRouteMode(value: RouteMode) = put(Keys.ROUTE_MODE, value.name)
    suspend fun setRouteType(value: RouteType) = put(Keys.ROUTE_TYPE, value.name)
    suspend fun setUpdateChecksEnabled(value: Boolean) = put(Keys.UPDATE_CHECKS_ENABLED, value)
    suspend fun setTelemetryEnabled(value: Boolean) = put(Keys.TELEMETRY_ENABLED, value)
    suspend fun setLastSeenChangelogVersionCode(value: Int) = put(Keys.LAST_SEEN_CHANGELOG_VERSION_CODE, value)
    suspend fun setShowMapNotes(value: Boolean) = put(Keys.SHOW_MAP_NOTES, value)
    suspend fun setShowMapNoteTitles(value: Boolean) = put(Keys.SHOW_MAP_NOTE_TITLES, value)

    override suspend fun backupSettings(): BackupSettings {
        val current = settings.first()
        return BackupSettings(
            ownPointColor = current.ownPointColor.name,
            gpsPointScale = current.gpsPointScale,
            destinationMarkerType = current.destinationMarkerType.name,
            gpsMode = current.gpsMode.name,
            accuracyWarningMeters = current.accuracyWarningMeters,
            showSelfCallsign = current.showSelfCallsign,
            showImportedCallsigns = current.showImportedCallsigns,
            callsignBehavior = current.callsignBehavior.name,
            routeMode = current.routeMode.name,
            routeType = current.routeType.name,
            showMapNotes = current.showMapNotes,
            showMapNoteTitles = current.showMapNoteTitles
        )
    }

    override suspend fun applyBackupSettings(settings: BackupSettings) {
        settings.ownPointColor?.toEnumOrNull<OwnPointColor>()?.let { setOwnPointColor(it) }
        settings.gpsPointScale?.let { setGpsPointScale(it) }
        settings.destinationMarkerType?.toEnumOrNull<DestinationMarkerType>()?.let { setDestinationMarkerType(it) }
        settings.gpsMode?.toEnumOrNull<GpsMode>()?.let { setGpsMode(it) }
        settings.accuracyWarningMeters?.let { setAccuracyWarningMeters(it) }
        settings.showSelfCallsign?.let { setShowSelfCallsign(it) }
        settings.showImportedCallsigns?.let { setShowImportedCallsigns(it) }
        settings.callsignBehavior?.toEnumOrNull<CallsignBehavior>()?.let { setCallsignBehavior(it) }
        settings.routeMode?.toEnumOrNull<RouteMode>()?.let { setRouteMode(it) }
        settings.routeType?.toEnumOrNull<RouteType>()?.let { setRouteType(it) }
        settings.showMapNotes?.let { setShowMapNotes(it) }
        settings.showMapNoteTitles?.let { setShowMapNoteTitles(it) }
    }

    override suspend fun reserveNextNoteNumber(): Int {
        var result = 1
        context.sectorDataStore.edit { prefs ->
            result = prefs[Keys.NEXT_NOTE_NUMBER] ?: 1
            prefs[Keys.NEXT_NOTE_NUMBER] = result + 1
        }
        return result
    }

    override suspend fun getOrCreateTelemetryInstallId(): String {
        var result = ""
        context.sectorDataStore.edit { prefs ->
            val existing = prefs[Keys.TELEMETRY_INSTALL_ID]
            if (existing.isNullOrBlank()) {
                result = uuidFactory()
                prefs[Keys.TELEMETRY_INSTALL_ID] = result
            } else {
                result = existing
            }
        }
        return result
    }

    suspend fun resetTelemetryInstallId(): String {
        val newInstallId = uuidFactory()
        context.sectorDataStore.edit { prefs ->
            prefs[Keys.TELEMETRY_INSTALL_ID] = newInstallId
        }
        return newInstallId
    }

    private suspend fun <T> put(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) {
        context.sectorDataStore.edit { it[key] = value }
    }

    private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T {
        return this?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: default
    }

    private inline fun <reified T : Enum<T>> String.toEnumOrNull(): T? =
        enumValues<T>().firstOrNull { it.name == this }

    private object Keys {
        val CALLSIGN = stringPreferencesKey("callsign")
        val FIRST_START_ACCEPTED = booleanPreferencesKey("first_start_accepted")
        val EXPORT_WARNING_ACCEPTED = booleanPreferencesKey("export_warning_accepted")
        val OWN_POINT_COLOR = stringPreferencesKey("own_point_color")
        val GPS_POINT_SCALE = floatPreferencesKey("gps_point_scale")
        val DESTINATION_MARKER_TYPE = stringPreferencesKey("destination_marker_type")
        val GPS_MODE = stringPreferencesKey("gps_mode")
        val ACTIVE_SEARCH_ENABLED = booleanPreferencesKey("active_search_enabled")
        val ACCURACY_WARNING_METERS = doublePreferencesKey("accuracy_warning_meters")
        val SHOW_SELF_CALLSIGN = booleanPreferencesKey("show_self_callsign")
        val SHOW_IMPORTED_CALLSIGNS = booleanPreferencesKey("show_imported_callsigns")
        val CALLSIGN_BEHAVIOR = stringPreferencesKey("callsign_behavior")
        val ROUTE_MODE = stringPreferencesKey("route_mode")
        val ROUTE_TYPE = stringPreferencesKey("route_type")
        val UPDATE_CHECKS_ENABLED = booleanPreferencesKey("update_checks_enabled")
        val TELEMETRY_ENABLED = booleanPreferencesKey("telemetry_enabled")
        val TELEMETRY_INSTALL_ID = stringPreferencesKey("telemetry_install_id")
        val LAST_SEEN_CHANGELOG_VERSION_CODE = intPreferencesKey("last_seen_changelog_version_code")
        val SHOW_MAP_NOTES = booleanPreferencesKey("show_map_notes")
        val SHOW_MAP_NOTE_TITLES = booleanPreferencesKey("show_map_note_titles")
        val NEXT_NOTE_NUMBER = intPreferencesKey("next_note_number")
    }
}
