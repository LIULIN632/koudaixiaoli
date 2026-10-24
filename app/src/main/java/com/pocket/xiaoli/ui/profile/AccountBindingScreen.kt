package com.pocket.xiaoli.ui.profile

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.HomeWork
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocket.xiaoli.data.CampusConfig
import com.pocket.xiaoli.data.CampusInfo
import com.pocket.xiaoli.data.jwTypeLabel
import com.pocket.xiaoli.data.learning.LearningPlatform
import com.pocket.xiaoli.data.card.YxyCardRepository
import com.pocket.xiaoli.data.model.LastImportInfo
import com.pocket.xiaoli.data.service.CasLogin
import com.pocket.xiaoli.data.service.ServiceAutoLoginMode
import com.pocket.xiaoli.data.service.TodayCampusPortal
import com.pocket.xiaoli.di.AppContainer
import com.pocket.xiaoli.network.CookieStore
import com.pocket.xiaoli.ui.components.DormBindingContent
import com.pocket.xiaoli.ui.components.PocketField
import com.pocket.xiaoli.ui.components.PocketPasswordTransformation
import kotlinx.coroutines.launch
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.LocalProfileRowPadding
import com.pocket.xiaoli.ui.theme.ServiceAccent
import com.pocket.xiaoli.ui.theme.miuixPress
import com.pocket.xiaoli.ui.theme.serviceAccentColor
import com.pocket.xiaoli.ui.theme.serviceAccentContainer
import com.pocket.xiaoli.ui.zhengfang.ZhengfangCaptchaHost
import com.pocket.xiaoli.util.BindingValidators
import com.pocket.xiaoli.util.YitAccountDefaults
import com.pocket.xiaoli.util.maskStudentId
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 绑定信息页。
 *
 * 结构本来就是卡片化的，所以这一轮**不动结构**，只收拾三处细节：
 *
 * 1. **绑定状态卡**（[BindingStatusCard]）—— 身份 / 教务 / 学习平台三行 + 状态标记，
 *    一眼看出漏绑了哪个。以前填完表单只能看到一张"已绑定"的英雄卡，
 *    打开服务才发现某项还没绑，就是缺这块。
 * 2. **输入框统一成"字段"外观**（[PocketField]）—— 白底 + borderDivider 描边 + 满圆角，
 *    与首页搜索入口、鲤鱼输入框同一套。
 * 3. **间距节奏** —— 卡间只有一档（大间距），卡内只有一档（行间距），
 *    原先散落的 10 / 11 / 12 / 14 / 18dp 全部归到这两档上。
 *
 * 页面上每个字都来自本机已有的真实状态（昵称 / 已保存的学校 / 本机保存的学号 /
 * 最近一次导入记录 / 两个学习平台的 Cookie），**没有任何推测值**：
 * 「什么时候用过」直接引用 `AppContainer.lastImport` 那条持久化记录，
 * 而不是显示一个"验证时间"—— 本机从来没有记录过这个字段，编一个出来就是假数据。
 *
 * ⚠️ **销毁性操作那一簇已搬到 `AccountDangerSection.kt`**（`PendingDanger` / `DangerRow` /
 * `AccountDangerCard`）。理由有两个：主页面那个「账号管理」**入口**与二级页组内的四条
 * **执行**行用的是同一个 `DangerRow`，放一起才看得出"入口有框、执行无框"是刻意的；
 * 而本文件已经进了优化清单 P2-1 的"巨型文件"名单，能整块拿走的就该拿走。
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountBindingScreen(
    container: AppContainer,
    chaoxingBound: Boolean = false,
    zhihuishuBound: Boolean = false,
    onOpenLearningPlatform: (LearningPlatform) -> Unit = {},
    onManageLearning: () -> Unit = {},
    onSyncLearning: () -> Unit = {},
    onBack: () -> Unit,
    onBoundChange: () -> Unit = {},
) {
    val skin = LocalSkin.current
    val context = LocalContext.current
    val colors = LocalPocketXiaoliColors.current
    val savedSchool by container.school.collectAsStateWithLifecycle()
    val lastImport by container.lastImport.collectAsStateWithLifecycle()

    // 服务自动登录（打开校园服务网页时自动填账号的策略）。
    // 读 observe() 流而不是 `.mode`：后者不是 Compose 可观察的读取，
    // 选完 chip 不会重组（看着像"没保存"）。
    val serviceAutoLoginMode by container.serviceAutoLoginStore
        .observe()
        .collectAsStateWithLifecycle(initialValue = container.serviceAutoLoginStore.mode)
    val initialCampus = remember { container.selectedCampus.value }
    val initialCred = remember(initialCampus.id) {
        initialCampus.jwBaseUrl?.let { container.loadCredential(it) }
    }
    var campusId by remember { mutableStateOf(initialCampus.id) }
    var school by remember { mutableStateOf(initialCampus.schoolName.orEmpty()) }
    var studentId by remember { mutableStateOf(initialCred?.first.orEmpty()) }
    var password by remember { mutableStateOf(initialCred?.second.orEmpty()) }
    var passwordVisible by remember { mutableStateOf(false) }

    // ── 教务表单：本地校验 + "验证账号" ──
    //
    // `submitted` 是"用户点过提交"的开关：错误文案只在点过之后出现，
    // 而不是用户刚打开表单、一个字还没填就先红一片（那是把人当犯人）。
    // 改动字段时把验证结果清掉 —— 上一次的"✓ 账号密码正确"对**新填的**值不作数。
    var submitted by remember { mutableStateOf(false) }
    val verifyVm: AcademicVerifyViewModel = viewModel(
        factory = AcademicVerifyViewModel.Factory(
            container.zhengfangClient,
            container.schoolRegistry,
            container,
        )
    )
    val verifying by verifyVm.isLoading.collectAsStateWithLifecycle()
    val verifyProgress by verifyVm.progress.collectAsStateWithLifecycle()
    val verifyError by verifyVm.error.collectAsStateWithLifecycle()
    val verified by verifyVm.verified.collectAsStateWithLifecycle()
    val verifyState: BindingActionUi = when {
        verifying -> BindingActionUi.Running(verifyProgress.ifBlank { "正在连接教务系统……" })
        verified -> BindingActionUi.Success(
            message = "账号密码正确，已保存",
            detail = "导入课表、网页导入、成绩查询会直接使用这份账号。",
        )
        verifyError != null -> BindingActionUi.Failed(
            message = verifyError!!,
            hint = "核对学号与密码后重试；改过密码请填新密码。教务系统维护时可选「仅保存」。",
        )
        else -> BindingActionUi.Idle
    }
    val schoolError = if (submitted) BindingValidators.schoolName(school) else null
    val studentIdError = if (submitted) BindingValidators.studentId(studentId) else null
    val passwordError = if (submitted) BindingValidators.password(password) else null
    val academicValid = schoolError == null && studentIdError == null && passwordError == null
    // 待二次确认的销毁性操作（见 PendingDanger）。
    // 改造前这里是 `showUnbindConfirm: Boolean`，只有「解绑教务」用它。
    var pendingDanger by remember { mutableStateOf<PendingDanger?>(null) }
    var dormBuilding by remember { mutableStateOf(container.dormInfoFor(initialCampus.id)?.building.orEmpty()) }
    var dormRoom by remember { mutableStateOf(container.dormInfoFor(initialCampus.id)?.room.orEmpty()) }
    var dormServiceCampusId by remember {
        mutableStateOf(container.dormInfoFor(initialCampus.id)?.serviceCampusId.orEmpty())
    }
    // 缴费系统用的宿舍编号（见 DormInfo.dormCode）：与房间号分开存，可留空
    var dormCode by remember {
        mutableStateOf(container.dormInfoFor(initialCampus.id)?.dormCode.orEmpty())
    }

    // ── 今日校园（智慧校园统一认证）──
    // 与教务账号分开存：两个系统改密码时经常只改一边（见 CasLogin.CREDENTIAL_KEY 的说明）
    var casStudentId by remember { mutableStateOf(container.loadCasCredential()?.first.orEmpty()) }
    var casPassword by remember { mutableStateOf(container.loadCasCredential()?.second.orEmpty()) }
    var casPasswordVisible by remember { mutableStateOf(false) }
    // 一键登录的结果留在页面上（不再只弹一次 Toast）：失败 / 要验证码时用户需要照着那句话去处理。
    var casLoginUi by remember { mutableStateOf<BindingActionUi>(BindingActionUi.Idle) }
    var portalLoggedIn by remember { mutableStateOf(TodayCampusPortal.cachedSnapshot()?.loggedIn) }
    val scope = rememberCoroutineScope()
    val casBound = casStudentId.isNotBlank() && casPassword.isNotBlank()

    // ── 校园卡（易校园）──
    // 易校园是**它自己的账号体系**（手机号 + 密码），既不等于学号也不等于智慧校园密码
    var cardPhone by remember { mutableStateOf(container.loadCardCredential()?.first.orEmpty()) }
    var cardPassword by remember { mutableStateOf(container.loadCardCredential()?.second.orEmpty()) }
    var cardPasswordVisible by remember { mutableStateOf(false) }
    var cardCheckUi by remember { mutableStateOf<BindingActionUi>(BindingActionUi.Idle) }
    val cardBound = cardPhone.isNotBlank() && cardPassword.isNotBlank()

    // 今日校园 / 校园卡两张表单的"点过提交"开关 + 本地校验（规则统一在 BindingValidators）。
    // ⚠️ 与教务表单同一个套路：按钮**不再静默禁用**，点了把具体原因写在字段下方。
    var casSubmitted by remember { mutableStateOf(false) }

    fun casValid(): Boolean =
        BindingValidators.studentId(casStudentId) == null &&
            BindingValidators.password(casPassword) == null

    var cardSubmitted by remember { mutableStateOf(false) }

    fun cardValid(): Boolean =
        BindingValidators.phone(cardPhone) == null &&
            BindingValidators.password(cardPassword) == null

    fun saveCard() {
        val phone = cardPhone.trim()
        if (phone.isBlank() || cardPassword.isBlank()) return
        container.saveCardCredential(phone, cardPassword)
        cardPhone = phone
        cardCheckUi = BindingActionUi.Idle
        Toast.makeText(context, "已保存校园卡账号", Toast.LENGTH_SHORT).show()
    }

    fun deleteCard() {
        container.deleteCardCredential()
        YxyCardRepository.clearCache()
        cardPhone = ""
        cardPassword = ""
        cardCheckUi = BindingActionUi.Idle
        Toast.makeText(context, "已删除本机保存的校园卡账号", Toast.LENGTH_SHORT).show()
    }

    /**
     * 存之前先真查一次：省得存了一个登不上的账号，到卡片页才发现。
     *
     * 查询成功即**顺手落盘**（与今日校园同一条约定）：用户已经把账号密码交出来、
     * 平台也认了，还要求他再点一次「仅保存」纯属多此一举 —— 而且很容易漏，
     * 漏了之后再回来页面还写着"未绑定"。
     */
    fun checkCard() {
        val phone = cardPhone.trim()
        if (phone.isBlank() || cardPassword.isBlank()) return
        cardCheckUi = BindingActionUi.Running("正在登录易校园并查询余额……")
        scope.launch {
            YxyCardRepository.query(
                context = context,
                phone = phone,
                password = cardPassword,
                forceRefresh = true,
            ).fold(
                onSuccess = { balance ->
                    container.saveCardCredential(phone, cardPassword)
                    cardPhone = phone
                    val who = if (balance.userName.isNotBlank()) "（${balance.userName}）" else ""
                    cardCheckUi = BindingActionUi.Success(
                        message = "账号可用 · 余额 ¥" +
                            String.format(java.util.Locale.getDefault(), "%.2f", balance.balance) + who,
                        detail = "账号已保存到本机，之后去「服务 → 校园卡」可以直接查余额。",
                    )
                },
                onFailure = {
                    cardCheckUi = BindingActionUi.Failed(
                        message = "查询失败：${it.message ?: "未知原因"}",
                        hint = "易校园密码和教务 / 智慧校园都不是同一个；忘记密码请先在易校园 App 里重置。",
                    )
                },
            )
        }
    }

    // 进页面问一次门户的登录态（门户自己给的 hasLogin，不是我们猜的）。
    // 读不到就保持"未知"，不编一个状态出来。
    LaunchedEffect(Unit) {
        TodayCampusPortal.load().onSuccess { portalLoggedIn = it.loggedIn }
    }

    fun saveCas() {
        val sid = casStudentId.trim()
        if (sid.isBlank() || casPassword.isBlank()) return
        container.saveCasCredential(sid, casPassword)
        casStudentId = sid
        casLoginUi = BindingActionUi.Idle
        Toast.makeText(context, "已保存今日校园账号", Toast.LENGTH_SHORT).show()
    }

    fun deleteCas() {
        container.deleteCasCredential()
        casStudentId = ""
        casPassword = ""
        casLoginUi = BindingActionUi.Idle
        Toast.makeText(context, "已删除本机保存的今日校园账号", Toast.LENGTH_SHORT).show()
    }

    fun clearCasLogin() {
        CasLogin.clearLocalSessions()
        portalLoggedIn = false
        casLoginUi = BindingActionUi.Idle
        Toast.makeText(context, "已退出今日校园登录态", Toast.LENGTH_SHORT).show()
    }

    /**
     * 保存并登录：不打开网页，直接走原生 CAS（详见 [CasLogin]）。
     * 只有用户自己按下这个按钮才会用密码；需要验证码时不硬闯。
     *
     * ⚠️ 登录成功即落盘账号（与「验证并保存」同一条约定）：CAS 只验证了密码对不对，
     * 不存下来的话下次打开还是"未绑定"，用户得从头再输一遍。
     */
    fun runCasLogin() {
        val sid = casStudentId.trim()
        if (sid.isBlank() || casPassword.isBlank()) return
        casLoginUi = BindingActionUi.Running("正在登录今日校园……需要验证码时会停下来提示")
        scope.launch {
            when (val outcome = CasLogin.login(studentId = sid, password = casPassword)) {
                is CasLogin.Outcome.Success -> {
                    CookieStore.saveServiceCookies(TodayCampusPortal.ORIGIN, outcome.serviceCookies)
                    CookieStore.saveServiceCookies(CasLogin.AUTH_SERVER, outcome.authCookies)
                    TodayCampusPortal.clearCache()
                    portalLoggedIn = TodayCampusPortal.load(forceRefresh = true)
                        .getOrNull()?.loggedIn ?: true
                    container.saveCasCredential(sid, casPassword)
                    casStudentId = sid
                    casLoginUi = BindingActionUi.Success(
                        message = "已登录今日校园",
                        detail = "账号已保存到本机，打开校园服务不用再输密码。",
                    )
                }

                CasLogin.Outcome.CaptchaRequired ->
                    casLoginUi = BindingActionUi.Failed(
                        message = "这次登录要图片验证码，原生登录走不通",
                        hint = "去「服务 → 今日校园」点「在内置网页登录」，账号会自动填好。",
                    )

                is CasLogin.Outcome.Failed ->
                    casLoginUi = BindingActionUi.Failed(
                        message = outcome.message,
                        hint = "智慧校园密码可能和教务不是同一个；改过密码请填新的那个。",
                    )
            }
        }
    }

    val campus = CampusConfig.byId(campusId)
    val isBound = studentId.isNotBlank()

    fun switchCampus(target: CampusInfo) {
        if (target.underConstruction) return
        campusId = target.id
        school = target.schoolName.orEmpty()
        val cred = target.jwBaseUrl?.let { container.loadCredential(it) }
        studentId = cred?.first.orEmpty()
        password = cred?.second.orEmpty()
        val dorm = container.dormInfoFor(target.id)
        dormBuilding = dorm?.building.orEmpty()
        dormRoom = dorm?.room.orEmpty()
        dormCode = dorm?.dormCode.orEmpty()
        dormServiceCampusId = dorm?.serviceCampusId.orEmpty()
        // ⚠️ 换校区必须清掉上一条验证结论：否则在新校区的表单下面还挂着
        // 旧校区的「学号或密码不正确」，看着像是新填的这组账号被判了错。
        submitted = false
        verifyVm.reset()
    }

    /** 教务表单的**本地**校验（格式层面）。真验证在 [AcademicVerifyViewModel]。 */
    fun academicValid(): Boolean =
        BindingValidators.schoolName(school) == null &&
            BindingValidators.studentId(studentId) == null &&
            BindingValidators.password(password) == null

    /**
     * 把教务账号落盘（**不负责导航**）。
     *
     * ⚠️ 改造前这个方法末尾有一句 `onBack()` —— 保存成功就**退出整个绑定信息页**回到「我的」，
     * 用户看不到刚绑好的那条状态；而宿舍那条路一直是 `subPage = null` 回状态清单
     *（那边的注释还写着"保存完回清单"）。现在两边一致：落盘 → 回清单。
     */
    fun persistAcademic(): Boolean {
        val target = CampusConfig.byId(campusId)
        if (target.underConstruction) return false
        val sid = studentId.trim()
        if (sid.isBlank() || password.isBlank()) return false
        container.setSelectedCampus(target.id)
        container.setSchool(school.ifBlank { target.schoolName.orEmpty() })
        container.saveCredential("campus_bind", sid, password)
        target.jwBaseUrl?.let { container.saveCredential(it, sid, password) }
        onBoundChange()
        return true
    }

    fun unbind() {
        val target = CampusConfig.byId(campusId)
        target.jwBaseUrl?.let { container.deleteCredential(it) }
        if (target.id == container.selectedCampus.value.id) {
            container.deleteCredential("campus_bind")
        }
        studentId = ""
        password = ""
        onBoundChange()
        Toast.makeText(context, "已解绑 ${target.name} 的教务账号", Toast.LENGTH_SHORT).show()
    }

    /** 当前打开的二级页；null = 停在主页面（绑定状态清单）。 */
    var subPage by remember { mutableStateOf<BindSubPage?>(null) }

    // ── 二级页导航 ──
    //
    // ⚠️ 二级页在本 Composable **内部**换页，不走 ProfileScreen 的路由。理由：
    // 本页有二十来个"正在编辑中"的本地状态（学号 / 密码 / 宿舍…），
    // 拆成独立 Composable 就得把它们全部提升或打包传递；而换页方式的返回键
    // 由内层 BackHandler 接管（Compose 内层优先），父页不需要配合。
    //
    // ⚠️ 不能对 `subPage` 直接智能转换：它是 `by remember` 的委托局部变量、又被下面的
    // lambda 捕获，Kotlin 不允许 —— 先取到局部 val 再判空。
    val activeSubPage = subPage

    // "有可管理的账号吗" —— 主页面入口与二级页那张卡共用同一个条件。
    // 用它把入口也一起挡掉：否则四个账号都没绑时，点进去会是一张空卡。
    val hasManageableAccount = isBound || portalLoggedIn == true || casBound || cardBound
    BackHandler(enabled = activeSubPage != null) { subPage = null }

    // 验证成功 → 落盘 + 回状态清单。
    //
    // 为什么用副作用而不是在按钮回调里直接处理：登录是异步的（要等网络、可能还要等验证码），
    // 结果由 ViewModel 的状态流回来 —— 用 `verified` 驱动才不会出现"协程还在跑，页面已经走了"。
    LaunchedEffect(verified) {
        if (verified && persistAcademic()) {
            Toast.makeText(context, "账号密码正确，已保存", Toast.LENGTH_SHORT).show()
            verifyVm.reset()
            subPage = null
        }
    }

    // 验证码弹窗走共享实现（`ui/zhengfang/`）—— `CLAUDE.md` 明令禁止在这里另抄一份。
    ZhengfangCaptchaHost(state = verifyVm)

    Scaffold(
        containerColor = colors.surfaceBase,
        topBar = {
            TopAppBar(
                title = { Text(activeSubPage?.title ?: "绑定信息", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        // 二级页的返回箭头回上一层；主页面的箭头退出本屏。
                        onClick = { if (activeSubPage != null) subPage = null else onBack() }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surfaceCard)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                // 左右用屏幕边距，底部留一段呼吸位。
                // ⚠️ 留白写在**滚动内容内部**：挪到栏外容器上会变成死白，
                // 并挖掉玻璃底栏的采样源（这个错项目里犯过四次）。
                .padding(
                    start = skin.screenH,
                    end = skin.screenH,
                    top = skin.spaceMd,
                    bottom = skin.spaceXxl,
                ),
            // 卡间只有一档：大间距（与首页的模块节奏同档）
            verticalArrangement = Arrangement.spacedBy(skin.sectionGap),
        ) {
            BindingStatusCard(
                visible = activeSubPage == null,
                onNavigate = { subPage = it },
                campus = campus,
                savedSchool = savedSchool,
                isBound = isBound,
                studentId = studentId,
                lastImport = lastImport,
                chaoxingBound = chaoxingBound,
                zhihuishuBound = zhihuishuBound,
                casBound = casBound,
                casStudentId = casStudentId,
                portalLoggedIn = portalLoggedIn,
                dormLabel = listOf(dormBuilding.trim(), dormRoom.trim())
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .ifBlank { null }
                    ?.let { label -> if (dormCode.isBlank()) label else "$label · 编号 $dormCode" },
                cardBound = cardBound,
            )

            // 主页面只留"一眼看全"的东西：状态清单 + 这一个入口 + 宿舍入口。
            // 危险操作进了二级页，但入口本身**仍用 danger 描边** —— "这里有不可恢复的
            // 操作"这个信号不能因为多了一层就藏起来。
            DangerRow(
                visible = activeSubPage == null && hasManageableAccount,
                trailingArrow = true,
                title = "账号管理",
                subtitle = "解绑教务 · 退出 / 删除本机保存的账号",
                onClick = { subPage = BindSubPage.ACCOUNT_DANGER },
            )

            BindingSectionCard(
                visible = activeSubPage == BindSubPage.ACADEMIC,
                icon = Icons.Outlined.HomeWork,
                title = "选择校区",
                subtitle = "切换后课表导入、成绩查询会使用对应教务系统",
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                    CampusConfig.campuses.forEach { item ->
                        FilterChip(
                            selected = item.id == campusId,
                            onClick = { switchCampus(item) },
                            enabled = !item.underConstruction,
                            label = { Text(item.shortName, fontSize = 13.sp) }
                        )
                    }
                }
                Text(
                    "当前教务：${campus.jwTypeLabel} · ${campus.schoolName ?: "未设置"}",
                    fontSize = 12.sp,
                    color = colors.textTertiary,
                )
            }

            BindingSectionCard(
                visible = activeSubPage == BindSubPage.ACADEMIC,
                icon = Icons.Outlined.Person,
                title = "教务账号",
                subtitle = "账号密码仅加密保存在本机",
            ) {
                AcademicAccountSection(
                    school = school,
                    onSchoolChange = {
                        school = it
                        submitted = false
                        verifyVm.reset()
                    },
                    studentId = studentId,
                    onStudentIdChange = {
                        studentId = it
                        submitted = false
                        verifyVm.reset()
                    },
                    password = password,
                    onPasswordChange = {
                        password = it
                        submitted = false
                        verifyVm.reset()
                    },
                    passwordVisible = passwordVisible,
                    onTogglePasswordVisible = { passwordVisible = !passwordVisible },
                    schoolError = schoolError,
                    studentIdError = studentIdError,
                    passwordError = passwordError,
                    verify = verifyState,
                    canVerify = campus.jwBaseUrl != null,
                    // 「验证并保存」：先本地校验（省一次注定失败的联网），再真的登一次。
                    // 登录成功时基类已把凭据写进本机，这里再把 campus_bind / 当前校区一并落盘。
                    onVerifyAndSave = {
                        submitted = true
                        if (academicValid()) verifyVm.verify(studentId, password)
                    },
                    // 「仅保存，不验证」：教务系统连不上时的退路（绑定信息本来只是本机存档）
                    onSaveOnly = {
                        submitted = true
                        if (academicValid() && persistAcademic()) {
                            verifyVm.reset()
                            Toast.makeText(context, "已保存账号", Toast.LENGTH_SHORT).show()
                            subPage = null
                        }
                    },
                    middle = {
                        // ── 服务自动登录 ──
                        // 2026-10 从设置页的「外观」组搬来这里：它用的正是上面保存的教务账号，
                        // 没绑账号时这一项毫无意义，所以归到账号配置旁边。
                        // 原先挤在「外观」里是错位 —— 它跟外观没有任何关系。
                        SettingsChoiceItem(
                            icon = Icons.AutoMirrored.Outlined.Login,
                            title = "服务自动登录",
                            subtitle = "打开校园服务时自动填入上面的账号",
                            options = ServiceAutoLoginMode.entries.map { it.label to it },
                            selected = serviceAutoLoginMode,
                            onSelect = { container.serviceAutoLoginStore.set(it) },
                            // 与上面三个字段左对齐：这个组件在设置页是"整行项"、自带一层水平内缩，
                            // 塞进卡片内容里会和字段差一截（见它自己的 KDoc）。
                            horizontalPadding = 0.dp,
                            accent = ServiceAccent.QUERY,
                        )
                    },
                )
            }

            // ── 宿舍绑定（二级页）──
            // 改造前它是一个弹窗：一个完整表单（两个转轮 + 服务校区 + 编号输入 + 实时摘要 +
            // 保存/解绑）挤在 AlertDialog 里，转轮最高只给到 168dp，两段说明也没地方放。
            // 现在它是整页（DormBindingContent）：字段分组进卡、转轮放宽、说明各归其位。
            if (activeSubPage == BindSubPage.DORM) {
                DormBindingContent(
                    initialBuilding = dormBuilding,
                    initialRoom = dormRoom,
                    initialServiceCampusId = dormServiceCampusId.takeIf { it.isNotBlank() },
                    initialDormCode = dormCode,
                    preferredCampusId = campusId,
                    canUnbind = dormBuilding.isNotBlank() || dormRoom.isNotBlank(),
                    onConfirm = { building, room, serviceCampusId, code ->
                        dormBuilding = building
                        dormRoom = room
                        dormServiceCampusId = serviceCampusId.orEmpty()
                        dormCode = code
                        container.setDormInfoFor(campusId, building, room, serviceCampusId, code)
                        Toast.makeText(context, "已保存宿舍 $building $room", Toast.LENGTH_SHORT).show()
                        // 保存完回清单 —— 用户进这一页的唯一目的就是把它绑好
                        subPage = null
                    },
                    onUnbind = {
                        dormBuilding = ""
                        dormRoom = ""
                        dormServiceCampusId = ""
                        dormCode = ""
                        container.setDormInfoFor(campusId, "", "")
                        Toast.makeText(context, "已解绑宿舍", Toast.LENGTH_SHORT).show()
                        subPage = null
                    },
                )
            }

            // ── 今日校园（智慧校园统一认证）──
            //
            // 单独一段、单独存一份账号，原因是实的：智慧校园（今日校园 / 易校园都靠它登）
            // 与教务是两套系统，初始密码规则一样，但改过密码之后经常只改了一边。
            // 共用教务那份密码，会出现"教务能登、今日校园登不上"这种最难查的状态。
            BindingSectionCard(
                visible = activeSubPage == BindSubPage.TODAY_CAMPUS,
                icon = Icons.Outlined.Today,
                accent = ServiceAccent.INFO,
                title = "今日校园",
                subtitle = "智慧校园统一认证 · 校园卡（易校园）也走它",
            ) {
                CasAccountSection(
                    studentId = casStudentId,
                    onStudentIdChange = {
                        casStudentId = it
                        casSubmitted = false
                        casLoginUi = BindingActionUi.Idle
                    },
                    studentIdError = if (casSubmitted) BindingValidators.studentId(casStudentId) else null,
                    password = casPassword,
                    onPasswordChange = {
                        casPassword = it
                        casSubmitted = false
                        casLoginUi = BindingActionUi.Idle
                    },
                    passwordError = if (casSubmitted) BindingValidators.password(casPassword) else null,
                    passwordVisible = casPasswordVisible,
                    onTogglePasswordVisible = { casPasswordVisible = !casPasswordVisible },
                    loginState = casLoginUi,
                    portalLoggedIn = portalLoggedIn,
                    hintStudentId = studentId,
                    // ⚠️ 不再静默禁用：点得动，点了把问题写在字段下方（与教务表单同一套规则）
                    onSave = {
                        casSubmitted = true
                        if (casValid()) saveCas()
                    },
                    onLogin = {
                        casSubmitted = true
                        if (casValid()) runCasLogin()
                    },
                )
            }

            // ── 校园卡（易校园）──
            //
            // 这一段的账号与上面两段都**不是**同一个：易校园用它自己的账号体系（手机号 + 密码）。
            // 填了它，校园卡才有原生余额页（见 ui/card/CampusCardScreen.kt）；
            // 不填也不影响用：那边会退回"拉起易校园 App / 进门户里的易校园入口"。
            BindingSectionCard(
                visible = activeSubPage == BindSubPage.CAMPUS_CARD,
                icon = Icons.Outlined.CreditCard,
                accent = ServiceAccent.PAYMENT,
                title = "校园卡（易校园）",
                subtitle = "易校园自己的账号，不是学号",
            ) {
                CardAccountSection(
                    phone = cardPhone,
                    onPhoneChange = {
                        cardPhone = it
                        cardSubmitted = false
                        cardCheckUi = BindingActionUi.Idle
                    },
                    phoneError = if (cardSubmitted) BindingValidators.phone(cardPhone) else null,
                    password = cardPassword,
                    onPasswordChange = {
                        cardPassword = it
                        cardSubmitted = false
                        cardCheckUi = BindingActionUi.Idle
                    },
                    passwordError = if (cardSubmitted) BindingValidators.password(cardPassword) else null,
                    passwordVisible = cardPasswordVisible,
                    onTogglePasswordVisible = { cardPasswordVisible = !cardPasswordVisible },
                    bound = cardBound,
                    checkState = cardCheckUi,
                    onSave = {
                        cardSubmitted = true
                        if (cardValid()) saveCard()
                    },
                    onCheck = {
                        cardSubmitted = true
                        if (cardValid()) checkCard()
                    },
                )
            }
            // ── 学习平台 ──
            // 排在最后：它与上面四个（教务 / 宿舍 / 今日校园 / 校园卡）互不相干 ——
            // 学习平台只同步考试与作业，不参与任何登录链路，所以不占靠前的位置。
            BindingSectionCard(
                visible = activeSubPage == BindSubPage.LEARNING,
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                accent = ServiceAccent.STUDY,
                title = "学习平台",
                subtitle = "Cookie 加密保存在本机",
            ) {
                LearningBindingRow(name = "学习通", bound = chaoxingBound) {
                    onOpenLearningPlatform(LearningPlatform.CHAOXING)
                }
                LearningBindingRow(name = "智慧树", bound = zhihuishuBound) {
                    onOpenLearningPlatform(LearningPlatform.ZHIHUISHU)
                }
                TextButton(
                    onClick = onSyncLearning,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("从学习平台同步到待办", color = colors.accentMain, fontSize = 14.sp)
                }
                TextButton(
                    onClick = onManageLearning,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("管理学习平台（考试 / 作业 / 收件箱）", color = colors.accentMain, fontSize = 14.sp)
                }
            }

            // ── 账号管理（危险操作）──
            //
            // 改造前四处销毁性操作**分散在各自的卡片里**（解绑教务在教务卡、
            // 退出登录态与删今日校园在今日校园卡、删校园卡在校园卡卡），
            // 全是居中的 danger 文字按钮，长得像普通链接 —— 指头一滑就点到。
            //
            // 现在聚到页尾、独立成组，并且**四条都过二次确认**（见 PendingDanger）：
            // 这一组的语义就是"不可恢复"，那就不该有"点了就删"的例外。
            //
            // ⚠️ 组内**不逐条描边**。改造前四条各自一圈红框 = 一面红墙，
            // 而且与主页面上那个同样是红框的「账号管理」**入口**长得一模一样，
            // 于是"点进去"与"点了就删"分不出来。现在：入口有框、执行无框（用分隔线），
            // 形状本身就在说明性质。
            if (hasManageableAccount) {
                val dangerRows = buildList {
                    if (isBound) {
                        add(
                            PendingDanger(
                                title = "解绑教务账号",
                                rowSubtitle = "删除本机保存的「${campus.name}」学号与密码",
                                message = "将删除 ${campus.name} 在本机保存的学号和密码，确定吗？",
                                confirmLabel = "确认解绑",
                                onConfirm = { unbind() },
                            )
                        )
                    }
                    if (portalLoggedIn == true) {
                        add(
                            PendingDanger(
                                title = "退出今日校园登录态",
                                rowSubtitle = "只清理本机的登录 Cookie，账号还在",
                                message = "将清理本机保存的今日校园登录 Cookie，账号与密码仍留在本机。确定吗？",
                                confirmLabel = "确认退出",
                                onConfirm = { clearCasLogin() },
                            )
                        )
                    }
                    if (casBound) {
                        add(
                            PendingDanger(
                                title = "删除今日校园账号",
                                rowSubtitle = "删除本机保存的学号与密码",
                                message = "将删除本机保存的今日校园学号与密码（教务账号不受影响）。确定吗？",
                                confirmLabel = "确认删除",
                                onConfirm = { deleteCas() },
                            )
                        )
                    }
                    if (cardBound) {
                        add(
                            PendingDanger(
                                title = "删除校园卡账号",
                                rowSubtitle = "删除本机保存的手机号与密码",
                                message = "将删除本机保存的易校园手机号与密码，校园余额页随即不可用。确定吗？",
                                confirmLabel = "确认删除",
                                onConfirm = { deleteCard() },
                            )
                        )
                    }
                }
                AccountDangerCard(
                    visible = activeSubPage == BindSubPage.ACCOUNT_DANGER,
                    rows = dangerRows,
                    onPick = { pendingDanger = it },
                )
            }
        }
    }

    // 销毁性操作的二次确认。**四条共用同一个框**，文案随那条动作走 ——
    // 各自写一个 AlertDialog 的话，加第五条时必然有人忘了写确认。
    pendingDanger?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingDanger = null },
            title = { Text(pending.title) },
            text = { Text(pending.message) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDanger = null
                    pending.onConfirm()
                }) { Text(pending.confirmLabel, color = colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDanger = null }) { Text("取消") }
            }
        )
    }

}

