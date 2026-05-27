package com.prishvindt.sector.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.prishvindt.sector.BuildConfig

@Composable
fun AboutScreen(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("О приложении") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("сектор", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "версия ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AboutSecondaryText
                    )
                }

                Text(
                    text = "картографический инструмент для работы с азимутом, секторами погрешности и маршрутами.",
                    style = MaterialTheme.typography.bodyMedium
                )

                AboutSection("Возможности")
                AboutBullet("отображение текущей gps-точки на карте;")
                AboutBullet("ввод азимута и погрешности;")
                AboutBullet("построение сектора направления;")
                AboutBullet("импорт и экспорт замеров;")
                AboutBullet("расчёт пересечения направлений;")
                AboutBullet("построение маршрута к выбранной точке.")

                AboutSection("Приватность")
                AboutBullet("приложение не отправляет координаты на сторонний сервер приложения;")
                AboutBullet("обмен замерами выполняется вручную пользователем;")
                AboutBullet("api карт используется только для отображения карты и маршрутов.")

                AboutSection("Карты и маршруты")
                Text(
                    text = "карта и маршрутизация работают на базе yandex mapkit.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "условия использования сервисов яндекс.карт: https://yandex.ru/legal/maps_api/",
                    style = MaterialTheme.typography.bodySmall,
                    color = AboutSecondaryText
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        },
        containerColor = AboutContainer,
        titleContentColor = AboutPrimaryText,
        textContentColor = AboutPrimaryText
    )
}

@Composable
private fun AboutSection(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = AboutAccent,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun AboutBullet(text: String) {
    Text(
        text = "• $text",
        style = MaterialTheme.typography.bodyMedium
    )
}

private val AboutContainer = Color(0xFF101418)
private val AboutPrimaryText = Color(0xFFE8ECEA)
private val AboutSecondaryText = Color(0xFFB7C1BC)
private val AboutAccent = Color(0xFF39D98A)
