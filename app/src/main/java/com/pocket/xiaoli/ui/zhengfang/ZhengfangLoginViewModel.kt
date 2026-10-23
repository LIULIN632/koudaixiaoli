package com.pocket.xiaoli.ui.zhengfang

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocket.xiaoli.data.JwSystemType
import com.pocket.xiaoli.data.School
import com.pocket.xiaoli.data.SchoolRegistry
import com.pocket.xiaoli.di.AppContainer
import com.pocket.xiaoli.network.CaptchaResult
import com.pocket.xiaoli.network.ZhengfangClient
import com.pocket.xiaoli.network.ZhengfangSchool
import com.pocket.xiaoli.util.PinyinSortUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * 正方教务「只登录不拉课表」流程的公共状态契约（UI 共享组件只依赖此接口）。
 * 由 ZhengfangLoginViewModel 及其子类实现。
 */
interface ZhengfangLoginState {
    val selectedSchool: StateFlow<School?>
    val campusOnly: Boolean
    val searchQuery: StateFlow<String>
    val filteredSchools: StateFlow<List<School>>
    val username: StateFlow<String>
    val password: StateFlow<String>
    val isLoading: StateFlow<Boolean>
    val progress: StateFlow<String>
    val error: StateFlow<String?>
    val showCaptcha: StateFlow<Boolean>
    val captchaBase64: StateFlow<String?>
    /** 当前学校/校区已绑定的学号（供 UI 展示「正在用哪个账号」），无绑定为 null */
    val boundStudentId: StateFlow<String?>
    /** 绑定账号自动登录状态 */
    val boundLoginStatus: StateFlow<BoundLoginStatus>

    fun updateSearch(query: String)
    fun selectSchool(school: School)
    fun updateUsername(v: String)
    fun updatePassword(v: String)
    fun backToSchoolList()
    fun startLogin()
    fun submitCaptcha(code: String)
    fun refreshCaptcha()
    fun cancelCaptcha()
    /**
     * 把绑定账号填进登录表单，**不发起登录**（进页面时用）。
     *
     * 与 [autoLoginIfBound] 的区别就是"要不要替用户按下那个按钮"：账号预填省掉了手打学号，
     * 但联网登录与验证码仍由用户点一下触发。
     */
    fun prefillBoundCredentials()

    /**
     * 用绑定账号自动登录（**只给用户自己发起的动作**，如「我的课程 → 一键同步」）。
     * ⚠️ 不要在进页面时调它 —— 理由见实现处的 KDoc。
     */
    fun autoLoginIfBound()
}

/** 绑定账号自动登录状态：RUNNING 显示进度，FAILED 提示可手动重试。 */
enum class BoundLoginStatus { IDLE, RUNNING, FAILED }

/**
 * 正方教务「只登录不拉课表」流程的公共基类（成绩查询 / 修课情况 / 成绩存档共用）：
 * 选学校 → 登录表单 → 验证码挂起恢复 → loginOnly 建立会话。
 *
 * 子类只需实现登录成功后的数据加载（[afterLogin]），并在需要时清理各自的查询结果
 * （[onBackToSchoolList] / [onLogout] / [onLoginStart]）。
 */
