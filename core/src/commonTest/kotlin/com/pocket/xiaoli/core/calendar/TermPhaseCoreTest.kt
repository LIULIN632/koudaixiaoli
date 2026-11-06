package com.pocket.xiaoli.core.calendar

import com.pocket.xiaoli.core.calendar.TermPhaseCore.Kind
import com.pocket.xiaoli.core.calendar.TermPhaseCore.Phase
import com.pocket.xiaoli.core.calendar.TermPhaseCore.Window
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TermPhaseCoreTest {

    private val term = Window(Kind.OTHER, 100L, 240L) // 假设的学期区间
    private val terms = listOf(term)

    private fun phase(today: Long, events: List<Window> = emptyList(), month: Int = 10) =
        TermPhaseCore.phaseOf(today, terms, events, monthOf = { month })

    @Test
    fun vacationWinsOverExam() {
        val vacation = Window(Kind.VACATION, 300L, 400L, name = "寒假")
        val exam = Window(Kind.EXAM, 280L, 310L, name = "期末考试周")
        assertEquals(Phase.WINTER_VACATION, phase(305L, listOf(vacation, exam)))
    }

    @Test
    fun examWindowSaysExamWeeks() {
        val exam = Window(Kind.EXAM, 241L, 260L, name = "期末考试周")
        assertEquals(Phase.EXAM_WEEKS, phase(250L, listOf(exam)))
    }

    @Test
    fun insideTermIsInTerm() {
        assertEquals(Phase.IN_TERM, phase(200L))
    }

    @Test
    fun outsideEverythingIsBeforeTerm() {
        assertEquals(Phase.BEFORE_TERM, phase(50L))
    }

    @Test
    fun winterOrSummerFromNameThenMonth() {
        assertTrue(TermPhaseCore.isWinter("寒假", 7))
        assertEquals(false, TermPhaseCore.isWinter("暑期实践", 1))
        // 没名字时按月份兜底：1 月算寒假、7 月算暑假
        assertTrue(TermPhaseCore.isWinter("", 1))
        assertEquals(false, TermPhaseCore.isWinter("", 7))
        assertEquals(Phase.SUMMER_VACATION, phase(350L, listOf(Window(Kind.VACATION, 300L, 400L, name = "暑假")), month = 7))
    }
}
