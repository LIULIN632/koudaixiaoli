package com.pocket.xiaoli.data.service

import com.pocket.xiaoli.data.CampusConfig
import com.pocket.xiaoli.data.CampusServiceKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 「校园跑」这一格的成格规则 —— 纯函数用例。
 *
 * 钉的是那条最容易做错的事：**没配直达链接时这一格不能出现**。
 * 出现了一格点了只会弹"暂未配置"，那不是"待接入"，是死格子。
 */
class SchoolRunSpecTest {

    @Test
    fun `没配链接就没有这一格`() {
        assertNull(CampusServiceRegistry.schoolRunSpec("ytlg_main", ""))
    }

    @Test
    fun `只有空格的链接同样算没配`() {
        // 配置文件里手滑留一个空格是常事；当成"配好了"就会渲染出一格打不开的入口。
        assertNull(CampusServiceRegistry.schoolRunSpec("ytlg_main", "   "))
    }

    @Test
    fun `配了链接就以支付宝渠道打开那条链接`() {
        val link = "alipays://platformapi/startapp?appId=2021000000000000&page=pages%2Frun%2Findex"
        val spec = CampusServiceRegistry.schoolRunSpec("ytlg_main", link)
        assertEquals(CampusServiceType.ALIPAY, spec?.type)
        assertEquals(link, spec?.target)
        assertEquals("校园跑", spec?.name)
        // 能力档位必须是 WEB：界面据此判定"这一格真能跳"，才会渲染出来。
        assertEquals(CampusServiceCapability.WEB, CampusServiceResolver.capabilityOfSpec(requireNotNull(spec)))
    }

    @Test
    fun `现网还没配任何校区的链接`() {
        // 这条不是断言"永远不配"，而是**提醒**：填了链接就要去真机点一次，
        // 并同步把 memory/10-service-registry.md 那一行改掉。
        val campuses = CampusConfig.campuses.map { it.id }
        val configured = campuses.filter {
            CampusServiceResolver.capabilityOf(
                CampusServiceKeys.SCHOOL_RUN,
                CampusConfig.byId(it),
            ) != CampusServiceCapability.INFO
        }
        assertEquals(emptyList<String>(), configured)
    }
}
