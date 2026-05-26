package com.prishvindt.sector.map

import com.prishvindt.sector.domain.GeoPoint
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.InputListener
import com.yandex.mapkit.map.Map

class MapTapHandler(
    private val onLongTap: (GeoPoint) -> Unit
) : InputListener {
    override fun onMapTap(map: Map, point: Point) = Unit

    override fun onMapLongTap(map: Map, point: Point) {
        onLongTap(GeoPoint(point.latitude, point.longitude))
    }
}
