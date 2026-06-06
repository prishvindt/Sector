package com.prishvindt.sector.ui.common

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class SectorThemeTest {
    @Test
    fun sectorColorSchemeIgnoresSystemDarkTheme() {
        val lightSystemScheme = sectorColorScheme(systemDarkTheme = false)
        val darkSystemScheme = sectorColorScheme(systemDarkTheme = true)

        assertEquals(lightSystemScheme, darkSystemScheme)
        assertEquals(Color(0xFFF7F8F8), darkSystemScheme.surface)
        assertEquals(Color(0xFF15191D), darkSystemScheme.onSurface)
    }
}
