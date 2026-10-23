package com.pocket.xiaoli.ui.treasurebox

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pocket.xiaoli.data.School
import com.pocket.xiaoli.data.SchoolRegistry
import com.pocket.xiaoli.data.entity.SavedGradeEntity
import com.pocket.xiaoli.di.AppContainer
import com.pocket.xiaoli.network.ZhengfangClient
import com.pocket.xiaoli.network.ZhengfangGradeApi
import com.pocket.xiaoli.network.ZfGradeItem
import com.pocket.xiaoli.network.ZfTerm
import com.pocket.xiaoli.ui.zhengfang.ZhengfangLoginViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 教务成绩查询（试验阶段）：选学校 → 只登录（不拉课表）→ 选学期 → 查成绩。
 * 登录/选校/验证码公共流程在 ZhengfangLoginViewModel。
 */
class GradeQueryViewModel(
    client: ZhengfangClient,
    schoolRegistry: SchoolRegistry,
    appContainer: AppContainer
) : ZhengfangLoginViewModel(client, schoolRegistry, appContainer) {

    private val api = ZhengfangGradeApi(client)
    private val savedGradeDao = appContainer.savedGradeDao

    // ── 已保存成绩 key（课程|学期|总评），用于成绩卡片标记 ──
    val savedKeys: StateFlow<Set<String>> = savedGradeDao.getAll()
        .map { list ->
            list.map { "${it.courseName}|${it.termLabel}|${it.totalScore}" }.toSet()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // ── 学期与成绩 ──
    private val _terms = MutableStateFlow<List<ZfTerm>>(emptyList())
    val terms: StateFlow<List<ZfTerm>> = _terms

    private val _selectedTerm = MutableStateFlow<ZfTerm?>(null)
    val selectedTerm: StateFlow<ZfTerm?> = _selectedTerm

    private val _grades = MutableStateFlow<List<ZfGradeItem>>(emptyList())
    val grades: StateFlow<List<ZfGradeItem>> = _grades

    private val _gradesLoaded = MutableStateFlow(false)
    val gradesLoaded: StateFlow<Boolean> = _gradesLoaded

    /** 登录成功 → 拉学期列表并查第一个学期的成绩 */
    override suspend fun afterLogin(school: School) = loadTerms(school.baseUrl)

    override fun onBackToSchoolList() = clearResults()
    override fun onLogout() = clearResults()
    override fun onLoginStart() { _gradesLoaded.value = false }

    private fun clearResults() {
        _terms.value = emptyList()
        _selectedTerm.value = null
        _grades.value = emptyList()
        _gradesLoaded.value = false
    }

    private fun loadTerms(baseUrl: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _progress.value = "正在获取学期列表……"
            try {
                val terms = api.fetchTerms(baseUrl)
                if (terms.isEmpty()) {
                    _error.value = "未获取到学期列表，可能是教务页面结构变更"
                } else {
                    _terms.value = terms
                    val first = terms.first()
                    _selectedTerm.value = first
                    loadGrades(baseUrl, first)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = "获取学期列表失败：${e.message ?: "未知错误"}"
                _isLoading.value = false
                _progress.value = ""
            }
        }
    }

    fun selectTerm(term: ZfTerm) {
        if (_selectedTerm.value == term) return
        _selectedTerm.value = term
        val base = _selectedSchool.value?.baseUrl ?: return
        loadGrades(base, term)
    }

    /** 重新查询当前学期（选择同一学期不会触发，这里强制刷新）。 */
    fun refresh() {
        val term = _selectedTerm.value ?: return
        val base = _selectedSchool.value?.baseUrl ?: return
        loadGrades(base, term)
    }

    /**
     * 失败后的重试入口 —— 按"失败在哪一步"分派。
     *
     * 为什么需要它，而不是让界面直接调 [refresh]：**学期列表没拿到时
     * [refresh] 会直接 return**（它要求 `_selectedTerm` 非空），
     * 于是那颗「重试」按钮点了毫无反应 —— 而学期加载失败恰恰是最常见的失败
     * （教务改版、会话失效）。照着错误卡片放一颗按不动的按钮，比不给按钮更糟。
     *
     * 三种情形：
     * 1. 学期列表已就绪，只是成绩查询失败 → 重查当前学期（**不必重新登录**）
     * 2. 学期列表为空 → 整条流程重来：`startLogin()` 会重新登录并触发 [afterLogin]
     * 3. 连凭据都不在了（未绑定且没手输过）→ 退回选校页，让用户重新填
     */
    fun retry() {
        if (_isLoading.value) return
        _error.value = null

        val term = _selectedTerm.value
        val base = _selectedSchool.value?.baseUrl
        if (term != null && base != null) {
            loadGrades(base, term)
            return
        }

        // 重新登录：`autoLoginIfBound()` 已把绑定凭据写进 _username/_password，
        // 所以这里复用基类的登录流程是安全的（它自己会处理验证码弹窗与失败回退）
        if (_username.value.isNotBlank() && _password.value.isNotBlank()) {
            startLogin()
        } else {
            backToSchoolList()
        }
    }

    private fun loadGrades(baseUrl: String, term: ZfTerm) {
        viewModelScope.launch {
            _isLoading.value = true
            _progress.value = "正在查询 ${term.label} 成绩……"
            try {
                val grades = api.fetchGrades(baseUrl, term.xnm, term.xqm)
                _grades.value = grades
                _gradesLoaded.value = true
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = "查询成绩失败：${e.message ?: "未知错误"}"
            } finally {
                _isLoading.value = false
                _progress.value = ""
            }
        }
    }

    // ── 保存成绩到本地（已修课程/成绩存档）──

    /** 保存单门成绩（已存在则跳过） */
    fun saveGrade(item: ZfGradeItem) {
        val school = _selectedSchool.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            item.toSavedGrade(school.name)?.let { savedGradeDao.insert(it) }
        }
    }

    /** 保存当前学期查询结果的全部成绩 */
    fun saveAllGrades() {
        val school = _selectedSchool.value ?: return
        val current = _grades.value
        if (current.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val items = current.mapNotNull { it.toSavedGrade(school.name) }
            if (items.isNotEmpty()) savedGradeDao.insertAll(items)
        }
    }

    class Factory(
        private val client: ZhengfangClient,
        private val schoolRegistry: SchoolRegistry,
        private val appContainer: AppContainer
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            GradeQueryViewModel(client, schoolRegistry, appContainer) as T
    }
}

/** 成绩记录 → 本地存档实体 */
internal fun ZfGradeItem.toSavedGrade(schoolName: String): SavedGradeEntity? {
    val name = courseName.trim()
    if (name.isBlank()) return null
    return SavedGradeEntity(
        courseName = name,
        totalScore = totalScore,
        credits = credits,
        gpa = gpa,
        regular = regular,
        final = final,
        midterm = midterm,
        examType = examType,
        termLabel = termLabel,
        schoolName = schoolName
    )
}
