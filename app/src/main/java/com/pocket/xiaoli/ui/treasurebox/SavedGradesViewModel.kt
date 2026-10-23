package com.pocket.xiaoli.ui.treasurebox

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pocket.xiaoli.data.School
import com.pocket.xiaoli.data.SchoolRegistry
import com.pocket.xiaoli.data.entity.SavedGradeEntity
import com.pocket.xiaoli.di.AppContainer
import com.pocket.xiaoli.network.ZhengfangClient
import com.pocket.xiaoli.network.ZhengfangGradeApi
import com.pocket.xiaoli.ui.zhengfang.ZhengfangLoginViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 已修课程（成绩存档）：本地展示已保存的课程成绩；可登录教务一键拉取全部学期成绩并保存。
 * 登录/选校/验证码公共流程在 ZhengfangLoginViewModel。
 */
class SavedGradesViewModel(
    client: ZhengfangClient,
    schoolRegistry: SchoolRegistry,
    appContainer: AppContainer
) : ZhengfangLoginViewModel(client, schoolRegistry, appContainer) {

    private val api = ZhengfangGradeApi(client)
    private val dao = appContainer.savedGradeDao

    // ── 本地已保存成绩（离线可看）──
    val savedGrades: StateFlow<List<SavedGradeEntity>> =
        dao.getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── 同步流程状态 ──
    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing
    private val _syncResult = MutableStateFlow<String?>(null)
    val syncResult: StateFlow<String?> = _syncResult

    /** 从同步流程返回本地列表 */
    override fun backToSchoolList() {
        _syncing.value = false
        super.backToSchoolList()
    }

    /** 开始同步流程（进入选学校/登录界面） */
    fun startSync() { _syncing.value = true }

    override fun onLoginStart() { _syncResult.value = null }

    /** 登录成功 → 遍历全部学期拉取成绩并保存（单学期失败不中断；会话失效则中断） */
    override suspend fun afterLogin(school: School) = syncAll(school)

    private suspend fun syncAll(school: School) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val baseUrl = school.baseUrl.trimEnd('/')
                _progress.value = "正在获取学期列表……"
                val terms = api.fetchTerms(baseUrl)
                if (terms.isEmpty()) {
                    _error.value = "未获取到学期列表，可能是教务页面结构变更"
                    _isLoading.value = false
                    _progress.value = ""
                    return@launch
                }
                val all = mutableListOf<SavedGradeEntity>()
                terms.forEachIndexed { index, term ->
                    _progress.value = "正在获取 ${term.label} 成绩 (${index + 1}/${terms.size})……"
                    val grades = try {
                        api.fetchGrades(baseUrl, term.xnm, term.xqm)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // 会话失效需要中断，其余错误跳过该学期继续
                        if (e.message?.contains("会话已失效") == true) throw e
                        emptyList()
                    }
                    all.addAll(grades.mapNotNull { it.toSavedGrade(school.name) })
                }
                val insertedCount = if (all.isEmpty()) 0
                    else dao.insertAll(all).count { it > 0 }
                _syncResult.value = if (insertedCount > 0) "已新增保存 $insertedCount 门课程成绩"
                    else if (all.isEmpty()) "未获取到成绩数据，可能本学期未选课"
                    else "没有新的课程成绩（已全部保存过）"
                // 同步完成，回到本地列表
                _syncing.value = false
                _selectedSchool.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = "同步失败：${e.message ?: "未知错误"}"
            } finally {
                _isLoading.value = false
                _progress.value = ""
            }
        }
    }

    fun deleteGrade(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { dao.deleteById(id) }
    }

    fun clearAll() {
        viewModelScope.launch(Dispatchers.IO) { dao.deleteAll() }
    }

    fun consumeSyncResult() { _syncResult.value = null }

    class Factory(
        private val client: ZhengfangClient,
        private val schoolRegistry: SchoolRegistry,
        private val appContainer: AppContainer
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            SavedGradesViewModel(client, schoolRegistry, appContainer) as T
    }
}
