package com.pocket.xiaoli.util

import com.google.gson.Gson
import com.pocket.xiaoli.data.entity.CourseEntity
import com.pocket.xiaoli.data.model.CourseBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 课表导入的固定样例集。
 *
 * 对应发布门禁清单「测试课表导入、导出与数据迁移」：
 * **正常文件 / 空文件 / 超大文件 / 字段缺失 / 旧版本数据** 五类样例，
 * 以及「导入 → 导出 → 再导入，字段一致」。
 *
 * 这些断言全部打在 [CourseFileParsers] 上 —— 它们是纯函数，
 * 不需要 `Uri`、`ContentResolver` 或真机，所以这五类样例才第一次真的跑得起来。
 */
class CourseFileParsersTest {

    private val gson = Gson()

    private fun course(
        name: String,
        teacher: String = "",
        room: String = "",
        day: Int = 1,
        start: Int = 1,
        end: Int = 2,
        weeks: String = ""
    ) = CourseEntity(
        name = name,
        teacher = teacher,
        room = room,
        dayOfWeek = day,
        startSlot = start,
        endSlot = end,
        weeks = weeks,
        schemeId = 0L
    )

    // ───────────────────────────────────────────────
    //  样例一：正常文件
    // ───────────────────────────────────────────────

    private val normalCsv = """
        课程名称,教师,教室,星期,开始节次,结束节次,周次
        高等数学,张老师,A101,1,1,2,1-16
        大学英语,李老师,B202,3,3,4,1-18
    """.trimIndent()

    @Test
    fun `正常 CSV 解析出全部课程且字段逐个对得上`() {
        val parsed = CourseFileParsers.csv(normalCsv, 0L)
        assertEquals(2, parsed.size)
        assertEquals(course("高等数学", "张老师", "A101", 1, 1, 2, "1-16"), parsed[0])
        assertEquals(course("大学英语", "李老师", "B202", 3, 3, 4, "1-18"), parsed[1])
    }

    @Test
    fun `没有表头的裸 CSV 第一行也当数据行`() {
        val parsed = CourseFileParsers.csv("高等数学,张老师,A101,1,1,2,1-16", 0L)
        assertEquals(1, parsed.size)
        assertEquals("高等数学", parsed[0].name)
    }

    @Test
    fun `带引号的课程名里的逗号不会被拆成两列`() {
        val parsed = CourseFileParsers.csv("\"思想道德与法治(含,实践)\",王老师,C1,2,5,6,1-8", 0L)
        assertEquals(1, parsed.size)
        assertEquals("思想道德与法治(含,实践)", parsed[0].name)
        assertEquals("王老师", parsed[0].teacher)
        assertEquals(2, parsed[0].dayOfWeek)
        assertEquals("1-8", parsed[0].weeks)
    }

    // ───────────────────────────────────────────────
    //  样例二：空文件
    // ───────────────────────────────────────────────

    @Test
    fun `空文件解析为空列表而不是抛异常`() {
        assertTrue(CourseFileParsers.csv("", 0L).isEmpty())
        assertTrue(CourseFileParsers.html("", 0L).isEmpty())
        assertTrue(CourseFileParsers.xlsx("", "", 0L).isEmpty())
        assertTrue(CourseFileParsers.backup("", 0L).isEmpty())
    }

    @Test
    fun `只有表头没有数据行时解析为空`() {
        assertTrue(CourseFileParsers.csv("课程名称,教师,教室,星期,开始节次,结束节次,周次", 0L).isEmpty())
    }

    @Test
    fun `只有空行的文件解析为空`() {
        assertTrue(CourseFileParsers.csv("\n\n   \n", 0L).isEmpty())
    }

    // ───────────────────────────────────────────────
    //  样例三：超大文件
    // ───────────────────────────────────────────────

    @Test
    fun `一千行的 CSV 一门课都不会丢`() {
        val rows = (1..1000).joinToString("\n") { i -> "课程$i,老师$i,R$i,${i % 7 + 1},1,2,1-16" }
        val parsed = CourseFileParsers.csv("课程名称,教师,教室,星期,开始节次,结束节次,周次\n$rows", 0L)
        assertEquals(1000, parsed.size)
        assertEquals("课程1", parsed.first().name)
        assertEquals("课程1000", parsed.last().name)
    }

    @Test
    fun `超大文件里的坏行被跳过但不影响其余行`() {
        val rows = (1..50).joinToString("\n") { i -> "课程$i,老师,R,1,1,2,1-16" }
        val parsed = CourseFileParsers.csv(
            "课程名称,教师,教室,星期,开始节次,结束节次,周次\n$rows\n坏行,老师,R,周三,一,二,1-16\n课程51,老师,R,1,1,2,1-16",
            0L
        )
        assertEquals("坏行必须被跳过而不是污染结果", 51, parsed.size)
    }

