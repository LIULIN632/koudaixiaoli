package com.pocket.xiaoli.core.academic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 共享教务解析器的 JVM 单测。
 *
 * Android 侧 `ZhengfangHtmlParserTest` 继续通过适配器覆盖同一份代码；这里再跑一遍
 * `:core:jvmTest`，保证桌面端消费的解析器本身也是绿的。
 */
class ZhengfangParserCoreTest {

    @Test
    fun `V8 grid 的 font 变体`() {
        val html = """
        <table id="kbgrid_table_0">
          <tr>
            <td class="td_wrap" id="3-2">
              <div class="timetable_con text-left">
                <p><font>第2节</font><font>(1-2节)1-16周</font></p>
                <p><font>东九楼D101</font></p>
                <p><font>张三</font></p>
                <p class="title"><font>●高等数学</font></p>
              </div>
            </td>
            <td class="td_wrap" id="1-3">
              <div class="timetable_con text-left">
                <p><font>第3节</font><font>(3-4节)1-8,11-16周</font></p>
                <p><font>教五楼B301</font></p>
                <p><font>李四</font></p>
                <p class="title"><font>★大学英语</font></p>
              </div>
            </td>
          </tr>
        </table>
        """
        val courses = ZhengfangHtmlParser.parseScheduleHtmlV8Grid(html)
        assertEquals(2, courses.size)

        val gaoshu = courses.first { it.dayOfWeek == 3 }
        assertEquals("高等数学", gaoshu.name)
        assertEquals("张三", gaoshu.teacher)
        assertEquals("东九楼D101", gaoshu.room)
        assertEquals(1, gaoshu.startSlot)
        assertEquals(2, gaoshu.endSlot)
        assertEquals("1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16", gaoshu.weeks)

        val yingyu = courses.first { it.dayOfWeek == 1 }
        assertEquals("大学英语", yingyu.name)
        assertEquals(3, yingyu.startSlot)
        assertEquals(4, yingyu.endSlot)
    }

    @Test
    fun `V8 grid 的 title span 变体`() {
        val html = """
        <table id="kbgrid_table_0">
          <tr>
            <td class="td_wrap" id="2-1">
              <div class="timetable_con">
                <p class="title"><font>大学物理</font></p>
                <span title="教师">王五</span>
                <span title="上课地点">南一楼201</span>
                <span title="节/周">1-2节/1-8周</span>
              </div>
            </td>
          </tr>
        </table>
        """
        val courses = ZhengfangHtmlParser.parseScheduleHtmlV8Grid(html)
        assertEquals(1, courses.size)
        assertEquals("大学物理", courses[0].name)
        assertEquals("王五", courses[0].teacher)
        assertEquals("南一楼201", courses[0].room)
    }

    @Test
    fun `V8 list 变体保留单双周`() {
        val html = """
        <table id="kblist_table">
          <tbody><tr><th>节次</th></tr></tbody>
          <tbody>
            <tr><th>周一</th></tr>
            <tr>
              <td>1-2</td>
              <td>
                <span class="title"><font>●数据结构</font></span>
                <p><font>周数：1-16周</font></p>
                <p><font>上课地点：西十二楼N301</font></p>
                <p><font>教师：王五</font></p>
              </td>
            </tr>
          </tbody>
          <tbody>
            <tr><th>周二</th></tr>
            <tr>
              <td>1-2</td>
              <td>
                <span class="title"><font>编译原理</font></span>
                <p><font>周数：1-16周(单)</font></p>
                <p><font>上课地点：教三302</font></p>
                <p><font>教师：钱七</font></p>
              </td>
            </tr>
          </tbody>
        </table>
        """
        val courses = ZhengfangHtmlParser.parseScheduleHtmlV8List(html)
        assertEquals(2, courses.size)

        val shuju = courses.first { it.name == "数据结构" }
        assertEquals(1, shuju.dayOfWeek)
        assertEquals("王五", shuju.teacher)
        assertEquals("西十二楼N301", shuju.room)

        val bianyi = courses.first { it.name == "编译原理" }
        assertEquals(2, bianyi.dayOfWeek)
        assertEquals("odd", bianyi.weekType)
        assertTrue(bianyi.weeks.split(",").all { it.toInt() % 2 == 1 })
    }

    @Test
    fun `周次工具能处理区间与单双周`() {
        assertEquals((1..16).toList(), parseWeeks("1-16周"))
        assertEquals(listOf(1, 3, 5, 7), parseWeeks("1-8周(单)"))
        assertEquals("odd", computeWeekType(parseWeeks("1-16周(单)"), "1-16周(单)"))
        assertEquals(3, parseSlotRange("第3-4节")?.first)
    }

