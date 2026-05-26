package com.prishvindt.sector.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {
    @Query("SELECT * FROM measurements ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<Measurement>>

    @Query("SELECT * FROM measurements WHERE source = :source AND active = 1 ORDER BY timestamp DESC LIMIT 1")
    fun observeLatestActiveBySource(source: MeasurementSource): Flow<Measurement?>

    @Query("SELECT * FROM measurements WHERE source = :source AND active = 1 ORDER BY timestamp DESC LIMIT 1")
    suspend fun latestActiveBySource(source: MeasurementSource): Measurement?

    @Query("SELECT * FROM measurements WHERE active = 1 ORDER BY timestamp DESC")
    fun observeActive(): Flow<List<Measurement>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(measurement: Measurement)

    @Delete
    suspend fun delete(measurement: Measurement)

    @Query("DELETE FROM measurements")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM measurements WHERE measurement_id = :measurementId")
    suspend fun countById(measurementId: String): Int
}
