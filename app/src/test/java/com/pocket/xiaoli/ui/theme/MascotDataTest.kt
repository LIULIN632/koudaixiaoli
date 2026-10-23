package com.pocket.xiaoli.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 吉祥物**数据层**的不变量。
 *
 * 这一层是"写错了不会报错"的重灾区：形状名写错 → `byName` 静默退回默认（界面上看不出错，
 * 但用户换了形状没反应）；色值写错 → 解析器退回默认蓝（用户选了颜色还是蓝的）。
 * 两者都表现为"点了没反应"，从截图上**完全看不出来** —— 所以只能靠这里的断言。
 *
 * 渲染本身（path 能不能被解析、画出来像不像一条鱼）由
 * `AssistantSettingsScreenshotTest` 的六形状一览守着：`PathParser` 遇到畸形数据**会抛**，
 * 那张基线渲不出来就是红。
 */
class MascotDataTest {

    @Test
    fun `形状名唯一，且默认值确实在表里`() {
        val names = MascotShapes.all.map { it.name }
        assertEquals(
            "形状名重复会让宫格里两个格子同时呈现选中态",
            names.size,
            names.toSet().size,
        )
        assertTrue(
            "默认形状名必须能在表里查到（listOf 的第一条）—— 查不到就会静默退回，改默认永远不生效",
            names.contains(MascotShapes.defaultName),
        )
    }

    @Test
    fun `不认识的形状名退回默认而不是抛`() {
        assertSame(MascotShapes.default, MascotShapes.byName("不存在的水怪"))
        assertSame(MascotShapes.default, MascotShapes.byName(""))
        assertEquals(MascotShapes.default.name, MascotLook(shapeName = "不存在的水怪").shape.name)
    }

    /**
     * path 数据的**结构**检查。
     *
     * 这是廉价但有效的第一道闸：漏一个逗号、混进一个中文全角字符、忘了收尾的 `Z`，
     * 都会在这里红 —— 而不是等到某天有人渲出半条鱼才发现。
     */
    @Test
    fun `每条 path 数据都以 M 开头、只含合法命令，且是闭合图形`() {
        val allowed = Regex("""^[MmLlHhVvCcSsQqTtAaZz0-9.,\-\s]+$""")

        fun check(owner: String, data: String) {
            assertTrue("$owner 的 path 数据必须以 M（移动）开头", data.startsWith("M"))
            assertTrue(
                "$owner 的 path 数据含非法字符（逗号/空格/命令之外的字符）：$data",
                allowed.matches(data),
            )
            assertTrue(
                "$owner 的 path 数据没有闭合（缺 Z）—— 填充会从终点直接拉回起点",
                data.contains("Z") || data.contains("z"),
            )
        }

        MascotShapes.all.forEach { shape ->
            check(shape.name, shape.pathData)
            // 叠色层（阴影 / 高光 / 肚皮）也是 path，同样会过 PathParser
            shape.layers.forEachIndexed { index, layer ->
                check("${shape.name} 的第 ${index + 1} 层", layer.pathData)
            }
            // 轮廓眼（Grok Ball / npface）同样是 path
            shape.eyeLayers.forEachIndexed { index, layer ->
                check("${shape.name} 的第 ${index + 1} 只轮廓眼", layer.pathData)
            }
        }
    }

    @Test
    fun `叠色层的透明度在 0 与 1 之间（不含 0）`() {
        MascotShapes.all.forEach { shape ->
            shape.layers.forEachIndexed { index, layer ->
                assertTrue(
                    "${shape.name} 的第 ${index + 1} 层透明度 ${layer.alpha} 越界：0 等于白画一层，>1 会被裁到 1",
                    layer.alpha > 0f && layer.alpha <= 1f,
                )
            }
        }
    }

