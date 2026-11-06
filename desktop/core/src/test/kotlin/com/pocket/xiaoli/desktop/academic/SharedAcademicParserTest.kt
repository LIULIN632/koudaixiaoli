package com.pocket.xiaoli.desktop.academic

import com.pocket.xiaoli.core.academic.ZhengfangHtmlParser
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 桌面端 × 共享教务解析的接线证明。
 *
 * Android 通过 `network` 包里的适配器消费它；桌面直接吃 `:core` 的 jvm 变体。
 * 这条测试跑在 `desktop/core`，证明「同一份解析器」在桌面 JVM 上真的可用。
 */
class SharedAcademicParserTest {

    @Test
    fun `桌面 JVM 能解析同一份 V8 课表 HTML`() {
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
          </tr>
        </table>
        """
        val course = ZhengfangHtmlParser.parseScheduleHtmlV8Grid(html).single()
        assertEquals("高等数学", course.name)
        assertEquals("张三", course.teacher)
        assertEquals("东九楼D101", course.room)
        assertEquals(3, course.dayOfWeek)
        assertEquals(1, course.startSlot)
        assertEquals(2, course.endSlot)
    }
}
