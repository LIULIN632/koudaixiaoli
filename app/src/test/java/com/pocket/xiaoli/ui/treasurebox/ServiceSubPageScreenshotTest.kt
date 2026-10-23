package com.pocket.xiaoli.ui.treasurebox

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.data.database.PocketXiaoliDatabase
import com.pocket.xiaoli.data.entity.GoodItemEntity
import com.pocket.xiaoli.data.entity.TeacherEntity
import com.pocket.xiaoli.data.entity.TreeHoleEntity
import com.pocket.xiaoli.data.repository.CourseRepository
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import com.pocket.xiaoli.ui.theme.SkinStyle
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 服务二级页的**真实整页**基线（2026-10-19 服务页美化那一轮的验收图）。
 *
 * ## 为什么这几页能整页渲，别的不能
 *
 * `FocusScreenshotTest` 那类只能渲子组件，卡点是 `AppContainer`。这几页的依赖更浅：
 *
 * | 页 | 数据来源 | 整页可渲 |
 * |---|---|---|
 * | 今天吃什么 / 学习资源 | `TreasureBoxViewModel`（SharedPreferences + Gson） | ✅ Robolectric 自带 Application |
 * | 留声树洞 / 好物清单 / 查询老师 | `CourseRepository`（Room） | ✅ 内存库 + 单线程查询执行器 |
 * | 图书馆座位 / 校园卡 / 宿舍电费 / 选课 | 要发**真实网络请求** | ❌ 不渲 —— 单测里碰校园网是红线 |
 *
 * 网络那四页这轮只改了顶栏与页面底色（同一处公共组件），观感风险低，
 * 由 [com.pocket.xiaoli.ui.components.ServicePageParts] 的共用渲染 + 这里同款的
 * 顶栏/底色承担一致性，不再为了截一张图去 mock 网络层。
 *
 * ## 数据全部写死
 *
 * 时间戳用固定常数（不用 `System.currentTimeMillis()`），否则"更新时间"那行会随日子漂，
 * 基线每天红一次（纪律见 `memory/04-ui-verification.md` §3）。
 * 今天吃什么 / 学习资源两页不喂任何数据 —— 它们本来就有出厂默认列表，
 * 喂反而多一套影子数据。
 *
 * ## Room 的两个非显然设置
 *
 * 1. `singleThreadQueryExecutor`：Compose 测试用后台线程池跑 Flow 收集，
 *    不指定执行器时 Room 用自己的 IO 池，`waitForIdle()` 等不到数据落地（图会是空态）；
 * 2. 每个测试**各建一个库**：`TeacherDao` 的表没有 schemeId，共用库会让
 *    "好物清单"那张图串进老师数据。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class ServiceSubPageScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun render(
        dark: Boolean = false,
        skin: SkinStyle = SkinStyle.VIVID,
        fontScale: Float = 1f,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = skin,
                darkTheme = dark,
            ) {
                // `fontScale != 1f` 时只改字号缩放、density 保持设备值 ——
                // 与用户在系统设置里调"字体大小"对 Compose 的作用方式完全一致。
                // 2026-10 布局修复轮用它验 hero 大数字的基线对齐（见下面两条用例）。
                // 1.0× 时**不套这层**：现有基线要逐像素不变，多一层 provide 就多一分风险。
                if (fontScale == 1f) {
                    content()
                } else {
                    val density = LocalDensity.current
                    CompositionLocalProvider(
                        LocalDensity provides Density(density.density, fontScale),
                    ) { content() }
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** 建一个只有这几个表有数据的内存库（其余表留空，各页只读自己要的那张）。 */
    private fun repository(seed: suspend (PocketXiaoliDatabase) -> Unit): CourseRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, PocketXiaoliDatabase::class.java)
            .allowMainThreadQueries()
            .setTransactionExecutor(Executors.newSingleThreadExecutor())
            .setQueryExecutor(Executors.newSingleThreadExecutor())
            .build()
        runBlocking { seed(db) }
        return CourseRepository(db.courseDao(), db.teacherDao(), db.examDao())
    }

    // ──────────────── 今天吃什么（LIFE） ────────────────

    @Test
    fun `今天吃什么 整页`() {
        render { FoodPickerPage(onBack = {}) }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/200-service-food-vivid.png")
    }

    @Test
    fun `今天吃什么 整页 极简皮肤`() {
        render(skin = SkinStyle.MINIMAL) { FoodPickerPage(onBack = {}) }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/201-service-food-minimal.png")
    }

    @Test
    fun `今天吃什么 整页 深色`() {
        render(dark = true) { FoodPickerPage(onBack = {}) }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/202-service-food-dark.png")
    }

    // ──────────────── 学习资源（STUDY） ────────────────

    @Test
    fun `学习资源 整页`() {
        render { LearningResourcePage(onBack = {}, onImportCourse = { _, _, _, _, _, _ -> }) }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/203-service-learning-vivid.png")
    }

    // ──────────────── 留声树洞（INFO） ────────────────

    /** 固定 2026-10-12 19:40 +08:00 附近的三个时刻，只为让"发布时间"三行永远一样。 */
    private val baseTs = 1_760_000_000_000L

    @Test
    fun `留声树洞 整页`() {
        renderPage(repository(::seedTreeHoles)) { repo ->
            TreeHoleScreen(repository = repo, schemeId = 0, onBack = {})
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/204-service-treehole-vivid.png")
    }

    /**
     * 树洞页的 **360dp 窄屏**版（2026-10 布局修复轮第④阶段）。
     *
     * 守两件事：① hero 卡那组"大数字 + 小单位"在窄屏折行时仍然对齐；
     * ② 右下角 FAB 与列表底部的净空 —— 那一档让位是从 **FAB 的实测高度**推算的，
     * 不是拍一个数，所以窄屏下最后一张卡该能完整滚到 FAB 之下。
     *
     * 宽度约束套在页面外层（页边距、卡片内边距都在页面内部），
     * 得到的可用宽就是 360dp 真机上的那一档。
     */
    @Test
    fun `留声树洞 整页 360dp 窄屏`() {
        renderPage(repository(::seedTreeHoles)) { repo ->
            Box(modifier = Modifier.width(360.dp).fillMaxHeight()) {
                TreeHoleScreen(repository = repo, schemeId = 0, onBack = {})
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/243-narrow360-service-treehole.png")
    }

    /** 三条固定文案 + 固定时间戳的树洞；两张树洞基线共用，免得两边数据漂开。 */
    private suspend fun seedTreeHoles(db: PocketXiaoliDatabase) {
        db.courseDao().insertTreeHole(TreeHoleEntity(content = "图书馆四楼靠窗的位置，期末之前都是我的。", author = "树洞人甲", createdAt = baseTs + 7_200_000L, schemeId = 0))
        db.courseDao().insertTreeHole(TreeHoleEntity(content = "食堂二楼的麻辣香锅换了厨师，变咸了，慎点。", author = "树洞人乙", createdAt = baseTs + 3_600_000L, schemeId = 0))
        db.courseDao().insertTreeHole(TreeHoleEntity(content = "有人捡到一张校园卡，卡在宿管那儿，报了个尾号。", author = "", createdAt = baseTs, schemeId = 0))
    }

    // ──────────────── 好物清单（INFO） ────────────────

    @Test
    fun `好物清单 整页`() {
        renderPage(
            repository { db ->
                db.courseDao().insertGoodItem(GoodItemEntity(name = "桌面理线器", category = "生活", reason = "桌下四根线扫不到地", referencePrice = "¥15", purchased = true, sortOrder = 0, schemeId = 0))
                db.courseDao().insertGoodItem(GoodItemEntity(name = "降噪耳机", category = "数码", reason = "宿舍晚上打游戏外放太吵", description = "早买早享受：自习室也能戴", referencePrice = "¥499", purchased = false, sortOrder = 1, schemeId = 0))
                db.courseDao().insertGoodItem(GoodItemEntity(name = "台灯（护眼）", category = "学习", reason = "上铺翻下来就不开大灯", referencePrice = "¥89", purchased = false, sortOrder = 2, schemeId = 0))
            },
        ) { repo -> GoodItemListScreen(repository = repo, schemeId = 0, onBack = {}) }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/205-service-goodlist-vivid.png")
    }

    // ──────────────── 查询老师（INFO） ────────────────

    @Test
    fun `查询老师 整页`() {
        renderPage(repository(::seedTeachers)) { repo ->
            TeacherLookupScreen(onBack = {}, repository = repo, schemeId = 0)
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/206-service-teacher-vivid.png")
    }

    /**
     * 老师页 hero 卡的**系统字体放大 1.3×**版。
     *
     * 这一张专门守 2026-10 布局修复轮的 P0-2：hero 上"34sp 大数字 + 13sp 单位"
     * 原来靠 `Alignment.Bottom` + `padding(bottom = 7.dp)` 手调对齐，字号一放大
     * 那 7dp 就不再是两者的基线差，单位会浮起来或沉下去。改成 `Alignment.TextBaseline`
     * 之后对齐由字体自己决定 —— 这张图就是那条改动的证据（数字与单位的底不再齐平，
     * 而是基线齐平）。
     */
    @Test
    fun `查询老师 整页 字体放大`() {
        renderPage(repository(::seedTeachers), fontScale = 1.3f) { repo ->
            TeacherLookupScreen(onBack = {}, repository = repo, schemeId = 0)
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/242-largefont-service-teacher.png")
    }

    /** 两位老师：一位资料齐全、一位只有姓名与职称，守 hero 数字与信息行两种密度。 */
    private suspend fun seedTeachers(db: PocketXiaoliDatabase) {
        db.teacherDao().insertTeacher(TeacherEntity(name = "王建国", office = "主楼 305", officeHours = "周三 14:00-16:00", phone = "0791-8888 0305", title = "教授", lastUpdated = baseTs))
        db.teacherDao().insertTeacher(TeacherEntity(name = "李文博", office = "", officeHours = "", phone = "", title = "讲师", lastUpdated = baseTs))
    }

    /**
     * 先建库、再在 `setContent` **之前**把数据插好。
     *
     * ⚠️ 顺序不能反：Room 的 Flow 只在数据变化时发射，若页面先挂上再插，
     * `waitForIdle()` 会先返回一次"空态"，截出来的是空状态卡而不是列表。
     */
    private fun renderPage(
        repo: CourseRepository,
        fontScale: Float = 1f,
        content: @Composable (CourseRepository) -> Unit,
    ) {
        render(fontScale = fontScale) { content(repo) }
    }
}