    /**
     * 自带配色的边界：**鱼必须留给色盘**，其余（DS 的 7 个 + MIT 的 4 个）自带本色。
     *
     * 这条盯的是"给某条鱼顺手填了个 body"这种手滑 —— 那会让用户点色盘时毫无反应，
     * 而截图上看不出异常（鱼还是那条鱼，只是颜色不跟着变了）。
     *
     * 2026-10 加了 Grok Ball 三种身体 + npface 头盔之后，这里改成**按名字集合**判断，
     * 不再写死「7 个 / 6 条」—— 否则每加一个形象都要来改一次数字（改数字这件事本身没错，
     * 但改错了没有任何东西会红）。
     */
    @Test
    fun `自带配色的形象按名字锁定，鱼仍然吃色盘`() {
        val fish = setOf("鲤鱼", "鲸鱼", "金鱼", "河豚", "水滴鱼", "海豚")
        val withBody = MascotShapes.all.filter { it.body != null }.map { it.name }.toSet()

        assertEquals("鱼不该自带配色（否则色盘点了没反应）", emptySet<String>(), withBody intersect fish)
        assertEquals(
            "自带配色的形象清单变了 —— 新加形象时请顺手把名字加到这里",
            setOf("星星", "蓝鲸", "月亮", "幽灵", "方糖", "睡猫", "雪花", "圆球", "楔子", "宝石", "头盔"),
            withBody,
        )

        MascotShapes.all.filter { it.body != null }.forEach { shape ->
            val body = requireNotNull(shape.body)
            assertEquals("${shape.name} 的兜底色不是不透明色", 0xFFL, body.argb ushr 24)
            // 渐变停靠点必须 0..1 且严格递增：乱序会让 brush 在两个停靠点之间倒着插值，
            // 画出来是脏的色带，但 Compose 不会报错。
            var previous = -1f
            body.gradient.forEach { stop ->
                assertTrue(
                    "${shape.name} 的渐变停靠点 ${stop.offset} 越界",
                    stop.offset in 0f..1f,
                )
                assertTrue(
                    "${shape.name} 的渐变停靠点没有升序：${stop.offset} 排在 $previous 后面",
                    stop.offset > previous,
                )
                previous = stop.offset
            }
        }
    }

    /**
     * 眼位的几何不变量。
     *
     * 两只眼睛**重叠**会糊成一个白团，而那是纯视觉问题、在数据上毫无异常 ——
     * 加这条断言是因为"给新形状抄一份坐标然后忘了改眼距"是几乎必然会发生的事。
     */
    @Test
    fun `眼位在画布内，且两只眼睛不重叠`() {
        MascotShapes.all.forEach { shape ->
            // 轮廓眼（Grok Ball / npface）用路径画，没有椭圆坐标可校验；
            // Grok 的三个身体更彻底：连眼形都不存，按表情现算（见 `projection`）。
            // 但两只眼是一条数据都不能少 —— 少一只就会渲成独眼。
            if (shape.eyes.isEmpty()) {
                val pathEyes = if (shape.projection != null) 2 else shape.eyeLayers.size
                assertEquals(
                    "${shape.name} 既没有椭圆眼也没有轮廓眼",
                    2,
                    pathEyes,
                )
                return@forEach
            }
            shape.eyes.forEach { eye ->
                assertTrue(
                    "${shape.name} 的眼位越出画布：(${eye.x}, ${eye.y})",
                    eye.x in 0f..100f && eye.y in 0f..100f,
                )
                assertTrue("${shape.name} 的眼睛半轴必须为正", eye.rx > 0f && eye.ry > 0f)
                assertTrue(
                    "${shape.name} 的眼睛倾角 ${eye.tilt}° 太夸张（上限 45°）",
                    kotlin.math.abs(eye.tilt) <= 45f,
                )
            }
            if (shape.eyes.size >= 2) {
                val left = shape.eyes[0]
                val right = shape.eyes[1]
                assertTrue(
                    "${shape.name} 的第一只眼睛必须在左边（x 更小）",
                    left.x < right.x,
                )
                val gap = right.x - left.x
                assertTrue(
                    "${shape.name} 的两只眼睛间距 $gap 不够（需要 > ${left.rx + right.rx}），会糊成一个白团",
                    gap > left.rx + right.rx,
                )
            }
        }
    }

