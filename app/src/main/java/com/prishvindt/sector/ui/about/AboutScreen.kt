package com.prishvindt.sector.ui.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Сектор", style = MaterialTheme.typography.titleMedium)
                Text("Версия 0.1.0 beta")
                Text("Package: com.prishvindt.sector")
                Text("Используется Яндекс MapKit")
                Text("Приложение работает локально")
                Text("Координаты и замеры не отправляются автоматически")
                Text("Все замеры хранятся локально на устройстве")
                Text("Экспорт выполняется только вручную пользователем")
                Text("Импорт выполняется только вручную пользователем")
                Text("Приложение не содержит рекламы, аналитики, Firebase, Crashlytics")
                Text("Условия использования Яндекс Карт: https://yandex.ru/legal/maps_api/")
                Text("Приватность", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Приложение не использует серверную синхронизацию. " +
                        "Координаты и замеры хранятся локально на устройстве. " +
                        "Данные передаются другим людям только вручную через экспорт. " +
                        "Приложение использует Яндекс MapKit для отображения карты и построения маршрутов."
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}
