package com.prishvindt.sector.domain

enum class RouteTargetType {
    SELF,
    IMPORTED,
    INTERSECTION,
    DESTINATION
}

data class RouteTarget(
    val type: RouteTargetType,
    val point: GeoPoint,
    val title: String,
    val subtitle: String? = null
)
