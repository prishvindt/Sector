package com.prishvindt.sector.ui.common

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorContrastTest {
    @Test
    fun lightBackgroundUsesDarkContent() {
        assertEquals(Color(0xFF111827), contrastingContentColor(Color(0xFFF2C94C)))
    }

    @Test
    fun darkBackgroundUsesWhiteContent() {
        assertEquals(Color.White, contrastingContentColor(Color(0xFF1F2937)))
    }
}