/**
 * 顶部「绑定状态」卡 —— 用户说的"填完看不出绑没绑好"的解药。
 *
 * 2026-10：卡内新增**绑定进度**（五段条 + `n / 5` + 还差哪几项）。
 * 五项按"教务 → 宿舍 → 今日校园 → 校园卡 → 学习平台"的顺序与下面清单、与二级页一一对应；
 * 每段用该项自己的 [ServiceAccent] 分类色，扫一眼长度看进度、扫一眼颜色看缺哪类。
 * 进度只统计"绑没绑"，与「教务」行红点表示的"最近一次导入成功没"是两件事。
 *
 * 三行对应三件事，每个状态标记都取本机已有的真实值：
 *
 * | 行 | 值从哪来 | 状态标记 |
 * |---|---|---|
 * | 身份 | 已保存的学校 + 本机保存的学号（脱敏） | 已绑定 / 未绑定 |
 * | 教务 | 校区教务类型 + **最近一次导入记录**（来源 / 结果 / 时间） | 已导入 / 导入失败 / 未导入 |
 * | 学习平台 | 学习通、智慧树各自的登录状态 | n/2 |
 *
 * 「什么时候验过」本机从来没有存过独立的"验证时间"字段，所以这里**不编一个**，
 * 直接引用 `AppContainer.lastImport` 那条真实记录 —— 成功导入过课表本身就是账号能用的证据。
 * 宿舍不在三行之列（它有自己的一张卡），但也不该从总览里消失，收成末尾一行小字。
 */
