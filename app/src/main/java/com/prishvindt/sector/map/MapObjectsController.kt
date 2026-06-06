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
import com.prishvindt.sector.data.ImportedLocation
import com.prishvindt.sector.data.Measurement
import com.prishvindt.sector.data.MeasurementColor
import com.prishvindt.sector.data.MeasurementSource
import com.prishvindt.sector.domain.GeoMath
import com.prishvindt.sector.domain.GeoPoint
import com.prishvindt.sector.domain.RouteTarget
import com.prishvindt.sector.domain.RouteTargetType
import com.prishvindt.sector.domain.SectorCalculator
import com.prishvindt.sector.domain.notes.MapNote
import com.prishvindt.sector.location.LocationState
import com.prishvindt.sector.ui.common.MapDisplaySettings
import com.yandex.mapkit.Animation
import com.yandex.mapkit.ScreenPoint
import com.yandex.mapkit.ScreenRect
import com.yandex.mapkit.geometry.Circle
import com.yandex.mapkit.geometry.Geometry
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt

class MapObjectsController(
    private val context: Context,
    private val mapWindow: MapWindow,
    private val onTargetTap: (RouteTarget) -> Unit
) {
    private val map = mapWindow.map
    private val gpsObjects = map.mapObjects.addCollection()
    private val measurementObjects = map.mapObjects.addCollection()
    private val importedLocationObjects = map.mapObjects.addCollection()
    private val noteObjects = map.mapObjects.addCollection()
    private val targetObjects = map.mapObjects.addCollection()
    private val routeObjects = map.mapObjects.addCollection()
    private val gpsTapListeners = mutableListOf<MapObjectTapListener>()
    private val measurementTapListeners = mutableListOf<MapObjectTapListener>()
    private val importedLocationTapListeners = mutableListOf<MapObjectTapListener>()
    private val noteTapListeners = mutableListOf<MapObjectTapListener>()
    private val targetTapListeners = mutableListOf<MapObjectTapListener>()
    private var initialCameraMoved = false
    private var lastFocusNonce = 0L
    private var lastRouteFocusNonce = 0L
    private var lastGpsObjectsKey: GpsObjectsKey? = null
    private var lastMeasurementObjectsKey: MeasurementObjectsKey? = null
    private var lastImportedLocationObjectsKey: ImportedLocationObjectsKey? = null
    private var lastNoteObjectsKey: NoteObjectsKey? = null
    private var lastTargetObjectsKey: TargetObjectsKey? = null
    private var lastRouteObjectsKey: RouteObjectsKey? = null

    fun update(
        locationState: LocationState,
        measurements: List<Measurement>,
        importedLocations: List<ImportedLocation>,
        mapNotes: List<MapNote>,
        intersection: RouteTarget?,
        selectedDestination: GeoPoint?,
        routeStartMarker: GeoPoint?,
        routePolyline: List<GeoPoint>,
        activeRouteBuilt: Boolean,
        drawGpsRouteArrow: Boolean,
        routeFocusPolyline: List<GeoPoint>,
        routeFocusNonce: Long,
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

        if (routeFocusNonce != lastRouteFocusNonce && routeFocusPolyline.size >= 2) {
            focusRoute(routeFocusPolyline)
            lastRouteFocusNonce = routeFocusNonce
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

        val gpsObjectsKey = GpsObjectsKey.from(
            locationState = locationState,
            displaySettings = displaySettings,
            drawGpsRouteArrow = drawGpsRouteArrow,
            routePolyline = routePolyline
        )
        if (gpsObjectsKey != lastGpsObjectsKey) {
            gpsObjects.clear()
            gpsTapListeners.clear()
            drawGpsObjects(
                locationState = locationState,
                displaySettings = displaySettings,
                drawGpsRouteArrow = drawGpsRouteArrow,
                routePolyline = routePolyline
            )
            lastGpsObjectsKey = gpsObjectsKey
        }

        val activeMeasurements = measurements.filter(MapObjectVisibilityPolicy::shouldShowMeasurement)
        val measurementObjectsKey = MeasurementObjectsKey.from(activeMeasurements, displaySettings)
        if (measurementObjectsKey != lastMeasurementObjectsKey) {
            measurementObjects.clear()
            measurementTapListeners.clear()
            activeMeasurements.forEach { measurement ->
                drawMeasurement(measurementObjects, measurement, displaySettings)
            }
            lastMeasurementObjectsKey = measurementObjectsKey
        }

        val importedLocationObjectsKey = ImportedLocationObjectsKey.from(importedLocations, displaySettings)
        if (importedLocationObjectsKey != lastImportedLocationObjectsKey) {
            importedLocationObjects.clear()
            importedLocationTapListeners.clear()
            importedLocations.filter(MapObjectVisibilityPolicy::shouldShowImportedLocation).forEach { location ->
                drawImportedLocation(importedLocationObjects, location, displaySettings)
            }
            lastImportedLocationObjectsKey = importedLocationObjectsKey
        }

        val activeNotes = mapNotes.filter { MapObjectVisibilityPolicy.shouldShowMapNote(it, displaySettings) }
        val noteObjectsKey = NoteObjectsKey.from(activeNotes, displaySettings)
        if (noteObjectsKey != lastNoteObjectsKey) {
            noteObjects.clear()
            noteTapListeners.clear()
            activeNotes.forEach { note ->
                drawMapNote(noteObjects, note, displaySettings)
            }
            lastNoteObjectsKey = noteObjectsKey
        }

        val targetObjectsKey = TargetObjectsKey(
            intersection = intersection,
            selectedDestination = selectedDestination,
            routeStartMarker = routeStartMarker,
            destinationMarkerType = displaySettings.destinationMarkerType
        )
        if (targetObjectsKey != lastTargetObjectsKey) {
            targetObjects.clear()
            targetTapListeners.clear()
            // Active route endpoints are intentionally hidden after activation.
            intersection?.let { target ->
                drawTargetMarker(targetObjects, target, MapStyle.INTERSECTION_COLOR)
            }
            routeStartMarker?.let { point ->
                drawRouteStartMarker(targetObjects, point)
            }
            selectedDestination
                ?.takeIf { it != routeStartMarker }
                ?.let { point ->
                    drawDestinationMarker(targetObjects, point, displaySettings.destinationMarkerType)
                }
            lastTargetObjectsKey = targetObjectsKey
        }

        val routeObjectsKey = RouteObjectsKey(routePolyline.toList(), activeRouteBuilt)
        if (routeObjectsKey != lastRouteObjectsKey) {
            routeObjects.clear()
            if (routePolyline.size >= 2) {
                val route = routeObjects.addPolyline(Polyline(routePolyline.map { it.toYandexPoint() }))
                route.setStrokeColor(
                    if (activeRouteBuilt) MapStyle.ROUTE_COLOR else MapStyle.FALLBACK_ROUTE_COLOR
                )
                route.strokeWidth = if (activeRouteBuilt) {
                    MapStyle.ROUTE_STROKE_WIDTH
                } else {
                    MapStyle.FALLBACK_ROUTE_STROKE_WIDTH
                }
            }
            lastRouteObjectsKey = routeObjectsKey
        }
    }

    private fun drawGpsObjects(
        locationState: LocationState,
        displaySettings: MapDisplaySettings,
        drawGpsRouteArrow: Boolean,
        routePolyline: List<GeoPoint>
    ) {
        val point = locationState.point ?: return
        val arrowBearing = if (drawGpsRouteArrow && routePolyline.size >= 2) {
            locationState.bearingDeg
                ?.takeIf { it.isFinite() }
                ?.toDouble()
                ?.let(GeoMath::normalizeBearing)
                ?: nearestRouteSegmentBearing(point, routePolyline)
        } else {
            null
        }

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
            markerShape = if (arrowBearing != null) PlacemarkShape.GPS_ARROW else PlacemarkShape.POINT,
            bearingDeg = arrowBearing,
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
        val color = MeasurementColor.resolve(
            measurement = measurement,
            ownColorArgb = displaySettings.ownPointColor,
            importedDefaultArgb = MapStyle.IMPORTED_COLOR
        )

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
        drawPlacemark(
            collection = collection,
            point = origin,
            color = color,
            label = MapObjectVisibilityPolicy.measurementLabel(measurement, displaySettings),
            tapListeners = measurementTapListeners,
            target = target
        )
    }

    private fun drawImportedLocation(
        collection: MapObjectCollection,
        location: ImportedLocation,
        displaySettings: MapDisplaySettings
    ) {
        val point = GeoPoint(location.latitude, location.longitude)
        val callsign = location.callsign.ifBlank { "Без позывного" }
        drawPlacemark(
            collection = collection,
            point = point,
            color = MapStyle.REMOTE_LOCATION_COLOR,
            label = MapObjectVisibilityPolicy.importedLocationLabel(location, displaySettings),
            tapListeners = importedLocationTapListeners,
            target = RouteTarget(
                type = RouteTargetType.REMOTE_LOCATION,
                point = point,
                title = callsign,
                subtitle = importedLocationSubtitle(location)
            ),
            markerShape = PlacemarkShape.REMOTE_LOCATION
        )
    }

    private fun drawMapNote(
        collection: MapObjectCollection,
        note: MapNote,
        displaySettings: MapDisplaySettings
    ) {
        drawPlacemark(
            collection = collection,
            point = note.point,
            color = MapStyle.MAP_NOTE_COLOR,
            label = MapObjectVisibilityPolicy.mapNoteLabel(note, displaySettings),
            tapListeners = noteTapListeners,
            target = RouteTarget(
                type = RouteTargetType.MAP_NOTE,
                point = note.point,
                title = note.title,
                subtitle = note.text.take(80).takeIf { it.isNotBlank() },
                objectId = note.objectId
            ),
            markerShape = PlacemarkShape.NOTE
        )
    }

    private fun drawTargetMarker(
        collection: MapObjectCollection,
        target: RouteTarget,
        color: Int,
        markerType: DestinationMarkerType = DestinationMarkerType.POINT,
        label: String? = target.title
    ) {
        drawPlacemark(
            collection = collection,
            point = target.point,
            color = color,
            label = label,
            target = target,
            tapListeners = targetTapListeners,
            markerShape = markerType.toPlacemarkShape()
        )
    }

    private fun drawDestinationMarker(
        collection: MapObjectCollection,
        point: GeoPoint,
        markerType: DestinationMarkerType
    ) {
        drawTargetMarker(
            collection = collection,
            target = RouteTarget(
                type = RouteTargetType.DESTINATION,
                point = point,
                title = "Точка назначения",
                subtitle = "${point.latitude.formatCoord()}, ${point.longitude.formatCoord()}"
            ),
            color = MapStyle.DESTINATION_COLOR,
            markerType = markerType,
            label = null
        )
    }

    private fun drawRouteStartMarker(
        collection: MapObjectCollection,
        point: GeoPoint
    ) {
        drawPlacemark(
            collection = collection,
            point = point,
            color = MapStyle.ROUTE_START_COLOR,
            label = null,
            target = null,
            tapListeners = targetTapListeners,
            markerShape = PlacemarkShape.ROUTE_START
        )
    }

    private fun drawPlacemark(
        collection: MapObjectCollection,
        point: GeoPoint,
        color: Int,
        label: String?,
        target: RouteTarget?,
        tapListeners: MutableList<MapObjectTapListener>,
        markerScale: Float = 1f,
        markerShape: PlacemarkShape = PlacemarkShape.POINT,
        bearingDeg: Double? = null
    ) {
        val marker = markerBitmap(
            color = color,
            label = label,
            markerScale = markerScale,
            markerShape = markerShape,
            bearingDeg = bearingDeg
        )
        val placemark = collection.addPlacemark()
        placemark.geometry = point.toYandexPoint()
        placemark.setIcon(
            ImageProvider.fromBitmap(marker.bitmap),
            IconStyle()
                .setAnchor(marker.anchor)
                .setTappableArea(Rect(PointF(0f, 0f), PointF(1f, 1f)))
        )
        if (target != null) {
            val tapListener = object : MapObjectTapListener {
                override fun onMapObjectTap(mapObject: MapObject, point: Point): Boolean {
                    onTargetTap(target)
                    return true
                }
            }
            tapListeners += tapListener
            placemark.addTapListener(tapListener)
        }
    }

    private fun markerBitmap(
        color: Int,
        label: String?,
        markerScale: Float,
        markerShape: PlacemarkShape,
        bearingDeg: Double?
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
            markerShape = markerShape,
            bearingDeg = bearingDeg
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
        markerShape: PlacemarkShape,
        bearingDeg: Double?
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (markerShape) {
            PlacemarkShape.POINT -> {
                paint.style = Style.FILL
                paint.color = MapStyle.withAlpha(color, 64)
                canvas.drawCircle(centerX, centerY, size * 20f / BaseMarkerSize, paint)
                paint.color = color
                canvas.drawCircle(centerX, centerY, size * 11f / BaseMarkerSize, paint)
                paint.color = android.graphics.Color.WHITE
                canvas.drawCircle(centerX, centerY, size * 4f / BaseMarkerSize, paint)
            }

            PlacemarkShape.FLAG -> {
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

            PlacemarkShape.TARGET -> {
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

            PlacemarkShape.GPS_ARROW -> {
                paint.style = Style.FILL
                paint.color = MapStyle.withAlpha(color, 48)
                canvas.drawCircle(centerX, centerY, size * 20f / BaseMarkerSize, paint)

                canvas.save()
                canvas.rotate((bearingDeg ?: 0.0).toFloat(), centerX, centerY)
                paint.color = color
                val arrow = Path().apply {
                    moveTo(centerX, centerY - size * 18f / BaseMarkerSize)
                    lineTo(centerX + size * 13f / BaseMarkerSize, centerY + size * 15f / BaseMarkerSize)
                    lineTo(centerX, centerY + size * 8f / BaseMarkerSize)
                    lineTo(centerX - size * 13f / BaseMarkerSize, centerY + size * 15f / BaseMarkerSize)
                    close()
                }
                canvas.drawPath(arrow, paint)
                paint.color = android.graphics.Color.WHITE
                canvas.drawCircle(centerX, centerY + size * 4f / BaseMarkerSize, size * 2.5f / BaseMarkerSize, paint)
                canvas.restore()
            }

            PlacemarkShape.ROUTE_START -> {
                paint.style = Style.FILL
                paint.color = MapStyle.withAlpha(color, 52)
                canvas.drawCircle(centerX, centerY, size * 17f / BaseMarkerSize, paint)

                paint.style = Style.STROKE
                paint.color = color
                paint.strokeWidth = size * 4f / BaseMarkerSize
                canvas.drawCircle(centerX, centerY, size * 10f / BaseMarkerSize, paint)

                paint.style = Style.FILL
                canvas.drawCircle(centerX, centerY, size * 4f / BaseMarkerSize, paint)
            }

            PlacemarkShape.REMOTE_LOCATION -> {
                paint.style = Style.FILL
                paint.color = MapStyle.withAlpha(color, 52)
                canvas.drawCircle(centerX, centerY, size * 19f / BaseMarkerSize, paint)

                paint.color = color
                val diamond = Path().apply {
                    moveTo(centerX, centerY - size * 16f / BaseMarkerSize)
                    lineTo(centerX + size * 16f / BaseMarkerSize, centerY)
                    lineTo(centerX, centerY + size * 16f / BaseMarkerSize)
                    lineTo(centerX - size * 16f / BaseMarkerSize, centerY)
                    close()
                }
                canvas.drawPath(diamond, paint)
                paint.color = android.graphics.Color.WHITE
                canvas.drawCircle(centerX, centerY, size * 4f / BaseMarkerSize, paint)
            }

            PlacemarkShape.NOTE -> {
                paint.style = Style.FILL
                paint.color = MapStyle.withAlpha(color, 54)
                canvas.drawCircle(centerX, centerY, size * 18f / BaseMarkerSize, paint)

                val noteWidth = size * 24f / BaseMarkerSize
                val noteHeight = size * 27f / BaseMarkerSize
                val left = centerX - noteWidth / 2f
                val top = centerY - noteHeight / 2f
                val right = centerX + noteWidth / 2f
                val bottom = centerY + noteHeight / 2f
                val fold = size * 7f / BaseMarkerSize
                val notePath = Path().apply {
                    moveTo(left, top)
                    lineTo(right, top)
                    lineTo(right, bottom - fold)
                    lineTo(right - fold, bottom)
                    lineTo(left, bottom)
                    close()
                }
                paint.color = color
                canvas.drawPath(notePath, paint)

                paint.color = android.graphics.Color.argb(125, 80, 63, 20)
                val foldPath = Path().apply {
                    moveTo(right, bottom - fold)
                    lineTo(right - fold, bottom)
                    lineTo(right - fold, bottom - fold)
                    close()
                }
                canvas.drawPath(foldPath, paint)

                paint.style = Style.STROKE
                paint.strokeWidth = size * 1.5f / BaseMarkerSize
                paint.strokeCap = Cap.ROUND
                paint.color = android.graphics.Color.WHITE
                val lineLeft = left + size * 5f / BaseMarkerSize
                val lineRight = right - size * 6f / BaseMarkerSize
                canvas.drawLine(
                    lineLeft,
                    top + size * 9f / BaseMarkerSize,
                    lineRight,
                    top + size * 9f / BaseMarkerSize,
                    paint
                )
                canvas.drawLine(
                    lineLeft,
                    top + size * 15f / BaseMarkerSize,
                    lineRight - size * 4f / BaseMarkerSize,
                    top + size * 15f / BaseMarkerSize,
                    paint
                )
            }
        }
    }

    private fun focusRoute(points: List<GeoPoint>) {
        runCatching {
            val width = mapWindow.width()
            val height = mapWindow.height()
            val geometry = Geometry.fromPolyline(Polyline(points.map { it.toYandexPoint() }))
            val camera = if (width > 0 && height > 0) {
                map.cameraPosition(geometry, routeFocusRect(width, height))
            } else {
                map.cameraPosition(geometry)
            }
            map.move(
                CameraPosition(camera.target, camera.zoom, map.cameraPosition.azimuth, map.cameraPosition.tilt),
                Animation(Animation.Type.SMOOTH, 0.7f),
                null
            )
        }.onFailure {
            focusRouteFallback(points)
        }
    }

    private fun routeFocusRect(width: Int, height: Int): ScreenRect {
        val left = 24f
        val top = 96f
        val right = (width - 24).coerceAtLeast(48).toFloat()
        val bottom = (height - 176).coerceAtLeast(128).toFloat()
        return ScreenRect(ScreenPoint(left, top), ScreenPoint(right, bottom))
    }

    private fun focusRouteFallback(points: List<GeoPoint>) {
        val center = boundsCenter(points)
        val zoom = routeFallbackZoom(points, center)
        map.move(
            CameraPosition(center.toYandexPoint(), zoom, map.cameraPosition.azimuth, map.cameraPosition.tilt),
            Animation(Animation.Type.SMOOTH, 0.7f),
            null
        )
    }

    private fun boundsCenter(points: List<GeoPoint>): GeoPoint {
        val minLatitude = points.minOf { it.latitude }
        val maxLatitude = points.maxOf { it.latitude }
        val minLongitude = points.minOf { it.longitude }
        val maxLongitude = points.maxOf { it.longitude }
        return GeoPoint(
            latitude = (minLatitude + maxLatitude) / 2.0,
            longitude = (minLongitude + maxLongitude) / 2.0
        )
    }

    private fun routeFallbackZoom(points: List<GeoPoint>, center: GeoPoint): Float {
        val diameterMeters = points.maxOf { GeoMath.distanceMeters(center, it) } * 2.0
        return when {
            diameterMeters < 200.0 -> 17f
            diameterMeters < 500.0 -> 16f
            diameterMeters < 1_000.0 -> 15f
            diameterMeters < 3_000.0 -> 14f
            diameterMeters < 7_000.0 -> 13f
            diameterMeters < 15_000.0 -> 12f
            diameterMeters < 30_000.0 -> 11f
            diameterMeters < 70_000.0 -> 10f
            diameterMeters < 150_000.0 -> 9f
            else -> 8f
        }
    }

    private fun nearestRouteSegmentBearing(point: GeoPoint, routePolyline: List<GeoPoint>): Double? {
        if (routePolyline.size < 2) return null
        var bestSegment: Pair<GeoPoint, GeoPoint>? = null
        var bestDistance = Double.MAX_VALUE
        for (index in 0 until routePolyline.lastIndex) {
            val start = routePolyline[index]
            val end = routePolyline[index + 1]
            val distance = distanceToSegmentSquared(point, start, end)
            if (distance < bestDistance) {
                bestDistance = distance
                bestSegment = start to end
            }
        }
        return bestSegment
            ?.takeIf { (start, end) -> start != end }
            ?.let { (start, end) -> GeoMath.initialBearing(start, end) }
    }

    private fun distanceToSegmentSquared(point: GeoPoint, start: GeoPoint, end: GeoPoint): Double {
        val latitudeScale = cos(Math.toRadians(point.latitude)).coerceAtLeast(0.01)
        val x = point.longitude * latitudeScale
        val y = point.latitude
        val x1 = start.longitude * latitudeScale
        val y1 = start.latitude
        val x2 = end.longitude * latitudeScale
        val y2 = end.latitude
        val dx = x2 - x1
        val dy = y2 - y1
        if (dx == 0.0 && dy == 0.0) {
            val px = x - x1
            val py = y - y1
            return px * px + py * py
        }
        val t = (((x - x1) * dx + (y - y1) * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
        val projectedX = x1 + t * dx
        val projectedY = y1 + t * dy
        val px = x - projectedX
        val py = y - projectedY
        return px * px + py * py
    }

    private fun importedLocationSubtitle(location: ImportedLocation): String {
        val callsign = location.callsign.ifBlank { "Без позывного" }
        val accuracy = location.accuracyMeters?.let { "\nТочность: ±${it.roundToInt()} м" }.orEmpty()
        return "Позывной: $callsign\n" +
            "Координаты: ${location.latitude.formatCoord()}, ${location.longitude.formatCoord()}\n" +
            "Время: ${formatEpochSeconds(location.timestampEpochSeconds)}" +
            accuracy
    }

    private fun formatEpochSeconds(epochSeconds: Long): String =
        Instant.ofEpochSecond(epochSeconds)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

    private fun DestinationMarkerType.toPlacemarkShape(): PlacemarkShape = when (this) {
        DestinationMarkerType.POINT -> PlacemarkShape.POINT
        DestinationMarkerType.FLAG -> PlacemarkShape.FLAG
        DestinationMarkerType.TARGET -> PlacemarkShape.TARGET
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

    private enum class PlacemarkShape {
        POINT,
        FLAG,
        TARGET,
        GPS_ARROW,
        ROUTE_START,
        REMOTE_LOCATION,
        NOTE
    }

    private data class GpsObjectsKey(
        val point: GeoPoint?,
        val accuracyMeters: Float?,
        val bearingDeg: Float?,
        val ownPointColor: Int,
        val gpsPointScale: Float,
        val showSelfCallsign: Boolean,
        val callsign: String,
        val drawGpsRouteArrow: Boolean,
        val routePolyline: List<GeoPoint>
    ) {
        companion object {
            fun from(
                locationState: LocationState,
                displaySettings: MapDisplaySettings,
                drawGpsRouteArrow: Boolean,
                routePolyline: List<GeoPoint>
            ): GpsObjectsKey = GpsObjectsKey(
                point = locationState.point,
                accuracyMeters = locationState.accuracyMeters,
                bearingDeg = locationState.bearingDeg,
                ownPointColor = displaySettings.ownPointColor,
                gpsPointScale = displaySettings.gpsPointScale,
                showSelfCallsign = displaySettings.showSelfCallsign,
                callsign = displaySettings.callsign,
                drawGpsRouteArrow = drawGpsRouteArrow,
                routePolyline = routePolyline.toList()
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
        val source: MeasurementSource,
        val colorArgb: Int?
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
                source = measurement.source,
                colorArgb = measurement.colorArgb
            )
        }
    }

    private data class ImportedLocationObjectsKey(
        val locations: List<ImportedLocationObjectKey>,
        val showImportedCallsigns: Boolean
    ) {
        companion object {
            fun from(
                locations: List<ImportedLocation>,
                displaySettings: MapDisplaySettings
            ): ImportedLocationObjectsKey =
                ImportedLocationObjectsKey(
                    locations = locations.map { ImportedLocationObjectKey.from(it) },
                    showImportedCallsigns = displaySettings.showImportedCallsigns
                )
        }
    }

    private data class ImportedLocationObjectKey(
        val locationKey: String,
        val callsign: String,
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Double?,
        val timestampEpochSeconds: Long,
        val receivedAtEpochMillis: Long
    ) {
        companion object {
            fun from(location: ImportedLocation): ImportedLocationObjectKey =
                ImportedLocationObjectKey(
                    locationKey = location.locationKey,
                    callsign = location.callsign,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyMeters = location.accuracyMeters,
                    timestampEpochSeconds = location.timestampEpochSeconds,
                    receivedAtEpochMillis = location.receivedAtEpochMillis
                )
        }
    }

    private data class NoteObjectsKey(
        val notes: List<NoteObjectKey>,
        val showMapNotes: Boolean,
        val showMapNoteTitles: Boolean
    ) {
        companion object {
            fun from(
                notes: List<MapNote>,
                displaySettings: MapDisplaySettings
            ): NoteObjectsKey =
                NoteObjectsKey(
                    notes = notes.map { NoteObjectKey.from(it) },
                    showMapNotes = displaySettings.showMapNotes,
                    showMapNoteTitles = displaySettings.showMapNoteTitles
                )
        }
    }

    private data class NoteObjectKey(
        val objectId: String,
        val latitude: Double,
        val longitude: Double,
        val title: String,
        val text: String,
        val updatedAt: Long
    ) {
        companion object {
            fun from(note: MapNote): NoteObjectKey =
                NoteObjectKey(
                    objectId = note.objectId,
                    latitude = note.point.latitude,
                    longitude = note.point.longitude,
                    title = note.title,
                    text = note.text,
                    updatedAt = note.updatedAt
                )
        }
    }

    private data class TargetObjectsKey(
        val intersection: RouteTarget?,
        val selectedDestination: GeoPoint?,
        val routeStartMarker: GeoPoint?,
        val destinationMarkerType: DestinationMarkerType
    )

    private data class RouteObjectsKey(
        val routePolyline: List<GeoPoint>,
        val activeRouteBuilt: Boolean
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
