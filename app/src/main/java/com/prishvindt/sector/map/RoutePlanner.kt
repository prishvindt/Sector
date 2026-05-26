package com.prishvindt.sector.map

import com.prishvindt.sector.data.RouteType
import com.prishvindt.sector.domain.GeoPoint
import com.yandex.mapkit.RequestPoint
import com.yandex.mapkit.RequestPointType
import com.yandex.mapkit.directions.DirectionsFactory
import com.yandex.mapkit.directions.driving.DrivingOptions
import com.yandex.mapkit.directions.driving.DrivingRoute
import com.yandex.mapkit.directions.driving.DrivingRouterType
import com.yandex.mapkit.directions.driving.DrivingSession
import com.yandex.mapkit.directions.driving.VehicleOptions
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.transport.TransportFactory
import com.yandex.mapkit.transport.masstransit.RouteOptions
import com.yandex.mapkit.transport.masstransit.Session
import com.yandex.mapkit.transport.masstransit.TimeOptions
import com.yandex.runtime.Error
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class RoutePlanner {
    suspend fun buildRoute(
        start: GeoPoint,
        finish: GeoPoint,
        type: RouteType
    ): Result<List<GeoPoint>> {
        return when (type) {
            RouteType.CAR -> requestDrivingRoute(start, finish)
            RouteType.WALK -> requestPedestrianRoute(start, finish)
        }
    }

    private suspend fun requestDrivingRoute(
        start: GeoPoint,
        finish: GeoPoint
    ): Result<List<GeoPoint>> = suspendCancellableCoroutine { continuation ->
        var session: DrivingSession? = null
        val listener = object : DrivingSession.DrivingRouteListener {
            override fun onDrivingRoutes(routes: MutableList<DrivingRoute>) {
                val points = routes.firstOrNull()
                    ?.geometry
                    ?.points
                    ?.map { it.toGeoPoint() }
                    .orEmpty()
                continuation.resume(
                    if (points.size >= 2) Result.success(points)
                    else Result.failure(IllegalStateException("Маршрут не построился"))
                )
            }

            override fun onDrivingRoutesError(error: Error) {
                continuation.resume(Result.failure(IllegalStateException("Маршрут не построился")))
            }
        }

        runCatching {
            session = DirectionsFactory.getInstance()
                .createDrivingRouter(DrivingRouterType.ONLINE)
                .requestRoutes(
                    requestPoints(start, finish),
                    DrivingOptions().setRoutesCount(1),
                    VehicleOptions(),
                    listener
                )
        }.onFailure {
            continuation.resume(Result.failure(it))
        }
        continuation.invokeOnCancellation { session?.cancel() }
    }

    private suspend fun requestPedestrianRoute(
        start: GeoPoint,
        finish: GeoPoint
    ): Result<List<GeoPoint>> = suspendCancellableCoroutine { continuation ->
        var session: Session? = null
        val listener = object : Session.RouteListener {
            override fun onMasstransitRoutes(routes: MutableList<com.yandex.mapkit.transport.masstransit.Route>) {
                val points = routes.firstOrNull()
                    ?.geometry
                    ?.points
                    ?.map { it.toGeoPoint() }
                    .orEmpty()
                continuation.resume(
                    if (points.size >= 2) Result.success(points)
                    else Result.failure(IllegalStateException("Маршрут не построился"))
                )
            }

            override fun onMasstransitRoutesError(error: Error) {
                continuation.resume(Result.failure(IllegalStateException("Маршрут не построился")))
            }
        }

        runCatching {
            session = TransportFactory.getInstance()
                .createPedestrianRouter()
                .requestRoutes(
                    requestPoints(start, finish),
                    TimeOptions(),
                    RouteOptions(),
                    listener
                )
        }.onFailure {
            continuation.resume(Result.failure(it))
        }
        continuation.invokeOnCancellation { session?.cancel() }
    }

    private fun requestPoints(start: GeoPoint, finish: GeoPoint): List<RequestPoint> =
        listOf(
            RequestPoint(start.toYandexPoint(), RequestPointType.WAYPOINT, null, null, null),
            RequestPoint(finish.toYandexPoint(), RequestPointType.WAYPOINT, null, null, null)
        )

    private fun GeoPoint.toYandexPoint(): Point = Point(latitude, longitude)

    private fun Point.toGeoPoint(): GeoPoint = GeoPoint(latitude, longitude)
}
