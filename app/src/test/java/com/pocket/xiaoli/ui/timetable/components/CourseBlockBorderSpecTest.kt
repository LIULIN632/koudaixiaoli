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
    private val warning = Color(0xFFFFB800)
    private val gold = Color(0xFFC08A00)

    /**
     * 带作业的块 = **实线加粗的高亮边框**（2026-10-31 用户：别在课名旁挂感叹号）。
     *
     * 优先级钉在这里：作业压过日程虚线、也压过用户的虚线网格设置 ——
     * "有作业要交"是这一格里最该被看见的一件事。
     */
    @Test
    fun `带作业是实线加粗高亮 压过日程与虚线设置`() {
        val hw = courseBlockBorderSpec(
            isAgenda = true,
            courseBorder = border,
            accent = accent,
            isGhost = false,
            borderStyle = 2,
            hasHomework = true,
            homeworkColor = warning,
        )
        assertEquals(warning, hw.color)
        assertFalse(hw.dashed)
        assertEquals(2f, hw.widthDp, 0.001f)
    }

    /**
     * 逾期/今天截止 = **同一金色加一圈内嵌双线**，不是换第二种颜色。
     *
     * 颜色回答"这是什么"（有作业），形状回答"有多急"。用橙/红两色等于把同一件事
     * 做成两种观感，且红色会把作业报成错误；双线对色觉障碍与深色模式同样成立。
     */
    @Test
    fun `紧急档不换色只加内圈双线`() {
        fun spec(urgent: Boolean) = courseBlockBorderSpec(
            isAgenda = false,
            courseBorder = border,
            accent = accent,
            isGhost = false,
            borderStyle = 0,
            hasHomework = true,
            homeworkUrgent = urgent,
            homeworkColor = gold,
        )
        assertEquals(gold, spec(true).color)
        assertEquals(gold, spec(false).color)
        assertTrue(spec(true).doubleLine)
        assertFalse(spec(false).doubleLine)
        // 两档都是实线加粗，且都要向内让位（否则 2dp 被 clip 吃掉只剩 1dp）
        assertTrue(spec(true).insetStroke)
        assertFalse(spec(true).dashed)
        assertEquals(2f, spec(true).widthDp, 0.001f)
    }

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
