package com.prishvindt.sector.ui.map

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.prishvindt.sector.location.LocationState
import com.prishvindt.sector.updates.UpdateStatus

@Composable
fun BoxScope.MapOverlays(
    location: LocationState,
    updateStatus: UpdateStatus,
    onMenuClick: () -> Unit,
    onGpsClick: () -> Unit,
    showRoutePanel: Boolean,
    isSelectingRouteEndPoint: Boolean,
    onCancelRouteEndSelection: () -> Unit,
    onRouteGpsClick: () -> Unit,
    onRouteFitClick: () -> Unit,
    onRouteShareClick: () -> Unit,
    onRouteDeleteClick: () -> Unit,
    onUpdateToggle: () -> Unit,
    onInstallUpdate: () -> Unit,
    onOpenUpdateLink: () -> Unit,
    onHideUpdate: () -> Unit
) {
    MapMenuButton(
        onClick = onMenuClick,
        modifier = Modifier
            .align(Alignment.TopStart)
            .statusBarsPadding()
            .padding(start = 12.dp, top = 12.dp)
    )

    GpsSignalIndicator(
        location = location,
        onClick = onGpsClick,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .statusBarsPadding()
            .padding(end = 12.dp, top = 12.dp)
    )

    UpdateBanner(
        expanded = updateStatus.expanded,
        latestVersion = updateStatus.updateInfo?.latestVersion,
        changelog = updateStatus.updateInfo?.changelog.orEmpty(),
        apkUrl = updateStatus.updateInfo?.apkUrl,
        isDownloading = updateStatus.isDownloading,
        downloadProgress = updateStatus.downloadProgress,
        downloadError = updateStatus.downloadError,
        onToggle = onUpdateToggle,
        onInstall = onInstallUpdate,
        onOpenLink = onOpenUpdateLink,
        onHide = onHideUpdate
    )

    if (isSelectingRouteEndPoint) {
        RouteEndSelectionPanel(
            onCancel = onCancelRouteEndSelection,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 72.dp, start = 16.dp, end = 16.dp)
        )
    }

    if (showRoutePanel) {
        RouteControlPanel(
            onGpsClick = onRouteGpsClick,
            onFitRouteClick = onRouteFitClick,
            onShareClick = onRouteShareClick,
            onDeleteClick = onRouteDeleteClick,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 108.dp, start = 16.dp, end = 16.dp)
        )
    }

    GpsStatusPanel(
        location = location,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = 48.dp, start = 16.dp, end = 16.dp)
    )
}

@Composable
private fun RouteEndSelectionPanel(
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = RoutePanelBackground,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Выберите конечную точку маршрута",
                color = RoutePanelContentColor,
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = onCancel) {
                Text("Отмена", color = RoutePanelContentColor)
            }
        }
    }
}

@Composable
private fun RouteControlPanel(
    onGpsClick: () -> Unit,
    onFitRouteClick: () -> Unit,
    onShareClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = RoutePanelBackground,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoutePanelButton(
                contentDescription = "К себе",
                onClick = onGpsClick
            ) {
                RouteArrowIcon()
            }
            RoutePanelButton(
                contentDescription = "Показать маршрут",
                onClick = onFitRouteClick
            ) {
                RouteLineIcon()
            }
            RoutePanelButton(
                contentDescription = "Поделиться GPS",
                onClick = onShareClick
            ) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = null,
                    tint = RoutePanelContentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            RoutePanelButton(
                contentDescription = "Удалить маршрут",
                onClick = onDeleteClick
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    tint = RoutePanelContentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun RoutePanelButton(
    contentDescription: String,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = Modifier
            .size(40.dp)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = RoutePanelButtonBackgroundAlpha),
        tonalElevation = 1.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}

@Composable
private fun RouteArrowIcon() {
    Canvas(modifier = Modifier.size(22.dp)) {
        val arrow = Path().apply {
            moveTo(size.width / 2f, size.height * 0.08f)
            lineTo(size.width * 0.82f, size.height * 0.88f)
            lineTo(size.width / 2f, size.height * 0.66f)
            lineTo(size.width * 0.18f, size.height * 0.88f)
            close()
        }
        drawPath(arrow, RoutePanelContentColor)
    }
}

@Composable
private fun RouteLineIcon() {
    Canvas(modifier = Modifier.size(24.dp)) {
        val route = Path().apply {
            moveTo(size.width * 0.16f, size.height * 0.76f)
            cubicTo(
                size.width * 0.30f,
                size.height * 0.10f,
                size.width * 0.66f,
                size.height * 0.96f,
                size.width * 0.86f,
                size.height * 0.24f
            )
        }
        drawPath(
            path = route,
            color = RoutePanelContentColor,
            style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
        )
        drawCircle(RoutePanelContentColor, radius = 2.2.dp.toPx(), center = Offset(size.width * 0.16f, size.height * 0.76f))
        drawCircle(RoutePanelContentColor, radius = 2.2.dp.toPx(), center = Offset(size.width * 0.86f, size.height * 0.24f))
    }
}

@Composable
private fun MapMenuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(48.dp),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = MenuButtonBackgroundAlpha),
        tonalElevation = 2.dp
    ) {
        IconButton(onClick = onClick) {
            Icon(Icons.Default.Menu, contentDescription = "Меню")
        }
    }
}

