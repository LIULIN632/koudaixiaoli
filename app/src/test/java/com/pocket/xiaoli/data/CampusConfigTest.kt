package com.pocket.xiaoli.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 校区 → 教务学校的锁定配置。
 *
 * ## 这条测试挡的是什么
 *
 * 2026-10 的坑：`ytu_south` 只写了展示名，没有 `schoolId` / `jwBaseUrl`，
 * 于是切到南校区后 `AppContainer.currentCampusSchool()` 返回 null，导入页
 * 退回**全校列表**、拿不到「按校区锁定 + 一键导入」—— 与
 * `docs/DedicatedImport.md` §3 的设计直接矛盾，而编译和既有测试都不会报错。
 *
 * 所以这里不加业务断言，只钉住"已开放校区必须能解析出一所学校"这条底线。
 */
class CampusConfigTest {

    @Test
    fun `已开放校区都配了可锁定的教务信息`() {
        val open = CampusConfig.campuses.filterNot { it.underConstruction }
        assertTrue("至少应有一个已开放校区", open.isNotEmpty())

        open.forEach { campus ->
            assertNotNull("${campus.name} 缺 schoolId", campus.schoolId)
            assertNotNull("${campus.name} 缺 schoolName", campus.schoolName)
            assertNotNull("${campus.name} 缺 jwBaseUrl", campus.jwBaseUrl)
            assertEquals("${campus.name} 目前只走正方", "ZHENGFANG", campus.jwType)
        }
    }

    @Test
    fun `烟大南校锁定到烟台大学旧版正方`() {
        val ytu = CampusConfig.byId("ytu_south")
        assertEquals("YTU", ytu.schoolId)
        assertEquals("烟台大学", ytu.schoolName)
        assertEquals("http://210.47.245.4/", ytu.jwBaseUrl)
    }
}
