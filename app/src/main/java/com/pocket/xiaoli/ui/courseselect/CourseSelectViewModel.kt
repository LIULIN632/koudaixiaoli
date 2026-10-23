package com.pocket.xiaoli.ui.courseselect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pocket.xiaoli.data.School
import com.pocket.xiaoli.data.SchoolRegistry
import com.pocket.xiaoli.di.AppContainer
import com.pocket.xiaoli.network.CourseSelectItem
import com.pocket.xiaoli.network.ZhengfangClient
import com.pocket.xiaoli.network.ZhengfangCourseSelectApi
import com.pocket.xiaoli.ui.zhengfang.ZhengfangLoginViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** 选课页的两个分区 */
enum class CourseSelectSection { PRE_SELECT, SELF_SELECT }

/**
 * 「可选 / 已选」子视图。
 *
 * 刻意不用一个可空列表来表达：可空只能区分"还没加载"与"加载完了"，
 * 而"加载完了但这门课确实没开/没选"和"解析出来是空的（可能是字段名变了）"是**两件事** ——
 * 后者要把用户引到开发者实验室去看原始响应，前者不该打扰用户。
 */
enum class SelectListPhase { IDLE, LOADING, LOADED, FAILED }

/**
 * 选课（只读浏览）：课程预选 + 自主选课。
 *
 * ## 为什么继承 [ZhengfangLoginViewModel]
 * 选校 / 登录表单 / 验证码刷新与恢复全部复用基类 —— 项目铁律，
 * 新增同类查询页**禁止**复制粘贴登录流程。本类只负责"登录成功之后拉什么、怎么展示"。
 *
 * ## 只读边界（重要）
 * 本页**只查询**，不代提交选课：
 * - 点「去选课」把用户送到教务网页，由本人在教务系统里完成提交；
 * - 这样既不触碰选课提交接口（风控与误操作风险），也不需要维护一套"谁被选上了"的状态机。
 *
 * 这与仓库既有设计一致（`docs/superpowers/specs/2026-10-09-campus-incremental-architecture-design.md`
 * §2.2.1 与 §0.3-N5：「模拟 POST 一键选课」**不做**）。
 */
