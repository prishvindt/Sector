package com.prishvindt.sector.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint.Align
import android.graphics.Paint.Cap
import android.graphics.Paint.Style
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import com.prishvindt.sector.data.DestinationMarkerType
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
import com.yandex.mapkit.map.Rect
import com.yandex.runtime.image.ImageProvider
import kotlin.math.ceil
import kotlin.math.roundToInt

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
    private val gpsTapListeners = mutableListOf<MapObjectTapListener>()
    private val measurementTapListeners = mutableListOf<MapObjectTapListener>()
    private val targetTapListeners = mutableListOf<MapObjectTapListener>()
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
            gpsTapListeners.clear()
            drawGpsObjects(locationState, displaySettings)
            lastGpsObjectsKey = gpsObjectsKey
        }

        val activeMeasurements = measurements.filter { it.active }
        val measurementObjectsKey = MeasurementObjectsKey.from(activeMeasurements, displaySettings)
        if (measurementObjectsKey != lastMeasurementObjectsKey) {
            measurementObjects.clear()
            measurementTapListeners.clear()
            activeMeasurements.forEach { measurement ->
                drawMeasurement(measurementObjects, measurement, displaySettings)
            }
            lastMeasurementObjectsKey = measurementObjectsKey
        }

        val targetObjectsKey = TargetObjectsKey(
            intersection = intersection,
            destination = destination,
            destinationMarkerType = displaySettings.destinationMarkerType
        )
        if (targetObjectsKey != lastTargetObjectsKey) {
            targetObjects.clear()
            targetTapListeners.clear()
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
                    MapStyle.DESTINATION_COLOR,
                    displaySettings.destinationMarkerType
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
            markerScale = displaySettings.gpsPointScale,
            tapListeners = gpsTapListeners,
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
            tapListeners = measurementTapListeners,
            target = target
        )
    }

    private fun drawTargetMarker(
        collection: MapObjectCollection,
        target: RouteTarget,
        color: Int,
        markerType: DestinationMarkerType = DestinationMarkerType.POINT
    ) {
        drawPlacemark(
            collection = collection,
            point = target.point,
            color = color,
            label = target.title,
            target = target,
            tapListeners = targetTapListeners,
            markerType = markerType
        )
    }

    private fun drawPlacemark(
        collection: MapObjectCollection,
        point: GeoPoint,
        color: Int,
        label: String?,
        target: RouteTarget,
        tapListeners: MutableList<MapObjectTapListener>,
        markerScale: Float = 1f,
        markerType: DestinationMarkerType = DestinationMarkerType.POINT
    ) {
        val marker = markerBitmap(
            color = color,
            label = label,
            markerScale = markerScale,
            markerType = markerType
        )
        val placemark = collection.addPlacemark()
        placemark.geometry = point.toYandexPoint()
        placemark.setIcon(
            ImageProvider.fromBitmap(marker.bitmap),
            IconStyle()
                .setAnchor(marker.anchor)
                .setTappableArea(Rect(PointF(0f, 0f), PointF(1f, 1f)))
        )
        val tapListener = object : MapObjectTapListener {
            override fun onMapObjectTap(mapObject: MapObject, point: Point): Boolean {
                onTargetTap(target)
                return true
            }
        }
        tapListeners += tapListener
        placemark.addTapListener(tapListener)
    }

    private fun markerBitmap(
        color: Int,
        label: String?,
        markerScale: Float,
        markerType: DestinationMarkerType
    ): MarkerBitmap {
        val size = (BaseMarkerSize * markerScale.coerceIn(1f, 5f)).roundToInt()
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.WHITE
            textSize = LabelTextSize
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Align.CENTER
        }
        val labelText = label
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.ellipsize(textPaint, MaxLabelTextWidth.toFloat())
        val labelWidth = labelText?.let { ceil(textPaint.measureText(it)).toInt() } ?: 0
        val capsuleWidth = if (labelText == null) 0 else labelWidth + LabelHorizontalPadding * 2
        val labelBlockHeight = if (labelText == null) 0 else LabelGap + LabelCapsuleHeight
        val bitmapWidth = maxOf(size, capsuleWidth).coerceAtLeast(1)
        val bitmapHeight = (size + labelBlockHeight).coerceAtLeast(1)
        val markerCenterX = bitmapWidth / 2f
        val markerCenterY = size / 2f
        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawMarkerShape(
            canvas = canvas,
            centerX = markerCenterX,
            centerY = markerCenterY,
            size = size,
            color = color,
            markerType = markerType
        )

        if (labelText != null) {
            val capsuleLeft = (bitmapWidth - capsuleWidth) / 2f
            val capsuleTop = size + LabelGap.toFloat()
            val capsuleRect = RectF(
                capsuleLeft,
                capsuleTop,
                capsuleLeft + capsuleWidth,
                capsuleTop + LabelCapsuleHeight
            )
            val capsulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = android.graphics.Color.argb(205, 31, 36, 42)
            }
            canvas.drawRoundRect(capsuleRect, LabelCapsuleHeight / 2f, LabelCapsuleHeight / 2f, capsulePaint)

            val baseline = capsuleTop + LabelCapsuleHeight / 2f -
                (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(labelText, bitmapWidth / 2f, baseline, textPaint)
        }

        return MarkerBitmap(
            bitmap = bitmap,
            anchor = PointF(markerCenterX / bitmapWidth, markerCenterY / bitmapHeight)
        )
    }

    private fun drawMarkerShape(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        size: Int,
        color: Int,
        markerType: DestinationMarkerType
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (markerType) {
            DestinationMarkerType.POINT -> {
                paint.style = Style.FILL
                paint.color = MapStyle.withAlpha(color, 64)
                canvas.drawCircle(centerX, centerY, size * 20f / BaseMarkerSize, paint)
                paint.color = color
                canvas.drawCircle(centerX, centerY, size * 11f / BaseMarkerSize, paint)
                paint.color = android.graphics.Color.WHITE
                canvas.drawCircle(centerX, centerY, size * 4f / BaseMarkerSize, paint)
            }

            DestinationMarkerType.FLAG -> {
                paint.style = Style.FILL
                paint.color = MapStyle.withAlpha(color, 46)
                canvas.drawCircle(centerX, centerY, size * 18f / BaseMarkerSize, paint)

                paint.color = color
                paint.strokeWidth = size * 3.2f / BaseMarkerSize
                paint.strokeCap = Cap.ROUND
                val poleX = centerX - size * 5f / BaseMarkerSize
                val top = centerY - size * 15f / BaseMarkerSize
                val bottom = centerY + size * 16f / BaseMarkerSize
                canvas.drawLine(poleX, top, poleX, bottom, paint)

                paint.style = Style.FILL
                val flag = Path().apply {
                    moveTo(poleX, top)
                    lineTo(poleX + size * 18f / BaseMarkerSize, top + size * 4f / BaseMarkerSize)
                    lineTo(poleX, top + size * 12f / BaseMarkerSize)
                    close()
                }
                canvas.drawPath(flag, paint)
            }

            DestinationMarkerType.TARGET -> {
                paint.style = Style.FILL
                paint.color = MapStyle.withAlpha(color, 44)
                canvas.drawCircle(centerX, centerY, size * 19f / BaseMarkerSize, paint)

                paint.style = Style.STROKE
                paint.color = color
                paint.strokeWidth = size * 3f / BaseMarkerSize
                paint.strokeCap = Cap.ROUND
                val radius = size * 13f / BaseMarkerSize
                canvas.drawCircle(centerX, centerY, radius, paint)
                canvas.drawLine(centerX - radius - size * 4f / BaseMarkerSize, centerY, centerX + radius + size * 4f / BaseMarkerSize, centerY, paint)
                canvas.drawLine(centerX, centerY - radius - size * 4f / BaseMarkerSize, centerX, centerY + radius + size * 4f / BaseMarkerSize, paint)

                paint.style = Style.FILL
                canvas.drawCircle(centerX, centerY, size * 2.6f / BaseMarkerSize, paint)
            }
        }
    }

    private fun GeoPoint.toYandexPoint(): Point = Point(latitude, longitude)

    private fun Double.formatCoord(): String = String.format(java.util.Locale.US, "%.6f", this)

    private fun String.ellipsize(paint: Paint, maxWidth: Float): String {
        if (paint.measureText(this) <= maxWidth) return this
        val ellipsis = "..."
        var endIndex = length
        while (endIndex > 0 && paint.measureText(take(endIndex) + ellipsis) > maxWidth) {
            endIndex--
        }
        return if (endIndex > 0) take(endIndex) + ellipsis else ellipsis
    }

    private data class MarkerBitmap(
        val bitmap: Bitmap,
        val anchor: PointF
    )

    private data class GpsObjectsKey(
        val point: GeoPoint?,
        val accuracyMeters: Float?,
        val ownPointColor: Int,
        val gpsPointScale: Float,
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
                gpsPointScale = displaySettings.gpsPointScale,
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
        val destination: GeoPoint?,
        val destinationMarkerType: DestinationMarkerType
    )

    private data class RouteObjectsKey(
        val routePolyline: List<GeoPoint>
    )

    private companion object {
        const val BaseMarkerSize = 44
        const val LabelGap = 6
        const val LabelCapsuleHeight = 32
        const val LabelHorizontalPadding = 12
        const val LabelTextSize = 20f
        const val MaxLabelTextWidth = 220
    }
}
