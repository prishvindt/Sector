package com.prishvindt.sector.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MeasurementSource {
    SELF,
    IMPORTED
}

@Entity(tableName = "measurements")
data class Measurement(
    @PrimaryKey
    @ColumnInfo(name = "measurement_id")
    val measurementId: String,
    @ColumnInfo(name = "session_marker")
    val sessionMarker: String = "SECTOR_MEASUREMENT_V1",
    val callsign: String,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "accuracy_m")
    val accuracyM: Double?,
    @ColumnInfo(name = "satellite_count")
    val satelliteCount: Int?,
    @ColumnInfo(name = "azimuth_deg")
    val azimuthDeg: Double,
    @ColumnInfo(name = "azimuth_error_deg")
    val azimuthErrorDeg: Double,
    @ColumnInfo(name = "signal_dbm")
    val signalDbm: Int?,
    @ColumnInfo(name = "range_km")
    val rangeKm: Double = 15.0,
    val timestamp: String,
    val source: MeasurementSource,
    val active: Boolean = true,
    val note: String? = null,
    @ColumnInfo(name = "color_argb")
    val colorArgb: Int? = null
)
