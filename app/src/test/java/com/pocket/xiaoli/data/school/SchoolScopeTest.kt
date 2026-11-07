package com.pocket.xiaoli.data.school

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pocket.xiaoli.data.database.PocketXiaoliDatabase
import com.pocket.xiaoli.data.entity.CourseEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 「换学校不串数据」的两条守门：
 *
 * 1. **行为**：A 校写入的课，切到 B 校读不到；切回 A 校原样在。
 *    这条是用户能感知的判据，不靠手点。
 * 2. **结构**：四张按校隔离的表，任何 `SELECT` / 全表 `DELETE` 都必须带 `app_state` 条件。
 *    漏一处就会静默串数据，而编译不会报错 —— 只能靠扫源码守。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SchoolScopeTest {

    private lateinit var context: Context
    private lateinit var db: PocketXiaoliDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // 每次跑测试都从干净状态开始：DB 是单例 + 文件，不清会带上别的用例的数据
        context.getSharedPreferences("pocket_xiaoli_prefs", Context.MODE_PRIVATE)
            .edit().putString("academic_school_id", "YIT").commit()
        PocketXiaoliDatabase.closeForTest()
        db = PocketXiaoliDatabase.getInstance(context)
    }

    @After
    fun tearDown() {
        PocketXiaoliDatabase.closeForTest()
        context.deleteDatabase("pocket_xiaoli.db")
    }

    @Test
    fun `另一所学校看不到这所学校的课，切回来还在`() = runBlocking {
        db.appStateDao().setSchoolId("YIT")
        db.courseDao().insert(CourseEntity(name = "高等数学", schemeId = 0))

        val atYit = db.courseDao().getAllCourses().first()
        assertTrue("YIT 应该看得到自己刚写的课", atYit.any { it.name == "高等数学" })
        assertEquals("YIT", atYit.first { it.name == "高等数学" }.schoolId)

        // 切到另一所学校：同一张表、同一份数据，但读不到
        db.appStateDao().setSchoolId("SDUT")
        assertTrue("切校后不应看到别校的课", db.courseDao().getAllCourses().first().isEmpty())
        assertTrue("成绩同理", db.savedGradeDao().getAll().first().isEmpty())
        assertTrue("考试同理", db.examDao().getAll().first().isEmpty())

        // B 校自己写一门，A 校的数据不能被它看到，也不能被它清掉
        db.courseDao().insert(CourseEntity(name = "大学物理", schemeId = 0))
        assertEquals(listOf("大学物理"), db.courseDao().getAllCourses().first().map { it.name })

        // 切回 A 校：原样在，B 校那门不出现
        db.appStateDao().setSchoolId("YIT")
        val back = db.courseDao().getAllCourses().first().map { it.name }
        assertEquals(listOf("高等数学"), back)
    }

    @Test
    fun `清空课表只清本校，不动另一所学校`() = runBlocking {
        db.appStateDao().setSchoolId("YIT")
        db.courseDao().insert(CourseEntity(name = "YIT 的课", schemeId = 0))
        db.appStateDao().setSchoolId("SDUT")
        db.courseDao().insert(CourseEntity(name = "SDUT 的课", schemeId = 0))

        // 在 SDUT 下清空课表：清的是本校
        db.courseDao().deleteAll()
        assertTrue("清空后本校应为空", db.courseDao().getAllCourses().first().isEmpty())

        db.appStateDao().setSchoolId("YIT")
        assertEquals(
            "清空是「清本校」，不是「清全库」—— 否则换一所学校导一次，另一所就没了",
            listOf("YIT 的课"),
            db.courseDao().getAllCourses().first().map { it.name },
        )
    }

    @Test
    fun `按校隔离的表的查询必须带 app_state 条件`() {
        val daoDir = File("src/main/java/com/pocket/xiaoli/data/dao")
        assertTrue("找不到 DAO 目录，测试的工作目录不对：${daoDir.absolutePath}", daoDir.isDirectory)

        val scoped = listOf(
            "courses", "homework", "saved_grades", "exams",
            "terms", "teachers", "academic_snapshot", "special_work_day",
            "campus_map_corrections", "time_slots",
        )
        // 刻意不按校过滤的表：节次表是「上下午几点到几点」这种全校通用的时间刻度，
        // 不含个人数据；而课表网格的行数就是它的行数——一旦按校过滤读空，网格高度变 0，
        // 所有课程会叠在同一格（2026-11-06 的「导入后课程全挤在一起」就是这个）。
        val deviceWide = listOf("time_slots")
        val offenders = mutableListOf<String>()
        daoDir.listFiles { f -> f.name.endsWith(".kt") }?.forEach { file ->
            file.readLines().forEachIndexed { i, line ->
                if (!line.contains("@Query(\"")) return@forEachIndexed
                val sql = line.substringAfter("@Query(\"").substringBefore("\")")
                val touchesScoped = scoped
                    .filterNot { it in deviceWide }
                    .any { t ->
                        sql.contains("FROM $t", ignoreCase = true) ||
                            sql.contains("DELETE FROM $t", ignoreCase = true)
                    }
                if (!touchesScoped) return@forEachIndexed
                // 只有「按主键操作单行」可以不带：id 全局唯一，改不到别校的行。
                // 按 schemeId / 名字 / 时间格删除**都不豁免**——它们能命中别校的同名课。
                val isById = sql.contains("WHERE id = :id", ignoreCase = true)
                if (!isById && !sql.contains("app_state", ignoreCase = true)) {
                    offenders += "${file.name}:${i + 1}  $sql"
                }
            }
        }
        assertTrue(
            "这些查询碰了按校隔离的表却没有 app_state 条件，会读到/删到别校数据：\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }
}
