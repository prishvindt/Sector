package com.prishvindt.sector.domain.routes

import com.prishvindt.sector.data.RouteType
import com.prishvindt.sector.domain.GeoPoint
import com.prishvindt.sector.domain.RouteTarget
import com.prishvindt.sector.domain.RouteTargetType
import java.util.Locale

object RouteTargetManager {
    fun destination(point: GeoPoint): RouteTarget =
        RouteTarget(
            type = RouteTargetType.DESTINATION,
            point = point,
            title = "Точка назначения",
            subtitle = point.formatCoordinates()
        )

    fun routeEndpoints(start: GeoPoint?, target: RouteTarget?): Result<RouteEndpoints> {
        if (start == null || target == null) {
            return Result.failure(MissingRouteEndpointException())
        }
        return Result.success(RouteEndpoints(start, target))
    }

    fun externalRouteLinks(start: GeoPoint, target: RouteTarget, routeType: RouteType): ExternalRouteLinks {
        val yandexRouteType = if (routeType == RouteType.WALK) "pd" else "auto"
        val rtext = "${start.latitude},${start.longitude}~${target.point.latitude},${target.point.longitude}"
        return ExternalRouteLinks(
            appUri = "yandexmaps://maps.yandex.ru/?rtext=$rtext&rtt=$yandexRouteType",
            webUri = "https://yandex.ru/maps/?rtext=$rtext&rtt=$yandexRouteType"
        )
    }

    private fun GeoPoint.formatCoordinates(): String =
        String.format(Locale.US, "%.6f, %.6f", latitude, longitude)
}

data class RouteEndpoints(
    val start: GeoPoint,
    val target: RouteTarget
)

data class ExternalRouteLinks(
    val appUri: String,
    val webUri: String
)

class MissingRouteEndpointException : IllegalStateException("GPS ещё не найден")
