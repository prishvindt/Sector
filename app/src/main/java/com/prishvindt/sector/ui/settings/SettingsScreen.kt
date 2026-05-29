package com.prishvindt.sector.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.prishvindt.sector.BuildConfig
import com.prishvindt.sector.data.AppSettings
import com.prishvindt.sector.data.CallsignBehavior
import com.prishvindt.sector.data.DestinationMarkerType
import com.prishvindt.sector.data.GpsMode
import com.prishvindt.sector.data.OwnPointColor
import com.prishvindt.sector.data.RouteMode
import com.prishvindt.sector.data.RouteType
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onDismiss: () -> Unit,
    onOwnPointColor: (OwnPointColor) -> Unit,
    onGpsPointScale: (Float) -> Unit,
    onDestinationMarkerType: (DestinationMarkerType) -> Unit,
    onGpsMode: (GpsMode) -> Unit,
    onActiveSearch: (Boolean) -> Unit,
    onAccuracyWarning: (String) -> Unit,
    onShowSelfCallsign: (Boolean) -> Unit,
    onShowImportedCallsigns: (Boolean) -> Unit,
    onCallsignBehavior: (CallsignBehavior) -> Unit,
    onRouteMode: (RouteMode) -> Unit,
    onRouteType: (RouteType) -> Unit,
    onUpdateChecks: (Boolean) -> Unit,
    onCheckUpdates: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(start = 4.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }
                Text(
                    text = "Настройки",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
            }
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                SectionTitle("Внешний вид")
                Text("Тема: следовать системной теме", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                OwnPointColor.entries.forEach { color ->
                    RadioRow(
                        text = color.label,
                        selected = settings.ownPointColor == color,
                        onClick = { onOwnPointColor(color) }
                    )
                }
                Spacer(Modifier.height(8.dp))
                GpsPointScaleSetting(
                    colorArgb = settings.ownPointColor.colorArgb,
                    scale = settings.gpsPointScale,
                    onValueChange = onGpsPointScale
                )
                Spacer(Modifier.height(8.dp))
                DestinationMarkerTypeSetting(
                    selected = settings.destinationMarkerType,
                    onSelect = onDestinationMarkerType
                )
                DividerSpace()

                SectionTitle("Геолокация")
                GpsMode.entries.forEach { mode ->
                    RadioRow(
                        text = mode.label,
                        selected = settings.gpsMode == mode,
                        onClick = { onGpsMode(mode) }
                    )
                }
                SwitchRow(
                    text = "Активный поиск",
                    checked = settings.activeSearchEnabled,
                    onCheckedChange = onActiveSearch
                )
                OutlinedTextField(
                    value = settings.accuracyWarningMeters.toInt().toString(),
                    onValueChange = onAccuracyWarning,
                    singleLine = true,
                    label = { Text("Предупреждать, если точность хуже, м") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                DividerSpace()

                SectionTitle("Отображение позывных")
                CheckboxRow(
                    text = "Показывать мой позывной на карте",
                    checked = settings.showSelfCallsign,
                    onCheckedChange = onShowSelfCallsign
                )
                CheckboxRow(
                    text = "Показывать чужие позывные на карте",
                    checked = settings.showImportedCallsigns,
                    onCheckedChange = onShowImportedCallsigns
                )
                CallsignBehavior.entries.forEach { behavior ->
                    RadioRow(
                        text = behavior.label,
                        selected = settings.callsignBehavior == behavior,
                        onClick = { onCallsignBehavior(behavior) }
                    )
                }
                DividerSpace()

                SectionTitle("Маршруты")
                RouteMode.entries.forEach { mode ->
                    RadioRow(
                        text = mode.label,
                        selected = settings.routeMode == mode,
                        onClick = { onRouteMode(mode) }
                    )
                }
                RouteType.entries.forEach { type ->
                    RadioRow(
                        text = type.label,
                        selected = settings.routeType == type,
                        onClick = { onRouteType(type) }
                    )
                }
                DividerSpace()

                SectionTitle("Обновления")
                CheckboxRow(
                    text = "Проверять обновления",
                    checked = settings.updateChecksEnabled,
                    onCheckedChange = onUpdateChecks
                )
                Text("Текущая версия: ${BuildConfig.APP_VERSION_LABEL}")
                TextButton(onClick = onCheckUpdates) {
                    Text("Проверить обновления")
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
    )
}

@Composable
private fun DividerSpace() {
    Spacer(Modifier.height(8.dp))
    HorizontalDivider()
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun GpsPointScaleSetting(
    colorArgb: Int,
    scale: Float,
    onValueChange: (Float) -> Unit
) {
    val roundedScale = scale.coerceIn(1f, 5f)
    Text(
        text = "размер gps-точки: ${String.format(Locale.US, "%.1fx", roundedScale)}",
        style = MaterialTheme.typography.bodyMedium
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Slider(
            modifier = Modifier.weight(1f),
            value = roundedScale,
            onValueChange = { value ->
                onValueChange((value * 10f).roundToInt() / 10f)
            },
            valueRange = 1f..5f,
            steps = 39
        )
        GpsPointPreview(color = Color(colorArgb), scale = roundedScale)
    }
}

@Composable
private fun GpsPointPreview(color: Color, scale: Float) {
    Canvas(modifier = Modifier.size(76.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val outerRadius = 6.dp.toPx() * scale
        val innerRadius = 3.4.dp.toPx() * scale
        val coreRadius = 1.3.dp.toPx() * scale

        drawCircle(color.copy(alpha = 0.25f), outerRadius, center)
        drawCircle(color, innerRadius, center)
        drawCircle(Color.White, coreRadius, center)
    }
}

@Composable
private fun DestinationMarkerTypeSetting(
    selected: DestinationMarkerType,
    onSelect: (DestinationMarkerType) -> Unit
) {
    Text("маркер точки назначения", style = MaterialTheme.typography.bodyMedium)
    DestinationMarkerType.entries.forEach { type ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = selected == type, onClick = { onSelect(type) })
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected == type, onClick = { onSelect(type) })
            DestinationMarkerPreview(type = type)
            Text(type.label)
        }
    }
}

@Composable
private fun DestinationMarkerPreview(type: DestinationMarkerType) {
    Canvas(
        modifier = Modifier
            .size(42.dp)
            .padding(end = 10.dp)
    ) {
        val color = Color(0xFF9B51E0)
        val center = Offset(size.width / 2f, size.height / 2f)
        val strokeWidth = 2.4.dp.toPx()

        when (type) {
            DestinationMarkerType.POINT -> {
                drawCircle(color.copy(alpha = 0.25f), radius = 14.dp.toPx(), center = center)
                drawCircle(color, radius = 7.5.dp.toPx(), center = center)
                drawCircle(Color.White, radius = 2.6.dp.toPx(), center = center)
            }

            DestinationMarkerType.FLAG -> {
                val poleX = center.x - 5.dp.toPx()
                val top = center.y - 14.dp.toPx()
                val bottom = center.y + 15.dp.toPx()
                drawCircle(color.copy(alpha = 0.18f), radius = 15.dp.toPx(), center = center)
                drawLine(
                    color = color,
                    start = Offset(poleX, top),
                    end = Offset(poleX, bottom),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                val flag = Path().apply {
                    moveTo(poleX, top)
                    lineTo(poleX + 17.dp.toPx(), top + 4.dp.toPx())
                    lineTo(poleX, top + 11.dp.toPx())
                    close()
                }
                drawPath(flag, color)
            }

            DestinationMarkerType.TARGET -> {
                val radius = 13.dp.toPx()
                drawCircle(color.copy(alpha = 0.16f), radius = 16.dp.toPx(), center = center)
                drawCircle(color, radius = radius, center = center, style = Stroke(strokeWidth))
                drawLine(
                    color = color,
                    start = Offset(center.x - radius - 3.dp.toPx(), center.y),
                    end = Offset(center.x + radius + 3.dp.toPx(), center.y),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = color,
                    start = Offset(center.x, center.y - radius - 3.dp.toPx()),
                    end = Offset(center.x, center.y + radius + 3.dp.toPx()),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawCircle(color, radius = 2.3.dp.toPx(), center = center)
            }
        }
    }
}

@Composable
private fun RadioRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text)
    }
}

@Composable
private fun CheckboxRow(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(text)
    }
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