@Composable
internal fun BindingStatusCard(
    campus: CampusInfo,
    savedSchool: String,
    isBound: Boolean,
    studentId: String,
    lastImport: LastImportInfo?,
    chaoxingBound: Boolean,
    zhihuishuBound: Boolean,
    casBound: Boolean,
    casStudentId: String,
    portalLoggedIn: Boolean?,
    dormLabel: String?,
    /** 校园卡（易校园）是否已绑 —— 它也是总览里要核对的一项。 */
    cardBound: Boolean,
    // false 时整张卡不渲染 —— 二级页导航用它决定"这一次渲染哪一块"。
    //
    // 为什么用参数而不是在调用点写 `if (...) { ... }`：
    // 2026-10 把本页拆成"主页面 / 五个二级页"之后，各块**仍在同一个 Column 里**
    //（本页有二十来个"正在编辑中"的本地状态，拆成独立 Composable 就得把它们
    // 全部提升或打包传递）。用 `if` 包住每一块，意味着把 300 多行整体重新缩进一遍 ——
    // 纯位移、没有语义收益，却让这次的 diff 无法阅读。所以把开关做成参数。
    visible: Boolean = true,
    // 清单行**本身就是入口**：点「教务」进教务页、点「今日校园」进今日校园页。
    // 传回调而不是直接读 `subPage` —— 清单在 BindingStatusCard 里，
    // 而那个状态属于调用方（AccountBindingScreen）。
    onNavigate: (BindSubPage) -> Unit,
) {
    if (!visible) return
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val schoolText = savedSchool.ifBlank { campus.schoolName.orEmpty() }.ifBlank { "未设置学校" }
    val learningCount = (if (chaoxingBound) 1 else 0) + (if (zhihuishuBound) 1 else 0)
    // 五项的「绑好了没」，顺序与下面清单、与页面卡片顺序一致 ——
    // 它只回答"绑齐了没"，与"最近一次导入成功没"是两件事（后者归「教务」行的红点）。
    val bindItems = listOf(
        Triple("教务", isBound, ServiceAccent.ACADEMIC),
        Triple("宿舍", dormLabel != null, ServiceAccent.LIFE),
        Triple("今日校园", casBound, ServiceAccent.INFO),
        Triple("校园卡", cardBound, ServiceAccent.PAYMENT),
        Triple("学习平台", learningCount == 2, ServiceAccent.STUDY),
    )
    val boundCount = bindItems.count { it.second }
    val missing = bindItems.filterNot { it.second }.map { it.first }
    val progressHint = when {
        missing.isEmpty() -> "五项都已绑好，校园服务都能用了"
        missing.size == bindItems.size -> "五项都还没绑，点下面任意一行开始"
        else -> "还差 ${missing.size} 项：${missing.joinToString("、")}"
    }
    val importExtra = when {
        lastImport == null ->
            if (isBound) "还没用这个账号成功导入过课表" else "绑定学号后可用「教务直连」导入课表"

        lastImport.success ->
            "最近导入：${lastImport.source} · 成功 ${formatImportTime(lastImport.timestamp)}"

        else ->
            "最近导入：${lastImport.source} · 失败 ${formatImportTime(lastImport.timestamp)}"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusCard),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(skin.cardPadding),
            // 卡内只有一档：行间距
            verticalArrangement = Arrangement.spacedBy(skin.spaceMd),
        ) {
            // ── 身份 ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(skin.radiusCard))
                        .background(colors.accentMain.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Person,
                        contentDescription = null,
                        tint = colors.accentMain,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Spacer(Modifier.width(skin.spaceMd))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        // 「昵称」整个概念已删（2026-10）：这一行改成与「我的」页那张入口卡
                        // 同名的标题 —— 用户点进来看到的是同一件事的明细。
                        "我的信息",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                    )
                    Text(
                        if (isBound) {
                            "$schoolText · 学号 ${maskStudentId(studentId)}"
                        } else {
                            "$schoolText · 尚未填写学号"
                        },
                        fontSize = 13.sp,
                        color = colors.textSecondary,
                        // 允许换到第二行：这一行同时装着学校和脱敏学号，
                        // 限一行会被省略号吃掉学号尾巴（"2022**…"），那正是用户要认的那串数字
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // 一条极细分隔线：上面是"我是谁"，下面是"我绑齐了没" ——
            // 两段性质不同，光靠留白分不开（卡内只有一档行距）。
            HorizontalDivider(color = colors.borderDivider, thickness = 0.5.dp)

            // ── 绑定进度 ──
            // 进这一页的第一句话就是"我还差什么"。改造前这个问题只能靠逐行读五个红/蓝点回答。
            // 五段条与下面五项**按同一顺序一一对应**，已绑的一段用该项自己的分类色，
            // 未绑的一段留灰 —— 看一眼长度就知道进度，扫一眼颜色就知道缺的是哪一类。
            Column(verticalArrangement = Arrangement.spacedBy(skin.spaceSmall)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "绑定进度",
                        modifier = Modifier.weight(1f),
                        fontSize = 13.sp,
                        color = colors.textSecondary,
                    )
                    Text(
                        "$boundCount / ${bindItems.size}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (boundCount == bindItems.size) colors.success else colors.textPrimary,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceXs)) {
                    bindItems.forEach { (_, done, accent) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(
                                    if (done) serviceAccentColor(accent) else colors.surfaceHighlight
                                )
                        )
                    }
                }
                Text(
                    progressHint,
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                )
            }

            HorizontalDivider(color = colors.borderDivider, thickness = 0.5.dp)

            Column {
                // ── 教务 ──
                ChecklistRow(
                    state = when {
                        lastImport == null -> BindState.TODO
                        lastImport.success -> BindState.OK
                        else -> BindState.FAILED
                    },
                    icon = Icons.Outlined.HomeWork,
                    accent = ServiceAccent.ACADEMIC,
                    title = "教务",
                    detail = "${campus.jwTypeLabel} · $schoolText",
                    onClick = { onNavigate(BindSubPage.ACADEMIC) },
                    // 「最近导入…」走**通栏**第二行。塞进 detail 会被省略号吃掉时间
                    // （那个时间正是这一行存在的意义），详见 ChecklistRow 的 extra 说明。
                    extra = importExtra,
                )

                // ⚠️ 分隔线是**卡片 Column 的直接子项**，不是塞在行内部 ——
                // 这样它的上下各是"行内边距 12dp + 卡片间距 14dp"，两侧**对称**，
                // 线才会读成"列表分隔"而不是"贴在上面那一行底下"。
                // 加线的理由：改前五行只靠 14dp 空白分组，而行的**高度本来就不齐**
                //（教务/今日校园是两行，其余一行）—— 空白 + 参差读起来像散落的句子。
                HorizontalDivider(color = colors.borderDivider)

                // 下面这几行的**顺序与页面上的卡片顺序一致**（教务 → 宿舍 → 今日校园 →
                // 校园卡 → 学习平台）：用户扫完清单往下滚，能逐项对上，不用来回找。
                ChecklistRow(
                    state = if (dormLabel != null) BindState.OK else BindState.TODO,
                    icon = Icons.Outlined.Home,
                    accent = ServiceAccent.LIFE,
                    title = "宿舍",
                    detail = dormLabel ?: "未绑定 · 绑定后自动匹配生活服务商",
                    onClick = { onNavigate(BindSubPage.DORM) },
                )

                HorizontalDivider(color = colors.borderDivider)

                // ── 今日校园 ──
                ChecklistRow(
                    state = when {
                        !casBound -> BindState.TODO
                        portalLoggedIn == true -> BindState.OK
                        portalLoggedIn == false -> BindState.TODO
                        // 门户接口读不到时**不编一个状态**，如实写成"未知"
                        else -> BindState.UNKNOWN
                    },
                    icon = Icons.Outlined.Today,
                    accent = ServiceAccent.INFO,
                    title = "今日校园",
                    detail = if (casBound) {
                        val loginState = when {
                            portalLoggedIn == true -> "已登录"
                            portalLoggedIn == false -> "已绑定，当前未登录"
                            else -> "登录态未知"
                        }
                        // ⚠️ 这里用**显式换行**，不要让它自动折：detail 那一列只有约 200dp，
                        // 自动折行会把「已绑定」拆成「已绑 / 定，当前未登录」（基线 82 拍到了）——
                        // 断在一个词中间看起来像错字。断在「·」处才是干净的。
                        // （试过 `LineBreak(WordBreak.PHRASE)`：本项目的 Compose 版本没有 PHRASE，
                        //  而且 Android 的词组断行要 API 33+，minSdk 29 上本来就不生效。）
                        "智慧校园 · 学号 ${maskStudentId(casStudentId)}\n$loginState"
                    } else {
                        "未绑定 · 校园卡（易校园）的门户入口也走它"
                    },
                    onClick = { onNavigate(BindSubPage.TODAY_CAMPUS) },
                )

                HorizontalDivider(color = colors.borderDivider)

                ChecklistRow(
                    state = if (cardBound) BindState.OK else BindState.TODO,
                    icon = Icons.Outlined.CreditCard,
                    accent = ServiceAccent.PAYMENT,
                    title = "校园卡",
                    detail = if (cardBound) "已绑定 · 可查余额" else "未绑定（可选，不影响其它功能）",
                    onClick = { onNavigate(BindSubPage.CAMPUS_CARD) },
                )

                HorizontalDivider(color = colors.borderDivider)

                // ── 学习平台 ──
                ChecklistRow(
                    // ⚠️ `== 2` 而不是 `>= 1`：改造前只通一个平台也会亮**蓝色实心点**
                    //（= 全好），而同一行的文字明明写着"智慧树 未登录" —— 点与字互相打架
                    //（基线 `82` 拍到了）。`BindState` 只有四态，没有"半通"这一态：
                    // 对用户来说"没绑齐"要做的动作是同一个（去绑另一个），所以归 TODO 是对的，
                    // 精确的差别由下面那行文字表达 —— 这正是 StateDot"点+文字"双通道的设计本意。
                    state = if (learningCount == 2) BindState.OK else BindState.TODO,
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                    accent = ServiceAccent.STUDY,
                    title = "学习平台",
                    // 两个平台是**平级**的，所以写成并列的两句话。
                    // 改造前把"学习通"放在 value、"智慧树"放在 extra（低一级文字），
                    // 读起来像"学习通是主、智慧树是附" —— 而它们并没有主次。
                    detail = "学习通 ${if (chaoxingBound) "已登录" else "未登录"}" +
                        " · 智慧树 ${if (zhihuishuBound) "已登录" else "未登录"}",
                    onClick = { onNavigate(BindSubPage.LEARNING) },
                )
            }
        }
    }
}

