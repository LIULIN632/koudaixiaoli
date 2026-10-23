package com.pocket.xiaoli.ui.treasurebox

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.outlined.FlashOn
import com.pocket.xiaoli.data.CampusConfig
import com.pocket.xiaoli.data.CampusServiceKeys
import com.pocket.xiaoli.data.service.CampusServiceCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 界面层能力判定的守护。
 *
 * ## 这份测试守的是"界面不说假话"
 *
 * 服务页**当时** 42 个入口长得一模一样，用户点「校园网」才知道它只是个说明页 ——
 * 于是对另外 41 个也不再信任。修法是给"仅介绍 / 未接入"挂上角标、并让它们沉到区末，
 * 但这套角标本身**极易变成新的谎话**：只要判定与真实可达性分家，
 * 界面就会挂着"能用"的牌子把人送进占位弹窗。
 *
 * 所以断言分两类：
 * 1. **名单不腐化**：`UI_NATIVE_SERVICE_KEYS` 里的每个 key 都必须真的判成原生可用
 *    （名单是"界面已接好原生页"的声明，声明与判定必须一致）；
 * 2. **角标语义**：只有"仅介绍 / 未接入"两档挂角标 —— 40/42 都挂角标等于没挂。
 */
class CampusServiceUiCapabilityTest {

    private val main = CampusConfig.byId("ytlg_main")

    private fun entry(title: String, serviceKey: String? = null) = ServiceEntry(
        title = title,
        subtitle = "",
        icon = Icons.Outlined.FlashOn,
        tint = Color.Unspecified,
        background = Color.Unspecified,
        iconFilled = Icons.Filled.FlashOn,
        serviceKey = serviceKey,
        onClick = {},
    )

    @Test
    fun uiNativeKeysAreNeverReportedAsUnusable() {
        assertTrue("名单为空则本测试失去意义", uiNativeServiceKeys.isNotEmpty())
        for (key in uiNativeServiceKeys) {
            val capability = serviceEntryCapability(entry("占位标题", key), main, null)
            assertEquals(
                "名单声明 $key 有原生页，判定却不是 NATIVE —— 名单与实现已分家",
                CampusServiceCapability.NATIVE,
                capability,
            )
        }
    }

    /**
     * 原生可用的档位**不挂角标**：它们的副标题本来就在说"原生入口 · 正选/补退选"，
     * 再加一个"能打开"的角标只是噪音，会把真正需要注意的两档淹掉。
     */
    @Test
    fun nativeAndWebEntriesCarryNoBadge() {
        val courseSelect = entry("选课", CampusServiceKeys.COURSE_SELECT)
        assertEquals(CampusServiceCapability.NATIVE, serviceEntryCapability(courseSelect, main, null))
        assertNull(courseSelect.withCapability(CampusServiceCapability.NATIVE).badge)
        assertNull(courseSelect.withCapability(CampusServiceCapability.WEB).badge)
    }

    @Test
    fun infoAndReservedEntriesCarryANeutralBadge() {
        for (capability in listOf(CampusServiceCapability.INFO, CampusServiceCapability.RESERVED)) {
            val display = entry("校园网").withCapability(capability)
            assertTrue("$capability 应当挂角标", display.badge != null)
            assertEquals(
                // 这两档都是**事实**而不是出错，染成橙色/红色会让人以为服务坏了
                "能力角标应当用中性色调：$capability",
                BadgeTone.NEUTRAL,
                display.badgeTone,
            )
        }
    }

    /**
     * 宿舍生活服务的档位跟着**宿舍路由**走：同一台手机、同一个"本校区"设置，
     * 宿舍楼不同就可能落到另一个校区的 spec 上。判定若只看当前校区，
     * 就会出现"标着能用、点下去打不开"。
     */
    @Test
    fun dormServiceCapabilityFollowsDormRouting() {
        val shower = entry("洗澡")
        // 无宿舍上下文：一律按当前校区判，必须稳定且不抛异常
        val noDorm = serviceEntryCapability(shower, main, null)
        assertEquals(noDorm, serviceEntryCapability(shower, main, null))
    }

    @Test
    fun entryWithoutRegistryKeyIsLocalAndUsable() {
        // 地图导航 / 今天吃什么 / 学习笔记 / 留声树洞：没有注册表 key，天然可用
        val localOnly = entry("地图导航")
        assertNull(localOnly.serviceRegistryKey)
        assertEquals(CampusServiceCapability.NATIVE, serviceEntryCapability(localOnly, main, null))
    }

    /**
     * `serviceRegistryKey` 的按标题回退表 —— 它是"形状"与"数据"之间的隐式契约。
     *
     * 这四个宿舍生活服务的显示名若被改动，回退会静默失效（能力角标消失、
     * 摘要也取不到），所以在这里钉住。
     */
    @Test
    fun dormServiceTitleFallbackStaysConnected() {
        assertEquals(CampusServiceKeys.ELECTRICITY, entry("宿舍电费").serviceRegistryKey)
        assertEquals(CampusServiceKeys.SHOWER, entry("洗澡").serviceRegistryKey)
        assertEquals(CampusServiceKeys.HOT_WATER, entry("热水").serviceRegistryKey)
        assertEquals(CampusConfig.SERVICE_LAUNDRY, entry("洗衣机").serviceRegistryKey)
        // 显式传入的 serviceKey 优先于标题回退
        assertEquals("explicit", entry("宿舍电费", "explicit").serviceRegistryKey)
    }
}
