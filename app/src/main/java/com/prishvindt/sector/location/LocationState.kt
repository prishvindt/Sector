package com.prishvindt.sector.location

import com.prishvindt.sector.domain.GeoPoint

data class LocationState(
    val isSearching: Boolean = true,
    val hasPermission: Boolean = false,
    val precisePermissionGranted: Boolean = false,
    val point: GeoPoint? = null,
    val accuracyMeters: Float? = null,
    val bearingDeg: Float? = null,
    val satelliteCount: Int? = null,
    val provider: String? = null,
    val lastUpdateMillis: Long? = null,
    val error: String? = null
)
