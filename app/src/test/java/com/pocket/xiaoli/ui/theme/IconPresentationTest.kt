package com.pocket.xiaoli.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 图标策略合成（[SkinTokens.resolveIcons]）的单元测试。
 *
 * ## 为什么必须测
 *
 * 这套逻辑的**全部差异都由数据表达** —— 渲染端没有 `if (isMinimal)` 分支，
 * 页面只读 [IconPresentation]。所以：
 *
 * > **合成错了，观感就错了，而编译器不会报错。**
 *
 * 典型反例：把 `colorful` 写成 `iconMode == COLORFUL`（漏掉 `AppearanceMode`），
 * 于是"极简 + 彩虹"也会上色 —— 编译通过、界面不崩，只是
 * **"极简 = 单色线性图标"这条风格承诺静默失效**。这类错误只能靠断言拦。
 *
 * ## 这里守的是三条设计决定
 *
 * 1. **皮肤只否决、不做加法** —— 极简声明 `MONOCHROME` 后，用户选彩虹也上不了色
 * 2. **线形 / 面性正交于用色强度** —— 调颜色不该改图标几何形状
 * 3. **衬底与彩色同生共死** —— 单色态还留彩色底就不叫单色
 *
 * 另有一条**穷举守卫**：四象限表必须覆盖 `AppearanceMode.entries` 的全部组合。
 * 将来加第三个用色强度时会直接红，逼着补决策，而不是静默沿用旧行为。
 */
class IconPresentationTest {

    private val skins: List<Pair<String, SkinTokens>> = listOf(
        "极简" to MinimalSkin,
        "鲜活" to VividSkin,
    )

    /**
     * 四象限期望表 —— 本测试的**单一真源**。
     *
     * 字段顺序即 [IconPresentation] 的构造顺序：
     * `(colorful, filled, showContainer, colorfulTags)`。
     */
    private val table: Map<Pair<SkinTokens, AppearanceMode>, IconPresentation> = linkedMapOf(
        // 极简：灰底白卡 + 小圆角 + 无阴影 + **单色线性图标**，用色强度对它无效
        (MinimalSkin to AppearanceMode.RAINBOW) to IconPresentation(false, false, false, false),
        (MinimalSkin to AppearanceMode.PURE) to IconPresentation(false, false, false, false),
        // 鲜活：纯白 + 大圆角 + 弥散阴影 + **多色面性图标**，但尊重用户的用色强度
        (VividSkin to AppearanceMode.RAINBOW) to IconPresentation(true, true, true, true),
        // 「鲜活 + 纯净」= 面性但单色：形状跟皮肤、颜色跟用户
        (VividSkin to AppearanceMode.PURE) to IconPresentation(false, true, false, false),
    )

    // ══════════════════════════════════════════════════════════════
    // 穷举：合成结果必须与表逐格一致
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `合成结果必须与四象限表逐格一致`() {
        table.forEach { (key, want) ->
            val (skin, appearance) = key
            assertEquals(
                "合成结果与期望不符：skin=$skin appearance=${appearance.label}",
                want,
                skin.resolveIcons(appearance),
            )
        }
    }

