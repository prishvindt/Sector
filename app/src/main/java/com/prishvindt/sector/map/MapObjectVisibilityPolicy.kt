package com.prishvindt.sector.map

import com.prishvindt.sector.data.ImportedLocation
import com.prishvindt.sector.data.Measurement
import com.prishvindt.sector.data.MeasurementSource
import com.prishvindt.sector.domain.notes.MapNote
import com.prishvindt.sector.domain.objects.SectorObjectType
import com.prishvindt.sector.ui.common.MapDisplaySettings

object MapObjectVisibilityPolicy {
    fun shouldShowObject(type: SectorObjectType): Boolean =
        when (type) {
            SectorObjectType.AZIMUTH_RAY,
            SectorObjectType.MAP_NOTE,
            SectorObjectType.SHARED_LOCATION -> true
            SectorObjectType.LIVE_LOCATION,
            SectorObjectType.UNKNOWN -> false
        }

    fun shouldShowMeasurement(measurement: Measurement): Boolean =
        measurement.active && shouldShowObject(SectorObjectType.AZIMUTH_RAY)

    fun shouldShowImportedLocation(location: ImportedLocation): Boolean =
        shouldShowObject(SectorObjectType.SHARED_LOCATION)

    fun shouldShowMapNote(
        note: MapNote,
        displaySettings: MapDisplaySettings
    ): Boolean =
        shouldShowObject(SectorObjectType.MAP_NOTE) && displaySettings.showMapNotes

    fun measurementLabel(
        measurement: Measurement,
        displaySettings: MapDisplaySettings
    ): String? {
        val showCallsign = when (measurement.source) {
            MeasurementSource.SELF -> displaySettings.showSelfCallsign
            MeasurementSource.IMPORTED -> displaySettings.showImportedCallsigns
        }
        return measurement.callsign.takeIf { showCallsign && it.isNotBlank() }
    }

    fun importedLocationLabel(
        location: ImportedLocation,
        displaySettings: MapDisplaySettings
    ): String? =
        location.callsign.takeIf { displaySettings.showImportedCallsigns && it.isNotBlank() }

    fun mapNoteLabel(
        note: MapNote,
        displaySettings: MapDisplaySettings
    ): String? =
        note.title.takeIf { displaySettings.showMapNoteTitles && it.isNotBlank() }
}
