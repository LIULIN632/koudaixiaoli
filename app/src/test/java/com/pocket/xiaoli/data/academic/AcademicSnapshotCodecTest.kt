package com.pocket.xiaoli.data.academic

import com.google.gson.Gson
import com.pocket.xiaoli.network.AcademicCourseItem
import com.pocket.xiaoli.network.AcademicTypeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「修课情况」快照的序列化与读取。
 *
 * 这一层最容易出的两类事故，各有一条用例钉着：
 * ① **漏给字段默认值** → Gson 走 `UnsafeAllocator`，所有默认值一起失效
 *   （`DormInfo` v1.0.93 崩溃的成因，见 CLAUDE.md）；
 * ② **一处坏 JSON 废掉整份快照** → 用户看到的是"鲤鱼突然说没有培养方案数据"。
 */
class AcademicSnapshotCodecTest {

    private fun type(name: String, id: String, missed: String = "") = AcademicTypeInfo(
        name = name, id = id, missedCredit = missed, requiredCredit = "170", earnedCredit = "95.0",
    )

    private fun course(name: String, typeId: String, status: String = "未修") = AcademicCourseItem(
        typeName = "类型", typeId = typeId, courseName = name, status = status, credit = "2.0",
    )

    @Test
    fun `每个字段都有默认值 —— 少一个就会让整份快照的默认值失效`() {
        // 判据是**语言层面的不变量**：存在无参构造 ⟺ 所有字段都有默认值。
        // 不要改成"喂一段 JSON 看读出来对不对"：测试环境不混淆，反射行为与 release 不一致。
        listOf(
            AcademicSnapshotDto::class.java,
            AcademicSnapshotDto.TypeDto::class.java,
            AcademicSnapshotDto.CourseDto::class.java,
        ).forEach { cls ->
            assertTrue(
                "${cls.simpleName} 必须每个字段都有默认值（否则 Gson 会用 UnsafeAllocator）",
                cls.declaredConstructors.any { it.parameterCount == 0 },
            )
        }
    }

    @Test
    fun `往返一圈数据不丢`() {
        val types = listOf(type("主修", "root"), type("思想政治类课程", "t1", missed = "1.0"))
        val courses = listOf(course("形势与政策", "t1"), course("运筹学A1", "t1", status = "已修"))

        val entity = AcademicSnapshotCodec.encode(types, courses, updatedAt = 1_700_000_000_000)
        val snapshot = AcademicSnapshotCodec.decode(entity)

        assertEquals(types, snapshot?.types)
        assertEquals(courses, snapshot?.courses)
        assertEquals(1_700_000_000_000, snapshot?.updatedAt)
    }

    @Test
    fun `解析层的字段一个都不能少地被存下来`() {
        // 这份是"字段对照表"：解析层加了字段但忘了进 DTO，就会出现
        // "页面上有、鲤鱼答不出来" —— 而那种缺失在界面上看不出来。
        val c = AcademicCourseItem(
            typeName = "通识必修", typeId = "t1", courseName = "思想道德修养与法律基础",
            credit = "2.0", nature = "必修", category = "本科", maxGrade = "83", score = "83",
            gradePoint = "3.3", hours = "理论(2.0)", makeupScore = "55", retakeScore = "62",
            status = "已修", term = "2024-2025 第1学期", suggestedTerm = "2026-2027 第1学期",
        )

        val back = AcademicSnapshotCodec.decode(
            AcademicSnapshotCodec.encode(listOf(type("通识必修", "t1")), listOf(c), 1L)
        )!!.courses.single()

        assertEquals(c, back)
    }

    @Test
    fun `一处坏 JSON 只丢那一半 不让整份快照失效`() {
        val good = AcademicSnapshotCodec.encode(listOf(type("主修", "root")), listOf(course("甲", "root")), 5L)

        val onlyTypes = good.copy(coursesJson = "{ 这不是 JSON")
        val snapshot = AcademicSnapshotCodec.decode(onlyTypes)

        assertEquals(1, snapshot?.types?.size)
        assertTrue(snapshot?.courses?.isEmpty() == true)
    }

    @Test
    fun `空快照读成 null 而不是一堆空对象`() {
        assertNull(AcademicSnapshotCodec.decode(null))
        assertNull(AcademicSnapshotCodec.decode(null))
        assertNull(AcademicSnapshotCodec.decode(com.pocket.xiaoli.data.entity.AcademicSnapshotEntity()))
        // 老版本存的 JSON 里没有新字段 → 读出默认值，不是崩、也不是 null
        val legacy = com.pocket.xiaoli.data.entity.AcademicSnapshotEntity(
            typesJson = """[{"name":"主修","id":"root"}]""",
            coursesJson = "[]",
        )
        assertEquals("主修", AcademicSnapshotCodec.decode(legacy)?.types?.single()?.name)
    }

    @Test
    fun `更新于按相对时间说 太久才报日期`() {
        val now = 1_700_000_000_000L
        val snapshot = AcademicSnapshot(updatedAt = now - 30_000, types = emptyList(), courses = emptyList())
        assertEquals("刚更新", snapshot.updatedLabel(now))
        assertEquals("30 分钟前更新", AcademicSnapshot(now - 30 * 60_000, emptyList(), emptyList()).updatedLabel(now))
        assertEquals("5 小时前更新", AcademicSnapshot(now - 5 * 3_600_000, emptyList(), emptyList()).updatedLabel(now))
        assertEquals("3 天前更新", AcademicSnapshot(now - 3 * 86_400_000, emptyList(), emptyList()).updatedLabel(now))
        // 没查过（updatedAt = 0）→ 不写那句话，而不是说"1970 年更新"
        assertEquals("", AcademicSnapshot(0, emptyList(), emptyList()).updatedLabel(now))
    }

    @Test
    fun `DTO 的 JSON 字段名是稳定契约`() {
        // 断言字段名而不是"能读回来"：改名会让老用户的快照静默丢值
        val json = Gson().toJson(AcademicSnapshotDto.CourseDto(courseName = "甲", suggestedTerm = "2026"))
        assertTrue(json.contains("\"courseName\""))
        assertTrue(json.contains("\"suggestedTerm\""))
    }
}
