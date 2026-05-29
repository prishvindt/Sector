package com.prishvindt.sector.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.prishvindt.sector.data.Measurement
import com.prishvindt.sector.data.MeasurementSource
import com.prishvindt.sector.domain.GeoMath
import com.prishvindt.sector.domain.GeoPoint
import com.prishvindt.sector.domain.RouteTarget
import com.prishvindt.sector.domain.RouteTargetType
import com.prishvindt.sector.domain.SectorCalculator
import com.prishvindt.sector.location.LocationState
import com.prishvindt.sector.ui.common.MapDisplaySettings
import com.yandex.mapkit.Animation
import com.yandex.mapkit.geometry.Circle
import com.yandex.mapkit.geometry.LinearRing
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.geometry.Polygon
import com.yandex.mapkit.geometry.Polyline
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.IconStyle
import com.yandex.mapkit.map.MapObject
import com.yandex.mapkit.map.MapObjectCollection
import com.yandex.mapkit.map.MapObjectTapListener
import com.yandex.mapkit.map.MapWindow
import com.yandex.runtime.image.ImageProvider

class MapObjectsController(
    private val context: Context,
    private val mapWindow: MapWindow,
    private val onTargetTap: (RouteTarget) -> Unit
) {
    private val map = mapWindow.map
    private val gpsObjects = map.mapObjects.addCollection()
    private val measurementObjects = map.mapObjects.addCollection()
    private val targetObjects = map.mapObjects.addCollection()
    private val routeObjects = map.mapObjects.addCollection()
    private var initialCameraMoved = false
    private var lastFocusNonce = 0L
    private var lastGpsObjectsKey: GpsObjectsKey? = null
    private var lastMeasurementObjectsKey: MeasurementObjectsKey? = null
    private var lastTargetObjectsKey: TargetObjectsKey? = null
    private var lastRouteObjectsKey: RouteObjectsKey? = null

    fun update(
        locationState: LocationState,
        measurements: List<Measurement>,
        intersection: RouteTarget?,
        destination: GeoPoint?,
        routePolyline: List<GeoPoint>,
        cameraFocus: GeoPoint?,
        cameraFocusNonce: Long,
        cameraFocusPreserveZoom: Boolean,
        displaySettings: MapDisplaySettings
    ) {
        map.isRotateGesturesEnabled = false

        if (cameraFocus != null && cameraFocusNonce != lastFocusNonce) {
            val currentCamera = map.cameraPosition
            val zoom = if (cameraFocusPreserveZoom) currentCamera.zoom else 14f
            map.move(
                CameraPosition(cameraFocus.toYandexPoint(), zoom, currentCamera.azimuth, currentCamera.tilt),
                Animation(Animation.Type.SMOOTH, 0.7f),
                null
            )
            lastFocusNonce = cameraFocusNonce
        }

        locationState.point?.let { point ->
            if (!initialCameraMoved) {
                map.move(
                    CameraPosition(point.toYandexPoint(), 12f, 0f, 0f),
                    Animation(Animation.Type.SMOOTH, 0.8f),
                    null
                )
                initialCameraMoved = true
            }
        }

        val gpsObjectsKey = GpsObjectsKey.from(locationState, displaySettings)
        if (gpsObjectsKey != lastGpsObjectsKey) {
            gpsObjects.clear()
            drawGpsObjects(locationState, displaySettings)
            lastGpsObjectsKey = gpsObjectsKey
        }

        val activeMeasurements = measurements.filter { it.active }
        val measurementObjectsKey = MeasurementObjectsKey.from(activeMeasurements, displaySettings)
        if (measurementObjectsKey != lastMeasurementObjectsKey) {
            measurementObjects.clear()
            activeMeasurements.forEach { measurement ->
                drawMeasurement(measurementObjects, measurement, displaySettings)
            }
            lastMeasurementObjectsKey = measurementObjectsKey
        }

        val targetObjectsKey = TargetObjectsKey(intersection, destination)
        if (targetObjectsKey != lastTargetObjectsKey) {
            targetObjects.clear()
            intersection?.let { target ->
                drawTargetMarker(targetObjects, target, MapStyle.INTERSECTION_COLOR)
            }
            destination?.let { point ->
                drawTargetMarker(
                    targetObjects,
                    RouteTarget(
                        type = RouteTargetType.DESTINATION,
                        point = point,
                        title = "Точка назначения",
                        subtitle = "${point.latitude.formatCoord()}, ${point.longitude.formatCoord()}"
                    ),
                    MapStyle.DESTINATION_COLOR
                )
            }
            lastTargetObjectsKey = targetObjectsKey
        }

        val routeObjectsKey = RouteObjectsKey(routePolyline.toList())
        if (routeObjectsKey != lastRouteObjectsKey) {
            routeObjects.clear()
            if (routePolyline.size >= 2) {
                val route = routeObjects.addPolyline(Polyline(routePolyline.map { it.toYandexPoint() }))
                route.setStrokeColor(MapStyle.ROUTE_COLOR)
                route.strokeWidth = 5f
            }
            lastRouteObjectsKey = routeObjectsKey
        }
    }

    private fun drawGpsObjects(
        locationState: LocationState,
        displaySettings: MapDisplaySettings
    ) {
        val point = locationState.point ?: return

        locationState.accuracyMeters?.let { accuracy ->
            val circle = gpsObjects.addCircle(Circle(point.toYandexPoint(), accuracy))
            circle.fillColor = MapStyle.withAlpha(displaySettings.ownPointColor, 44)
            circle.strokeColor = MapStyle.withAlpha(displaySettings.ownPointColor, 130)
            circle.strokeWidth = 1.5f
        }
        drawPlacemark(
            collection = gpsObjects,
            point = point,
            color = displaySettings.ownPointColor,
            label = displaySettings.callsign.takeIf {
                displaySettings.showSelfCallsign && it.isNotBlank()
            },
            target = RouteTarget(
                type = RouteTargetType.SELF,
                point = point,
                title = displaySettings.callsign.ifBlank { "Моя GPS-точка" },
                subtitle = locationState.accuracyMeters?.let { "Точность: ±${it.toInt()} м" }
            )
        )
    }

    private fun drawMeasurement(
        collection: MapObjectCollection,
        measurement: Measurement,
        displaySettings: MapDisplaySettings
    ) {
        val origin = GeoPoint(measurement.latitude, measurement.longitude)
        val color = if (measurement.source == MeasurementSource.SELF) {
            displaySettings.ownPointColor
        } else {
            MapStyle.IMPORTED_COLOR
        }

        val sectorPoints = SectorCalculator.sectorPolygon(
            origin = origin,
            azimuthDeg = measurement.azimuthDeg,
            errorDeg = measurement.azimuthErrorDeg,
            rangeKm = measurement.rangeKm
        ).map { it.toYandexPoint() }
        val polygon = collection.addPolygon(Polygon(LinearRing(sectorPoints), emptyList()))
        polygon.fillColor = MapStyle.withAlpha(color, 44)
        polygon.strokeColor = MapStyle.withAlpha(color, 120)
        polygon.strokeWidth = 1f

        val linePoints = SectorCalculator.centralLine(
            origin = origin,
            azimuthDeg = measurement.azimuthDeg,
            rangeKm = measurement.rangeKm
        ).map { it.toYandexPoint() }
        val line = collection.addPolyline(Polyline(linePoints))
        line.setStrokeColor(color)
        line.strokeWidth = 3f

        val target = RouteTarget(
            type = if (measurement.source == MeasurementSource.SELF) RouteTargetType.SELF else RouteTargetType.IMPORTED,
            point = origin,
            title = measurement.callsign.ifBlank { "Без позывного" },
            subtitle = "Азимут ${measurement.azimuthDeg}° ±${measurement.azimuthErrorDeg}°"
        )
        val showLabel = when (measurement.source) {
            MeasurementSource.SELF -> displaySettings.showSelfCallsign
            MeasurementSource.IMPORTED -> displaySettings.showImportedCallsigns
        }
        drawPlacemark(
            collection = collection,
            point = origin,
            color = color,
            label = measurement.callsign.takeIf { showLabel && it.isNotBlank() },
            target = target
        )
    }

    private fun drawTargetMarker(collection: MapObjectCollection, target: RouteTarget, color: Int) {
        drawPlacemark(collection, target.point, color, target.title, target)
    }

    private fun drawPlacemark(
        collection: MapObjectCollection,
        point: GeoPoint,
        color: Int,
        label: String?,
        target: RouteTarget
    ) {
        val placemark = collection.addPlacemark()
        placemark.geometry = point.toYandexPoint()
        placemark.setIcon(ImageProvider.fromBitmap(markerBitmap(color)))
        label?.let { runCatching { placemark.setText(it) } }
        placemark.addTapListener(object : MapObjectTapListener {
            override fun onMapObjectTap(mapObject: MapObject, point: Point): Boolean {
                onTargetTap(target)
                return true
            }
        })
    }

    private fun markerBitmap(color: Int): Bitmap {
        val size = 44
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = MapStyle.withAlpha(color, 64)
        canvas.drawCircle(size / 2f, size / 2f, 20f, paint)
        paint.color = color
        canvas.drawCircle(size / 2f, size / 2f, 11f, paint)
        paint.color = android.graphics.Color.WHITE
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawCircle(size / 2f, size / 2f, 4f, paint)
        return bitmap
    }

    private fun GeoPoint.toYandexPoint(): Point = Point(latitude, longitude)

    private fun Double.formatCoord(): String = String.format(java.util.Locale.US, "%.6f", this)

    private data class GpsObjectsKey(
        val point: GeoPoint?,
        val accuracyMeters: Float?,
        val ownPointColor: Int,
        val showSelfCallsign: Boolean,
        val callsign: String
    ) {
        companion object {
            fun from(
                locationState: LocationState,
                displaySettings: MapDisplaySettings
            ): GpsObjectsKey = GpsObjectsKey(
                point = locationState.point,
                accuracyMeters = locationState.accuracyMeters,
                ownPointColor = displaySettings.ownPointColor,
                showSelfCallsign = displaySettings.showSelfCallsign,
                callsign = displaySettings.callsign
            )
        }
    }

    private data class MeasurementObjectsKey(
        val measurements: List<MeasurementObjectKey>,
        val ownPointColor: Int,
        val showSelfCallsign: Boolean,
        val showImportedCallsigns: Boolean
    ) {
        companion object {
            fun from(
                activeMeasurements: List<Measurement>,
                displaySettings: MapDisplaySettings
            ): MeasurementObjectsKey = MeasurementObjectsKey(
                measurements = activeMeasurements.map { MeasurementObjectKey.from(it) },
                ownPointColor = displaySettings.ownPointColor,
                showSelfCallsign = displaySettings.showSelfCallsign,
                showImportedCallsigns = displaySettings.showImportedCallsigns
            )
        }
    }

    private data class MeasurementObjectKey(
        val measurementId: String,
        val callsign: String,
        val latitude: Double,
        val longitude: Double,
        val azimuthDeg: Double,
        val azimuthErrorDeg: Double,
        val rangeKm: Double,
        val source: MeasurementSource
    ) {
        companion object {
            fun from(measurement: Measurement): MeasurementObjectKey = MeasurementObjectKey(
                measurementId = measurement.measurementId,
                callsign = measurement.callsign,
                latitude = measurement.latitude,
                longitude = measurement.longitude,
                azimuthDeg = measurement.azimuthDeg,
                azimuthErrorDeg = measurement.azimuthErrorDeg,
                rangeKm = measurement.rangeKm,
                source = measurement.source
            )
        }
    }

    private data class TargetObjectsKey(
        val intersection: RouteTarget?,
        val destination: GeoPoint?
    )

    private data class RouteObjectsKey(
        val routePolyline: List<GeoPoint>
    )
}
