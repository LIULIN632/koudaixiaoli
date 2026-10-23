package com.pocket.xiaoli.ui.import_

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pocket.xiaoli.data.JwSystemType
import com.pocket.xiaoli.data.School
import com.pocket.xiaoli.data.SchoolRegistry
import com.pocket.xiaoli.data.isJwSystemAvailable
import com.pocket.xiaoli.data.isPickerSchool
import com.pocket.xiaoli.data.pickerRank
import com.pocket.xiaoli.data.repository.CourseRepository
import com.pocket.xiaoli.di.AppContainer
import com.pocket.xiaoli.network.LoginResult
import com.pocket.xiaoli.network.QiangZhiClient
import com.pocket.xiaoli.network.QiangZhiSchool
import com.pocket.xiaoli.network.ZhengfangClient
import com.pocket.xiaoli.network.ZhengfangSchool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class ImportViewModel(
    private val repository: CourseRepository,
    private val client: ZhengfangClient,
    private val qiangzhiClient: QiangZhiClient,
    private val schoolRegistry: SchoolRegistry,
    private val appContainer: AppContainer
) : ViewModel() {
    private val _selectedSystem = MutableStateFlow<JwSystemType?>(null)
    val selectedSystem: StateFlow<JwSystemType?> = _selectedSystem

    private val _selectedSchool = MutableStateFlow<School?>(null)
    val selectedSchool: StateFlow<School?> = _selectedSchool
    private val campusSchool: School? = appContainer.currentCampusSchool()

    val campusOnly: Boolean get() = campusSchool != null

    /** 已绑定账号：优先当前校区教务地址，兼容旧的 campus_bind。 */
    val boundCredentials: Pair<String, String>? = campusSchool?.let { school ->
        appContainer.loadCredential(school.baseUrl) ?: appContainer.loadCredential("campus_bind")
    }

    val hasBoundAccount: Boolean get() = boundCredentials != null

    init {
        campusSchool?.let { school ->
            _selectedSystem.value = school.jwType
            _selectedSchool.value = school
        } ?: run {
            // 产品重心已收窄到烟台（当前对接校区用正方，后续覆盖的烟台学校也都是正方），
            // 所以进页面直接落到「正方 + 学校列表」，不再让用户在 5 个系统里先做一次选择。
            // 强智仍可在学校列表顶部切过去（原生导入只支持这两家）。
            _selectedSystem.value = JwSystemType.ZHENGFANG
        }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _favoriteIds = appContainer.favoriteSchoolIds
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds

    fun toggleFavorite(school: School) {
        appContainer.toggleFavoriteSchool(school.id)
    }

    val filteredSchools: StateFlow<List<School>> = combine(
        _searchQuery, _selectedSystem, _favoriteIds, appContainer.customSchools
    ) { query, system, favIds, customSchools ->
        if (campusOnly) {
            return@combine listOfNotNull(campusSchool).filter {
                query.isBlank() || it.name.contains(query, ignoreCase = true)
            }
        }
        val registrySchools = if (system != null) schoolRegistry.filter(system, query) else emptyList()
        val matchingCustoms = if (system != null) {
            customSchools.filter {
                it.jwType == system && (query.isBlank() || it.name.contains(query, ignoreCase = true))
            }
        } else emptyList()
        (matchingCustoms + registrySchools)
            .filter { it.isPickerSchool() }
            .sortedWith(
                compareByDescending<School> { it.id in favIds }
                    .thenBy { it.pickerRank() }
                    .thenBy { com.pocket.xiaoli.util.PinyinSortUtil.sortKey(it.name) }
            )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _progress = MutableStateFlow("")
    val progress: StateFlow<String> = _progress

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _result = MutableStateFlow<LoginResult?>(null)
    val result: StateFlow<LoginResult?> = _result

    private val _captchaBase64 = MutableStateFlow<String?>(null)
    val captchaBase64: StateFlow<String?> = _captchaBase64

    private val _showCaptcha = MutableStateFlow(false)
    val showCaptcha: StateFlow<Boolean> = _showCaptcha

    // ── Coroutine lifecycle management (matches Dawn-Course pattern) ──
    private var loginJob: Job? = null
    private var captchaContinuation: ((String) -> Unit)? = null
    private var captchaRefresher: (suspend () -> com.pocket.xiaoli.network.CaptchaResult)? = null

    fun selectSystem(type: JwSystemType) {
        if (campusOnly) return
        if (isJwSystemAvailable(type)) {
            _selectedSystem.value = type
            _selectedSchool.value = null
            _searchQuery.value = ""
        }
    }

    fun selectSchool(school: School?) {
        if (campusOnly && school?.id != campusSchool?.id) return
        _selectedSchool.value = school
    }

    /** 添加用户手动输入的学校（指定教务系统类型，持久化到 SharedPreferences），返回新学校供自动选中。 */
    fun addCustomSchool(name: String, url: String, jwType: JwSystemType): School? {
        val trimmedUrl = url.trim().trimEnd('/')
        if (name.isBlank() || trimmedUrl.isBlank()) return null
        val school = School(
            id = "custom_${System.currentTimeMillis()}",
            name = name.trim(),
            city = "自定义",
            jwType = jwType,
            baseUrl = if (trimmedUrl.startsWith("http")) trimmedUrl else "https://$trimmedUrl",
            isV8 = true
        )
        appContainer.addCustomSchool(school)
        return school
    }

    /** 编辑自定义学校（按 id 定位更新），返回更新后的学校；输入无效返回 null */
    fun updateCustomSchool(old: School, name: String, url: String): School? {
        val trimmedUrl = url.trim().trimEnd('/')
        if (name.isBlank() || trimmedUrl.isBlank()) return null
        val updated = old.copy(
            name = name.trim(),
            baseUrl = if (trimmedUrl.startsWith("http")) trimmedUrl else "https://$trimmedUrl"
        )
        appContainer.updateCustomSchool(old, updated)
        return updated
    }

    /** 删除自定义学校 */
    fun removeCustomSchool(school: School) {
        appContainer.removeCustomSchool(school)
    }

    // ── Credential memory ──
    fun loadSavedCredentials(baseUrl: String): Pair<String, String>? =
        appContainer.loadCredential(baseUrl)

    fun saveCredentials(baseUrl: String, username: String, password: String) {
        appContainer.saveCredential(baseUrl, username, password)
    }

    fun deleteCredentials(baseUrl: String) {
        appContainer.deleteCredential(baseUrl)
    }

    fun updateSearch(query: String) {
        _searchQuery.value = query
    }

    fun goBack() {
        cancelCaptcha()
        _result.value = null
        _progress.value = ""
        if (campusOnly) return
        // 系统选择已经收成列表顶部的一排胶囊，不再是独立的一步 —— 返回只退学校。
        _selectedSchool.value = null
        _searchQuery.value = ""
    }

    fun startLogin(username: String, password: String) {
        val school = _selectedSchool.value ?: return

        // Cancel any previous login attempt
        loginJob?.cancel()
        captchaContinuation = null
        captchaRefresher = null

        _isLoading.value = true
        _progress.value = ""
        _result.value = null
        _showCaptcha.value = false

        val zfSchool = ZhengfangSchool(school.name, school.baseUrl.trimEnd('/'), school.isV8)
        val qzSchool = QiangZhiSchool(school.name, school.baseUrl.trimEnd('/'))

        loginJob = viewModelScope.launch {
            try {
                val res = when (school.jwType) {
                    JwSystemType.ZHENGFANG -> client.login(
                        school = zfSchool,
                        username = username,
                        password = password,
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
                                    cont.invokeOnCancellation {
                                        captchaContinuation = null
                                    }
                                }
                            }
                        }
                    )
                    else -> qiangzhiClient.login(
                        school = qzSchool,
                        username = username,
                        password = password,
                        onProgress = { msg -> _progress.value = msg }
                    )
                }
                _isLoading.value = false
                _showCaptcha.value = false
                _result.value = res

                if (res.success && res.courses != null) {
                    // 与 Web 导入一致：写入当前激活的方案，而不是默认方案
                    val schemeId = appContainer.activeSchemeId.value
                    try {
                        val existing = repository.allCourses.first()
                        val newCourses = res.courses
                            .filter { new ->
                                existing.none { existingCourse ->
                                    existingCourse.schemeId == schemeId &&
                                    existingCourse.name == new.name &&
                                    existingCourse.dayOfWeek == new.dayOfWeek &&
                                    existingCourse.startSlot == new.startSlot
                                }
                            }
                            .map { it.copy(schemeId = schemeId) }
                        repository.insertAll(newCourses)
                        appContainer.recordImport("教务直连", newCourses.size, true)
                    } catch (e: Exception) {
                        // DB 写入失败不覆盖登录成功的结果，仅记录提示
                        android.util.Log.w("ImportViewModel", "课程保存失败", e)
                        _progress.value = "课程已获取，但保存失败：${e.message ?: "未知错误"}"
                    }

                    // 自动回填学期设置（仅当用户尚未设置过开学日期）
                    if (!res.semesterStart.isNullOrBlank() && appContainer.semesterStart.value.isBlank()) {
                        appContainer.setSemesterStart(res.semesterStart!!)
                        if (res.semesterWeeks != null && res.semesterWeeks!! in 1..60) {
                            appContainer.setSemesterWeeks(res.semesterWeeks!!)
                        }
                    }
                    // 教务未返回学期信息且用户未设过开学日期 → 课表周次会对不上，
                    // 标记后由课表页弹出「学期周次校准」引导（选当前是第几周反推开学日期）
                    if (appContainer.semesterStart.value.isBlank()) {
                        appContainer.requestSemesterCalibration()
                    }
                } else {
                    appContainer.recordImport("教务直连", 0, false, res.error)
                }
            } catch (e: CancellationException) {
                // ViewModel scope cancelled — ignore silently, this is normal
                _isLoading.value = false
                _showCaptcha.value = false
            } catch (e: Exception) {
                _isLoading.value = false
                _showCaptcha.value = false
                _result.value = LoginResult(false, "登录失败：${e.message ?: "未知错误"}")
                appContainer.recordImport("教务直连", 0, false, e.message)
            }
        }
    }

    fun submitCaptcha(code: String) {
        _showCaptcha.value = false
        captchaContinuation?.invoke(code)
        captchaContinuation = null
    }

    fun refreshCaptcha() {
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
                // Normal cancellation, ignore
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _progress.value = ""
                }
            }
        }
    }

    fun cancelCaptcha() {
        _showCaptcha.value = false
        captchaContinuation = null
        captchaRefresher = null
        loginJob?.cancel()
        loginJob = null
        _isLoading.value = false
    }

    fun reset() {
        _result.value = null
        _progress.value = ""
        _showCaptcha.value = false
    }

    override fun onCleared() {
        super.onCleared()
        captchaContinuation = null
        captchaRefresher = null
        loginJob?.cancel()
        loginJob = null
    }

    class Factory(
        private val repository: CourseRepository,
        private val client: ZhengfangClient,
        private val qiangzhiClient: QiangZhiClient,
        private val schoolRegistry: SchoolRegistry,
        private val appContainer: AppContainer
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ImportViewModel(repository, client, qiangzhiClient, schoolRegistry, appContainer) as T
    }
}
