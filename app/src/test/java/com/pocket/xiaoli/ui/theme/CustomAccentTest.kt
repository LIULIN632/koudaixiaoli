package com.pocket.xiaoli.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomAccentTest {

    @Test
    fun presetsAreUnique() {
        assertEquals(CustomAccent.presets.size, CustomAccent.presets.toSet().size)
    }

    @Test
    fun defaultIsZeroAndNotInPresets() {
        assertEquals(0, CustomAccent.DEFAULT)
        assertTrue(CustomAccent.DEFAULT !in CustomAccent.presets)
    }
}