/**
 * 「教务账号」表单本体：三个字段 + 初始密码捷径 + 两段说明 + [middle] + 提交区。
 *
 * ## 为什么从 `AccountBindingScreen` 里抽出来
 *
 * 整页的第一个参数是 `AppContainer`（真 DI 容器，构造要 Context + 加密存储，单测造不出来），
 * 所以「用户真正要填的那张表单」一直没有视觉基线 —— 而"绑定界面好不好看"问的正是它。
 * 抽成纯组件后，快照渲的是**生产代码本体**，不是测试里另写一份仿制品
 *（`docs/UI验收手册.md` 坑 2 记过"预览与实际漂移"的账）。
 *
 * ## 提交区为什么是**两个**动作（2026-10）
 *
 * 用户的要求原话是"最好能加个表单验证那种验证一下，能确定账号和信息的正确"。落地成：
 *
 * | 动作 | 做什么 | 什么时候用 |
 * |---|---|---|
 * | **验证并保存**（主） | 跑一次真登录（需要时弹验证码）；成功即已保存 | 默认路径 —— 能**确定**账号对不对 |
 * | **仅保存，不验证**（次） | 只做本地格式校验后落盘 | 教务系统临时不通、或本来就想先存着 |
 *
 * ⚠️ 不做成"验证通过才允许保存"的硬闸门：那会让**离线/教务维护期间完全绑不了账号**，
 * 而绑定信息本来只是一个本机存档。两个动作都写清楚，用户自己选。
 *
 * ⚠️ 需要验证码时走的是 `ui/zhengfang/` 的共享弹窗（`ZhengfangCaptchaHost`），
 * **不要**在这里另写一个验证码框 —— 那是 `CLAUDE.md` 明令禁止的复制粘贴。
 *
 * ## 参数里的错误文案由调用方算
 *
 * [schoolError] / [studentIdError] / [passwordError] 是**调用方**（页面）用
 * `BindingValidators` 算好传进来的：这个组件保持纯展示，才能被快照渲出来；
 * 而"什么时候开始报错"（点了保存之后 / 改字段之后清掉）也属于页面的状态机。
 */
