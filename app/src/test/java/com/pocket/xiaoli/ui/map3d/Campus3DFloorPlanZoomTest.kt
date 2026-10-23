package com.pocket.xiaoli.ui.map3d

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Campus3DFloorPlanZoomTest {

    @Test
    fun `原始比例下平移到原点`() {
        val clamped = clampPlanOffset(
            offset = Offset(60f, -80f),
            scale = 1f,
            viewport = IntSize(1000, 800),
        )

        assertEquals(Offset.Zero, clamped)
    }

    @Test
    fun `放大后平移按超出的半幅夹取`() {
        val clamped = clampPlanOffset(
            offset = Offset(900f, -900f),
            scale = 2f,
            viewport = IntSize(1000, 800),
        )

        assertEquals(Offset(500f, -400f), clamped)
    }

    @Test
    fun `未超出边界的偏移保持不变`() {
        val offset = Offset(100f, 80f)

        val clamped = clampPlanOffset(
            offset = offset,
            scale = 1.5f,
            viewport = IntSize(1000, 800),
        )

        assertEquals(offset, clamped)
    }

    @Test
    fun `视口未测量时不保留偏移`() {
        val clamped = clampPlanOffset(
            offset = Offset(200f, 200f),
            scale = 2f,
            viewport = IntSize.Zero,
        )

        assertEquals(Offset.Zero, clamped)
    }

    @Test
    fun `按钮缩放按四分之一步进并在复位后归零`() {
        val state = PlanZoomState()
        state.attachViewport(IntSize(1000, 800))

        state.zoomIn()
        state.zoomIn()

        assertEquals(1.5f, state.scale, 0.001f)
        assertEquals(150, state.percent)
        assertTrue(state.canZoomOut)
        assertTrue(state.isZoomed)

        repeat(10) { state.zoomIn() }
        assertEquals(3f, state.scale, 0.001f)
        assertFalse(state.canZoomIn)

        state.reset()

        assertEquals(1f, state.scale, 0.001f)
        assertEquals(Offset.Zero, state.offset)
        assertFalse(state.isZoomed)
    }
}
