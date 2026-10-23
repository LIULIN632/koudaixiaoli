package com.pocket.xiaoli.data.service

import com.pocket.xiaoli.data.CampusConfig
import com.pocket.xiaoli.data.CampusServiceKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusCourseSelectRegistryTest {

    @Test
    fun courseSelectIsNativeSpec() {
        val spec = CampusServiceRegistry.specFor(
            campusId = CampusConfig.defaultCampus.id,
            key = CampusServiceKeys.COURSE_SELECT,
        )
        assertNotNull(spec)
        assertEquals(CampusServiceType.NATIVE, spec?.type)
        assertEquals("native://course_select", spec?.target)
        // ⚠️ 2026-10 有意变更：fallback 原先是 `jiaowu.yitsd.edu.cn/jwxt/xkxt.htm`，
        // 那是**上一代选课系统**的地址，与现在在用的正方 v9（`jwxt.yitsd.edu.cn/jwglxt`）
        // 不是同一套 —— 照旧跳过去只会落到一个打不开的页面。
        // 现在指向正方 v9 的自主选课入口页（N253512）。
        assertTrue(
            "fallback 应指向正方 v9 自主选课页",
            spec?.fallback?.contains("/jwglxt/xsxk/zzxkyzb_cxZzxkYzbIndex.html") == true,
        )
        assertTrue("必须带上正确的功能模块号", spec?.fallback?.contains("N253512") == true)
        assertFalse(
            "不许再指向已废弃的旧版选课系统",
            spec?.fallback?.contains("xkxt.htm") == true,
        )
    }
}
