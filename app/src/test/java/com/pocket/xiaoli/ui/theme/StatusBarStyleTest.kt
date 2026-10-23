package com.pocket.xiaoli.ui.theme

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * 系统栏底色与图标明暗的守门人。
 *
 * 背景：底色曾经写死在 `res/values/themes.xml`（#FFFFFF，且没有 values-night），
 * 图标明暗却由 `isDark` 单独决定 —— 深色模式下白底白图标，其余时候系统栏比页面白一度。
 * 这两条测试分别守住「判据对」和「XML 里的兜底值与 Kotlin 里的页面底色不脱节」。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StatusBarStyleTest {

    // ── 判据本身 ────────────────────────────────────────────────

    @Test
    fun `亮色页面底色用深色图标`() {
        assertTrue(
            "亮色页底 ${PocketSoftLight.surfaceBase} 应当配深色图标",
            wantsDarkStatusBarIcons(PocketSoftLight.surfaceBase),
        )
    }

    @Test
    fun `深色页面底色用浅色图标`() {
        assertFalse(
            "深色页底 ${PocketSoftDark.surfaceBase} 应当配浅色图标",
            wantsDarkStatusBarIcons(PocketSoftDark.surfaceBase),
        )
    }

    /**
     * 每一套皮肤 × 明暗两种基底，推导出的 `surfaceBase` 都必须落在「明显亮」或「明显暗」，
     * 不能卡在 0.5 附近 —— 否则 `wantsDarkStatusBarIcons` 的 0.5 阈值就成了运气问题。
     */
    @Test
    fun `所有皮肤的页底都不落在中间灰`() {
        SkinStyle.entries.forEach { style ->
            val tokens = style.tokens()
            listOf(true, false).forEach { isDark ->
                val base = tokens.applySurfaces(
                    if (isDark) PocketSoftDark else PocketSoftLight,
                    isDark,
                ).surfaceBase
                val l = base.luminance()
                assertTrue(
                    "皮肤 ${style.key}（isDark=$isDark）的页底 $base 亮度 $l 太接近 0.5，图标明暗会飘",
                    l > 0.75f || l < 0.25f,
                )
                assertEquals(
                    "皮肤 ${style.key}（isDark=$isDark）判据与自己推导的底色不一致",
                    l > 0.5f,
                    wantsDarkStatusBarIcons(base),
                )
            }
        }
    }

    // ── XML 兜底值不能与 Kotlin 真源脱节 ─────────────────────────

    /**
     * Gradle 的工作目录随 task 变（`testDebugUnitTest` 跑在 `app/` 下，IDE 与命令行又不同），
     * 所以三个候选路径一个都不能少 —— 同 `CalendarAssetTest.assetFile()` 的写法。
     */
    private fun resourceFile(relative: String): File {
        val candidates = listOf(
            File("src/main/$relative"),
            File("app/src/main/$relative"),
            File("../app/src/main/$relative"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: throw AssertionError("三个候选路径都没有 $relative（试过 ${candidates.joinToString()}）")
    }

    private fun statusBarHex(relative: String): String {
        val file = resourceFile(relative)
        val match = Regex("""<color name="status_bar">(#[0-9A-Fa-f]{6})</color>""")
            .find(file.readText())
        assertTrue("${file.path} 里没有 status_bar 色值", match != null)
        return match!!.groupValues[1].uppercase()
    }

    private fun Color.hex(): String = "#%06X".format(0xFFFFFF and toArgbInt())

    private fun Color.toArgbInt(): Int =
        (alpha * 255).toInt().shl(24) or
            (red * 255).toInt().shl(16) or
            (green * 255).toInt().shl(8) or
            (blue * 255).toInt()

    @Test
    fun `亮色 status_bar 与页面底色同值`() {
        assertEquals(
            "values/colors.xml 的 status_bar 必须等于 PocketSoftLight.surfaceBase" +
                "（否则冷启动那一帧系统栏会和页面差半度）",
            PocketSoftLight.surfaceBase.hex(),
            statusBarHex("res/values/colors.xml"),
        )
    }

    @Test
    fun `夜版 status_bar 与深色页面底色同值`() {
        assertEquals(
            "values-night/colors.xml 的 status_bar 必须等于 PocketSoftDark.surfaceBase",
            PocketSoftDark.surfaceBase.hex(),
            statusBarHex("res/values-night/colors.xml"),
        )
    }

    @Test
    fun `夜版主题把图标切成浅色`() {
        val night = resourceFile("res/values-night/themes.xml")
        assertTrue("缺少 values-night/themes.xml：深色模式会退回亮色窗口主题", night.isFile)
        val text = night.readText()
        assertTrue(
            "夜版主题必须写 android:windowLightStatusBar=false，否则深底上还是深色图标",
            text.contains("windowLightStatusBar\">false<"),
        )
    }

    // ── 主题本身能被解析出来（写坏父样式会在这里炸，而不是在真机上白屏）──

    private fun themeAttr(attr: Int, night: Boolean): Int {
        val context = if (night) {
            val base = RuntimeEnvironment.getApplication()
            base.createConfigurationContext(
                android.content.res.Configuration(base.resources.configuration).apply {
                    uiMode = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        android.content.res.Configuration.UI_MODE_NIGHT_YES
                },
            )
        } else {
            RuntimeEnvironment.getApplication()
        }
        val attrs = context.obtainStyledAttributes(
            com.pocket.xiaoli.R.style.Theme_PocketXiaoli,
            intArrayOf(attr),
        )
        return try {
            attrs.getColor(0, -1)
        } finally {
            attrs.recycle()
        }
    }

    @Test
    fun `亮色主题能解析出状态栏底色`() {
        assertEquals(
            "亮色 themes.xml 的 statusBarColor 应等于页面底色 #F7F8FA",
            AndroidColor.parseColor("#F7F8FA"),
            themeAttr(android.R.attr.statusBarColor, night = false),
        )
    }

    @Test
    fun `夜版主题能解析出深色状态栏底色`() {
        assertEquals(
            "夜版 themes.xml 的 statusBarColor 应等于深色页面底色 #0F1115",
            AndroidColor.parseColor("#0F1115"),
            themeAttr(android.R.attr.statusBarColor, night = true),
        )
    }
}