@Composable
private fun GpsStatusPanel(
    location: LocationState,
    modifier: Modifier = Modifier
) {
    val point = location.point
    val satelliteText = "спутники: ${location.satelliteCount?.toString() ?: "н/д"}"
    val accuracyText = location.accuracyMeters
        ?.let { "точность: ±${it.toInt()} м" }
        ?: "точность: н/д"
    val coordinatesText = point
        ?.let { "${it.latitude.formatCoord()}, ${it.longitude.formatCoord()}" }
        ?: "—, —"

    Surface(
        modifier = modifier.widthIn(max = 280.dp),
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFF0B1F16).copy(alpha = GpsPanelBackgroundAlpha)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = satelliteText,
                    color = Color(0xFF39D98A),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = accuracyText,
                    color = Color(0xFF39D98A),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                text = coordinatesText,
                color = Color(0xFF39D98A),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun GpsSignalIndicator(
    location: LocationState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status = location.signalStatus()
    val interactionSource = remember { MutableInteractionSource() }
    val transition = rememberInfiniteTransition(label = "gps-indicator")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 250),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gps-indicator-alpha"
    )
    val lampColor = when (status) {
        GpsSignalStatus.Unknown -> Color(0xFF8A9299)
        GpsSignalStatus.Searching -> Color(0xFF39D98A)
        GpsSignalStatus.Active -> Color(0xFF39D98A)
        GpsSignalStatus.Error -> Color(0xFFFF9F43)
    }
    val lampAlpha = if (status == GpsSignalStatus.Searching) pulseAlpha else 1f
    val description = when (status) {
        GpsSignalStatus.Unknown -> "GPS: состояние неизвестно"
        GpsSignalStatus.Searching -> "GPS: поиск"
        GpsSignalStatus.Active -> "GPS: координаты получены"
        GpsSignalStatus.Error -> "GPS: ошибка или нет разрешения"
    }

    Surface(
        modifier = modifier
            .size(48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .semantics { contentDescription = description },
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = MenuButtonBackgroundAlpha),
        tonalElevation = 2.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(lampColor.copy(alpha = lampAlpha))
            )
        }
    }
}

@Composable
private fun UpdateBanner(
    expanded: Boolean,
    latestVersion: String?,
    changelog: List<String>,
    apkUrl: String?,
    isDownloading: Boolean,
    downloadProgress: Int?,
    downloadError: String?,
    onToggle: () -> Unit,
    onInstall: () -> Unit,
    onOpenLink: () -> Unit,
    onHide: () -> Unit
) {
    if (latestVersion == null || apkUrl == null) return
    val installText = when {
        isDownloading && downloadProgress != null -> "Загрузка $downloadProgress%"
        isDownloading -> "Загрузка..."
        else -> "Установить"
    }
    val updateButtonColors = ButtonDefaults.textButtonColors(
        contentColor = UpdateBannerContentColor,
        disabledContentColor = UpdateBannerContentColor
    )
    Surface(
        modifier = Modifier
            .statusBarsPadding()
            .padding(top = 12.dp, start = 76.dp, end = 76.dp)
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = UpdateBannerBackgroundAlpha),
        contentColor = UpdateBannerContentColor,
        tonalElevation = 2.dp
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = "Доступна версия $latestVersion",
                color = UpdateBannerContentColor,
                style = MaterialTheme.typography.titleSmall
            )
            if (expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    changelog.forEach {
                        Text(
                            text = "• $it",
                            color = UpdateBannerContentColor,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (isDownloading) {
                        Text(
                            text = installText,
                            color = UpdateBannerContentColor,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (downloadError != null) {
                        Text(
                            text = downloadError,
                            color = UpdateBannerContentColor,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = onInstall,
                            enabled = !isDownloading,
                            colors = updateButtonColors
                        ) { Text(installText) }
                        TextButton(
                            onClick = onOpenLink,
                            enabled = !isDownloading,
                            colors = updateButtonColors
                        ) { Text("Открыть ссылку") }
                        TextButton(
                            onClick = onHide,
                            enabled = !isDownloading,
                            colors = updateButtonColors
                        ) { Text("Скрыть") }
                    }
                }
            }
        }
    }
}

private enum class GpsSignalStatus {
    Unknown,
    Searching,
    Active,
    Error
}

private fun LocationState.signalStatus(): GpsSignalStatus = when {
    (!hasPermission && !isSearching) || error != null -> GpsSignalStatus.Error
    !hasPermission -> GpsSignalStatus.Unknown
    point != null -> GpsSignalStatus.Active
    isSearching -> GpsSignalStatus.Searching
    else -> GpsSignalStatus.Unknown
}

private const val MenuButtonBackgroundAlpha = 0.54f
private const val RoutePanelButtonBackgroundAlpha = 0.54f
private const val UpdateBannerBackgroundAlpha = 0.63f
private const val GpsPanelBackgroundAlpha = 0.59f
private val RoutePanelBackground = Color(0xFF15191E).copy(alpha = 0.72f)
private val RoutePanelContentColor = Color(0xFFE8ECEA)
private val UpdateBannerContentColor = Color.White

private fun Double.formatCoord(): String = String.format(java.util.Locale.US, "%.6f", this)
