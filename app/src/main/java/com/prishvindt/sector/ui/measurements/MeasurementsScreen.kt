package com.prishvindt.sector.ui.measurements

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prishvindt.sector.data.Measurement
import com.prishvindt.sector.domain.GeoPoint

@Composable
fun MeasurementsScreen(
    measurements: List<Measurement>,
    currentPosition: GeoPoint?,
    ownColorArgb: Int,
    onDismiss: () -> Unit,
    onDelete: (Measurement) -> Unit,
    onClearAll: () -> Unit,
    onCopyCoordinates: (Measurement) -> Unit,
    onCenter: (Measurement) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Замеры") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
            ) {
                if (measurements.isEmpty()) {
                    Text("Замеров пока нет")
                } else {
                    LazyColumn {
                        items(measurements, key = { it.measurementId }) { measurement ->
                            MeasurementListItem(
                                measurement = measurement,
                                currentPosition = currentPosition,
                                ownColorArgb = ownColorArgb,
                                onDelete = { onDelete(measurement) },
                                onCopyCoordinates = { onCopyCoordinates(measurement) },
                                onCenter = { onCenter(measurement) }
                            )
                            HorizontalDivider()
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row {
                    TextButton(onClick = onClearAll) {
                        Text("Очистить все замеры")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}
