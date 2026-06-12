package com.prishvindt.sector

import org.junit.Assert.assertEquals
import org.junit.Test

class AppVersionTest {
    @Test
    fun appVersionIsZeroTwoThreeCodeFourteen() {
        assertEquals("0.2.3", BuildConfig.VERSION_NAME)
        assertEquals(14, BuildConfig.VERSION_CODE)
    }
}
