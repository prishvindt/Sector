package com.prishvindt.sector.ui.measurements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.prishvindt.sector.data.Measurement
import com.prishvindt.sector.data.MeasurementColor
import com.prishvindt.sector.data.MeasurementSource
import com.prishvindt.sector.domain.GeoMath
import com.prishvindt.sector.domain.GeoPoint

@Composable
fun MeasurementListItem(
    measurement: Measurement,
    currentPosition: GeoPoint?,
    ownColorArgb: Int,
    onDelete: () -> Unit,
    onCopyCoordinates: () -> Unit,
    onCenter: () -> Unit
) {
    val point = GeoPoint(measurement.latitude, measurement.longitude)
    val distance = currentPosition?.let { GeoMath.formatDistance(GeoMath.distanceMeters(it, point)) }
    val rayColor = Color(
        MeasurementColor.resolve(
            measurement = measurement,
            ownColorArgb = ownColorArgb
        )
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(10.dp)
                .background(rayColor, CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = measurement.callsign.ifBlank { "Без позывного" },
                style = MaterialTheme.typography.titleSmall,
                color = rayColor
            )
            Text(
                text = "${if (measurement.source == MeasurementSource.SELF) "мой" else "импорт"} · " +
                    "азимут ${measurement.azimuthDeg}° ±${measurement.azimuthErrorDeg}°" +
                    (measurement.signalDbm?.let { " · ${it} dBm" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = rayColor
            )
            Text(
                text = "${measurement.latitude.formatCoord()}, ${measurement.longitude.formatCoord()}" +
                    (distance?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = measurement.timestamp,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onCenter) {
            Icon(Icons.Default.MyLocation, contentDescription = "Центрировать", tint = rayColor)
        }
        IconButton(onClick = onCopyCoordinates) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Копировать координаты")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Удалить")
        }
    }
}

private fun Double.formatCoord(): String = String.format(java.util.Locale.US, "%.6f", this)
