package com.pocket.xiaoli.ui.courseselect

import com.pocket.xiaoli.network.CourseSelectItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [CourseListFilter] 的穷举测试（纯函数，不需要 Android 环境） */
class CourseListFilterTest {

    private fun item(
        kcmc: String,
        kch: String = "",
        jsxm: String = "",
        kclbmc: String = "",
        kcxzmc: String = "",
        xf: String = "",
        sxrs: String = "",
        yxrs: String = "",
        sxrsmc: String = "",
    ) = CourseSelectItem(
        kch = kch,
        kcmc = kcmc,
        jsxm = jsxm,
        kclbmc = kclbmc,
        kcxzmc = kcxzmc,
        xf = xf,
        sxrs = sxrs,
        yxrs = yxrs,
        sxrsmc = sxrsmc,
    )

    // ────────────────────── 检索 ──────────────────────

    @Test
    fun `空关键词返回全部`() {
        val list = listOf(item("高等数学"), item("大学英语"))
        assertEquals(2, CourseListFilter.apply(list, "").size)
        assertEquals("只输入空格也应视为空", 2, CourseListFilter.apply(list, "   ").size)
    }

    @Test
    fun `按课程名检索`() {
        val list = listOf(item("高等数学A"), item("大学英语"), item("高等数学B"))
        val hit = CourseListFilter.apply(list, "高等数学")
        assertEquals(2, hit.size)
    }

    @Test
    fun `按课程代码检索且大小写不敏感`() {
        val list = listOf(item("高等数学", kch = "B1234567"), item("大学英语", kch = "b7654321"))
        // 课程代码里的字母大小写不该影响命中
        assertEquals(1, CourseListFilter.apply(list, "b1234567").size)
        assertEquals(1, CourseListFilter.apply(list, "B7654321").size)
    }

    @Test
    fun `按教师与课程类别检索`() {
        val list = listOf(
            item("高等数学", jsxm = "张三", kclbmc = "通识必修课"),
            item("大学英语", jsxm = "李四", kclbmc = "专业选修课"),
        )
        assertEquals("高等数学", CourseListFilter.apply(list, "张三").single().kcmc)
        assertEquals("大学英语", CourseListFilter.apply(list, "专业选修").single().kcmc)
        // 课程性质也应在检索范围内
        val byNature = listOf(item("A课", kcxzmc = "必修"), item("B课", kcxzmc = "选修"))
        assertEquals("B课", CourseListFilter.apply(byNature, "选修").single().kcmc)
    }

    @Test
    fun `多个关键词必须全部命中`() {
        val list = listOf(
            item("高等数学", jsxm = "张三"),
            item("高等数学", jsxm = "李四"),
            item("大学英语", jsxm = "张三"),
        )
        // "高数 + 张三" 应收窄到那个教学班，而不是把两个条件分别命中的人全给出来
        val hit = CourseListFilter.apply(list, "高等数学 张三")
        assertEquals(1, hit.size)
        assertEquals("张三", hit[0].jsxm)
    }

    @Test
    fun `全角空格也能分词`() {
        val list = listOf(item("高等数学", jsxm = "张三"), item("大学英语", jsxm = "李四"))
        // 中文输入法下很容易打出全角空格；不处理的话用户会以为"搜不到"
        assertEquals(1, CourseListFilter.apply(list, "高等数学　张三").size)
    }

    @Test
    fun `无命中返回空列表`() {
        val list = listOf(item("高等数学", kch = "B1"))
        assertTrue(CourseListFilter.apply(list, "量子力学").isEmpty())
    }

    // ────────────────────── 学分合计 ──────────────────────

    @Test
    fun `学分合计把无法解析的值按 0 计`() {
        val list = listOf(
            item("高等数学", xf = "4"),
            item("大学英语", xf = "3.5"),
            item("体育", xf = ""),
            item("形势与政策", xf = "待定"),
        )
        // 3.5 + 4 = 7.5；空串与"待定"不该让整条计算崩掉或返回 NaN
        assertEquals(7.5, CourseListFilter.totalCredits(list), 0.001)
    }

    @Test
    fun `学分展示整数不补小数点`() {
        assertEquals("12", CourseListFilter.formatCredits(12.0))
        assertEquals("7.5", CourseListFilter.formatCredits(7.5))
        // 0 或负数返回空串：不显示「合计 0 学分」这种废话
        assertEquals("", CourseListFilter.formatCredits(0.0))
        assertEquals("", CourseListFilter.formatCredits(-1.0))
    }

    // ────────────────────── 余量判读 ──────────────────────

    @Test
    fun `余量优先用独立的人数字段`() {
        val cap = CourseListFilter.capacity(item("高等数学", sxrs = "120", yxrs = "60"))
        assertEquals(120, cap!!.limit)
        assertEquals(60, cap.used)
        assertEquals(60, cap.remaining)
        assertFalse(cap.isFull)
        assertEquals(0.5f, cap.ratio, 0.001f)
    }

    @Test
    fun `没有独立字段时从已排版文本里拆`() {
        // 教务的 sxrsmc 形如 "120/60" = 上限/已选
        val cap = CourseListFilter.capacity(item("高等数学", sxrsmc = "120/60"))
        assertEquals(120, cap!!.limit)
        assertEquals(60, cap.used)
    }

    @Test
    fun `人数信息不足时返回 null 而不是 0 比 0`() {
        // ⚠️ 这条是刻意的：宁可不画进度条，也不能画一个 0/0 的假条 ——
        // 那会被读成"这门课没人选"，是**主动误导**。
        assertNull(CourseListFilter.capacity(item("高等数学")))
        assertNull(CourseListFilter.capacity(item("高等数学", sxrs = "120")))
        assertNull(CourseListFilter.capacity(item("高等数学", sxrsmc = "暂无")))
        assertNull(CourseListFilter.capacity(item("高等数学", sxrs = "0", yxrs = "0")))
    }

    @Test
    fun `满员与超额都判为已满 且比例不超过 1`() {
        val full = CourseListFilter.capacity(item("高等数学", sxrs = "60", yxrs = "60"))!!
        assertTrue(full.isFull)
        assertEquals(0, full.remaining)

        // 超额（有人退课/系统延迟）时比例必须夹在 1.0，否则进度条会画出界
        val over = CourseListFilter.capacity(item("高等数学", sxrs = "60", yxrs = "75"))!!
        assertTrue(over.isFull)
        assertEquals(1.0f, over.ratio, 0.001f)
    }
}
