package com.prishvindt.sector.data

import kotlinx.coroutines.flow.Flow

class MeasurementRepository(
    private val dao: MeasurementDao
) {
    fun observeAll(): Flow<List<Measurement>> = dao.observeAll()

    fun observeActive(): Flow<List<Measurement>> = dao.observeActive()

    fun observeLatestSelf(): Flow<Measurement?> =
        dao.observeLatestActiveBySource(MeasurementSource.SELF)

    suspend fun latestSelf(): Measurement? =
        dao.latestActiveBySource(MeasurementSource.SELF)

    suspend fun upsert(measurement: Measurement) {
        dao.upsert(measurement)
    }

    suspend fun delete(measurement: Measurement) {
        dao.delete(measurement)
    }

    suspend fun clear() {
        dao.clear()
    }

    suspend fun exists(measurementId: String): Boolean =
        dao.countById(measurementId) > 0
}
