package com.pocket.xiaoli.data.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 门户解析。
 *
 * ⚠️ [REAL_GUEST_RESPONSE] 是 **2026-09-28 从 `http://ehall.yitsd.edu.cn/jsonp/serviceCenterData.json`
 * 逐字节抓下来的真实响应**（未登录），刻意不做美化 —— 这类解析最怕照着自己猜的格式写测试，
 * 测过了照样在真机上不对。
 */
class TodayCampusPortalTest {

    private val REAL_GUEST_RESPONSE = """
        {"searchResult":[{"appId":"7198857910810582","appName":"学生请销假","categoryList":[{"categoryId":"2__cdfce41a-6cc3-4967-a0df-e1ba12d80d03","categoryName":"学工服务","hasUnReadMark":false,"parentId":null,"subCategoryList":null,"appManageInfoList":[],"appList":[]}],"middleIcon":"http://ehall.yitsd.edu.cn/resources/app/7198857910810582/1.0_EM1/icon_72.png?_=1719909886000","type":0,"pinyingFirstChar":"X","description":"学生请销假","appPc":{"appId":"7198857910810582","isHasHead":1,"isThirdParty":0,"descriptionAuth":0,"isOpenInNewTab":1,"isEnableDescription":1,"isOpenAssessPage":0},"assessAvgScore":0.0,"maintaining":false,"opening":true,"favorite":false,"hasPermission":false,"unviewableDescription":false,"version":"1.0_EM1","appType":0,"appKey":"7198857910810582-1.0_EM1","appPcCardList":null,"favoriteCount":4,"favoriteApp":false},{"appId":"5858946796270354","appName":"站群管理","categoryList":[],"middleIcon":"http://ehall.yitsd.edu.cn/resources/app/5858946796270354/1.0_R1/icon_72.png?_=1629439789000","type":0,"pinyingFirstChar":"Z","description":"站群管理后台","appPc":{"appId":"5858946796270354","isHasHead":1,"isThirdParty":0,"descriptionAuth":0,"isOpenInNewTab":1,"isEnableDescription":1,"isOpenAssessPage":0},"assessAvgScore":0.0,"maintaining":false,"opening":true,"favorite":false,"hasPermission":false,"unviewableDescription":false,"version":"1.0_R1","appType":0,"appKey":"5858946796270354-1.0_R1","appPcCardList":null,"favoriteCount":13,"favoriteApp":false}],"lastOnlineAppResult":[],"hasLogin":false,"contextPath":""}
    """.trimIndent()

    @Test
    fun parsesRealGuestResponse() {
        val snapshot = TodayCampusPortal.parse(REAL_GUEST_RESPONSE)

        assertFalse("匿名访问应当识别为未登录", snapshot?.loggedIn == true)
        assertEquals(2, snapshot?.services?.size)
        assertEquals("学生请销假", snapshot?.services?.first()?.name)
        assertEquals("7198857910810582", snapshot?.services?.first()?.appId)
        assertEquals("学工服务", snapshot?.services?.first()?.category)
        assertEquals("站群管理", snapshot?.services?.get(1)?.name)
        // 没有分类时是 null，而不是空串 —— 展示层据此决定要不要画分类标签
        assertNull(snapshot?.services?.get(1)?.category)
    }

    @Test
    fun loggedInResponseKeepsServices() {
        // 真实响应的"登录后"变体：结构一样，只把 hasLogin 翻成 true 并加一项
        val loggedIn = REAL_GUEST_RESPONSE.replace(
            "\"hasLogin\":false",
            "\"hasLogin\":true",
        )
        val snapshot = TodayCampusPortal.parse(loggedIn)

        assertTrue(snapshot?.loggedIn == true)
        assertEquals(2, snapshot?.services?.size)
    }

    @Test
    fun serviceUrlPointsAtThePortalAppPage() {
        // 校外实测的直达形态：未登录 302 到统一认证，登录后直接进该服务
        assertEquals(
            "http://ehall.yitsd.edu.cn/new/mobileAppShow?appId=7198857910810582",
            TodayCampusPortal.serviceUrl("7198857910810582"),
        )
    }

    @Test
    fun missingFieldsAreSkippedInsteadOfCrashing() {
        // Gson 不走 Kotlin 默认值：字段可能整个缺失或为 null（见 CLAUDE.md 那条教训）
        val partial = """{"searchResult":[{"appId":"1"},{"appName":"没 appId"},{"appId":"2","appName":"  "},{"appId":"3","appName":"可用","opening":false},{"appId":"4","appName":"可用2"}],"hasLogin":null}"""

        val snapshot = TodayCampusPortal.parse(partial)

        assertEquals(1, snapshot?.services?.size)
        assertEquals("可用2", snapshot?.services?.first()?.name)
        assertFalse(snapshot?.loggedIn == true)
    }

    @Test
    fun brokenBodyReturnsNull() {
        assertNull(TodayCampusPortal.parse("not json at all"))
        assertNull(TodayCampusPortal.parse(""))
        // 结构完全对不上（不是对象）也不该抛
        assertNull(TodayCampusPortal.parse("[1,2,3]"))
    }
}
