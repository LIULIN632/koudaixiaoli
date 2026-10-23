package com.pocket.xiaoli.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.data.CampusConfig
import com.pocket.xiaoli.data.model.LastImportInfo
import com.pocket.xiaoli.data.service.ServiceAutoLoginMode
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.util.BindingValidators
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import com.pocket.xiaoli.ui.theme.SkinStyle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 「绑定信息」页（`AccountBindingScreen`）的外观快照。
 *
 * ## 为什么只能渲"页面里的块"，不能渲整个 `AccountBindingScreen`
 *
 * 那个函数的第一个参数是 `AppContainer`（真 DI 容器，构造要 Context + 加密存储），
 * 单测里造不出来 —— 项目现有的 120 个测试类里一个都没造过它。
 * 所以这里渲的是**页面实际会显示的几块本体**（`BindingStatusCard` / `DangerRow` /
 * `BindingSectionCard`），按页面真实的**边距与卡间距**摆在一起：
 *
 * | 文件 | 对应真实页面的哪一屏 |
 * |---|---|
 * | `80-binding-home-bound` | 主页面（全绑定）：状态卡 + 「账号管理」入口行 |
 * | `81-binding-home-fresh` | 主页面（全新用户）—— ⚠️ 此时入口行**不渲染**（`hasManageableAccount == false`），所以这一张只有状态卡 |
 * | `82-binding-home-mixed` | 主页面（混合态：导入失败 + 门户未登录 + 学习平台 1/2） |
 * | `83-binding-home-mixed-dark` | 同上，深色 |
 * | `84-binding-manage` | 二级页「账号管理」（4 条销毁性操作） |
 * | `85-binding-academic-form` | 二级页「教务账号」表单（已填 · 密码隐藏 · 初始密码捷径在 · 可保存） |
 * | `86-binding-academic-form-empty` | 同上，全新用户（空表单 · 「保存绑定」**禁用态** · 无初始密码捷径） |
 * | `87-binding-academic-form-dark` | 同上，深色 + 密码**明文可见**（眼睛图标切过的那一态） |
 *
 * ⚠️ **这几块是按真实调用点的参数复现的**，不是另画一份仿制品（`UI验收手册.md` 坑 2 记过
 * "预览与实际漂移"的账）。
 *
 * 2026-10 之前，**表单类二级页**（教务账号 / 今日校园 / 校园卡 / 宿舍）一个都没在这组里 ——
 * 它们的字段状态有二十来个 `remember`，整页又吃 `AppContainer`。那一轮把「教务账号」的
 * 表单本体抽成了 `AcademicAccountSection`（纯组件，见它的 KDoc），于是
 * `85..87` 渲的就是**生产代码本体**。今日校园 / 校园卡 / 宿舍三张表单**仍未覆盖**，
 * 原因相同（字段状态还在整页里）；要覆盖得先照同样的办法抽出来。
 *
 * ## 确定性
 *
 * `LastImportInfo.timestamp` 默认取 `System.currentTimeMillis()`，直接用它会让基准图
 * **每天都变**（`UI验收手册.md` 的第一条警告）。这里传固定值。
 * ⚠️ 残留风险：`formatImportTime` 用 `MM-dd HH:mm` 格式化，**跨时区会不同** ——
 * 基准图因此在别的时区机器上可能需要重录。取 UTC 正午附近的时刻把日期部分钉稳。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class AccountBindingScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private companion object {
        /**
         * 固定基准时刻（与 `AssistantWelcomeAndListScreenshotTest.NOW` 同一个值，
         * 便于全仓只记一个"测试用现在"）。UTC 10:00，日期部分在东八区/UTC/西八区都是同一天。
         */
        const val NOW = 1780826400000L
    }

    private val campus = CampusConfig.campuses.first()

    /** 渲染用例：文件名 + 状态卡的入参 + 是否深色 + 是否渲「账号管理」二级页。 */
    private data class Case(
        val name: String,
        val dark: Boolean,
        val isBound: Boolean,
        val lastImport: LastImportInfo?,
        val dormLabel: String?,
        val casBound: Boolean,
        val portalLoggedIn: Boolean?,
        val cardBound: Boolean,
        val chaoxing: Boolean,
        val zhihuishu: Boolean,
        val managePage: Boolean = false,
    )

    private val cases = listOf(
        // 全绑定 + 最近一次导入成功
        Case(
            name = "80-binding-home-bound",
            dark = false,
            isBound = true,
            lastImport = LastImportInfo(
                source = "教务直连",
                courseCount = 42,
                success = true,
                timestamp = NOW - 2 * 60 * 60 * 1000L,
            ),
            dormLabel = "3号楼 412",
            casBound = true,
            portalLoggedIn = true,
            cardBound = true,
            chaoxing = true,
            zhihuishu = true,
        ),
        // 全新用户：什么都没绑，且没有可管理的账号 → 入口行不渲染
        Case(
            name = "81-binding-home-fresh",
            dark = false,
            isBound = false,
            lastImport = null,
            dormLabel = null,
            casBound = false,
            portalLoggedIn = null,
            cardBound = false,
            chaoxing = false,
            zhihuishu = false,
        ),
        // 混合态：最有信息量的一屏（导入失败 + 已绑但未登录 + 学习平台只通一半）
        Case(
            name = "82-binding-home-mixed",
            dark = false,
            isBound = true,
            lastImport = LastImportInfo(
                source = "网页导入",
                courseCount = 0,
                success = false,
                detail = "登录态失效",
                timestamp = NOW - 26 * 60 * 60 * 1000L,
            ),
            dormLabel = null,
            casBound = true,
            portalLoggedIn = false,
            cardBound = false,
            chaoxing = true,
            zhihuishu = false,
        ),
        // 同上，深色
        Case(
            name = "83-binding-home-mixed-dark",
            dark = true,
            isBound = true,
            lastImport = LastImportInfo(
                source = "网页导入",
                courseCount = 0,
                success = false,
                detail = "登录态失效",
                timestamp = NOW - 26 * 60 * 60 * 1000L,
            ),
            dormLabel = null,
            casBound = true,
            portalLoggedIn = false,
            cardBound = false,
            chaoxing = true,
            zhihuishu = false,
        ),
        // 二级页「账号管理」
        Case(
            name = "84-binding-manage",
            dark = false,
            isBound = true,
            lastImport = null,
            dormLabel = "3号楼 412",
            casBound = true,
            portalLoggedIn = true,
            cardBound = true,
            chaoxing = true,
            zhihuishu = true,
            managePage = true,
        ),
    )

    /**
     * 「教务账号」表单那一屏的入参。
     *
     * 单独一组、单独一个用例函数：它与上面的 `Case`（状态卡的各种绑定组合）**互不相干**，
     * 硬塞进同一个 `Case` 只会让每个字段都变成"两组用例各用一半"的可选参数。
     */
    private data class FormCase(
        val name: String,
        val dark: Boolean,
        val school: String,
        val studentId: String,
        val password: String,
        val passwordVisible: Boolean,
        /** true = 渲染"点过提交之后"的形态：三个字段下方带红字原因 */
        val showErrors: Boolean = false,
        /** 「验证并保存」那条路的结果 */
        val verify: AcademicVerifyUi = AcademicVerifyUi.Idle,
    )

    private val formCases = listOf(
        // 已填 + 密码与「初始密码」不同 → 显示"填入初始密码"捷径；两个动作都可点
        FormCase(
            name = "85-binding-academic-form",
            dark = false,
            school = "烟台理工学院",
            studentId = "2023123456789",
            password = "我自己改过的密码",
            passwordVisible = false,
        ),
        // 校验错误态：空表单点过「验证并保存」之后 —— 每个字段下方给出**具体原因**
        // （这正是改造前最缺的东西：按钮静默禁用，不说为什么）
        FormCase(
            name = "86-binding-academic-form-invalid",
            dark = false,
            school = "",
            studentId = "2023abc",
            password = "123",
            passwordVisible = false,
            showErrors = true,
        ),
        // 深色 + 明文可见（眼睛图标切过的那一态）；密码**等于**初始密码 → 捷径隐藏
        FormCase(
            name = "87-binding-academic-form-dark",
            dark = true,
            school = "烟台理工学院",
            studentId = "2023123456789",
            password = "Ytlg@56789",
            passwordVisible = true,
        ),
        // 验证失败：学号或密码不对（或验证码错、网络不通）时界面长什么样。
        // 这是"验证账号"这个功能里**最需要看懂**的一屏，所以单独占一张基线。
        FormCase(
            name = "88-binding-academic-verify-failed",
            dark = false,
            school = "烟台理工学院",
            studentId = "2023123456789",
            password = "错的密码哈哈哈",
            passwordVisible = false,
            verify = AcademicVerifyUi.Failed("学号或密码不正确，请核对后重试"),
        ),
    )

    @Test
    fun `绑定信息页教务账号表单截图`() {
        val current = mutableStateOf(formCases.first())
        composeRule.setContent {
            val case = current.value
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = case.dark,
            ) {
                AcademicFormPage(case)
            }
        }

        formCases.forEach { case ->
            composeRule.runOnIdle { current.value = case }
            composeRule.waitForIdle()
            composeRule.onRoot().captureRoboImage("src/test/screenshots/${case.name}.png")
        }
    }

    /**
     * 二级页「教务账号」—— 渲**生产组件本体** `AcademicAccountSection`
     *（它正是为了这件事从整页里抽出来的），外套真实的 `BindingSectionCard`。
     */
    @Composable
    private fun AcademicFormPage(case: FormCase) {
        val skin = LocalSkin.current
        val colors = LocalPocketXiaoliColors.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.surfaceBase)
                .padding(
                    start = skin.screenH,
                    end = skin.screenH,
                    top = skin.spaceMd,
                    bottom = skin.spaceXxl,
                ),
            verticalArrangement = Arrangement.spacedBy(skin.sectionGap),
        ) {
            BindingSectionCard(
                icon = Icons.Outlined.Person,
                title = "教务账号",
                subtitle = "账号密码仅加密保存在本机",
            ) {
                AcademicAccountSection(
                    school = case.school,
                    onSchoolChange = {},
                    studentId = case.studentId,
                    onStudentIdChange = {},
                    password = case.password,
                    onPasswordChange = {},
                    passwordVisible = case.passwordVisible,
                    onTogglePasswordVisible = {},
                    onVerifyAndSave = {},
                    onSaveOnly = {},
                    // 错误文案由页面用 BindingValidators 算好传进来（组件保持纯展示），
                    // 所以这里也照那个办法算 —— 与生产代码同一条规则，不是另写一套判据。
                    schoolError = if (case.showErrors) BindingValidators.schoolName(case.school) else null,
                    studentIdError = if (case.showErrors) BindingValidators.studentId(case.studentId) else null,
                    passwordError = if (case.showErrors) BindingValidators.password(case.password) else null,
                    verify = case.verify,
                    middle = {
                        SettingsChoiceItem(
                            icon = Icons.AutoMirrored.Outlined.Login,
                            title = "服务自动登录",
                            subtitle = "打开校园服务时自动填入上面的账号",
                            options = ServiceAutoLoginMode.entries.map { it.label to it },
                            selected = ServiceAutoLoginMode.AUTO_LOGIN,
                            onSelect = {},
                            horizontalPadding = 0.dp,
                        )
                    },
                )
            }
        }
    }

    @Test
    fun `绑定信息页各屏截图`() {
        val current = mutableStateOf(cases.first())
        composeRule.setContent {
            val case = current.value
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = case.dark,
            ) {
                if (case.managePage) {
                    ManagePage()
                } else {
                    BindingHomePage(case)
                }
            }
        }

        cases.forEach { case ->
            composeRule.runOnIdle { current.value = case }
            composeRule.waitForIdle()
            composeRule.onRoot().captureRoboImage("src/test/screenshots/${case.name}.png")
        }
    }

    /** 复现主页面：状态卡 + 「账号管理」入口行（后者按 `hasManageableAccount` 决定在不在）。 */
    @Composable
    private fun BindingHomePage(case: Case) {
        val skin = LocalSkin.current
        val colors = LocalPocketXiaoliColors.current
        val hasManageableAccount =
            case.isBound || case.portalLoggedIn == true || case.casBound || case.cardBound
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.surfaceBase)
                // 页面真实边距：左右 screenH、顶 spaceMd、底 spaceXxl
                .padding(
                    start = skin.screenH,
                    end = skin.screenH,
                    top = skin.spaceMd,
                    bottom = skin.spaceXxl,
                ),
            verticalArrangement = Arrangement.spacedBy(skin.sectionGap),
        ) {
            BindingStatusCard(
                campus = campus,
                savedSchool = "烟台理工学院",
                isBound = case.isBound,
                studentId = if (case.isBound) "2023123456789" else "",
                lastImport = case.lastImport,
                chaoxingBound = case.chaoxing,
                zhihuishuBound = case.zhihuishu,
                casBound = case.casBound,
                casStudentId = if (case.casBound) "2023123456789" else "",
                portalLoggedIn = case.portalLoggedIn,
                dormLabel = case.dormLabel,
                cardBound = case.cardBound,
                onNavigate = {},
            )
            DangerRow(
                visible = hasManageableAccount,
                trailingArrow = true,
                title = "账号管理",
                subtitle = "解绑教务 · 退出 / 删除本机保存的账号",
                onClick = {},
            )
        }
    }

    /** 复现二级页「账号管理」—— 直接渲**生产组件本体** `AccountDangerCard`，不再另写仿制品。 */
    @Composable
    private fun ManagePage() {
        val skin = LocalSkin.current
        val colors = LocalPocketXiaoliColors.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.surfaceBase)
                .padding(
                    start = skin.screenH,
                    end = skin.screenH,
                    top = skin.spaceMd,
                    bottom = skin.spaceXxl,
                ),
            verticalArrangement = Arrangement.spacedBy(skin.sectionGap),
        ) {
            // 行文案与真实条件分支一致（isBound / portalLoggedIn / casBound / cardBound 全真）
            AccountDangerCard(
                rows = listOf(
                    PendingDanger(
                        title = "解绑教务账号",
                        rowSubtitle = "删除本机保存的「${campus.name}」学号与密码",
                        message = "将删除 ${campus.name} 在本机保存的学号和密码，确定吗？",
                        confirmLabel = "确认解绑",
                        onConfirm = {},
                    ),
                    PendingDanger(
                        title = "退出今日校园登录态",
                        rowSubtitle = "只清理本机的登录 Cookie，账号还在",
                        message = "将清理本机保存的今日校园登录 Cookie，账号与密码仍留在本机。确定吗？",
                        confirmLabel = "确认退出",
                        onConfirm = {},
                    ),
                    PendingDanger(
                        title = "删除今日校园账号",
                        rowSubtitle = "删除本机保存的学号与密码",
                        message = "将删除本机保存的今日校园学号与密码（教务账号不受影响）。确定吗？",
                        confirmLabel = "确认删除",
                        onConfirm = {},
                    ),
                    PendingDanger(
                        title = "删除校园卡账号",
                        rowSubtitle = "删除本机保存的手机号与密码",
                        message = "将删除本机保存的易校园手机号与密码，校园余额页随即不可用。确定吗？",
                        confirmLabel = "确认删除",
                        onConfirm = {},
                    ),
                ),
                onPick = {},
            )
        }
    }
}
