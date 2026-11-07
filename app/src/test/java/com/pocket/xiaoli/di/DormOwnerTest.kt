package com.pocket.xiaoli.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 宿舍与学籍校**解耦**（2026-11-06 用户要求）的守卫。
 *
 * ## 为什么要解耦
 *
 * 用户的两种情况都是真的：一个学校多个校区（宿舍归不同生活区），以及 **A 校学生住 B 校宿舍**
 * （考研公寓、跨校借宿、联合培养）。宿舍回答的是「我住哪」，与「我在哪上学」无关。
 *
 * ## 这条测试钉的是四个语义
 *
 * 1. 绑别的学校的生活区，能存能读（不再被"必须是本校校区"挡住）；
 * 2. 换学籍校，宿舍不丢（`dorm_owner_campus` 才是读路径的唯一依据）；
 * 3. 未建成/未收录的校区一律拒绝（存进去也读不回来，等于骗用户）；
 * 4. 老存档（只有 `dorm_<校区>`、没有 owner）第一次读的时候补上 owner，且只补一次。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DormOwnerTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun prefs() = context.getSharedPreferences("pocket_xiaoli_prefs", Context.MODE_PRIVATE)

    @Before
    fun reset() {
        prefs().edit().clear().apply()
        prefs().edit().putString("academic_school_id", "YIT").apply()
    }

    @Test
    fun `住别校宿舍也能存能读`() {
        val container = AppContainer(context)

        // 学籍校是烟台理工，宿舍绑到烟大南校区（跨校借宿的真实情况）
        val saved = container.setDormInfoFor("ytu_south", "12 号楼", "305")

        assertTrue("跨校绑定必须被接受", saved)
        assertEquals("ytu_south", container.dormOwnerCampus()?.id)
        assertEquals("12 号楼", container.dormInfo.value?.building)
        assertEquals("305", container.dormInfo.value?.room)
    }

    @Test
    fun `换学籍校后宿舍还在`() {
        val before = AppContainer(context)
        assertTrue(before.setDormInfoFor("ytu_south", "12 号楼", "305"))

        // 模拟用户把学籍校切成山东理工（未收录校园服务）
        prefs().edit().putString("academic_school_id", "SDUT").apply()
        val after = AppContainer(context)

        assertEquals("换校不该把宿舍弄丢", "ytu_south", after.dormOwnerCampus()?.id)
        assertEquals("305", after.dormInfo.value?.room)
        assertNull("学籍校没有校区配置", after.currentSchoolCampus())
    }

    @Test
    fun `未建成的生活区一律拒绝`() {
        val container = AppContainer(context)

        // ytlg_new（烟台理工新校区）underConstruction = true：存进去也读不回来
        assertFalse(container.setDormInfoFor("ytlg_new", "1 号楼", "101"))
        assertNull("拒绝时不能留下任何存档", container.dormInfo.value)
        assertFalse(
            "不能写出 dorm_ytlg_new 这种读不回来的键",
            prefs().contains("dorm_ytlg_new"),
        )
    }

    @Test
    fun `老存档第一次读就补上 owner 且不重复迁移`() {
        // 解耦之前的存档：只有 dorm_<校区>，没有 dorm_owner_campus
        prefs().edit()
            .putString("dorm_ytu_south", """{"building":"10 号楼","room":"602"}""")
            .remove("dorm_owner_campus")
            .apply()

        val container = AppContainer(context)

        assertEquals("迁移必须从已绑定的校区里认出 owner", "ytu_south", container.dormOwnerCampus()?.id)
        assertEquals("10 号楼", container.dormInfo.value?.building)
        assertEquals("owner 要落盘，下次不用再猜", "ytu_south", prefs().getString("dorm_owner_campus", null))
    }
}
