package com.pocket.xiaoli.ui.treasurebox

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pocket.xiaoli.data.School
import com.pocket.xiaoli.data.SchoolRegistry
import com.pocket.xiaoli.data.academic.AcademicSnapshotCodec
import com.pocket.xiaoli.di.AppContainer
import com.pocket.xiaoli.network.AcademicCourseItem
import com.pocket.xiaoli.network.AcademicSummary
import com.pocket.xiaoli.network.AcademicTypeInfo
import com.pocket.xiaoli.network.ZhengfangAcademicApi
import com.pocket.xiaoli.network.ZhengfangClient
import com.pocket.xiaoli.network.isAcademicTypeId
import com.pocket.xiaoli.ui.treasurebox.withGateCounts
import com.pocket.xiaoli.ui.zhengfang.ZhengfangLoginViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 修课情况查询（试验阶段）：选学校 → 只登录（不拉课表）→ 拉取学业情况统计与已修课程列表。
 * 登录/选校/验证码公共流程在 ZhengfangLoginViewModel。
 */
class AcademicQueryViewModel(
    client: ZhengfangClient,
    schoolRegistry: SchoolRegistry,
    appContainer: AppContainer
) : ZhengfangLoginViewModel(client, schoolRegistry, appContainer) {

    private val api = ZhengfangAcademicApi(client)

    // ── 学业数据 ──
    private val _summary = MutableStateFlow<AcademicSummary?>(null)
    val summary: StateFlow<AcademicSummary?> = _summary

    private val _types = MutableStateFlow<List<AcademicTypeInfo>>(emptyList())
    val types: StateFlow<List<AcademicTypeInfo>> = _types

    private val _courses = MutableStateFlow<List<AcademicCourseItem>>(emptyList())
    val courses: StateFlow<List<AcademicCourseItem>> = _courses

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    /**
     * 解析自检（只在解析数量明显不对时才有值）。
     * 显示在页面上是刻意的：解析出问题时，用户能截一张图就把它发过来，
     * 而不是被要求"连电脑看 logcat"（2026-10 连着两版解析事故都是卡在这一步）。
     */
    private val _parseDiag = MutableStateFlow("")
    val parseDiag: StateFlow<String> = _parseDiag

    /**
     * 解析结果**真的**可疑吗（决定要不要弹那行红字）。
     *
     * ⚠️ 不能"只要自检串非空就报异常"：一两个重名是**正常**的（培养方案里真有同名节点），
     * 每次都弹红字就是狼来了 —— v0.1.15 实测过。
     */
    private val _diagSuspicious = MutableStateFlow(false)
    val diagSuspicious: StateFlow<Boolean> = _diagSuspicious

    /** 一键复制用的取证文本（原始响应里关键位置的原文） */
    private val _probe = MutableStateFlow("")
    val probe: StateFlow<String> = _probe

    /**
     * 汇总环用的学分进度。
     *
     * ⚠️ **不能用 `types` 求和**：这个页面是两级树（平台/模块 → 子模块），
     * 父节点的值约等于子节点之和，全加起来就是双倍甚至三倍（用户会看到"已获 400/要求 700"）。
     * 有根节点（「主修」）就用它那两档 —— 那才是学校给的总要求/总已修。
     */
    private val _totalProgress = MutableStateFlow<CreditProgress?>(null)
    val totalProgress: StateFlow<CreditProgress?> = _totalProgress

    /** 登录成功 → 拉取学业情况统计 + 各类型课程明细 */
    override suspend fun afterLogin(school: School) = loadData(school.baseUrl)

    override fun onBackToSchoolList() = clearResults()
    override fun onLogout() = clearResults()
    override fun onLoginStart() { _loaded.value = false }

    private fun clearResults() {
        _summary.value = null
        _types.value = emptyList()
        _courses.value = emptyList()
        _loaded.value = false
        _parseDiag.value = ""
        _diagSuspicious.value = false
        _probe.value = ""
        _totalProgress.value = null
    }

    private fun loadData(baseUrl: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _progress.value = "正在获取学业情况……"
            try {
                val index = api.fetchIndex(baseUrl)
                if (index.summary.studentId.isBlank() && index.types.isEmpty()) {
                    _error.value = "未获取到学业情况数据，可能是教务页面结构变更"
                    _isLoading.value = false
                    _progress.value = ""
                    return@launch
                }
                _summary.value = index.summary
                _types.value = index.types
                _probe.value = index.probe
                _parseDiag.value = index.parseDiag.summary()
                // 两级树：总进度取根节点（「主修」），没有根节点才退回按类型求和
                _totalProgress.value = index.rootType?.let { CreditProgress.of(listOf(it)) }
                    ?: CreditProgress.of(index.types)

                val all = mutableListOf<AcademicCourseItem>()
                // 类型 id 应为 ASCII 字母/数字（烟理是 32 位 GUID）。形状不对 ⇒ 解析降级了，
                // 宁可少一组明细，也不要把 `xfyqjd_id=0.0` 这种值发出去：
                // 接口对认不出的 id 可能照旧返回**别的**类型的课程，界面上看起来正常，
                // 实际是"每个类型下都挂着同一批课"。
                val wanted = index.types.filter { isAcademicTypeId(it.id) }
                index.types.filterNot { isAcademicTypeId(it.id) }.forEach {
                    android.util.Log.w("AcademicApi", "skip type=${it.name} bad id=${it.id}")
                }
                // ⚠️ 一组一组**串行**取，页面就有 24 个类型 = 24 次串行往返（学校自己的 JS 更狠：
                // `ajaxSetup({async:false})` 全同步）。按 4 个一批并发：总时长大致变成 1/4，
                // 又不至于把教务系统当压测对象打。
                wanted.chunked(DETAIL_CONCURRENCY).forEach { chunk ->
                    _progress.value = if (wanted.size <= 1) {
                        "正在获取「${chunk.first().name}」课程……"
                    } else {
                        "正在获取课程明细 (${all.size} 门已入库 / ${wanted.size} 个类型)……"
                    }
                    val part = coroutineScope {
                        chunk.map { type ->
                            async { api.fetchCourses(baseUrl, type.id, type.name, index.formFields) }
                        }.awaitAll()
                    }
                    part.forEach { all.addAll(it) }
                }
                if (wanted.isNotEmpty()) {
                    _progress.value = "正在获取课程明细 (${all.size} 门已入库 / ${wanted.size} 个类型)……"
                }
                _courses.value = all
                // 「共 N 门 / 通过 N 门」用刚取回的明细行**自己数**：源 HTML 里这两处是占位
                // （`<font name='zms'>0</font>` / `<font name='tgms'>0</font>`），
                // 学校页面上也是前端 `calcXf()` 数出来的 —— 口径逐字一致，且不需要任何额外请求。
                // 见 `withGateCounts` 的 KDoc（那里有学校那段 JS 的原文）。
                val enriched = index.types.map { t ->
                    t.withGateCounts(all.filter { c -> c.typeId == t.id })
                }
                _types.value = enriched
                _loaded.value = true
                // 可疑时**不写**快照（见 saveSnapshot 的说明）：残缺数据会覆盖上一次好数据
                saveSnapshot(enriched, all, index.parseDiag.suspicious)
                // 自检串要带上"明细取回了几组"：这一条数字能直接分开两种完全不同的故障
                // ——"类型没解析出来"（类型数不对）与"类型解析对了但明细接口没给数据"（组数不对）。
                // 少了它，用户在屏幕上看到的都是"这一页没内容"。
                val groupsWithCourses = index.types.count { t -> all.any { it.typeId == t.id } }
                _parseDiag.value = index.parseDiag.summary() +
                    " · 明细 $groupsWithCourses/${index.types.size} 组有课程" +
                    // 兜底接口的命中数：为 0 说明"查不到"的那些类型是真的没有数据，
                    // 不为 0 说明它们一直挂在另一个接口上（用户报的「有的课程明细怎么查不到」）
                    "（兜底 ${api.nonCourseFallbackGroups()} 组）"
                _diagSuspicious.value = index.parseDiag.suspicious
                // 复制诊断用的文本：页面取证（可疑时才有）+ 明细接口的一条原始记录。
                // 后者是**唯一**能定死「学时 / 补考 / 重修 / 建议修读学年学期」字段名的东西。
                _probe.value = buildString {
                    if (index.probe.isNotBlank()) append(index.probe).append('\n')
                    val sample = api.lastCoursesSample.ifBlank { api.lastEmptyCoursesSample }
                    append("【明细样例】")
                    append(
                        when {
                            sample.isBlank() -> "（本次没取到明细）"
                            api.lastCoursesSample.isBlank() -> "（只有空明细）$sample"
                            else -> sample
                        }
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = "获取学业情况失败：${e.message ?: "未知错误"}"
                _isLoading.value = false
                _progress.value = ""
            } finally {
                _isLoading.value = false
                _progress.value = ""
            }
        }
    }

    /**
     * 查询成功后落一份本地快照，供鲤鱼回答"我还差多少学分 / 哪些课没修"。
     *
     * 三条刻意的规矩：
     * 1. **不额外发任何请求** —— 快照就是这一页刚取到的数据（`ProactiveEvaluator` 的同一条铁律）；
     * 2. **解析明显不对时不写** —— 否则一份残缺快照会覆盖掉上一次好数据，
     *    而用户看到的只是"鲤鱼突然说不清了"。判据用 `parseDiag.suspicious`（同页面那行红字）；
     * 3. **写失败只记日志、不影响页面** —— 快照是锦上添花，不能让它把查询本身搞失败。
     */
    private suspend fun saveSnapshot(
        types: List<AcademicTypeInfo>,
        courses: List<AcademicCourseItem>,
        suspicious: Boolean,
    ) {
        if (suspicious || types.isEmpty()) return
        runCatching {
            appContainer.academicSnapshotDao.upsert(
                AcademicSnapshotCodec.encode(types, courses, System.currentTimeMillis())
            )
        }.onFailure { android.util.Log.w("AcademicQuery", "快照写入失败", it) }
    }

    class Factory(
        private val client: ZhengfangClient,
        private val schoolRegistry: SchoolRegistry,
        private val appContainer: AppContainer
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            AcademicQueryViewModel(client, schoolRegistry, appContainer) as T
    }
}

/**
 * 明细接口的并发批大小。
 *
 * 24 个类型串行取 = 24 次串行往返（学校自己的 JS 更极端：`ajaxSetup({async:false})` 全同步）。
 * 4 是个折中：总时长掉到大约 1/4，又不至于把教务系统当压测对象打 ——
 * **不要**往上调，那是学校的系统，不是我们的。
 */
private const val DETAIL_CONCURRENCY = 4