@Composable
internal fun ColumnScope.AcademicAccountSection(
    school: String,
    onSchoolChange: (String) -> Unit,
    studentId: String,
    onStudentIdChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onTogglePasswordVisible: () -> Unit,
    onVerifyAndSave: () -> Unit,
    onSaveOnly: () -> Unit,
    schoolError: String? = null,
    studentIdError: String? = null,
    passwordError: String? = null,
    verify: BindingActionUi = BindingActionUi.Idle,
    /** 当前校区是否有可验证的教务系统；没有（如南校区）时只给「保存绑定」 */
    canVerify: Boolean = true,
    /** 夹在"字段"与"提交"之间的配置项（当前是「服务自动登录」） */
    middle: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = LocalPocketXiaoliColors.current
    val running = verify is BindingActionUi.Running
    // ── IME 类型（2026-10 修「绑定界面输入有问题」）──
    //
    // 三个字段**必须**各自锁住输入法类型，理由见下方每个参数旁的注释。
    // 判断依据是实测对比：两个导入页一直是这么做的（`WebImportScreen` 用 `Ascii` / `Password`），
    // 而绑定页此前一个都没设 —— 那正是"导入页没问题、绑定页有问题"的差别所在。
    PocketField(
        label = "学校名称",
        value = school,
        onValueChange = onSchoolChange,
        placeholder = "填写学校名称",
        error = schoolError,
    )
    PocketField(
        label = "学号",
        value = studentId,
        onValueChange = onStudentIdChange,
        placeholder = "输入学号",
        error = studentIdError,
        // 学号是数字（个别学校带字母），**不要中文候选**：中文输入法停在联想态时，
        // 打数字要走候选条、还可能被自动纠错改写。
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
    )
    PocketField(
        label = "教务密码",
        value = password,
        onValueChange = onPasswordChange,
        placeholder = "输入密码",
        error = passwordError,
        visualTransformation = if (passwordVisible) {
            VisualTransformation.None
        } else {
            PocketPasswordTransformation
        },
        // 密码框**必须**是 `KeyboardType.Password`：输入法会进入"无联想、无组词"的密码模式。
        // 少了它，中文输入法仍在组合文字，而字段又把内容转成圆点 ——
        // 组合区与变换后的显示打架，表现为吞字 / 候选不上屏 / 光标乱跳。
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = onTogglePasswordVisible) {
                Icon(
                    if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                    tint = colors.textTertiary
                )
            }
        },
    )

    val defaultPwd = remember(studentId) { YitAccountDefaults.passwordFromStudentId(studentId) }
    if (defaultPwd != null && password != defaultPwd) {
        TextButton(
            onClick = { onPasswordChange(defaultPwd) },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("填入初始密码（Ytlg@ + 学号后 6 位）", fontSize = 12.sp, color = colors.accentMain)
        }
    }
    Text(
        YitAccountDefaults.HINT,
        fontSize = 11.sp,
        color = colors.textTertiary
    )
    Text(
        "绑定后导入课表、网页导入、成绩查询都会直接使用；账号密码只保存在本机。",
        fontSize = 12.sp,
        color = colors.textTertiary
    )

    middle()

    // 分隔线：上面是"填 / 选账号"，下面是"提交"。
    // 改造前「服务自动登录」直接怼在密码字段与这个按钮之间，把表单和它的
    // 提交按钮隔开了 —— 这条线把"配置"与"提交"两段切开。
    HorizontalDivider(color = colors.borderDivider, thickness = 0.5.dp)

    // ── 验证结果 ──
    //
    // 三种结果都落在**按钮上方**：用户刚点完按钮，视线就在那里；放在按钮下方要往下找。
    // 与今日校园 / 校园卡共用同一个 `BindingActionNotice` —— 三个页面的
    // "进行中·成功·失败"不该有三种长相。
    BindingActionNotice(verify)

    if (canVerify) {
        BindingPrimaryButton(
            text = if (running) "正在验证…" else "验证并保存",
            onClick = onVerifyAndSave,
            enabled = !running,
        )
        TextButton(
            onClick = onSaveOnly,
            enabled = !running,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(
                "仅保存，不验证（教务系统连不上时用）",
                fontSize = 12.sp,
                color = colors.textSecondary,
            )
        }
    } else {
        // 没有教务系统的校区（如烟大南校）：只能本地保存，别给一个注定失败的"验证"
        BindingPrimaryButton(text = "保存绑定", onClick = onSaveOnly)
    }
}

