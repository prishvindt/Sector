package com.prishvindt.sector.ui.firststart

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun FirstStartDialog(
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Сектор 0.1.0 beta") },
        text = {
            Text(
                "Приложение работает локально.\n\n" +
                    "Координаты и замеры не отправляются автоматически.\n\n" +
                    "Экспорт содержит ваши координаты, азимут, погрешность, мощность и время замера. " +
                    "Отправляйте эти данные только доверенным людям.\n\n" +
                    "Для активного поиска в фоне потребуется разрешение геолокации \"Всегда\"."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Понятно")
            }
        }
    )
}
