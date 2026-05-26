package com.prishvindt.sector.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prishvindt.sector.domain.GeoMath
import com.prishvindt.sector.domain.GeoPoint
import com.prishvindt.sector.domain.RouteTarget
import com.prishvindt.sector.domain.RouteTargetType

@Composable
fun ExportWarningDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Экспорт содержит координаты") },
        text = {
            Text("Экспорт содержит ваши координаты, азимут и время замера. Отправляйте эти данные только доверенным людям.")
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Понятно") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отменить") } }
    )
}

@Composable
fun BackgroundLocationRationaleDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Активный поиск") },
        text = {
            Text("Для активного поиска нужно разрешение геолокации 'Всегда'. Без него GPS может остановиться после сворачивания приложения.")
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Продолжить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отменить") } }
    )
}

@Composable
fun TargetMenuDialog(
    target: RouteTarget,
    onDismiss: () -> Unit,
    onInAppRoute: () -> Unit,
    onExternalRoute: () -> Unit,
    onCopyCoordinates: () -> Unit,
    onDeleteDestination: (() -> Unit)?
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(target.title) },
        text = {
            Column {
                Text(target.subtitle ?: "${target.point.latitude}, ${target.point.longitude}")
                TextButton(modifier = androidx.compose.ui.Modifier.fillMaxWidth(), onClick = onInAppRoute) {
                    Text("Маршрут внутри приложения")
                }
                TextButton(modifier = androidx.compose.ui.Modifier.fillMaxWidth(), onClick = onExternalRoute) {
                    Text("Открыть в Яндекс.Картах")
                }
                TextButton(modifier = androidx.compose.ui.Modifier.fillMaxWidth(), onClick = onCopyCoordinates) {
                    Text("Скопировать координаты")
                }
                if (target.type == RouteTargetType.DESTINATION && onDeleteDestination != null) {
                    TextButton(modifier = androidx.compose.ui.Modifier.fillMaxWidth(), onClick = onDeleteDestination) {
                        Text("Удалить точку")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DestinationTargetBottomSheet(
    target: RouteTarget,
    currentPosition: GeoPoint?,
    onDismiss: () -> Unit,
    onInAppRoute: () -> Unit,
    onExternalRoute: () -> Unit,
    onCopyCoordinates: () -> Unit,
    onDeleteDestination: () -> Unit
) {
    val coordinates = target.point.formatCoordinates()
    val distance = currentPosition?.let {
        GeoMath.formatDistance(GeoMath.distanceMeters(it, target.point))
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("точка назначения", style = MaterialTheme.typography.titleLarge)
            Text("Координаты: $coordinates", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = distance?.let { "Расстояние от GPS: $it" } ?: "Расстояние от GPS: GPS ещё не найден",
                style = MaterialTheme.typography.bodyMedium
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onInAppRoute
            ) {
                Text("Маршрут внутри приложения")
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onExternalRoute
            ) {
                Text("Открыть в Яндекс.Картах")
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onCopyCoordinates
            ) {
                Text("Скопировать координаты")
            }
            TextButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onDeleteDestination
            ) {
                Text("Удалить точку")
            }
        }
    }
}

private fun GeoPoint.formatCoordinates(): String =
    String.format(java.util.Locale.US, "%.6f, %.6f", latitude, longitude)
