package com.pocket.xiaoli.ui.home

import com.pocket.xiaoli.data.MockScenario
import com.pocket.xiaoli.data.entity.CourseEntity
import java.time.LocalDate

/**
 * 把开发者实验室选择的 [MockScenario] 应用到首页状态。
 *
 * 纯函数：输入真实 [HomeUiState]，输出可预览的 Mock 状态；[MockScenario.NONE] 原样返回。
 */
object MockHomeState {

    fun apply(state: HomeUiState, scenario: MockScenario): HomeUiState {
        return when (scenario) {
            MockScenario.NONE -> state
            MockScenario.TODAY_HAS_CLASS -> state.copy(
                mockBanner = "Mock 数据：今天有课（仅首页演示）",
                todayCourses = listOf(mockCourse(ongoing = true)),
                nextCourse = mockCourse(ongoing = true),
            )
            MockScenario.TODAY_NO_CLASS -> state.copy(
                mockBanner = "Mock 数据：今天无课（仅首页演示）",
                todayCourses = emptyList(),
                nextCourse = mockCourse(ongoing = false, dayOffset = 1),
            )
            MockScenario.EXAM_WEEK -> state.copy(
                mockBanner = "Mock 数据：考试周（仅首页演示）",
                week = 18,
                todayCourses = emptyList(),
                nextCourse = null,
            )
            MockScenario.NETWORK_ERROR -> state.copy(
                mockBanner = "Mock 数据：网络异常（仅状态演示）",
                todayCourses = emptyList(),
                nextCourse = null,
            )
        }
    }

    private fun mockCourse(ongoing: Boolean, dayOffset: Int = 0): HomeCourse {
        val date = LocalDate.now().plusDays(dayOffset.toLong())
        val course = CourseEntity(
            id = -1_000L - dayOffset,
            name = if (ongoing) "Mock 高等数学" else "Mock 大学英语",
            credits = "3",
            teacher = "张老师",
            room = "A101",
            dayOfWeek = date.dayOfWeek.value,
            startSlot = 1,
            endSlot = 2,
            colorIndex = 1,
        )
        return HomeCourse(
            course = course,
            startTime = "08:00",
            endTime = "09:40",
            dayOffset = dayOffset,
            isOngoing = ongoing,
            isFinished = false,
        )
    }
}
