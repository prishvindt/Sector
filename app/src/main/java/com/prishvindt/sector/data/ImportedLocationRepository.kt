package com.prishvindt.sector.data

import kotlinx.coroutines.flow.Flow

class ImportedLocationRepository(
    private val dao: ImportedLocationDao
) {
    fun observeAll(): Flow<List<ImportedLocation>> = dao.observeAll()

    suspend fun upsert(location: ImportedLocation) {
        dao.upsert(location)
    }

    suspend fun clear() {
        dao.clear()
    }
}
