package com.prishvindt.sector.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportedLocationDao {
    @Query("SELECT * FROM imported_locations ORDER BY received_at DESC")
    fun observeAll(): Flow<List<ImportedLocation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(location: ImportedLocation)

    @Query("DELETE FROM imported_locations")
    suspend fun clear()
}
