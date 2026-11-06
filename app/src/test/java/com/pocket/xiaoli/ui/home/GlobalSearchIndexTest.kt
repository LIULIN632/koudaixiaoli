package com.pocket.xiaoli.ui.home

import com.pocket.xiaoli.data.CampusConfig
import com.pocket.xiaoli.data.CampusInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 全局搜索的索引**按学校取舍** —— 与服务页那档可见性同一条判据。
 *
 * ## 这份测试守的是什么
 *
 * 服务页已经做到"未收录学校的格子不出现"，但搜索是**另一条入口**：它读
 * `CampusServiceRegistry.catalogFor(campus)`，而 `campus` 以前是 `selectedCampus` ——
 * 对没收录的学校那是兜底出来的烟台理工本校区。于是山东理工的学生搜「电费」，
 * 会看到一条「宿舍电费 · 校园电费 · 服务入口」，点过去落在服务页上**一个并不存在的格子**；
 * 搜「地图」会跳到烟台理工的楼栋。
 *
 * 这不是"搜索少了四条"，而是**搜索结果说假话** —— 用户按搜索结果去点，代价比按不到大。
 * 所以断言分两边：
 *
 * 1. **本校不缺东西**：烟台理工的索引里，地图 / 教室分布图 / 服务入口都还在（挡住误删）；
 * 2. **别校不借东西**：`campus = null` 时，凡是 route 指向学校专属页的命中一条都不许出现，
 *    而课表 / 鲤鱼 / 我的 / 帮助这些与学校无关的命中必须在（挡住把整页搜空）。
 */
class GlobalSearchIndexTest {

    private val ytlg = CampusConfig.byId("ytlg_main")

    private fun titles(campus: CampusInfo?) = buildIndex(campus).map { it.title }

    @Test
    fun 本校索引该有的都在() {
        titles(ytlg).let { titles ->
            listOf("课表", "导入课表", "校园地图", "教室分布图", "鲤鱼", "我的").forEach {
                assertTrue("本校索引少了「$it」", it in titles)
            }
        }
        val serviceHits = buildIndex(ytlg).filter { it.route == "services" }
        assertTrue("校园服务一条都没索引到，说明 catalogFor 没读到校区配置", serviceHits.isNotEmpty())
    }

    /**
     * 未收录学校：**不许出现任何指向学校专属页的命中**。
     *
     * 判据写成"route 白名单"而不是数条数 —— 以后加一格学校专属入口（校车、图书馆…）时，
     * 只要它的 route 在这三个里，这条测试就自动要求它跟着学校走，不必再来改断言。
     */
    @Test
    fun 未收录学校不借本校的服务与地图() {
        val items = buildIndex(null)
        val schoolOnlyRoutes = setOf("services", "campus_map", "room_layout")
        val offenders = items.filter { it.route in schoolOnlyRoutes }
        assertTrue("这些命中属于学校专属入口，未收录学校不该看到：${offenders.map { it.title }}", offenders.isEmpty())
    }

    @Test
    fun 未收录学校仍能搜到与学校无关的东西() {
        val titles = titles(null)
        // 搜索页对任何学校都不该是空的：课表 / 导入 / 助手 / 我的 + 内置帮助都还在
        listOf("课表", "导入课表", "鲤鱼", "我的").forEach {
            assertTrue("索引少了「$it」，未收录学校的搜索页会变成空壳", it in titles)
        }
        assertTrue("内置帮助（FAQ）不该跟着学校消失", buildIndex(null).any { it.body != null })
    }

    /** 命中不许重复：`distinctBy(title)` 是这条链的地基，服务名与功能名撞车时用户只看到一条。 */
    @Test
    fun 索引里没有重复标题() {
        val titles = titles(ytlg)
        assertEquals(titles.distinct().size, titles.size)
    }
}
