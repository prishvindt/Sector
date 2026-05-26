package com.prishvindt.sector.domain

import com.prishvindt.sector.data.Measurement

object MeasurementMerge {
    fun upsert(existing: List<Measurement>, incoming: Measurement): List<Measurement> {
        val index = existing.indexOfFirst { it.measurementId == incoming.measurementId }
        return if (index < 0) {
            existing + incoming
        } else {
            existing.toMutableList().also { it[index] = incoming }
        }
    }
}