    // ───────────────────────────────────────────────
    //  样例四：字段缺失
    // ───────────────────────────────────────────────

    @Test
    fun `列数不足的行被跳过`() {
        assertTrue(CourseFileParsers.csv("高等数学,张老师,A101", 0L).isEmpty())
        assertTrue(CourseFileParsers.csv("高等数学,张老师,A101,1,1", 0L).isEmpty())
    }

    @Test
    fun `缺少周次列时仍能导入，周次留空`() {
        val parsed = CourseFileParsers.csv("高等数学,张老师,A101,1,1,2", 0L)
        assertEquals(1, parsed.size)
        assertEquals("", parsed[0].weeks)
    }

    @Test
    fun `星期或节次不是数字的行被跳过`() {
        assertTrue(CourseFileParsers.csv("高等数学,张老师,A101,周一,1,2,1-16", 0L).isEmpty())
        assertTrue(CourseFileParsers.csv("高等数学,张老师,A101,1,一节,2,1-16", 0L).isEmpty())
    }

    @Test
    fun `整行缺字段不会让别的行一起失败`() {
        val parsed = CourseFileParsers.csv(
            "课程名称,教师,教室,星期,开始节次,结束节次,周次\n坏行,只有,两列\n高等数学,张老师,A101,1,1,2,1-16",
            0L
        )
        assertEquals(1, parsed.size)
        assertEquals("高等数学", parsed[0].name)
    }

    // ───────────────────────────────────────────────
    //  HTML / xlsx
    // ───────────────────────────────────────────────

    private val normalHtml = """
        <table>
          <tr><td>课程名称</td><td>教师</td><td>教室</td><td>星期</td><td>开始节次</td><td>结束节次</td><td>周次</td></tr>
          <tr><td>高等数学</td><td>张老师</td><td>A101</td><td>1</td><td>1</td><td>2</td><td>1-16</td></tr>
          <tr><td>大学英语</td><td>李老师</td><td>B202</td><td>3</td><td>3</td><td>4</td><td>1-18</td></tr>
        </table>
    """.trimIndent()

    @Test
    fun `HTML 表格跳过表头行并按同样字段解析`() {
        val parsed = CourseFileParsers.html(normalHtml, 0L)
        assertEquals(2, parsed.size)
        assertEquals(course("高等数学", "张老师", "A101", 1, 1, 2, "1-16"), parsed[0])
        assertEquals(course("大学英语", "李老师", "B202", 3, 3, 4, "1-18"), parsed[1])
    }

    @Test
    fun `HTML 里成对的标签被剥掉，只留文字`() {
        val parsed = CourseFileParsers.html(
            "<tr><td>x</td></tr><tr><td><b>高等数学</b></td><td>张老师</td><td>A101</td><td>1</td><td>1</td><td>2</td><td>1-16</td></tr>",
            0L
        )
        assertEquals("高等数学", parsed[0].name)
    }

    @Test
    fun `xlsx 的 sharedStrings 与 sheet1 组合能解析出课程`() {
        val shared = "<sst><si><t>课程名称</t></si><si><t>高等数学</t></si><si><t>张老师</t></si>" +
            "<si><t>A101</t></si><si><t>1-16</t></si></sst>"
        val sheet = "<worksheet><sheetData>" +
            "<row><c t=\"s\"><v>0</v></c><c t=\"s\"><v>0</v></c><c t=\"s\"><v>0</v></c>" +
            "<c><v>1</v></c><c><v>1</v></c><c><v>2</v></c><c t=\"s\"><v>0</v></c></row>" +
            "<row><c t=\"s\"><v>1</v></c><c t=\"s\"><v>2</v></c><c t=\"s\"><v>3</v></c>" +
            "<c><v>1</v></c><c><v>1</v></c><c><v>2</v></c><c t=\"s\"><v>4</v></c></row>" +
            "</sheetData></worksheet>"
        val parsed = CourseFileParsers.xlsx(shared, sheet, 0L)
        assertEquals(1, parsed.size)
        assertEquals("高等数学", parsed[0].name)
        assertEquals("张老师", parsed[0].teacher)
        assertEquals("A101", parsed[0].room)
        assertEquals("1-16", parsed[0].weeks)
    }

    @Test
    fun `xlsx 里没有 sheet1 时解析为空`() {
        assertTrue(CourseFileParsers.xlsx("<sst/>", "", 0L).isEmpty())
    }

    // ───────────────────────────────────────────────
    //  样例五：旧版本数据（迁移）
    // ───────────────────────────────────────────────

