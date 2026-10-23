package com.pocket.xiaoli.ui.import_

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.data.entity.SchemeEntity
import com.pocket.xiaoli.data.model.LastImportInfo
import com.pocket.xiaoli.ui.profile.components.ShareMenuBody
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import com.pocket.xiaoli.ui.theme.SkinStyle
import com.pocket.xiaoli.ui.timetable.components.SchemeManagerBody
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 三条「我的 → 课表与学习」链路改版后的外观与语义判据：
 *
 * | 链路 | 外壳 | 基线 |
 * |---|---|---|
 * | 导入课表 | 整页（Scaffold） | `280..283` |
 * | 课表管理 | `WindowBottomSheet` | `284..286` |
 * | 备份与分享 | `WindowBottomSheet` | `287..288` |
 *
 * ## 为什么抽屉只渲正文
 *
 * `WindowBottomSheet` 渲染在**独立窗口**里，JVM 快照抓的是 Compose 根节点，
 * 抓不到那个窗口 —— 与 `AppUpdateSheetScreenshotTest` 同一条限制。
 * 正文抽成 `internal` 正是为了这里能单独上镜；外面套的 [SheetFrame]
 * 只是还原"底部卡片 + 页面底色"，不参与业务。
 *
 * ## 语义测试守的是什么
 *
 * 截图只能证明"好看"，证明不了"点得动"。旧「备份与分享」是七行平铺文字，
 * 现在每行都有副标题与分组 —— 一旦某个 `onClick` 接错，截图完全看不出来，
 * 所以这里逐行点一遍，确认七个回调各归各位。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class ImportShareUiScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var dark by mutableStateOf(false)

    // 固定时间戳：2025-10-09 前后，避免基线每天都不一样。
    private val successImport = LastImportInfo(
        source = "教务直连",
        courseCount = 11,
        success = true,
        detail = null,
        timestamp = 1_760_000_000_000L,
    )

    private val failedImport = LastImportInfo(
        source = "网页导入",
        courseCount = 0,
        success = false,
        detail = "登录超时，请重试",
        timestamp = 1_760_000_000_000L,
    )

    private val schemes = listOf(
        SchemeEntity(id = 0, name = "默认课表", sortOrder = 0),
        SchemeEntity(id = 1, name = "2026 秋 · 主修", sortOrder = 1),
        SchemeEntity(id = 2, name = "辅修 / 重修", sortOrder = 2),
    )

    // ── 导入课表 ──────────────────────────────────────────────

    private data class EntryCase(
        val name: String,
        val dark: Boolean,
        val lockedSchool: String?,
        val boundStudentId: String?,
        val lastImport: LastImportInfo?,
        val hasFileImport: Boolean,
    )

    private val entryCases = listOf(
        EntryCase(
            name = "280-import-entry-light",
            dark = false,
            lockedSchool = "示例大学",
            boundStudentId = "20239999",
            lastImport = successImport,
            hasFileImport = true,
        ),
        EntryCase(
            name = "281-import-entry-dark",
            dark = true,
            lockedSchool = "示例大学",
            boundStudentId = "20239999",
            lastImport = successImport,
            hasFileImport = true,
        ),
        // 最空的形态：没锁定学校、没文件入口、也没导入过 —— 首屏只剩两张自动导入卡。
        EntryCase(
            name = "282-import-entry-empty",
            dark = false,
            lockedSchool = null,
            boundStudentId = null,
            lastImport = null,
            hasFileImport = false,
        ),
        // 失败态：红色状态块 + 「查看全部」，但**不出现**「去课表」。
        EntryCase(
            name = "283-import-entry-failed",
            dark = false,
            lockedSchool = "示例大学",
            boundStudentId = "20239999",
            lastImport = failedImport,
            hasFileImport = true,
        ),
    )

    @Test
    fun `导入课表入口页截图`() {
        val current = mutableStateOf(entryCases.first())
        composeRule.setContent {
            val case = current.value
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = case.dark,
            ) {
                TimetableImportEntryScreen(
                    onBack = {},
                    onWebImport = {},
                    onDirectImport = {},
                    onFileImport = if (case.hasFileImport) ({}) else null,
                    lastImport = case.lastImport,
                    onOpenTimetable = {},
                    onOpenHistory = {},
                    lockedSchoolName = case.lockedSchool,
                    boundStudentId = case.boundStudentId,
                )
            }
        }
        entryCases.forEach { case ->
            composeRule.runOnIdle { current.value = case }
            composeRule.waitForIdle()
            composeRule.onRoot().captureRoboImage("src/test/screenshots/${case.name}.png")
        }
    }

    @Test
    fun `导入课表每张卡与最近一次的动作各归各位`() {
        var web = 0
        var direct = 0
        var file = 0
        var timetable = 0
        var history = 0

        composeRule.setContent {
            PocketXiaoliTheme(appearanceMode = AppearanceMode.RAINBOW, skinStyle = SkinStyle.VIVID) {
                TimetableImportEntryScreen(
                    onBack = {},
                    onWebImport = { web++ },
                    onDirectImport = { direct++ },
                    onFileImport = { file++ },
                    lastImport = successImport,
                    onOpenTimetable = { timetable++ },
                    onOpenHistory = { history++ },
                    lockedSchoolName = "示例大学",
                    boundStudentId = "20239999",
                )
            }
        }

        composeRule.onNodeWithText("网页导入").performScrollTo().performClick()
        composeRule.onNodeWithText("教务直连导入").performScrollTo().performClick()
        composeRule.onNodeWithText("文件导入").performScrollTo().performClick()
        composeRule.onNodeWithText("查看全部").performScrollTo().performClick()
        composeRule.onNodeWithText("去课表").performScrollTo().performClick()

        assertEquals(1, web)
        assertEquals(1, direct)
        assertEquals(1, file)
        assertEquals(1, timetable)
        assertEquals(1, history)

        // 学号只用于预填，**不允许**出现在这个会被截图分享的页面上。
        composeRule.onAllNodesWithText("20239999", substring = true)
            .assertCountEquals(0)
    }

    @Test
    fun `导入失败时不给去课表`() {
        composeRule.setContent {
            PocketXiaoliTheme(appearanceMode = AppearanceMode.RAINBOW, skinStyle = SkinStyle.VIVID) {
                TimetableImportEntryScreen(
                    onBack = {},
                    onWebImport = {},
                    onDirectImport = {},
                    lastImport = failedImport,
                    onOpenTimetable = {},
                    onOpenHistory = {},
                )
            }
        }
        composeRule.onNodeWithText("登录超时，请重试").assertExists()
        composeRule.onNodeWithText("去课表").assertDoesNotExist()
        composeRule.onNodeWithText("查看全部").assertExists()
    }

    // ── 课表管理 ──────────────────────────────────────────────

    @Test
    fun `课表管理抽屉内容截图`() {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = dark,
            ) {
                SheetFrame {
                    SchemeManagerBody(
                        schemes = schemes,
                        activeSchemeId = 0L,
                        onSwitch = {},
                        onCreate = {},
                        onRename = {},
                        onDelete = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/284-scheme-manager-light.png")

        dark = true
        composeRule.waitForIdle()
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/285-scheme-manager-dark.png")
    }

    @Test
    fun `课表管理空态截图`() {
        composeRule.setContent {
            PocketXiaoliTheme(appearanceMode = AppearanceMode.RAINBOW, skinStyle = SkinStyle.VIVID) {
                SheetFrame {
                    SchemeManagerBody(
                        schemes = emptyList(),
                        activeSchemeId = 0L,
                        onSwitch = {},
                        onCreate = {},
                        onRename = {},
                        onDelete = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/286-scheme-manager-empty.png")
    }

    @Test
    fun `课表管理切换重命名删除新建各归各位`() {
        val switched = mutableListOf<Long>()
        val renamed = mutableListOf<Long>()
        val deleted = mutableListOf<Long>()
        var created = 0

        composeRule.setContent {
            PocketXiaoliTheme(appearanceMode = AppearanceMode.RAINBOW, skinStyle = SkinStyle.VIVID) {
                SheetFrame {
                    SchemeManagerBody(
                        schemes = schemes,
                        activeSchemeId = 0L,
                        onSwitch = { switched += it.id },
                        onCreate = { created++ },
                        onRename = { renamed += it.id },
                        onDelete = { deleted += it.id },
                    )
                }
            }
        }

        composeRule.onNodeWithText("2026 秋 · 主修").performClick()
        composeRule.onNodeWithContentDescription("重命名「默认课表」").performClick()
        composeRule.onNodeWithContentDescription("删除「辅修 / 重修」").performClick()
        composeRule.onNodeWithText("新建方案").performScrollTo().performClick()

        assertEquals(listOf(1L), switched)
        assertEquals(listOf(0L), renamed)
        assertEquals(listOf(2L), deleted)
        assertEquals(1, created)

        // 默认方案（id = 0）没有删除入口 —— 它是数据库种子的空壳，删掉会让 App 无处可落。
        composeRule.onNodeWithContentDescription("删除「默认课表」").assertDoesNotExist()
    }

    // ── 备份与分享 ────────────────────────────────────────────

    @Test
    fun `备份与分享抽屉内容截图`() {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = dark,
            ) {
                SheetFrame {
                    ShareMenuBody(
                        onGenerateCode = {},
                        onExportBackup = {},
                        onExportCsv = {},
                        onExportIcs = {},
                        onExportFullBackup = {},
                        onRestoreFullBackup = {},
                        onExportImage = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/287-share-menu-light.png")

        dark = true
        composeRule.waitForIdle()
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/288-share-menu-dark.png")
    }

    @Test
    fun `备份与分享七行各归各位`() {
        val hits = mutableListOf<String>()

        composeRule.setContent {
            PocketXiaoliTheme(appearanceMode = AppearanceMode.RAINBOW, skinStyle = SkinStyle.VIVID) {
                SheetFrame {
                    ShareMenuBody(
                        onGenerateCode = { hits += "code" },
                        onExportBackup = { hits += "backup" },
                        onExportCsv = { hits += "csv" },
                        onExportIcs = { hits += "ics" },
                        onExportFullBackup = { hits += "full" },
                        onRestoreFullBackup = { hits += "restore" },
                        onExportImage = { hits += "image" },
                    )
                }
            }
        }

        listOf(
            "生成分享口令",
            "导出课表图片",
            "课表备份文件",
            "Excel / CSV",
            ".ics 日历文件",
            "导出完整备份",
            "恢复完整备份",
        ).forEach { title ->
            composeRule.onNodeWithText(title).performScrollTo().performClick()
        }

        assertEquals(
            listOf("code", "image", "backup", "csv", "ics", "full", "restore"),
            hits,
        )
    }
}

/** 模拟底部抽屉在页面上的位置与底色（只是外壳，不参与业务）。 */
@Composable
private fun SheetFrame(content: @Composable () -> Unit) {
    val colors = LocalPocketXiaoliColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceBase),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(colors.surfaceCard)
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            content()
        }
    }
}
