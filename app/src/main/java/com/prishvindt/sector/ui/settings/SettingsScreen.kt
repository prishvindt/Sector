package com.prishvindt.sector.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.prishvindt.sector.BuildConfig
import com.prishvindt.sector.data.AppSettings
import com.prishvindt.sector.data.CallsignBehavior
import com.prishvindt.sector.data.GpsMode
import com.prishvindt.sector.data.OwnPointColor
import com.prishvindt.sector.data.RouteMode
import com.prishvindt.sector.data.RouteType

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onDismiss: () -> Unit,
    onOwnPointColor: (OwnPointColor) -> Unit,
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройки") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
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
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
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