    @Test
    fun `当前版本的备份导出后能原样读回来`() {
        val source = listOf(
            course("高等数学", "张老师", "A101", 1, 1, 2, "1-16"),
            course("大学英语", "李老师", "B202", 3, 3, 4, "1-18")
        )
        val json = gson.toJson(CourseBackup(version = 1, exportTime = "2026-10-16 10:00", school = "烟台理工学院", courses = source))
        assertEquals(source, CourseFileParsers.backup(json, 0L))
    }

    @Test
    fun `缺 version exportTime school 的老备份照样能读出来`() {
        val json = """{"courses":[{"name":"高等数学","teacher":"张老师","room":"A101","dayOfWeek":1,"startSlot":1,"endSlot":2,"weeks":"1-16"}]}"""
        val parsed = CourseFileParsers.backup(json, 0L)
        assertEquals(1, parsed.size)
        assertEquals("高等数学", parsed[0].name)
        assertEquals(0, CourseFileParsers.backupVersion(json))
    }

    @Test
    fun `version 为 0 的老备份仍能读出来`() {
        val json = """{"version":0,"courses":[{"name":"大学英语","dayOfWeek":3,"startSlot":3,"endSlot":4}]}"""
        assertEquals(1, CourseFileParsers.backup(json, 0L).size)
        assertEquals(0, CourseFileParsers.backupVersion(json))
    }

    @Test
    fun `备份里课程字段名变了也不会崩，缺什么补什么`() {
        val json = """{"version":2,"courses":[{"name":"大学英语","dayOfWeek":3,"startSlot":3,"endSlot":4}]}"""
        val parsed = CourseFileParsers.backup(json, 0L)
        assertEquals(1, parsed.size)
        assertEquals("", parsed[0].teacher)
        assertEquals("", parsed[0].room)
        assertEquals(2, CourseFileParsers.backupVersion(json))
    }

    @Test
    fun `损坏的 JSON 返回空列表而不是抛异常`() {
        assertTrue(CourseFileParsers.backup("{不是json", 0L).isEmpty())
        assertTrue(CourseFileParsers.backup("[1,2,3]", 0L).isEmpty())
        assertTrue(CourseFileParsers.backup("""{"version":1}""", 0L).isEmpty())
        assertTrue(CourseFileParsers.backup("""{"courses":"不是数组"}""", 0L).isEmpty())
    }

    // ───────────────────────────────────────────────
    //  导入 → 导出 → 再导入：字段一致 + 幂等
    // ───────────────────────────────────────────────

    @Test
    fun `导入 导出 再导入，字段完全一致`() {
        val firstImport = CourseFileParsers.csv(normalCsv, 0L)

        // 导出（ProfileDialogHost 的完整备份走的就是 CourseBackup + Gson）
        val exported = gson.toJson(CourseBackup(courses = firstImport))
        // 再导入
        val secondImport = CourseFileParsers.backup(exported, 0L)

        assertEquals("再导入的课程数必须与第一次相同", firstImport.size, secondImport.size)
        assertEquals("再导入的字段必须与第一次逐个相同", firstImport, secondImport)
    }

    @Test
    fun `同名同天同节次的课在第二次导入时被判为重复`() {
        val existing = CourseFileParsers.csv(normalCsv, 0L)
        val incoming = CourseFileParsers.csv(normalCsv, 0L)
        assertTrue("同一份文件导入两次不该产生新课程", CourseFileParsers.mergeNew(existing, incoming).isEmpty())
    }

    @Test
    fun `同名课程换到别的节次仍算新课`() {
        val existing = listOf(course("高等数学", "张老师", "A101", 1, 1, 2))
        val incoming = listOf(course("高等数学", "张老师", "A101", 1, 3, 4))
        assertEquals(1, CourseFileParsers.mergeNew(existing, incoming).size)
    }

    @Test
    fun `同名课程换到别的星期仍算新课`() {
        val existing = listOf(course("高等数学", "张老师", "A101", 1, 1, 2))
        val incoming = listOf(course("高等数学", "张老师", "A101", 2, 1, 2))
        assertEquals(1, CourseFileParsers.mergeNew(existing, incoming).size)
    }

    @Test
    fun `教师或教室变了但课名与时间没变，按现有规则算重复`() {
        val existing = listOf(course("高等数学", "张老师", "A101", 1, 1, 2))
        val incoming = listOf(course("高等数学", "李老师", "B999", 1, 1, 2))
        assertTrue(CourseFileParsers.mergeNew(existing, incoming).isEmpty())
    }

    @Test
    fun `四类文件格式解析同一份课表得到相同结果`() {
        val fromCsv = CourseFileParsers.csv(normalCsv, 0L)
        val fromHtml = CourseFileParsers.html(normalHtml, 0L)
        val fromBackup = CourseFileParsers.backup(
            gson.toJson(CourseBackup(courses = fromCsv)),
            0L
        )
        assertEquals(fromCsv, fromHtml)
        assertEquals(fromCsv, fromBackup)
    }
}