    /**
     * 行标签写着「第1-2节」，课程就得落在 1..2 节 —— 节次来自**标签**，不是行号。
     *
     * 2026-11-06 修的：解析器只认「第N节」，于是「第1-2节」「第3-4节」这类标签全都读不出来，
     * 退化成"每行一节"：1-2 节的课被压成第 1 节、3-4 节的课被压成第 2 节。
     * 整张表比真实位置矮一半，相邻两行的课叠进同一格 —— 用户报的"导入后课程都挤在一起"就是这个观感。
     */
    @Test
    fun `课程按行标签落在真实节次区间`() {
        val html = """
        <table id="Table1">
          <tr><th>时间</th><th>周一</th><th>周二</th><th>周三</th><th>周四</th><th>周五</th></tr>
          <tr>
            <td>第1-2节</td>
            <td>高等数学<br>张三<br>一教101<br>1-16周</td>
            <td></td>
            <td>大学英语<br>李四<br>一教102<br>1-16周</td>
            <td></td><td></td>
          </tr>
          <tr>
            <td>第3-4节</td>
            <td>体育<br>王五<br>操场<br>1-16周</td>
            <td></td><td></td><td></td><td></td>
          </tr>
        </table>
        """
        val courses = ZhengfangHtmlParser.parseHtmlScheduleTable(html)
        assertEquals(3, courses.size)

        val gaoshu = courses.first { it.name == "高等数学" }
        assertEquals(1, gaoshu.dayOfWeek)
        assertEquals(1, gaoshu.startSlot)
        assertEquals(2, gaoshu.endSlot)

        val yingyu = courses.first { it.name == "大学英语" }
        assertEquals(3, yingyu.dayOfWeek)
        assertEquals(1, yingyu.startSlot)
        assertEquals(2, yingyu.endSlot)

        // 第二行的标签写着 3-4 节：按行数只会得到"第 2 节"，按标签才是第 3-4 节
        val tiyu = courses.first { it.name == "体育" }
        assertEquals(1, tiyu.dayOfWeek)
        assertEquals(3, tiyu.startSlot)
        assertEquals(4, tiyu.endSlot)
    }

    /**
     * 读不出来就返回空，**不许造**。
     *
     * 这里原本有一段兜底：把页面里的中文 `<td>` 按四个一组当作「课名/教师/教室/周次」，
     * 星期从 1、节次从 1 开始递增。于是"没有可选课程"这种空页也能"成功导入"几门课，
     * 而且它们全落在周一的第 1、2、3 节 —— 界面课程挤在一起，导入条数却看着正常。
     */
    @Test
    fun `读不懂的页面返回空而不是凭空造课程`() {
        val html = """
        <table id="Table1">
          <tr><td>课程名称</td><td>任课教师</td><td>上课地点</td><td>周次数</td></tr>
          <tr><td>没有可选课程</td><td>请更换学期后再试</td><td>教务处</td><td>—</td></tr>
          <tr><td>提示：请在下拉框中选择学期</td><td>系统维护中</td><td>数据同步作业中</td><td>—</td></tr>
        </table>
        """
        assertTrue(ZhengfangHtmlParser.parseHtmlScheduleTable(html).isEmpty())
    }

    /** 任何一条路径都不许交出 0 或负数的节次与星期：网格按它们定位，0 会画到网格上方。 */
    @Test
    fun `任何解析结果都落在合法坐标内`() {
        val html = """
        <table id="Table1">
          <tr><th>时间</th><th>周一</th><th>周二</th><th>周三</th><th>周四</th><th>周五</th></tr>
          <tr><td>08:00-08:45</td><td>高等数学<br>张三<br>一教101<br>1-16周</td><td></td><td></td><td></td><td></td></tr>
          <tr><td>08:55-09:40</td><td></td><td>大学英语<br>李四<br>一教102<br>1-16周</td><td></td><td></td><td></td></tr>
        </table>
        """
        val courses = ZhengfangHtmlParser.parseHtmlScheduleTable(html)
        assertEquals(2, courses.size)
        courses.forEach {
            assertTrue(it.dayOfWeek in 1..7, "星期越界: $it")
            assertTrue(it.startSlot in 1..12, "节次越界: $it")
            assertTrue(it.endSlot >= it.startSlot, "结束早于开始: $it")
        }
        // 时间标签不是节次标签，所以按行数数：第一行第 1 节、第二行第 2 节
        assertEquals(1, courses.first { it.name == "高等数学" }.startSlot)
        assertEquals(2, courses.first { it.name == "大学英语" }.startSlot)
    }
}
