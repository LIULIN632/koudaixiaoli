package com.pocket.xiaoli.ui.timetable.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 课程块边框：日程的"强调色虚线"这条语义的唯一守门人。
 *
 * 之所以是单测而不是截图：边框画在 `drawBehind` 里，`CourseCardContent` 截不到，
 * 而 `PeriodGrid` 整页截图要 `AppContainer`/ViewModel。抽成纯函数后这里能直接断言。
 */
class CourseBlockBorderSpecTest {

    private val border = Color(0xFF123456)
    private val accent = Color(0xFF4D6BFE)

    @Test
    fun `普通课程是实线且用课程边框色`() {
        val spec = courseBlockBorderSpec(
            isAgenda = false,
            courseBorder = border,
            accent = accent,
            isGhost = false,
            borderStyle = 0,
        )
        assertEquals(border, spec.color)
        assertFalse(spec.dashed)
    }

    @Test
    fun `日程是强调色虚线`() {
        val agendaSpec = courseBlockBorderSpec(
            isAgenda = true,
            courseBorder = border,
            accent = accent,
            isGhost = false,
            borderStyle = 0,
        )
        assertTrue(agendaSpec.dashed)
        // 压到 55% 透明：比正文弱，但和课程边框色明显不是同一个色
        assertEquals(accent.copy(alpha = 0.55f), agendaSpec.color)
    }

    @Test
    fun `非本周块本来就是虚线`() {
        assertTrue(
            courseBlockBorderSpec(false, border, accent, isGhost = true, borderStyle = 0).dashed
        )
    }

    @Test
    fun `用户选了虚线边框样式`() {
        assertTrue(
            courseBlockBorderSpec(false, border, accent, isGhost = false, borderStyle = 2).dashed
        )
    }

    @Test
    fun `日程优先于用户边框样式且不改变其他课程`() {
        // borderStyle 不是 2 时，普通课程仍是实线
        assertFalse(
            courseBlockBorderSpec(false, border, accent, isGhost = false, borderStyle = 1).dashed
        )
        // 日程在 borderStyle = 2 下保持强调色（颜色不被用户样式吃掉）
        val agenda = courseBlockBorderSpec(true, border, accent, isGhost = false, borderStyle = 2)
        assertEquals(accent.copy(alpha = 0.55f), agenda.color)
        assertTrue(agenda.dashed)
    }
}