/**
 * 检查清单的一行：**分类图标块（右下角带状态点） + 名称**占一行，
 * **详情**另起一行（可选再跟一条补充行）。
 *
 * 2026-10（第三次）：左侧从"裸状态点"换成 **36dp 分类色图标块**，状态点缩成块右下角的小徽章
 * （垫一圈卡片底色分隔）。五行原本只有蓝/橙/红三种点，扫读时五项长得一模一样；
 * 有了分类图标（教务楼 / 家 / 日历 / 卡 / 书），找"校园卡"不用先读字。
 * 状态点仍保留，语义与尺寸不变 —— 点继续是唯一的"好用 / 待办 / 失败"通道（见 [StateDot]）。
 *
 * ## 2026-10（第二次）：从"名称 + 右侧详情"改成两行式
 *
 * 改造前是「点 · 固定 72dp 名称 · 详情（weight(1f)） · 箭头」挤在一行里，
 * 详情列因此只剩约 **190dp**。基线把代价拍得很清楚：
 *
 * - `81-binding-home-fresh`：「校园卡（易校园）的**门户入 / 口也走它**」——
 *   一句话从词组中间被劈成两行；
 * - `82-binding-home-mixed`：「宿舍」那行"未绑定 · 绑定后自动匹配生活服务商"**顶到箭头**上，
 *   一点余量都没有；「今日校园」两行详情与垂直居中的名称形成错位，扫读时像两个不相干的块。
 *
 * 两行式之后详情有整行宽度（约 300dp），上述三处一起消失，且每行的"H 形"结构统一：
 * 名称行（点 + 名称 + 箭头，箭头与名称同高对齐）、详情行（缩进到名称左边缘）。
 * 代价是卡片高约 60–80dp —— 这一页主页面本来大片留白，高一点反而更稳。
 *
 * 其余尺度与它们当时的理由（都保留）：
 *
 * | 项 | 值 | 为什么 |
 * |---|---|---|
 * | 垂直内边距 | `LocalProfileRowPadding`（标准档 14dp） | 行高够，`clickable` 的最小交互尺寸也稳过 48dp |
 * | 名称字号 | **16sp** Medium | 用户要求"账号绑定的字体再大一点"；它落在字号令牌阶梯（24/20/16/14/12/10）上 |
 * | 详情字号 / 行高 | 13sp / 18sp | 两行详情不至于挤成一块 |
 * | 行间分隔 | 发丝线（由调用方插，见 `BindingStatusCard`） | 行高本就不齐（一条补充行只出现在「教务」），纯空白读起来像散落的句子 |
 *
 * ⚠️ 名称列**不再有固定宽度**（改名 `weight(1f)`）：两行式下详情已经独占整行，
 * 名称不需要再为了"让详情左边缘对齐"而牺牲自己的宽度。四字名称（"学习平台"）之外
 * 也再没有被截断的风险。
 */
