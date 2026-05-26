package com.prishvindt.sector.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromSource(source: MeasurementSource): String = source.name

    @TypeConverter
    fun toSource(value: String): MeasurementSource = MeasurementSource.valueOf(value)
}
