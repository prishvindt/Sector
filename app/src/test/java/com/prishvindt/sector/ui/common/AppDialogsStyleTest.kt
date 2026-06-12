package com.prishvindt.sector.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class AppDialogsStyleTest {
    @Test
    fun exportBackupButtonLabelMentionsZip() {
        assertEquals("Сохранить данные в .zip", EXPORT_BACKUP_BUTTON_LABEL)
    }

    @Test
    fun exportTabsUseDialogBackground() {
        assertEquals(ExportDialogContainerColor, ExportTabsContainerColor)
    }
}
