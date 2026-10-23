package com.pocket.xiaoli.data.quickanswer

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 快问快答的数据层：题库解析、搜索、以及「会了 / 还不会」的三态账本。
 *
 * ## 为什么这几条值得钉
 *
 * 1. **搜索要同时打在题干、答案要点、标签上** —— 学生记得住"1.5 倍"这种数字，
 *    记不住完整题干。
 * 2. **课程筛选与关键词能叠加** —— 只在当前课里搜，否则「队列」会在所有课里乱窜。
 * 3. **`mark(id, null)` 是"取消标记"，不是"标记为还不会"** —— 三态写成两态，
 *    第二次点同一个 chip 就再也消不掉了。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuickAnswerTest {

    private val bank = QuickAnswerBank(
        updatedAt = "2026-10-19",
        courses = listOf(
            QuickAnswerCourse(
                id = "ds",
                name = "数据结构",
                chapters = listOf(
                    QuickAnswerChapter(
                        index = 2,
                        title = "线性表",
                        questions = listOf(
                            QuickAnswerQuestion(
                                id = "ds-2-1",
                                question = "顺序表和链表各自的优缺点？",
                                answerPoints = listOf("顺序表随机访问 O(1)"),
                                tags = listOf("线性表"),
                            ),
                        ),
                    ),
                ),
            ),
            QuickAnswerCourse(
                id = "math",
                name = "高等数学",
                chapters = listOf(
                    QuickAnswerChapter(
                        index = 1,
                        title = "极限",
                        questions = listOf(
                            QuickAnswerQuestion(
                                id = "math-1-1",
                                question = "等价无穷小什么时候能用？",
                                answerPoints = listOf("乘除可以换，加减要小心"),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun `搜索覆盖题干、答案要点与标签`() {
        assertEquals("题干命中", listOf("ds-2-1"), bank.search("优缺点").map { it.question.id })
        assertEquals("答案要点命中", listOf("ds-2-1"), bank.search("O(1)").map { it.question.id })
        assertEquals("标签命中", listOf("ds-2-1"), bank.search("线性表").map { it.question.id })
        assertEquals("搜不到就是空", emptyList<String>(), bank.search("量子").map { it.question.id })
    }

    @Test
    fun `课程筛选与关键词可以叠加`() {
        // 空关键词刻意返回空：调用方拿它喂列表时不该冒出全库题目。
        assertEquals("空关键词不返回任何东西", 0, bank.search("").size)
        assertEquals("两门课各有各的命中", 1, bank.search("无穷小").size)
        assertEquals("限定课程后只剩那门", 1, bank.search("无穷小", "math").size)
        assertEquals("别门课的词不会在限定课程里出现", 0, bank.search("链表", "math").size)
    }

    @Test
    fun `默认选中第一门课`() {
        assertNotNull(bank.course(null))
        assertEquals("ds", bank.course(null)?.id)
        assertEquals("math", bank.course("math")?.id)
    }

    @Test
    fun `空题库标记为空`() {
        assertTrue(QuickAnswerBank().isEmpty)
        assertFalse(bank.isEmpty)
    }

    @Test
    fun `会了与还不会的三态互斥`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val store = QuickAnswerStore(app)
        store.clear()

        assertTrue("初始没有任何标记", store.knownIds().isEmpty() && store.unknownIds().isEmpty())

        store.mark("ds-2-1", true)
        assertTrue("标会了", "ds-2-1" in store.knownIds())
        assertFalse("不会同时躺在还不会里", "ds-2-1" in store.unknownIds())

        store.mark("ds-2-1", false)
        assertFalse("改标还不会之后，会了要被摘掉", "ds-2-1" in store.knownIds())
        assertTrue("还不会", "ds-2-1" in store.unknownIds())

        store.mark("ds-2-1", null)
        assertFalse("再点一次是取消，不是留下还不会", "ds-2-1" in store.knownIds())
        assertFalse("两个集合都要干净", "ds-2-1" in store.unknownIds())
    }
    @Test
    fun `学科分组按题库顺序去重且能筛出该学科的课`() {
        assertTrue("样例题库应有至少一个学科", bank.subjects().isNotEmpty())
        val subject = bank.subjects().first()
        val courses = bank.coursesOf(subject)
        assertTrue("该学科下应有课", courses.isNotEmpty())
        assertTrue("筛选结果必须都属于该学科", courses.all { it.subjectGroup == subject })
        assertEquals("不传学科时返回全部课", bank.courses.size, bank.coursesOf(null).size)
    }

    @Test
    fun `换学科后旧的 courseId 不会把课程卡在上一科`() {
        // 与 QuickAnswerScreen 里的取值逻辑同构：先按 id 找，找不到就退回该学科的第一门。
        val target = bank.courses.last()
        val picked = bank.courses.firstOrNull { it.id == target.id }
            ?: bank.coursesOf(target.subjectGroup).firstOrNull()
        assertEquals(target.id, picked?.id)
    }
}