    @Test
    fun `调色板无重复、默认色可选，且不含纯黑`() {
        val hexes = MascotPalette.all.map { it.hex }
        assertEquals("色值重复会让色盘里两个格子同时呈现选中态", hexes.size, hexes.toSet().size)
        assertEquals(
            "颜色名重复会让读屏用户分不清两个格子",
            MascotPalette.all.size,
            MascotPalette.all.map { it.label }.toSet().size,
        )
        assertTrue(
            "默认色必须出现在色盘里，否则新用户打开设置页看不到任何选中格",
            hexes.any { it.equals(MascotPalette.DEFAULT, ignoreCase = true) },
        )
        assertTrue(
            "不允许纯黑：深色主题的背景是 #1A1C1E，纯黑的鱼压在上面等于没有形象",
            hexes.none { it.equals("#000000", ignoreCase = true) },
        )
    }

    @Test
    fun `默认色的两个字面量解析后是同一个颜色`() {
        // MascotPalette.DEFAULT（存储用的字符串）与 defaultColor（兜底用的 Color）
        // 是同一个颜色的两个写法。改一个忘一个会让"没选过色"和"存储值坏了"落到两种蓝上。
        assertEquals(MascotPalette.defaultColor, parseMascotColor(MascotPalette.DEFAULT))
    }

    @Test
    fun `颜色解析：合法的照原样，非法的退回默认色`() {
        assertEquals(parseMascotColor("#3B8BFF"), parseMascotColor("#3b8bff"))
        assertEquals(parseMascotColor("#F5C518"), parseMascotColor("  #F5C518  "))
        assertEquals(parseMascotColor("#FFFFFF"), parseMascotColor("#ffffff"))

        // 存储值是不可信输入（老版本写的 / 手改 prefs），一律兜底，绝不抛
        listOf("", "#", "red", "#12345", "#1234567", "#GGGGGG", "#3B8BFF00", "0x3B8BFF").forEach { bad ->
            assertEquals(
                "非法色值「$bad」必须退回默认色而不是抛异常",
                MascotPalette.defaultColor,
                parseMascotColor(bad),
            )
        }
    }

    @Test
    fun `色值到名字的映射：表外的颜色给自定义而不是崩`() {
        assertEquals("蓝", MascotPalette.labelOf("#3B8BFF"))
        assertEquals("自定义", MascotPalette.labelOf("#123456"))
        assertNull(MascotPalette.all.firstOrNull { it.hex == "#123456" })
    }

    /**
     * 存储默认值与渲染默认值必须是**同一个形象**。
     *
     * [TabAppearance] 是"没读过任何 prefs 时"的值，[MascotLook] 是"没人 provide
     * CompositionLocal 时"的值（快照测试走这条）。两者不一致的话，
     * 同一台设备上"刚装好"与"渲一张快照"会看到两条不同的鱼。
     */
    @Test
    fun `TabAppearance 的默认形象与 MascotLook 默认值一致`() {
        val tab = TabAppearance()
        val look = MascotLook()
        assertEquals(look.shapeName, tab.assistantMascotShape)
        assertEquals(look.colorHex, tab.assistantMascotColor)
        assertEquals(tab.assistantMascotLook, look)

        // 而且这个默认名/默认色**真的在表里**（不是靠兜底蒙对的）
        assertTrue(
            "TabAppearance 的默认形状名必须真的在形状表里",
            MascotShapes.all.any { it.name == tab.assistantMascotShape },
        )
        assertTrue(
            "TabAppearance 的默认色必须真的在色盘里",
            MascotPalette.all.any { it.hex.equals(tab.assistantMascotColor, ignoreCase = true) },
        )
    }
}