@Composable
private fun ChecklistRow(
    state: BindState,
    /** 这一项属于哪类账号 —— 只用来取分类色，让五行的图标一眼能分开。 */
    icon: ImageVector,
    accent: ServiceAccent,
    title: String,
    detail: String,
    // 非 null 时整行可点、并在右侧画一个箭头 —— **清单行本身就是入口**：
    // 点「教务」进教务页、点「今日校园」进今日校园页。
    // 宿舍没有二级页（它的编辑是弹窗），所以那一行传 null，只报状态不导航。
    onClick: (() -> Unit)? = null,
    /**
     * 整行下方**再一条**补充行（目前只有「教务」用它显示最近一次导入）。
     *
     * ⚠️ 两行式之后它已经放得下了（详情那行约 300dp 宽，"最近导入：教务直连 · 成功 06-07 16:40"
     * 只要约 240dp），但仍然**单独成行**：它说的是"这条通道上次的结果"，
     * 与 detail 的"当前状态"不是一回事，混成一行会让"失败 06-06 16:00"读起来像是
     * 校园卡的状态、或宿舍的状态。
     */
    extra: String? = null,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    // 详情行与**名称**左对齐（不是与状态点对齐）：缩进 = 图标块宽度 + 块与名称的间距。
    // 这条缩进就是"这是上面那一条的说明"的全部视觉表达，比加个小标题便宜得多。
    val tileSize = 36.dp
    val detailIndent = tileSize + skin.spaceMd
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            )
            // 每行给足**垂直内边距**，具体值来自「外观 → 我的 → 列表密度」
            //（`LocalProfileRowPadding`，标准档 14dp）。改造前行高完全由文字撑，
            // 单行只有约 20dp 的可视高度 —— 点得中全靠 Compose 给 `clickable`
            // 自动注入的最小交互尺寸（隐形的），看着紧、按着也没底。
            .padding(vertical = LocalProfileRowPadding.current),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 分类色图标块 + 右下角状态点：图标负责"这是哪一类账号"，
            // 点继续承担唯一的"好用 / 待办 / 失败"语义（见 StateDot 的 KDoc）。
            // 点压在块上时垫一圈卡片底色，橙色/红色点在绿色块上才不会糊成一片。
            Box(
                modifier = Modifier
                    .size(tileSize)
                    .clip(RoundedCornerShape(skin.radiusChip))
                    .background(serviceAccentContainer(accent)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = serviceAccentColor(accent),
                    modifier = Modifier.size(19.dp),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(colors.surfaceCard, CircleShape)
                        .padding(2.dp),
                ) {
                    StateDot(state)
                }
            }
            Spacer(Modifier.width(skin.spaceMd))
            Text(
                title,
                modifier = Modifier.weight(1f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (onClick != null) {
                Spacer(Modifier.width(skin.spaceXs))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.height(skin.spaceMicro))
        Text(
            detail,
            modifier = Modifier.padding(start = detailIndent),
            fontSize = 13.sp,
            // 行高 18sp：两行详情时行与行之间不至于挤成一块
            lineHeight = 18.sp,
            color = colors.textSecondary,
            // maxLines = 2：两行式下可用宽度约 300dp，实测最长的「今日校园」
            //（"智慧校园 · 学号 *********6789 · 已绑定，当前未登录"）在 Pixel 5 上占两行；
            // 再长的学号也不至于把它挤成省略号。
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!extra.isNullOrBlank()) {
            Spacer(Modifier.height(skin.spaceMicro))
            Text(
                text = extra,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = detailIndent),
                // 与 detail 同为次级色：这一行也是"要读的信息"，
                // 不用 textTertiary（2.15:1，读不出）—— 理由见 DangerRow 的 KDoc。
                fontSize = 12.sp,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 绑定状态的四态 —— 本页**唯一**一套状态语义色。
 *
 * | 态 | 用于 | 画法 |
 * |---|---|---|
 * | [OK] | 已绑定 / 已导入 / 已登录 | 主题色**实心**点 |
 * | [TODO] | 未绑定 / 未导入 / 未登录 | warning **空心**点（描边） |
 * | [FAILED] | 导入失败 | danger **实心**点 |
 * | [UNKNOWN] | 本机确实读不到（如门户登录态） | 弱化色空心点 |
 *
 * ⚠️ 不用"低透明度 warning / danger 当底色"：深色主题下那点色差读不出来
 *（`CLAUDE.md` 记过这个坑）。所以待办与失败都靠**描边 / 实心点**表意。
 */
internal enum class BindState { OK, TODO, FAILED, UNKNOWN }

/**
 * 状态点 —— 本页**唯一**的状态可视化组件。
 *
 * ## 为什么是"点 + 文字"，而不是一枚 pill
 *
 * 改造前状态标记是一枚 `StatusChip`（浅底 + 彩字）："已绑定"与"未绑定"只差
 * 底色深浅与字色。扫读时读不出**哪一项还没通** —— 而那正是用户进这一页的第一目的。
 *
 * 现在：点给一眼可见的强弱（颜色），文字给精确语义（"导入失败"与"未导入"
 * 光靠颜色分不出来，色盲用户也读不出颜色）。**缺一不可，所以只加颜色、不删文字。**
 *
 * ⚠️ 尺寸取 **10dp**、空心态的描边取 **2dp**（2026-10 两行式改版时调的）：
 * 改造前是 9dp / 1.5dp，配 16sp 的名称显得过于细小，深色下空心环（待办/未知两态）
 * 尤其几乎看不出颜色 —— 而"哪一项还没通"正是用户进这一页的第一目的。
 * 再大就会盖过名称的字重，所以只加 1dp 与 0.5dp。
 *
 * ⚠️ 刻意**不**给点加"低透明度状态色光晕"：`CLAUDE.md` 禁止用语义色低透明度当底色
 *（深色下那点色差读不出来，"标了但看不出"）。点的颜色本身就承担了表意。
 */
@Composable
internal fun StateDot(state: BindState) {
    val colors = LocalPocketXiaoliColors.current
    val color = when (state) {
        BindState.OK -> colors.accentMain
        BindState.TODO -> colors.warning
        BindState.FAILED -> colors.danger
        BindState.UNKNOWN -> colors.textTertiary
    }
    val filled = state == BindState.OK || state == BindState.FAILED
    Box(
        modifier = Modifier
            .size(10.dp)
            .then(
                if (filled) Modifier.background(color, CircleShape)
                else Modifier.border(2.dp, color, CircleShape)
            )
    )
}


/** 区块卡片：图标 + 标题 + 副标题，内容一律按「卡内行间距」排列。 */
@Composable
internal fun BindingSectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    /** 与主页面清单那一行同一个分类色：进哪一项，标题块的色就接着哪一项。 */
    accent: ServiceAccent = ServiceAccent.ACADEMIC,
    // false 时整张卡不渲染（理由见 BindingStatusCard 的同名参数）。
    //
    // ⚠️ 必须放在 `content` **之前**：尾随 lambda 只会绑定到最后一个参数，
    // 放到后面会让所有 `BindingSectionCard(...) { ... }` 调用点编译失败。
    visible: Boolean = true,
    /**
     * 危险组（目前只有「账号管理」用）。
     *
     * 只影响**取色**，不影响结构：
     * - 图标取 `danger` 色，衬底改用中性 `surfaceHighlight`
     * - 副标题从 `textTertiary`（2.15:1）提到 `textSecondary` ——
     *   "销毁性操作，删了不可恢复"这句话必须读得清
     *
     * ⚠️ 衬底**不能**用 `danger.copy(alpha = 0.12f)`：`CLAUDE.md` 明文禁止
     * 用语义色低透明度当底色（深色下那点红只偏移约 2 个色阶，"标了但看不出来"）。
     */
    danger: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val iconTint = if (danger) colors.danger else serviceAccentColor(accent)
    val iconContainer = if (danger) colors.surfaceHighlight else serviceAccentContainer(accent)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusCard),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(skin.cardPadding),
            verticalArrangement = Arrangement.spacedBy(skin.spaceMd),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(skin.radiusChip))
                        .background(iconContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(skin.spaceMd))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Text(
                        subtitle,
                        fontSize = 12.sp,
                        // 副标题是"这一页在做什么"的唯一说明，用次级色而不是最弱一档：
                        // textTertiary 压卡底约 2.15:1，长句几乎读不清（见 DangerRow 的说明）。
                        color = colors.textSecondary,
                    )
                }
            }
            content()
        }
    }
}

