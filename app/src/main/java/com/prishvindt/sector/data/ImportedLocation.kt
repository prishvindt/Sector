package com.prishvindt.sector.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "imported_locations")
data class ImportedLocation(
    @PrimaryKey
    @ColumnInfo(name = "location_key")
    val locationKey: String,
    val callsign: String,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "accuracy_m")
    val accuracyMeters: Double?,
    @ColumnInfo(name = "timestamp")
    val timestampEpochSeconds: Long,
    @ColumnInfo(name = "received_at")
    val receivedAtEpochMillis: Long
)
