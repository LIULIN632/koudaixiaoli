package com.pocket.xiaoli.data.knowledge

import com.pocket.xiaoli.data.academic.AcademicSnapshot
import com.pocket.xiaoli.data.entity.SavedGradeEntity
import com.pocket.xiaoli.data.map.RoomCell
import com.pocket.xiaoli.data.map.RoomCellType
import com.pocket.xiaoli.network.AcademicCourseItem
import com.pocket.xiaoli.network.AcademicTypeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantFormattersTest {

    private fun grade(name: String, score: String, gpa: String = "", term: String = "") =
        SavedGradeEntity(courseName = name, totalScore = score, gpa = gpa, termLabel = term)

    private fun room(room: String, building: String = "3 号教学楼", floor: String = "2 层", name: String = "") =
        RoomCell(id = room, campusId = "ytlg_main", building = building, floor = floor, room = room, name = name)

    @Test
    fun gradesWithoutDataGivesActionableHint() {
        val outcome = AssistantFormatters.grades(emptyList(), "我成绩怎么样")
        assertTrue(outcome.text.contains("还没有保存成绩"))
        assertTrue(outcome.text.contains("成绩查询"))
        // 没有数据就**不该有卡片**：界面据此不渲染空卡（否则会出现一张"平均绩点 —"的卡）
        assertNull(outcome.card)
    }

    @Test
    fun gradesMatchedByCourseName() {
        val data = listOf(
            grade("高等数学", "92", "4.0", "2024-2025-1"),
            grade("大学英语", "78", "3.0", "2024-2025-1"),
        )
        val outcome = AssistantFormatters.grades(data, "我高数多少分")
        assertTrue(outcome.text.contains("查到 1 条"))
        assertTrue(outcome.text.contains("高等数学：92 分"))
        assertTrue(outcome.text.contains("2024-2025-1"))

        // 命中课程名 → 卡片是**明细**形态，明细行与文本说的是同一门课、同一个分数
        val card = outcome.card as AssistantCard.Grades
        assertEquals(1, card.rows.size)
        assertEquals("高等数学", card.rows[0].course)
        assertEquals("92", card.rows[0].score)
        assertEquals("2024-2025-1", card.rows[0].term)
        // ⚠️ 与统计**互斥**：问某门课时旁边摆一个"全库均分"，用户会以为那是这门课的均分
        assertNull(card.stats)
    }

    @Test
    fun gradesOverallStatisticsWhenNoCourseMatched() {
        val data = listOf(
            grade("A", "90", "4.0"),
            grade("B", "50", "0"),
        )
        val outcome = AssistantFormatters.grades(data, "我成绩怎么样")
        assertTrue(outcome.text.contains("已存档 2 门"))
        assertTrue(outcome.text.contains("平均分 70.0"))
        assertTrue(outcome.text.contains("平均绩点 2.00"))
        assertTrue(outcome.text.contains("1 门低于 60 分"))

        // 卡片里的数字必须与文本**同源**（同一个 grades 列表、同一次计算）——
        // 这条断言就是"卡片说 2 门、模型说 3 门"那类双口径的守门人
        val stats = (outcome.card as AssistantCard.Grades).stats
        assertNotNull(stats)
        assertEquals(2, stats!!.total)
        assertEquals(70.0, stats.averageScore!!, 0.001)
        assertEquals(2.0, stats.averageGpa!!, 0.001)
        assertEquals(1, stats.failedCount)
        assertTrue((outcome.card as AssistantCard.Grades).rows.isEmpty())
    }

    @Test
    fun roomsWithoutDataGivesActionableHint() {
        val answer = AssistantFormatters.rooms(emptyList(), "A101 在哪")
        assertTrue(answer.contains("还没有教室分布数据"))
    }

    @Test
    fun roomsMatchedByRoomNumber() {
        val cells = listOf(room("A101", name = "多媒体教室"), room("A102"))
        val answer = AssistantFormatters.rooms(cells, "A101 在哪")
        assertTrue(answer.contains("查到 1 个"))
        assertTrue(answer.contains("A101"))
        assertTrue(answer.contains("3 号教学楼"))
        assertTrue(answer.contains("2 层"))
        assertTrue(answer.contains(RoomCellType.CLASSROOM.label))
    }

    @Test
    fun roomsUnmatchedSuggestsMap() {
        val answer = AssistantFormatters.rooms(listOf(room("A101")), "食堂在哪")
        assertTrue(answer.contains("没找到对应教室"))
        assertTrue(answer.contains("教室分布图"))
        assertTrue(answer.contains("地图导航"))
    }

    // ── 培养方案进度（修课情况快照 → 鲤鱼的回答） ──

    private fun academicSnapshot(): AcademicSnapshot = AcademicSnapshot(
        updatedAt = System.currentTimeMillis() - 3 * 3_600_000,
        types = listOf(
            AcademicTypeInfo(
                name = "主修", id = "root",
                requiredCredit = "170", earnedCredit = "95.0", missedCredit = "75.0",
            ),
            AcademicTypeInfo(
                name = "通识必修课程模块", id = "m1", parentId = "root", depth = 1,
                requiredCredit = "45.5", earnedCredit = "39.5", missedCredit = "6.0",
            ),
            AcademicTypeInfo(
                name = "语言与工具类课程", id = "t2", parentId = "m1", depth = 2,
                requiredCredit = "16", earnedCredit = "16", missedCredit = "",
            ),
        ),
        courses = listOf(
            AcademicCourseItem(
                typeName = "通识必修课程模块", typeId = "m1", courseName = "思想道德修养与法律基础",
                credit = "2.0", status = "已修", maxGrade = "83", term = "2024-2025 第1学期",
            ),
            AcademicCourseItem(
                typeName = "通识必修课程模块", typeId = "m1", courseName = "形势与政策",
                credit = "2.0", status = "未修", suggestedTerm = "2026-2027 第1学期",
            ),
        ),
    )

    @Test
    fun academicWithoutSnapshotGivesActionableHint() {
        val outcome = AssistantFormatters.academicProgress(null, "我还差多少学分")
        assertTrue(outcome.text.contains("服务 → 修课情况"))
        // 没有数据就**不该有卡片**（同 grades 那条规矩）
        assertNull(outcome.card)
    }

    @Test
    fun academicOverallAnswerIsConsistentBetweenTextAndCard() {
        val outcome = AssistantFormatters.academicProgress(academicSnapshot(), "我还差多少学分")

        assertTrue(outcome.text.contains("还差 75.0 学分"))
        assertTrue(outcome.text.contains("未修/未过课程 1 门"))
        val card = outcome.card as AssistantCard.AcademicProgress
        // 文本与卡片必须来自同一次取数：这里断言两边的数字一致
        assertEquals("75.0", card.stats?.missedCredit)
        assertEquals(1, card.stats?.untakenCourseCount)
        // 问"还差多少"时，行要给"哪块缺"（缺得最多的排前面）
        assertEquals("通识必修课程模块", card.rows.first().title)
        assertEquals("缺 6.0 学分", card.rows.first().tag)
        assertTrue(card.rows.none { it.title == "语言与工具类课程" })
        // "更新于"是必须的：培养方案每学期会调整，不写就是把旧要求当现行要求
        assertTrue(card.updatedLabel.contains("小时前更新"))
    }

    @Test
    fun academicCourseQuestionSaysTakenOrNotAndWhenToTake() {
        val outcome = AssistantFormatters.academicProgress(academicSnapshot(), "形势与政策修了没")

        assertTrue(outcome.text.contains("形势与政策"))
        assertTrue(outcome.text.contains("未修"))
        assertTrue(outcome.text.contains("建议 2026-2027 第1学期 修"))
        val card = outcome.card as AssistantCard.AcademicProgress
        assertEquals("形势与政策", card.rows.single().title)
        assertEquals("未修", card.rows.single().tag)
        // 问某门课时总进度仍要给（它说的是总进度，不会被误读成这门课的）
        assertEquals("170", card.stats?.requiredCredit)
    }
}
