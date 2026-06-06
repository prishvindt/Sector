package com.prishvindt.sector

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.prishvindt.sector.data.AppDatabase
import com.prishvindt.sector.data.SectorObjectRepository
import com.prishvindt.sector.data.SettingsRepository
import com.prishvindt.sector.domain.backup.BackupManager
import com.prishvindt.sector.domain.backup.FileBackupMediaStorage
import com.prishvindt.sector.domain.locations.LocationShareManager
import com.prishvindt.sector.domain.measurements.MeasurementManager
import com.prishvindt.sector.domain.notes.NoteManager
import com.prishvindt.sector.domain.telemetry.TelemetryConfig
import com.prishvindt.sector.domain.telemetry.TelemetryPayloadFactory
import com.prishvindt.sector.domain.telemetry.TelemetryRepository
import com.prishvindt.sector.domain.telemetry.TelemetrySessionTracker
import com.prishvindt.sector.lifecycle.TelemetryLifecycleObserver
import com.prishvindt.sector.location.LocationTracker
import com.prishvindt.sector.map.RoutePlanner
import com.prishvindt.sector.media.notes.NoteMediaManager
import com.prishvindt.sector.telemetry.TelemetryHttpClient
import com.prishvindt.sector.updates.UpdateChecker
import com.prishvindt.sector.updates.UpdateInstaller
import com.prishvindt.sector.updates.UpdateRepository
import com.yandex.mapkit.MapKitFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SectorApplication : Application() {
    lateinit var appContainer: AppContainer
        private set
    private lateinit var telemetryLifecycleObserver: TelemetryLifecycleObserver

    override fun onCreate() {
        super.onCreate()
        initializeMapKit()
        val telemetryConfig = TelemetryConfig(
            baseUrl = BuildConfig.TELEMETRY_URL,
            appToken = BuildConfig.TELEMETRY_APP_TOKEN
        )
        val settingsRepository = SettingsRepository(
            context = this,
            telemetryAvailable = telemetryConfig.isAvailable
        )
        val database = AppDatabase.get(this)
        val sectorObjectRepository = SectorObjectRepository(database.sectorObjectDao())
        val noteMediaManager = NoteMediaManager(this)
        val noteManager = NoteManager(
            repository = sectorObjectRepository,
            numberStore = settingsRepository,
            attachmentStorage = noteMediaManager
        )
        val telemetryRepository = TelemetryRepository(
            config = telemetryConfig,
            settingsSource = settingsRepository,
            client = TelemetryHttpClient(telemetryConfig),
            payloadFactory = TelemetryPayloadFactory(
                appVersion = BuildConfig.APP_VERSION_LABEL,
                versionCode = BuildConfig.VERSION_CODE
            )
        )
        appContainer = AppContainer(
            sectorObjectRepository = sectorObjectRepository,
            backupManager = BackupManager(
                objectRepository = sectorObjectRepository,
                settingsStore = settingsRepository,
                mediaStorage = FileBackupMediaStorage(filesDir)
            ),
            measurementManager = MeasurementManager(sectorObjectRepository),
            noteManager = noteManager,
            noteMediaManager = noteMediaManager,
            locationShareManager = LocationShareManager(sectorObjectRepository),
            settingsRepository = settingsRepository,
            locationTracker = LocationTracker(this),
            routePlanner = RoutePlanner(),
            updateChecker = UpdateChecker(UpdateRepository()),
            updateInstaller = UpdateInstaller(this),
            telemetryRepository = telemetryRepository
        )
        telemetryLifecycleObserver = TelemetryLifecycleObserver(
            TelemetrySessionTracker(
                recorder = telemetryRepository,
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            )
        )
        ProcessLifecycleOwner.get().lifecycle.addObserver(telemetryLifecycleObserver)
    }

    private fun initializeMapKit() {
        val key = BuildConfig.MAPKIT_API_KEY
        if (key.isBlank()) {
            _mapKitState.value = MapKitState(
                isReady = false,
                message = "Не задан MAPKIT_API_KEY. Добавьте ключ в local.properties."
            )
            return
        }

        runCatching {
            MapKitFactory.setApiKey(key)
            MapKitFactory.initialize(this)
        }.onSuccess {
            _mapKitState.value = MapKitState(isReady = true)
        }.onFailure { error ->
            _mapKitState.value = MapKitState(
                isReady = false,
                message = "Ошибка инициализации MapKit: ${error.message ?: "неизвестная ошибка"}"
            )
        }
    }

    companion object {
        private val _mapKitState = MutableStateFlow(MapKitState())
        val mapKitState: StateFlow<MapKitState> = _mapKitState
    }
}

data class MapKitState(
    val isReady: Boolean = false,
    val message: String? = null
)

data class AppContainer(
    val sectorObjectRepository: SectorObjectRepository,
    val backupManager: BackupManager,
    val measurementManager: MeasurementManager,
    val noteManager: NoteManager,
    val noteMediaManager: NoteMediaManager,
    val locationShareManager: LocationShareManager,
    val settingsRepository: SettingsRepository,
    val locationTracker: LocationTracker,
    val routePlanner: RoutePlanner,
    val updateChecker: UpdateChecker,
    val updateInstaller: UpdateInstaller,
    val telemetryRepository: TelemetryRepository
)
