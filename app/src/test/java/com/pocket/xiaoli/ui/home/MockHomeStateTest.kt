package com.pocket.xiaoli.ui.home

import com.pocket.xiaoli.data.MockScenario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mock 数据模式：只改首页展示，不改真实数据流。 */
class MockHomeStateTest {

    private val base = HomeUiState(
        school = "测试大学",
        week = 3,
        nextCourse = null,
    )

    @Test
    fun noneKeepsRealState() {
        assertEquals(base, MockHomeState.apply(base, MockScenario.NONE))
    }

    @Test
    fun todayHasClassShowsOngoingMockCourse() {
        val state = MockHomeState.apply(base, MockScenario.TODAY_HAS_CLASS)
        assertNotNull(state.nextCourse)
        assertTrue(state.nextCourse!!.isOngoing)
        assertTrue(state.mockBanner!!.contains("今天有课"))
    }

    @Test
    fun todayNoClassOffsetsToTomorrow() {
        val state = MockHomeState.apply(base, MockScenario.TODAY_NO_CLASS)
        assertTrue(state.todayCourses.isEmpty())
        assertEquals(1, state.nextCourse?.dayOffset)
        assertTrue(state.mockBanner!!.contains("今天无课"))
    }

    @Test
    fun examWeekShowsExamBanner() {
        val state = MockHomeState.apply(base, MockScenario.EXAM_WEEK)
        assertEquals(18, state.week)
        assertNull(state.nextCourse)
        assertTrue(state.mockBanner!!.contains("考试周"))
    }

    @Test
    fun networkErrorShowsErrorBanner() {
        val state = MockHomeState.apply(base, MockScenario.NETWORK_ERROR)
        assertTrue(state.mockBanner!!.contains("网络异常"))
        assertNull(state.nextCourse)
    }
}