class CourseSelectViewModel(
    client: ZhengfangClient,
    schoolRegistry: SchoolRegistry,
    appContainer: AppContainer,
) : ZhengfangLoginViewModel(client, schoolRegistry, appContainer) {

    private val api = ZhengfangCourseSelectApi(client)

    // ── 分区与检索 ──
    private val _section = MutableStateFlow(CourseSelectSection.PRE_SELECT)
    val section: StateFlow<CourseSelectSection> = _section

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    // ── 课程预选 ──
    private val _preSelectItems = MutableStateFlow<List<CourseSelectItem>>(emptyList())
    private val _preSelectTotal = MutableStateFlow(0)
    private val _preSelectPhase = MutableStateFlow(SelectListPhase.IDLE)

    // ── 自主选课 ──
    private val _selfSelectItems = MutableStateFlow<List<CourseSelectItem>>(emptyList())
    private val _selfSelectTotal = MutableStateFlow(0)
    private val _selfSelectPhase = MutableStateFlow(SelectListPhase.IDLE)

    /** 加载进度文案（与基类的登录进度分开，避免登录态被覆盖） */
    private val _loadProgress = MutableStateFlow("")
    val loadProgress: StateFlow<String> = _loadProgress

    /**
     * 服务端声明了条数、但本地没解析出对应数量的行 —— 典型信号是容器名/字段名与预期不符。
     * 为 true 时界面给一句"可能没解析全，可去开发者实验室看原始响应"，而不是假装正常。
     */
    private val _possiblyTruncated = MutableStateFlow(false)
    val possiblyTruncated: StateFlow<Boolean> = _possiblyTruncated

    // 两个分区各自只自动加载一次
    private var preSelectRequested = false
    private var selfSelectRequested = false

    // ══════════════════ 对外动作 ══════════════════

    fun selectSection(value: CourseSelectSection) {
        if (_section.value == value) return
        _section.value = value
        // 只在已登录时才自动拉数据；未登录时用户还在选校/登录，没必要预取
        if (loggedIn.value) ensureLoaded(value)
    }

    fun updateQuery(v: String) { _query.value = v }

    /** 切换分区时按需拉取（每个分区只拉一次，返回时不会重复请求） */
    fun ensureLoaded(value: CourseSelectSection = _section.value) {
        when (value) {
            CourseSelectSection.PRE_SELECT -> {
                if (preSelectRequested) return
                preSelectRequested = true
                loadPreSelect()
            }
            CourseSelectSection.SELF_SELECT -> {
                if (selfSelectRequested) return
                selfSelectRequested = true
                loadSelfSelect()
            }
        }
    }

    /** 用户手动重试当前分区（失败后用） */
    fun retryCurrent() {
        when (_section.value) {
            CourseSelectSection.PRE_SELECT -> {
                preSelectRequested = true
                loadPreSelect()
            }
            CourseSelectSection.SELF_SELECT -> {
                selfSelectRequested = true
                loadSelfSelect()
            }
        }
    }

    // ══════════════════ 数据暴露（检索过滤在 UI 侧做，这里给原始列表） ══════════════════

    val preSelectItems: StateFlow<List<CourseSelectItem>> = _preSelectItems
    val selfSelectItems: StateFlow<List<CourseSelectItem>> = _selfSelectItems

    val preSelectPhase: StateFlow<SelectListPhase> = _preSelectPhase
    val selfSelectPhase: StateFlow<SelectListPhase> = _selfSelectPhase

    val preSelectTotal: StateFlow<Int> = _preSelectTotal
    val selfSelectTotal: StateFlow<Int> = _selfSelectTotal

    // ══════════════════ 加载 ══════════════════

    private fun loadPreSelect() {
        val baseUrl = selectedSchool.value?.baseUrl ?: return
        viewModelScope.launch {
            _preSelectPhase.value = SelectListPhase.LOADING
            _loadProgress.value = "正在获取预选课程……"
            _error.value = null
            _possiblyTruncated.value = false
            try {
                val baseList = api.fetchPreSelectCourses(baseUrl)
                // 已选列表单独取一次：它带 `xsyxb_id` 真实 ID 与停开状态，
                // 是"我有没有选上"的权威口径（列表端点的 xsyxb_id 只区分选没选）。
                val choosedList = runCatching { api.fetchPreSelectChoosed(baseUrl) }.getOrNull()

                val merged = PreSelectMerger.merge(baseList.items, choosedList?.items.orEmpty())
                _preSelectItems.value = merged
                // 优先用已选端点的总数（它才是"已选几门"），拿不到就用列表端点的
                _preSelectTotal.value = baseList.totalResult.takeIf { it > 0 }
                    ?: choosedList?.totalResult ?: 0
                _possiblyTruncated.value = baseList.parsed && baseList.totalResult > baseList.items.size
                _preSelectPhase.value = SelectListPhase.LOADED
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.message ?: "获取预选课程失败"
                _preSelectPhase.value = SelectListPhase.FAILED
            } finally {
                _loadProgress.value = ""
            }
        }
    }

    private fun loadSelfSelect() {
        val baseUrl = selectedSchool.value?.baseUrl ?: return
        viewModelScope.launch {
            _selfSelectPhase.value = SelectListPhase.LOADING
            _loadProgress.value = "正在获取自主选课课程……"
            _error.value = null
            _possiblyTruncated.value = false
            try {
                val result = api.fetchSelfSelectCourses(baseUrl)
                _selfSelectItems.value = result.items
                _selfSelectTotal.value = result.totalResult
                _possiblyTruncated.value = result.parsed && result.totalResult > result.items.size
                _selfSelectPhase.value = SelectListPhase.LOADED
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.message ?: "获取自主选课课程失败"
                _selfSelectPhase.value = SelectListPhase.FAILED
            } finally {
                _loadProgress.value = ""
            }
        }
    }

    /**
     * 合成预选列表 —— 实现已提到纯函数 [PreSelectMerger.merge]。
     *
     * 为什么提出去：ViewModel 依赖 `AppContainer`，在纯 JVM 单测里构造不出来，
     * 逻辑留在里面就等于**测不到**；而这段是"显示错了我选错课"的那类逻辑，
     * 必须有穷举测试。合成规则与理由见该文件的 KDoc。
     */
    // （原先这里有 mergePreSelect / mergeKey 两个私有实现，已迁至 PreSelectMerger.kt）

    // ══════════════════ 基类扩展点 ══════════════════

    /** 登录成功 → 拉当前分区的列表 */
    override suspend fun afterLogin(school: School) {
        // 基类在 afterLogin 之前已把 isLoading 置 false；这里用自己的进度状态，
        // 不去写基类的 _isLoading（那会让基类 finally 里的复位语义变模糊）。
        ensureLoaded(_section.value)
    }

    override fun onLoginStart() {
        _preSelectPhase.value = SelectListPhase.IDLE
        _selfSelectPhase.value = SelectListPhase.IDLE
        _preSelectItems.value = emptyList()
        _selfSelectItems.value = emptyList()
        _preSelectTotal.value = 0
        _selfSelectTotal.value = 0
        preSelectRequested = false
        selfSelectRequested = false
        _possiblyTruncated.value = false
    }

    override fun onBackToSchoolList() = clearResults()
    override fun onLogout() = clearResults()

    private fun clearResults() {
        _preSelectItems.value = emptyList()
        _selfSelectItems.value = emptyList()
        _preSelectTotal.value = 0
        _selfSelectTotal.value = 0
        _preSelectPhase.value = SelectListPhase.IDLE
        _selfSelectPhase.value = SelectListPhase.IDLE
        preSelectRequested = false
        selfSelectRequested = false
        _possiblyTruncated.value = false
        _loadProgress.value = ""
    }

    class Factory(
        private val client: ZhengfangClient,
        private val schoolRegistry: SchoolRegistry,
        private val appContainer: AppContainer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CourseSelectViewModel(client, schoolRegistry, appContainer) as T
    }
}
