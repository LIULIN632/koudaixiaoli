package com.pocket.xiaoli.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZhengfangHtmlParserTest {

    @Test
    fun gridPFontVariant() {
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
        assertEquals("李四", yingyu.teacher)
        assertEquals("教五楼B301", yingyu.room)
        assertEquals(3, yingyu.startSlot)
        assertEquals(4, yingyu.endSlot)
    }

    @Test
    fun gridTitleSpanVariantRegression() {
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
        assertEquals(1, courses[0].startSlot)
        assertEquals(2, courses[0].endSlot)
    }

    @Test
    fun listTbodyVariant() {
        val html = """
        <table id="kblist_table">
          <tbody>
            <tr><th>节次</th></tr>
          </tbody>
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
            <tr>
              <td>3-4</td>
              <td>
                <span class="title"><font>操作系统</font></span>
                <p><font>周数：1-8周</font></p>
                <p><font>上课地点：东九楼A101</font></p>
                <p><font>教师：赵六</font></p>
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
        assertEquals(3, courses.size)

        val shuju = courses.first { it.name == "数据结构" }
        assertEquals(1, shuju.dayOfWeek)
        assertEquals(1, shuju.startSlot)
        assertEquals(2, shuju.endSlot)
        assertEquals("王五", shuju.teacher)
        assertEquals("西十二楼N301", shuju.room)

        val bianyi = courses.first { it.name == "编译原理" }
        assertEquals(2, bianyi.dayOfWeek)
        assertEquals("odd", bianyi.weekType)
        assertTrue(bianyi.weeks.split(",").all { it.toInt() % 2 == 1 })
    }

    @Test
    fun gridJcIdVariantRegression() {
        val html = """
        <table id="kbgrid_table_0">
          <tr>
            <td class="td_wrap" id="1-2">
              <div class="timetable_con text-left">
                <p class="title"><font>线性代数</font></p>
                <span title="教师">孙八</span>
                <span title="上课地点">科技楼402</span>
                <span title="节/周">3-4节/1-16周</span>
              </div>
            </td>
          </tr>
        </table>
        """
        val courses = ZhengfangHtmlParser.parseScheduleHtmlV8Grid(html)
        assertEquals(1, courses.size)
        assertEquals("线性代数", courses[0].name)
        assertEquals("孙八", courses[0].teacher)
    }
}
