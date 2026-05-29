package com.prishvindt.sector.service

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri

class ExternalActionService(
    private val context: Context
) {
    fun shareText(
        text: String,
        chooserTitle: String = "Экспорт замера",
        clipLabel: String = "Замер Сектор"
    ) {
        copyText(clipLabel, text)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    fun copyText(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun openExternalRoute(appUri: String, webUri: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(appUri)))
        } catch (_: ActivityNotFoundException) {
            openUrl(webUri)
        }
    }

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }
}