/**
 * 导入时间。
 *
 * ⚠️ `timestamp <= 0` 显示**"时间未知"**，不要格式化成 1970（那会渲染成 `01-01 08:00`）：
 * 老存档缺这个字段时 Gson 反射赋值会留下 0，而格式化出来的假时间**看起来像真时间**，
 * 比"未知"更糟 —— 与绑定状态卡 `BindState.UNKNOWN` 是同一条原则：读不到就别编。
 */
private fun formatImportTime(timestamp: Long): String =
    if (timestamp <= 0L) "时间未知"
    else SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

/**
 * 「学习平台」的一张行卡：平台名 + 登录状态 + 去登录。
 *
 * 2026-10 改：整行可点（此前只有右侧那个小文字按钮可点，命中区约 48×20dp），
 * 左侧补上一块与总览清单同款的分类图标，状态点 + 文字也沿用 [StateDot] ——
 * 这一页的所有"登录没登录"都用同一套语义，不该在最后一张卡里换一种说法。
 */
@Composable
private fun LearningBindingRow(
    name: String,
    bound: Boolean,
    onLogin: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surfaceHighlight),
        shape = RoundedCornerShape(skin.radiusChip),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 按压反馈走全 App 的 MIUIX 手感（`miuixPress` 已内置 clickable）
                .miuixPress(onClick = onLogin)
                .padding(skin.spaceCompact),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(skin.radiusChip))
                    .background(serviceAccentContainer(ServiceAccent.STUDY)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.MenuBook,
                    contentDescription = null,
                    tint = serviceAccentColor(ServiceAccent.STUDY),
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(skin.spaceMd))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(skin.spaceMicro),
            ) {
                // 与「我的」这一片所有行标题同号（15sp）
                Text(name, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary)
                // 状态标记改成「点 + 词」：与总览检查清单同一套语义（见 StateDot）。
                // 改造前这里是一枚 pill（"已登录"），与总览里的 "n/2" 说法对不上 ——
                // 用户要读两遍才知道说的是同一件事。
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StateDot(if (bound) BindState.OK else BindState.TODO)
                    Spacer(Modifier.width(skin.spaceXs))
                    Text(
                        if (bound) "已登录 · Cookie 已加密保存在本机"
                        else "未登录 · 登录后可同步考试 / 作业",
                        fontSize = 12.sp,
                        // 这里承载的是"去登录能得到什么"，用次级色而不是最弱的一档
                        color = colors.textSecondary,
                    )
                }
            }
            Spacer(Modifier.width(skin.spaceSm))
            Text(
                if (bound) "换绑" else "登录",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = colors.accentMain,
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// ═══ 二级页面 ═══
//
// 为什么要有二级页（2026-10，修正上一轮）：
// 改造后这一页有 7 张卡 / 7 个输入框 / 11 个按钮 / 14 个状态行 / 约 3.5 屏，
// 滚到底很累。层级与滚动是一对**无法两全**的张力，所以按**入口维度**切：
//
//   主页面  → 绑定状态清单（行本身兼作入口）+ 账号管理入口
//   二级页  → 教务账号（含校区）/ 宿舍绑定 / 今日校园 / 校园卡 / 学习平台 / 账号管理
//
// 主页面因此只剩约 1 屏，而且进页面第一眼就能回答"我绑齐了没"
//（这正是用户进这一页的第一目的）。
//
// ⚠️ 2026-10 追加：宿舍**也**做成了二级页。它的表单原来挤在一个 AlertDialog 里
//（两个转轮 + 服务校区 + 编号输入 + 实时摘要 + 保存/解绑），转轮最高只给到 168dp，
// 两段说明也没地方放。移到整页后字段能喘口气 —— 见 DormBindingForm.kt 的文件头。
internal enum class BindSubPage(val title: String) {
    ACADEMIC("教务账号"),
    TODAY_CAMPUS("今日校园"),
    CAMPUS_CARD("校园卡（易校园）"),
    LEARNING("学习平台"),
    DORM("宿舍绑定"),
    ACCOUNT_DANGER("账号管理"),
}
