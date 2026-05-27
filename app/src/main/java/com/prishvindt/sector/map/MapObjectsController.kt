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
import com.yandex.mapkit.map.MapObjectTapListener
import com.yandex.mapkit.map.MapWindow
import com.yandex.runtime.image.ImageProvider

class MapObjectsController(
    private val context: Context,
    private val mapWindow: MapWindow,
    private val onTargetTap: (RouteTarget) -> Unit
) {
    private val map = mapWindow.map
    private var initialCameraMoved = false
    private var lastFocusNonce = 0L

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
        map.mapObjects.clear()

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

            locationState.accuracyMeters?.let { accuracy ->
                val circle = map.mapObjects.addCircle(Circle(point.toYandexPoint(), accuracy))
                circle.fillColor = MapStyle.withAlpha(displaySettings.ownPointColor, 44)
                circle.strokeColor = MapStyle.withAlpha(displaySettings.ownPointColor, 130)
                circle.strokeWidth = 1.5f
            }
            drawPlacemark(
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

        measurements.filter { it.active }.forEach { measurement ->
            drawMeasurement(measurement, displaySettings)
        }

        intersection?.let { target ->
            drawTargetMarker(target, MapStyle.INTERSECTION_COLOR)
        }

        destination?.let { point ->
            drawTargetMarker(
                RouteTarget(
                    type = RouteTargetType.DESTINATION,
                    point = point,
                    title = "Точка назначения",
                    subtitle = "${point.latitude.formatCoord()}, ${point.longitude.formatCoord()}"
                ),
                MapStyle.DESTINATION_COLOR
            )
        }

        if (routePolyline.size >= 2) {
            val route = map.mapObjects.addPolyline(Polyline(routePolyline.map { it.toYandexPoint() }))
            route.setStrokeColor(MapStyle.ROUTE_COLOR)
            route.strokeWidth = 5f
        }
    }

    private fun drawMeasurement(
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
        val polygon = map.mapObjects.addPolygon(Polygon(LinearRing(sectorPoints), emptyList()))
        polygon.fillColor = MapStyle.withAlpha(color, 44)
        polygon.strokeColor = MapStyle.withAlpha(color, 120)
        polygon.strokeWidth = 1f

        val linePoints = SectorCalculator.centralLine(
            origin = origin,
            azimuthDeg = measurement.azimuthDeg,
            rangeKm = measurement.rangeKm
        ).map { it.toYandexPoint() }
        val line = map.mapObjects.addPolyline(Polyline(linePoints))
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
            point = origin,
            color = color,
            label = measurement.callsign.takeIf { showLabel && it.isNotBlank() },
            target = target
        )
    }

    private fun drawTargetMarker(target: RouteTarget, color: Int) {
        drawPlacemark(target.point, color, target.title, target)
    }

    private fun drawPlacemark(
        point: GeoPoint,
        color: Int,
        label: String?,
        target: RouteTarget
    ) {
        val placemark = map.mapObjects.addPlacemark()
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
}
