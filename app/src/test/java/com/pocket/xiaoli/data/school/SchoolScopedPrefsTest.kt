package com.pocket.xiaoli.data.school

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 换校不串数据 · 第 3 步：按校隔离的 SharedPreferences。
 *
 * 这四条 key（问答标记 / 导入记忆 / URL 历史 / 服务页收藏）以前是全局的，
 * 换校后 A 校的状态会显示到 B 校。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SchoolScopedPrefsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun setSchool(id: String) {
        context.getSharedPreferences("pocket_xiaoli_prefs", Context.MODE_PRIVATE)
            .edit().putString("academic_school_id", id).apply()
    }

    @Test
    fun `文件名带学校后缀`() {
        setSchool("YIT")
        assertEquals("quickanswer_marks@YIT", SchoolScopedPrefs.nameFor(context, "quickanswer_marks"))
        setSchool("YTU")
        assertEquals("quickanswer_marks@YTU", SchoolScopedPrefs.nameFor(context, "quickanswer_marks"))
    }

    @Test
    fun `没选过学校时用兜底学校`() {
        context.getSharedPreferences("pocket_xiaoli_prefs", Context.MODE_PRIVATE)
            .edit().remove("academic_school_id").apply()
        assertEquals(
            SchoolScopedPrefs.FALLBACK_SCHOOL_ID,
            SchoolScopedPrefs.currentSchoolId(context),
        )
    }

    @Test
    fun `一所学校写的标记另一所学校读不到`() {
        setSchool("YIT")
        SchoolScopedPrefs.of(context, "quickanswer_marks").edit().putBoolean("ds:1", true).apply()

        setSchool("YTU")
        assertFalse(SchoolScopedPrefs.of(context, "quickanswer_marks").getBoolean("ds:1", false))

        // 切回去还在：换校是隔离，不是清空。
        setSchool("YIT")
        assertTrue(SchoolScopedPrefs.of(context, "quickanswer_marks").getBoolean("ds:1", false))
    }

    @Test
    fun `升级后把旧的全局文件复制给当前学校且只复制一次`() {
        context.getSharedPreferences("pocket_xiaoli_prefs", Context.MODE_PRIVATE)
            .edit().clear().apply()
        setSchool("YIT")
        // 老版本写在无后缀文件里
        context.getSharedPreferences("egg_rice_treasure", Context.MODE_PRIVATE)
            .edit().putStringSet("favorites", setOf("成绩查询")).apply()

        val scoped = SchoolScopedPrefs.of(context, "egg_rice_treasure")
        assertEquals(setOf("成绩查询"), scoped.getStringSet("favorites", emptySet()))

        // 复制过一次之后不再覆盖：学校文件里的增量改动要保住
        scoped.edit().putStringSet("favorites", setOf("宿舍电费")).apply()
        val again = SchoolScopedPrefs.of(context, "egg_rice_treasure")
        assertEquals(setOf("宿舍电费"), again.getStringSet("favorites", emptySet()))
    }
}
