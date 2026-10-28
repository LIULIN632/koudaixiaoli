package com.pocket.xiaoli.util

import com.pocket.xiaoli.data.entity.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/** 实体上的「生效色」规则（依赖 CourseEntity，故留在 Android 侧）。见共享层 `core/course/CourseColorIndexTest`。 */
class CourseColorEntityTest {

    @Test
    fun `实体上的生效色：未钉住按课名，钉住则用用户选的值`() {
        val auto = CourseEntity(id = 1, name = "高等数学", dayOfWeek = 1, startSlot = 1, endSlot = 2, weeks = "1-16")
        assertEquals(CourseColorIndex.forCourseName("高等数学"), auto.effectiveColorIndex)
        val pinned = auto.copy(colorPinned = true, colorIndex = 7)
        assertEquals(7, pinned.effectiveColorIndex)
    }
}