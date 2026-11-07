package com.pocket.xiaoli.di

import com.pocket.xiaoli.data.CampusConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `pickCurrentSchoolCampus` 的守卫。
 *
 * ## 为什么要这条测试
 *
 * 2026-11-06 用户报「现在的宿舍咋绑定不了了」。根因是多校隔离那次把绑定页的校区
 * 从"用户选的那个"换成了"学校第一个校区"（`CampusConfig.forSchoolId`），而烟台理工
 * 有两个校区、宿舍存档的键是 `dorm_<校区 id>`：
 *
 * - 新校区的学生在页面上把宿舍填好、点保存 → 写进 `dorm_ytlg_new`；
 * - 回到清单却按 `dorm_ytlg_main` 去读 → 永远"未绑定"，看起来就是"绑不上"。
 *
 * 所以这条测试钉的不是某个实现细节，而是**"用户选的校区优先"**这个语义。
 */
class SchoolCampusPickTest {

    @Test
    fun `用户选中的本校校区优先于学校第一校区`() {
        val main = CampusConfig.byIdOrNull("ytlg_main")
        requireNotNull(main) { "测试前提：烟台理工本校区必须在 CampusConfig 里" }

        val picked = pickCurrentSchoolCampus("YIT", main)

        assertEquals("ytlg_main", picked?.id)
    }

    @Test
    fun `未建成的校区没有 schoolId 时退回学校第一校区`() {
        // ytlg_new（烟台理工新校区）在 CampusConfig 里 underConstruction = true 且没有 schoolId：
        // 它不是一个能存宿舍的档，选到它也必须退回本校区，否则会写出 `dorm_ytlg_new` 这种读不回来的键。
        val newCampus = CampusConfig.byIdOrNull("ytlg_new")
        requireNotNull(newCampus)

        val picked = pickCurrentSchoolCampus("YIT", newCampus)

        assertEquals("ytlg_main", picked?.id)
    }

    @Test
    fun `没选过校区时退回学校的第一校区`() {
        val picked = pickCurrentSchoolCampus("YIT", selected = null)

        assertEquals("ytlg_main", picked?.id)
    }

    @Test
    fun `选中的校区属于别的学校时不能借用`() {
        // 烟大南校区（ytu_south）不属于烟台理工：换校途中可能残留，必须退回本校第一校区
        val otherSchoolCampus = CampusConfig.byIdOrNull("ytu_south")
        requireNotNull(otherSchoolCampus)

        val picked = pickCurrentSchoolCampus("YIT", otherSchoolCampus)

        assertEquals("ytlg_main", picked?.id)
    }

    @Test
    fun `学校没配校区时返回 null 而不是借默认校区`() {
        // 山东理工这类未收录学校：给 null 让界面说"还没收录"，绝不退回烟台理工
        assertNull(pickCurrentSchoolCampus("SDUT", CampusConfig.byIdOrNull("ytlg_main")))
    }

    @Test
    fun `连学校都没选时返回 null`() {
        assertNull(pickCurrentSchoolCampus(null, CampusConfig.byIdOrNull("ytlg_main")))
    }
}
