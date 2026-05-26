package com.prishvindt.sector.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class GnssSatelliteTracker(
    private val context: Context
) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val _satelliteCount = MutableStateFlow<Int?>(null)
    val satelliteCount: StateFlow<Int?> = _satelliteCount

    private val callback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var used = 0
            for (index in 0 until status.satelliteCount) {
                if (status.usedInFix(index)) used++
            }
            _satelliteCount.value = used.takeIf { it > 0 } ?: status.satelliteCount.takeIf { it > 0 }
        }

        override fun onStopped() {
            _satelliteCount.value = null
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (!hasLocationPermission()) {
            _satelliteCount.value = null
            return
        }
        runCatching {
            locationManager.registerGnssStatusCallback(context.mainExecutor, callback)
        }
    }

    fun stop() {
        runCatching { locationManager.unregisterGnssStatusCallback(callback) }
        _satelliteCount.value = null
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }
}
