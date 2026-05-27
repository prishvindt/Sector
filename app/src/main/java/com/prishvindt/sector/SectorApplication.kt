package com.prishvindt.sector

import android.app.Application
import com.prishvindt.sector.data.AppDatabase
import com.prishvindt.sector.data.MeasurementRepository
import com.prishvindt.sector.data.SettingsRepository
import com.prishvindt.sector.domain.measurements.MeasurementManager
import com.prishvindt.sector.location.LocationTracker
import com.prishvindt.sector.map.RoutePlanner
import com.prishvindt.sector.updates.UpdateChecker
import com.prishvindt.sector.updates.UpdateInstaller
import com.prishvindt.sector.updates.UpdateRepository
import com.yandex.mapkit.MapKitFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SectorApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        initializeMapKit()
        val database = AppDatabase.get(this)
        val measurementRepository = MeasurementRepository(database.measurementDao())
        appContainer = AppContainer(
            measurementRepository = measurementRepository,
            measurementManager = MeasurementManager(measurementRepository),
            settingsRepository = SettingsRepository(this),
            locationTracker = LocationTracker(this),
            routePlanner = RoutePlanner(),
            updateChecker = UpdateChecker(UpdateRepository()),
            updateInstaller = UpdateInstaller(this)
        )
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
    val measurementRepository: MeasurementRepository,
    val measurementManager: MeasurementManager,
    val settingsRepository: SettingsRepository,
    val locationTracker: LocationTracker,
    val routePlanner: RoutePlanner,
    val updateChecker: UpdateChecker,
    val updateInstaller: UpdateInstaller
)
