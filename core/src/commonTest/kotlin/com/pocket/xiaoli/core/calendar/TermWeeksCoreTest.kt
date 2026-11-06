package com.pocket.xiaoli.core.calendar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TermWeeksCoreTest {

    private val teaching = 18

    @Test
    fun beforeTermIsBefore() {
        assertEquals(TermWeeksCore.Phase.BEFORE, TermWeeksCore.phaseOf(null, teaching))
    }

    @Test
    fun teachingWeeksStayTeaching() {
        assertEquals(TermWeeksCore.Phase.TEACHING, TermWeeksCore.phaseOf(1, teaching))
        assertEquals(TermWeeksCore.Phase.TEACHING, TermWeeksCore.phaseOf(18, teaching))
    }

    @Test
    fun examRangeIsTeachingPlusTwo() {
        assertEquals(19..20, TermWeeksCore.examWeekRange(teaching))
        assertTrue(TermWeeksCore.isExamWeek(19, teaching))
        assertTrue(TermWeeksCore.isExamWeek(20, teaching))
        assertEquals(TermWeeksCore.Phase.EXAM, TermWeeksCore.phaseOf(20, teaching))
    }

    @Test
    fun weekAfterExamIsAfter() {
        assertEquals(TermWeeksCore.Phase.AFTER, TermWeeksCore.phaseOf(21, teaching))
    }

    @Test
    fun progressUsesTeachingPlusExamAsDenominator() {
        // 第 9 周：过完 8 周 / 20 周 = 0.4（旧桌面口径 /26 只有 0.31）
        assertEquals(0.4f, TermWeeksCore.progressFraction(9, teaching))
        assertEquals(0f, TermWeeksCore.progressFraction(null, teaching))
        assertEquals(1f, TermWeeksCore.progressFraction(30, teaching))
        // 考试周进度钉在 18/20
        assertEquals(0.9f, TermWeeksCore.progressFraction(20, teaching))
    }
}
