package com.prishvindt.sector.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.prishvindt.sector.data.GpsMode
import com.prishvindt.sector.domain.GeoPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LocationTracker(
    private val context: Context,
    private val satelliteTracker: GnssSatelliteTracker = GnssSatelliteTracker(context)
) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(LocationState())
    val state: StateFlow<LocationState> = _state

    private var listener: LocationListener? = null

    init {
        scope.launch {
            satelliteTracker.satelliteCount.collectLatest { satellites ->
                _state.value = _state.value.copy(satelliteCount = satellites)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun start(mode: GpsMode) {
        stopLocationOnly()
        val fine = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!fine && !coarse) {
            _state.value = LocationState(
                isSearching = false,
                hasPermission = false,
                precisePermissionGranted = false,
                error = "Нет разрешения геолокации"
            )
            return
        }

        _state.value = _state.value.copy(
            isSearching = true,
            hasPermission = true,
            precisePermissionGranted = fine,
            error = if (fine) null else "Выдана только приблизительная геолокация"
        )

        satelliteTracker.start()
        val updateListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                _state.value = _state.value.copy(
                    isSearching = false,
                    hasPermission = true,
                    precisePermissionGranted = fine,
                    point = GeoPoint(location.latitude, location.longitude),
                    accuracyMeters = location.accuracy.takeIf { location.hasAccuracy() },
                    provider = location.provider,
                    lastUpdateMillis = System.currentTimeMillis(),
                    error = if (fine) null else "Выдана только приблизительная геолокация"
                )
            }

            override fun onProviderDisabled(provider: String) {
                _state.value = _state.value.copy(error = "Провайдер геолокации отключён: $provider")
            }
        }
        listener = updateListener

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { provider -> locationManager.getProvider(provider) != null }

        providers.forEach { provider ->
            runCatching {
                locationManager.requestLocationUpdates(
                    provider,
                    mode.intervalMillis,
                    2f,
                    updateListener
                )
                locationManager.getLastKnownLocation(provider)?.let(updateListener::onLocationChanged)
            }.onFailure { error ->
                _state.value = _state.value.copy(error = error.message)
            }
        }
    }

    fun stop() {
        stopLocationOnly()
        satelliteTracker.stop()
    }

    private fun stopLocationOnly() {
        listener?.let { runCatching { locationManager.removeUpdates(it) } }
        listener = null
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