abstract class ZhengfangLoginViewModel(
    protected val client: ZhengfangClient,
    protected val schoolRegistry: SchoolRegistry,
    protected val appContainer: AppContainer
) : ViewModel(), ZhengfangLoginState {

    // ── 学校选择（专用客户端：锁定当前校区学校）──
    private val campusSchool: School? = appContainer.currentCampusSchool()
    override val campusOnly: Boolean get() = campusSchool != null
    protected val _selectedSchool = MutableStateFlow<School?>(campusSchool)
    override val selectedSchool: StateFlow<School?> = _selectedSchool

    /** 绑定信息里的账号：优先当前学校教务地址，兼容旧的 campus_bind 键 */
    private fun boundCredentialFor(school: School?): Pair<String, String>? = school?.let {
        appContainer.loadCredential(it.baseUrl) ?: appContainer.loadCredential("campus_bind")
    }

    override val boundStudentId: StateFlow<String?> = _selectedSchool
        .map { boundCredentialFor(it)?.first }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            boundCredentialFor(_selectedSchool.value)?.first,
        )

    private val _boundLoginStatus = MutableStateFlow(BoundLoginStatus.IDLE)
    override val boundLoginStatus: StateFlow<BoundLoginStatus> = _boundLoginStatus
    private var autoLoginTriggered = false
    private var autoLoginPending = false

    protected val _searchQuery = MutableStateFlow("")
    override val searchQuery: StateFlow<String> = _searchQuery

    override val filteredSchools: StateFlow<List<School>> = combine(
        _searchQuery, appContainer.favoriteSchoolIds, appContainer.customSchools
    ) { query, favIds, customs ->
        if (campusOnly) {
            return@combine listOfNotNull(campusSchool)
        }
        val registrySchools = schoolRegistry.filter(JwSystemType.ZHENGFANG, query)
        val matchingCustoms = customs.filter {
            it.jwType == JwSystemType.ZHENGFANG &&
                (query.isBlank() || it.name.contains(query, ignoreCase = true))
        }
        (matchingCustoms + registrySchools).sortedWith(
            compareByDescending<School> { it.id in favIds }
                .thenBy { PinyinSortUtil.sortKey(it.name) }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── 登录表单 ──
    protected val _username = MutableStateFlow("")
    override val username: StateFlow<String> = _username
    protected val _password = MutableStateFlow("")
    override val password: StateFlow<String> = _password
    protected val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading
    protected val _progress = MutableStateFlow("")
    override val progress: StateFlow<String> = _progress
    protected val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error

    init {
        boundCredentialFor(campusSchool)?.let { cred ->
            _username.value = cred.first
            _password.value = cred.second
        }
    }

    // ── 登录态 ──
    protected val _loggedIn = MutableStateFlow(false)
    val loggedIn: StateFlow<Boolean> = _loggedIn

    // ── 验证码（挂起恢复模式，与 ImportViewModel 同款）──
    protected val _captchaBase64 = MutableStateFlow<String?>(null)
    override val captchaBase64: StateFlow<String?> = _captchaBase64
    protected val _showCaptcha = MutableStateFlow(false)
    override val showCaptcha: StateFlow<Boolean> = _showCaptcha
    private var captchaContinuation: ((String) -> Unit)? = null
    private var captchaRefresher: (suspend () -> CaptchaResult)? = null
    private var loginJob: Job? = null

    override fun updateSearch(query: String) { _searchQuery.value = query }

    override fun selectSchool(school: School) {
        if (campusOnly && school.id != campusSchool?.id) return
        _selectedSchool.value = school
        // 自动回填已记忆的账号密码
        val cred = boundCredentialFor(school)
        _username.value = cred?.first ?: ""
        _password.value = cred?.second ?: ""
        _error.value = null
    }

    override fun updateUsername(v: String) { _username.value = v }
    override fun updatePassword(v: String) { _password.value = v }

    /** 返回学校列表（换学校/返回），并清理登录与查询状态 */
    override fun backToSchoolList() {
        loginJob?.cancel()
        if (!campusOnly) {
            _selectedSchool.value = null
        }
        _loggedIn.value = false
        _error.value = null
        _progress.value = ""
        _showCaptcha.value = false
        failAutoLoginIfPending()
        _boundLoginStatus.value = BoundLoginStatus.IDLE
        onBackToSchoolList()
    }

    /** 退出登录（保留选中的学校，回到登录表单） */
    fun logout() {
        loginJob?.cancel()
        _loggedIn.value = false
        _error.value = null
        _progress.value = ""
        failAutoLoginIfPending()
        _boundLoginStatus.value = BoundLoginStatus.IDLE
        onLogout()
    }

    /** 只登录建立会话，成功后调用 [afterLogin] 加载各自数据 */
    override fun startLogin() {
        val school = _selectedSchool.value ?: return
        val u = _username.value.trim()
        val p = _password.value
        if (u.isBlank() || p.isBlank()) {
            _error.value = "请输入学号和密码"
            failAutoLoginIfPending()
            return
        }
        if (!autoLoginPending) {
            // 手动登录：清掉上一次「自动登录失败」的提示
            _boundLoginStatus.value = BoundLoginStatus.IDLE
        }

        loginJob?.cancel()
        captchaContinuation = null
        captchaRefresher = null

        _isLoading.value = true
        _progress.value = ""
        _error.value = null
        _showCaptcha.value = false
        onLoginStart()

        val zfSchool = ZhengfangSchool(school.name, school.baseUrl.trimEnd('/'), school.isV8)
        loginJob = viewModelScope.launch {
            try {
                val res = client.loginOnly(
                    school = zfSchool,
                    username = u,
                    password = p,
                    onProgress = { msg -> _progress.value = msg },
                    onCaptcha = { captchaResult ->
                        captchaRefresher = captchaResult.refresh
                        withContext(Dispatchers.Main) {
                            _captchaBase64.value = captchaResult.base64
                            _showCaptcha.value = true
                        }
                        suspendCancellableCoroutine<String> { cont ->
                            if (cont.isActive) {
                                captchaContinuation = { code ->
                                    if (cont.isActive && !code.isNullOrBlank()) {
                                        cont.resume(code)
                                    }
                                }
                                cont.invokeOnCancellation { captchaContinuation = null }
                            }
                        }
                    }
                )
                _isLoading.value = false
                _showCaptcha.value = false
                if (res.success) {
                    appContainer.saveCredential(school.baseUrl, u, p)
                    autoLoginPending = false
                    _boundLoginStatus.value = BoundLoginStatus.IDLE
                    _loggedIn.value = true
                    afterLogin(school)
                } else {
                    _error.value = res.error ?: "登录失败"
                    failAutoLoginIfPending()
                }
            } catch (e: CancellationException) {
                // 正常的协程取消，静默处理
                _isLoading.value = false
                _showCaptcha.value = false
            } catch (e: Exception) {
                _isLoading.value = false
                _showCaptcha.value = false
                _error.value = "登录失败：${e.message ?: "未知错误"}"
                failAutoLoginIfPending()
            }
        }
    }

    /**
     * 进页面时只**预填**绑定账号，不登录。
     *
     * 用户的抱怨是"点进去就自动开始"，而不是"表单空着要手打" —— 所以省点击这件事照做，
     * 只是把最后那一下（联网登录 / 弹验证码）还给用户。
     */
    override fun prefillBoundCredentials() {
        val cred = boundCredentialFor(_selectedSchool.value) ?: return
        if (_loggedIn.value || _isLoading.value) return
        _username.value = cred.first
        _password.value = cred.second
    }

    /**
     * 进入查询页时：若已绑定账号，则用绑定信息自动登录一次（验证码照常弹窗）；
     * 失败会置 [BoundLoginStatus.FAILED] 并回退到已预填的手动表单，不会重复自动重试。
     *
     * ⚠️ **只给"用户自己发起的动作"用**（目前唯一调用点是「我的课程」里点了「一键同步」）。
     * 2026-10 用户报"查成绩 / 调入课表点进去就自动开始" —— 进页面就自动登录有三条代价：
     * ① 用户没要求就**联网登录教务**；② 学校要验证码时会**立刻弹验证码框**打断；
     * ③ 慢网下页面自己转圈，看着像卡死。进页面改用 [prefillBoundCredentials]（只填表不登录）。
     */
    override fun autoLoginIfBound() {
        val school = _selectedSchool.value
        if (!BoundLogin.shouldAutoLogin(
                alreadyTriggered = autoLoginTriggered,
                loggedIn = _loggedIn.value,
                loading = _isLoading.value,
                hasSchool = school != null,
                hasCredential = boundCredentialFor(school) != null,
            )
        ) return

        val cred = boundCredentialFor(school) ?: return
        autoLoginTriggered = true
        autoLoginPending = true
        _boundLoginStatus.value = BoundLoginStatus.RUNNING
        _username.value = cred.first
        _password.value = cred.second
        startLogin()
    }

    private fun failAutoLoginIfPending() {
        if (autoLoginPending) {
            autoLoginPending = false
            _boundLoginStatus.value = BoundLoginStatus.FAILED
        }
    }
    // ── 子类扩展点 ──

    /** 登录成功后的数据加载（在 viewModelScope 中调用） */
    protected open suspend fun afterLogin(school: School) {}

    /** 开始登录时清理各自的旧数据 */
    protected open fun onLoginStart() {}

    /** 返回学校列表时清理各自的查询结果 */
    protected open fun onBackToSchoolList() {}

    /** 退出登录时清理各自的查询结果 */
    protected open fun onLogout() {}

    // ── 验证码操作 ──

    override fun submitCaptcha(code: String) {
        _showCaptcha.value = false
        captchaContinuation?.invoke(code)
        captchaContinuation = null
    }

    override fun refreshCaptcha() {
        val refresher = captchaRefresher ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _progress.value = "正在刷新验证码……"
                val result = refresher()
                withContext(Dispatchers.Main) {
                    _captchaBase64.value = result.base64
                }
                captchaRefresher = result.refresh
                _progress.value = ""
            } catch (e: CancellationException) {
                // 正常取消，忽略
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { _progress.value = "" }
            }
        }
    }

    override fun cancelCaptcha() {
        _showCaptcha.value = false
        captchaContinuation = null
        captchaRefresher = null
        loginJob?.cancel()
        loginJob = null
        _isLoading.value = false
        failAutoLoginIfPending()
    }

    fun clearError() { _error.value = null }

    override fun onCleared() {
        super.onCleared()
        captchaContinuation = null
        captchaRefresher = null
        loginJob?.cancel()
        loginJob = null
    }
}