    @Test
    fun `四象限表必须覆盖全部皮肤与用色强度组合`() {
        // 新增 AppearanceMode 条目时这条会红 —— 那是刻意的：
        // 逼着为新的用色强度补一条决策，而不是让它静默沿用旧行为。
        val allCombos = skins.flatMap { (_, skin) -> AppearanceMode.entries.map { skin to it } }
        assertEquals(
            "合成表漏了组合，或表里有皮肤实例写错。当前 AppearanceMode=${AppearanceMode.entries}",
            allCombos.size,
            table.size,
        )
        allCombos.forEach { combo ->
            assertTrue("四象限表缺少组合：$combo", table.containsKey(combo))
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 决定一：皮肤只否决、不做加法
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `极简下任何用色强度都不能让图标上色`() {
        AppearanceMode.entries.forEach { appearance ->
            assertFalse(
                "极简 × ${appearance.label} 竟然上色了 —— " +
                    "「极简 = 单色线性图标」这条风格承诺失效",
                MinimalSkin.resolveIcons(appearance).colorful,
            )
        }
    }

    @Test
    fun `鲜活加彩虹才上色`() {
        assertTrue(
            "鲜活 × 彩虹应当是彩色图标",
            VividSkin.resolveIcons(AppearanceMode.RAINBOW).colorful,
        )
    }

    @Test
    fun `鲜活加纯净必须退回单色`() {
        assertFalse(
            "鲜活 × 纯净竟然是彩色 —— 说明合成漏掉了 AppearanceMode，" +
                "用户调「用色强度」将毫无效果",
            VividSkin.resolveIcons(AppearanceMode.PURE).colorful,
        )
    }

    // ══════════════════════════════════════════════════════════════
    // 决定二：线形 / 面性正交于用色强度
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `线形面性必须正交于用色强度 —— 改颜色不该改形状`() {
        // 若将来有人把 filled 也并入交集（"纯净模式也退回线性"），这条会红。
        // 那时请先确认产品意图：让"用色强度"这个旋钮同时改变图标几何形状，
        // 反馈面会超出用户预期。
        skins.forEach { (name, skin) ->
            assertEquals(
                "$name 的 filled 随用色强度变了 —— 图标形状不该被颜色偏好影响",
                skin.resolveIcons(AppearanceMode.RAINBOW).filled,
                skin.resolveIcons(AppearanceMode.PURE).filled,
            )
        }
    }

    @Test
    fun `极简是线性图标、鲜活是面性图标`() {
        assertFalse("极简应为线性（Icons.Outlined）", MinimalSkin.resolveIcons(AppearanceMode.RAINBOW).filled)
        assertTrue("鲜活应为面性（Icons.Filled）", VividSkin.resolveIcons(AppearanceMode.RAINBOW).filled)
    }

    // ══════════════════════════════════════════════════════════════
    // 决定三：衬底与彩色同生共死
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `彩色衬底必须与 colorful 同生共死`() {
        table.forEach { (key, want) ->
            val (skin, appearance) = key
            val got = skin.resolveIcons(appearance)
            assertEquals(
                "衬底与彩色不一致（skin=$skin appearance=${appearance.label}）：" +
                    "单色态留彩色底就不叫单色；彩色态没有底就丢了「面性」",
                got.colorful,
                got.showContainer,
            )
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 彩色标签同样受上限约束
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `彩色标签受皮肤上限约束`() {
        assertFalse(
            "极简不该有彩色标签",
            MinimalSkin.resolveIcons(AppearanceMode.RAINBOW).colorfulTags,
        )
        assertTrue(
            "鲜活 × 彩虹应当有彩色标签",
            VividSkin.resolveIcons(AppearanceMode.RAINBOW).colorfulTags,
        )
        assertFalse(
            "鲜活 × 纯净不该有彩色标签 —— 与图标的降级必须一致",
            VividSkin.resolveIcons(AppearanceMode.PURE).colorfulTags,
        )
    }

    @Test
    fun `colorfulTags 与 colorful 在当前数据下必须一致`() {
        // 记录一处**刻意的冗余**：以现有两套皮肤，colorfulTags ≡ colorful。
        // 两个字段分开保留，是为了将来可能出现"彩色图标 + 中性角标"的第三套皮肤。
        // 这条测试同时是提醒：若某天它红了，说明真的出现了分岔的皮肤 ——
        // 那时请删掉这条断言（而不是把数据改回去）。
        table.forEach { (key, want) ->
            val (skin, appearance) = key
            val got = skin.resolveIcons(appearance)
            assertEquals(
                "colorfulTags 与 colorful 分岔了（skin=$skin appearance=${appearance.label}）—— " +
                    "若这是新皮肤的有意设计，请删掉本断言并更新文档",
                got.colorful,
                got.colorfulTags,
            )
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 查表完整性：每套 SkinStyle 都有令牌实例
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `每套 SkinStyle 都能查到令牌实例且互不相同`() {
        val resolved = SkinStyle.entries.map { it to it.tokens() }
        resolved.forEach { (style, tokens) ->
            assertNotNull("$style 没有对应的令牌实例", tokens)
        }
        assertEquals(
            "有 SkinStyle 条目查到了重复的令牌实例 —— tokens() 映射漏了分支",
            SkinStyle.entries.size,
            resolved.map { it.second }.distinct().size,
        )
    }

    @Test
    fun `resolveIcons 是纯函数 —— 同样输入必须得到同样输出`() {
        skins.forEach { (name, skin) ->
            AppearanceMode.entries.forEach { appearance ->
                assertEquals(
                    "$name × ${appearance.label} 两次调用结果不同 —— 合成里混进了状态",
                    skin.resolveIcons(appearance),
                    skin.resolveIcons(appearance),
                )
            }
        }
    }
}
