package com.pocket.xiaoli.desktop.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 配置体检的判据测试。
 *
 * 这张卡的意义是「缺哪项就点哪项」，所以每行的 `ok` 与 `action` 必须对上：
 * 把「未登录」标成就绪，或者给已就绪的行挂一个动作，都会让用户白点一次。
 */
class ConfigChecklistTest {

    private fun rows(
        jwUrl: String? = "https://jwxt.example.edu.cn/jwglxt",
        loggedIn: Boolean = true,
        ai: Boolean = true,
        origin: String = "import",
        courses: Int = 12,
        startup: Long? = 1400,
    ) = buildConfigChecklist(
        jwUrl = jwUrl,
        jwLoggedIn = loggedIn,
        aiConfigured = ai,
        aiModel = "deepseek-chat",
        origin = origin,
        courseCount = courses,
        startupMillis = startup,
    ).rows.associateBy { it.label }

    @Test
    fun `全部就绪时没有待补项`() {
        val checklist = buildConfigChecklist(
            jwUrl = "https://jwxt.example.edu.cn/jwglxt",
            jwLoggedIn = true,
            aiConfigured = true,
            aiModel = "deepseek-chat",
            origin = "import",
            courseCount = 12,
            startupMillis = 1400,
        )
        assertEquals(0, checklist.pending)
        assertNull(rows()["教务"]?.action)
        assertTrue(rows()["教务"]!!.ok)
    }

    @Test
    fun `教务已配置但未登录时给出登录动作`() {
        val jw = rows(loggedIn = false)["教务"]!!
        assertFalse(jw.ok)
        assertEquals("登录", jw.action)
        assertTrue(jw.detail.contains("未登录"))
    }

    @Test
    fun `校区没有教务地址时提示换校区而不是登录`() {
        val jw = rows(jwUrl = null)["教务"]!!
        assertFalse(jw.ok)
        assertEquals("换校区", jw.action)
        assertTrue("导课表" in jw.detail || "地址" in jw.detail)
    }

    @Test
    fun `AI 未配置时不算就绪并给出配置动作`() {
        val ai = rows(ai = false)["AI"]!!
        assertFalse(ai.ok)
        assertEquals("配置", ai.action)
        assertTrue("知识库" in ai.detail)
    }

    @Test
    fun `示例数据算待补并指向导入`() {
        val source = rows(origin = "seed", courses = 13)["数据来源"]!!
        assertFalse(source.ok)
        assertEquals("导入课表", source.action)
        assertTrue("内置示例数据" in source.detail)
    }

    @Test
    fun `手动维护有课算就绪，零门课算待补`() {
        assertTrue(rows(origin = "manual", courses = 3)["数据来源"]!!.ok)
        val empty = rows(origin = "manual", courses = 0)["数据来源"]!!
        assertFalse(empty.ok)
        assertEquals("新建/导入", empty.action)
    }

    @Test
    fun `启动超过两秒半算待补，没测到也算待补`() {
        assertTrue(rows(startup = 1400)["启动"]!!.ok)
        assertFalse(rows(startup = 3100)["启动"]!!.ok)
        val missing = rows(startup = null)["启动"]!!
        assertFalse(missing.ok)
        assertEquals("还没测到", missing.detail)
    }
}
