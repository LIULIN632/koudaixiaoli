package com.pocket.xiaoli.di

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.pocket.xiaoli.PocketXiaoliApplication
import com.pocket.xiaoli.data.JwSystemType
import com.pocket.xiaoli.data.School
import com.pocket.xiaoli.data.CampusConfig
import com.pocket.xiaoli.data.CampusInfo
import com.pocket.xiaoli.data.SchoolRegistry
import com.pocket.xiaoli.data.learning.LearningPlatform
import com.pocket.xiaoli.data.qzxy.QzxyDeviceStore
import com.pocket.xiaoli.data.qzxy.QzxyRepository
import com.pocket.xiaoli.data.qzxy.QzxySessionStore
import com.pocket.xiaoli.data.qzxy.QzxyValveStore
import com.pocket.xiaoli.data.map.CampusPoi
import com.pocket.xiaoli.data.map.BuiltinPois
import com.pocket.xiaoli.data.map.CampusMapRepository
import com.pocket.xiaoli.data.map.CampusLocationStore
import com.pocket.xiaoli.data.promo.PromoRepository
import com.pocket.xiaoli.network.AppUpdateChecker
import com.pocket.xiaoli.data.proactive.ProactiveStore
import com.pocket.xiaoli.ui.theme.BottomBarStyleStore
import com.pocket.xiaoli.data.service.CasLogin
import com.pocket.xiaoli.data.service.ServiceAutoLoginStore
import com.pocket.xiaoli.ui.theme.TabAppearanceStore
import com.pocket.xiaoli.data.MockScenario
import com.pocket.xiaoli.data.FeatureFlag
import com.pocket.xiaoli.ui.home.HomeLayout
import com.pocket.xiaoli.ui.theme.CourseColorMode
import com.pocket.xiaoli.ui.theme.CustomAccent
import com.pocket.xiaoli.ui.home.HomeModule
import com.pocket.xiaoli.data.map.BuiltinRoomCells
import com.pocket.xiaoli.data.map.RoomCell
import com.pocket.xiaoli.data.map.RoomDirectorySearch
import com.pocket.xiaoli.data.map.RoomDirectorySearch.RoomMergeDirection
import com.pocket.xiaoli.data.calendar.CalendarRepository
import com.pocket.xiaoli.data.holiday.HolidayDay
import com.pocket.xiaoli.data.holiday.HolidayRepository
import com.pocket.xiaoli.data.model.DormInfo
import com.pocket.xiaoli.data.model.LastImportInfo
import com.pocket.xiaoli.data.model.NoteItem
import com.pocket.xiaoli.data.knowledge.KnowledgeStore
import com.pocket.xiaoli.data.library.LibraryRepository
import com.pocket.xiaoli.data.quickanswer.QuickAnswerRepository
import com.pocket.xiaoli.data.quickanswer.QuickAnswerStore
import com.pocket.xiaoli.data.knowledge.KnowledgeUpdater
import com.pocket.xiaoli.appwidget.ClassWidgetProvider
import com.pocket.xiaoli.focus.FocusController
import com.pocket.xiaoli.focus.FocusNotifier
import com.pocket.xiaoli.focus.FocusStore
import com.pocket.xiaoli.focus.FocusTimerService
import com.pocket.xiaoli.notification.ClassReminderScheduler
import com.pocket.xiaoli.data.dao.AcademicSnapshotDao
import com.pocket.xiaoli.data.dao.SavedGradeDao
import com.pocket.xiaoli.data.dao.SpecialWorkDayDao
import com.pocket.xiaoli.data.repository.CourseRepository
import com.pocket.xiaoli.data.agent.AssistantAgentLoop
import com.pocket.xiaoli.data.agent.AssistantChatRepository
import com.pocket.xiaoli.data.agent.AssistantLlmClient
import com.pocket.xiaoli.data.agent.AssistantLlmConfig
import com.pocket.xiaoli.data.agent.AssistantStreamSource
import com.pocket.xiaoli.data.agent.AI_DEFAULT_NAME
import com.pocket.xiaoli.data.agent.AgentPhase
import com.pocket.xiaoli.data.agent.AssistantToolBindings
import com.pocket.xiaoli.network.CookieStore
import com.pocket.xiaoli.network.NetworkLogStore
import com.pocket.xiaoli.network.QiangZhiClient
import com.pocket.xiaoli.network.SchoolIndexUpdater
import com.pocket.xiaoli.network.ZhengfangClient
import com.pocket.xiaoli.util.currentWeekFrom
import com.pocket.xiaoli.util.parseSemesterStart
import com.google.gson.Gson
import java.time.LocalDate
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import com.pocket.xiaoli.data.entity.SpecialWorkDayEntity
import com.pocket.xiaoli.data.announcement.AppFeedRepository
import com.pocket.xiaoli.data.announcement.AppFeedStore
import com.pocket.xiaoli.data.weather.WeatherRepository
import com.pocket.xiaoli.data.weather.WeatherStore
import com.pocket.xiaoli.notification.ExamReminderScheduler

class AppContainer(context: Context) {
    private val app = context.applicationContext as PocketXiaoliApplication
    val repository: CourseRepository get() = app.repository
    val campusMapRepository by lazy { CampusMapRepository(app, app.database.campusMapCorrectionDao()) }
    val campusLocationStore by lazy { CampusLocationStore(app) }
    val zhengfangClient by lazy { ZhengfangClient() }
    val qiangzhiClient by lazy { QiangZhiClient() }
    val schoolRegistry by lazy { SchoolRegistry(context) }
    val schoolIndexUpdater by lazy { SchoolIndexUpdater(context) }

    /**
     * 更新 + 公告的唯一门面：`announce/feed.json` → 首页抽屉 / 红点 / 「我的」更新卡。
     *
     * 它同时管三件事：有没有新版、有没有话要说、说过的话记没记住。
     * 判定逻辑全在 `network/ReleaseGate`（纯函数、有单测），这里只是持有实例。
     */
    val appFeedStore by lazy { AppFeedStore(app) }

    /**
     * 更新检查器：**全 App 共用一个实例**。
     *
     * 「上次成功的镜像」（省一次 DNS/TLS）和 OkHttp 连接池都挂在实例上；
     * 之前 `ProfileDialogHost` 自己 `remember { AppUpdateChecker() }` 又建了一个，
     * 于是仓库层刚热过的连接，点到「检查更新」时一条也用不上。
     */
    val appUpdateChecker by lazy { AppUpdateChecker() }
    val appFeedRepository by lazy { AppFeedRepository(app, appFeedStore, appUpdateChecker) }

    /**
     * 天气：Open-Meteo（免 Key）+ 30 分钟本地缓存。
     *
     * 全 App 共用一个实例，页面进出时不会各自建 OkHttp 连接池；
     * 缓存也只有一个真源（见 `WeatherRepository` 的 KDoc）。
     */
    val weatherStore by lazy { WeatherStore(app) }
    val weatherRepository by lazy { WeatherRepository(weatherStore) }


    /**
     * 校园知识库（Obsidian 维护的校园事实，构建成 assets + 镜像热更新）。
     *
     * ⚠️ 取内容必须走挂起函数 `knowledgeStore.ensureLoaded()`，**不能读"当前值"**：
     * 冷启动立刻提问时异步加载还没完成，那一刻的空列表会被鲤鱼说成
     * 「知识库暂无内容」，用户以为知识库是空的。
     *
     * 内容不在仓库里：仓库只放 `tools/kb/` 的构建管线（见
     * `docs/superpowers/specs/2026-10-03-obsidian-campus-knowledge-base-design.md`）。
     */
    val knowledgeStore by lazy { KnowledgeStore(context) }

    /** 校园知识库的镜像拉取器。与 [knowledgeStore] 分开，便于将来单独替换/测试。 */
    val knowledgeUpdater by lazy { KnowledgeUpdater(context) }

    /**
     * 资料站：课件 / 历年卷 / 习题答案。
     *
     * 索引 `library/index.json` 与文件都在 Gitee Release 上（免备案、零成本），
     * App 只负责「拉索引 → 按需下载 → 用系统应用打开」。上传由 `tools/upload-library.ps1` 做。
     */
    val libraryRepository by lazy { LibraryRepository(context) }

    /** 快问快答题库：内置一份，联网可被远端覆盖（加题不用发版）。 */
    val quickAnswerRepository by lazy { QuickAnswerRepository(context) }

    /** 「会了 / 还不会」的本地账本，只写 SharedPreferences，不联网。 */
    val quickAnswerStore by lazy { QuickAnswerStore(context) }
    val savedGradeDao: SavedGradeDao by lazy { app.database.savedGradeDao() }
    val specialWorkDayDao: SpecialWorkDayDao by lazy { app.database.specialWorkDayDao() }

    /**
     * 「修课情况」快照（单行表）。
     *
     * 存在的理由：教务那份培养方案只在**打开「修课情况」页并查成功**那一刻存在，
     * 退出就没了 —— 而鲤鱼需要能回答"我还差多少学分 / 哪些课没修"。
     * 所以在那一页查询成功后落一份本地快照（不额外发任何请求，见 `AcademicQueryViewModel`），
     * 鲤鱼只读这份快照。数据不出本机（约束 3：仅本地 Room 存储）。
     */
    val academicSnapshotDao: AcademicSnapshotDao by lazy { app.database.academicSnapshotDao() }

    /**
     * 推广位内容（服务端下发）。
     *
     * 内容放服务端的理由：推广文案原本硬编码在 `TreasureBoxScreen` 里，改一句要发一次版。
     * 合作推广需要"随时上架/下架"，所以文案与图片必须可远程更新。
     * 校验（域名白名单 / 时间窗 / 逐条容错）在 `data/promo/PromoConfig.kt`，有单测覆盖。
     */
    val promoRepository by lazy { PromoRepository(appContext, prefs) }

    /** 底栏样式（悬浮胶囊 ↔ 液态玻璃）。独立维度，与配色主题 / 皮肤风格正交。 */
    val bottomBarStyleStore by lazy { BottomBarStyleStore(appContext) }

    /**
     * 主动提醒的记账本（档位 / 冷却时间戳 / 闲聊去重 / 成绩游标 / 余额快照）。
     *
     * 走独立 prefs 文件而不是主 prefs：这些键都是**可以随时丢掉的记账**
     *（丢了最多重复提醒一次），见 `data/proactive/ProactiveStore.kt`。
     */
    val proactiveStore by lazy { ProactiveStore(context) }
    /** 「服务自动登录」模式（三档，默认只填学号）—— 见 data/service/ServiceAutoLogin.kt */
    val serviceAutoLoginStore by lazy { ServiceAutoLoginStore(appContext) }

    /**
     * 各 tab 独有的外观设置（首页问候语 / 鲤鱼字号 / 服务列数 / 我的密度 …）。
     *
     * 与全局项（配色主题、皮肤风格）分开：全局项改一处全 App 生效，
     * 而这些只影响单个 tab。混在一起用户会误判改动的作用范围。
     */
    val tabAppearanceStore by lazy { TabAppearanceStore(appContext) }

    /**
     * 番茄钟（专注计时）的瞬态真源。
     *
     * 走独立 prefs 文件（`pocket_focus`）而不是主 prefs：快照里存的是一段**还没结束的会话**
     *（截止点、暂停位置、关联待办），混进主 prefs 会让"清设置"之类的操作顺手把它清掉，
     * 用户会觉得计时莫名其妙没了。
     *
     * ⚠️ 必须与 [focusController]、前台服务共用**同一个实例**：
     * 页面与服务各自 `new FocusStore(...)` 的话，两边 StateFlow 会各说各话。
     * 所以装配只在这里发生，其余代码一律走 `FocusController.of(context)`。
     */
    val focusStore by lazy { FocusStore(appContext) }

    /** 番茄钟的唯一入口（起/停/跳过/收尾都在它里面）。见 `focus/FocusController.kt`。 */
    val focusController by lazy {
       FocusController(appContext, focusStore, app.database.focusSessionDao())
    }

    /**
     * 每条待办已完整跑完的番茄数（`tasks.id` → 个数）。首页待办行显示它。
     *
     * **派生而不是在 `tasks` 上再存一个字段**：理由写在 `FocusSessionDao.observeCompletedByTask`
     * 的注释里（同一件事不存两份 + 省一次 Room 迁移）。
     */
    val focusCompletedByTask: Flow<Map<Long, Int>> by lazy {
       app.database.focusSessionDao().observeCompletedByTask()
          .map { rows -> rows.associate { it.taskId to it.count } }
    }

    // ── Agent（助手 LLM）──
    // 注：credPrefs 是 private 的，但 AssistantLlmConfig 需要它来加密存 API Key，
    // 所以在类内部构造，不把 credPrefs 暴露到外部（避免别处绕过封装直接读写凭据）。
    val assistantLlmConfig: AssistantLlmConfig by lazy { AssistantLlmConfig(prefs, credPrefs) }
    private val assistantLlmClient by lazy { AssistantLlmClient() }
    val assistantChatRepository: AssistantChatRepository by lazy {
       AssistantChatRepository(app.database.assistantChatDao())
    }

    /**
     * 构造一次 Agent 循环。
     *
     * 每次对话都新建：它持有本轮的工具注册表与配置快照，
     * 复用实例会让"改了设置要不要生效"变得含糊（缓存了旧 config）。
     *
     * 循环只依赖 [AssistantStreamSource]，不直接认识 [AssistantLlmClient] 与 config ——
     * 这两个都在这条装配语句里绑定，循环本身因此可以脱网单测。
     */
    fun newAssistantAgentLoop(): AssistantAgentLoop =
       AssistantAgentLoop(
          streamSource = AssistantStreamSource { request ->
             assistantLlmClient.stream(assistantLlmConfig, request)
          },
          registry = AssistantToolBindings.build(this),
       )

    // ── 校历：随包资产（assets/calendar/ytlg.json），每学年随版本更新一次 ──
    // 刻意 `by lazy`：冷启动时没人看校历页，别在启动路径上多一次文件 IO。
    val calendarRepository by lazy { CalendarRepository(context) }

    // ── 节假日 / 调休：数据源 holiday-cn，移植自「聚在工大」 ──
    private val holidayRepository by lazy { HolidayRepository(context) }
    private val _holidays = MutableStateFlow(holidayRepository.cachedAll())
    val holidays: StateFlow<List<HolidayDay>> = _holidays
    private val holidayScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 启动预热：先出本地缓存，再异步补齐今年与明年的数据（新年度放假安排通常在年底公布）。
     * 网络失败静默降级为缓存，不影响 UI。
     */
    fun refreshHolidays() {
       val year = LocalDate.now().year
       holidayScope.launch {
           holidayRepository.ensureYear(year)
           holidayRepository.ensureYear(year + 1)
           _holidays.value = holidayRepository.cachedAll()
       }
    }

    // ── 调休补课映射：放假通知只公布补班日期，不公布「补哪天的课」，由用户手动指定 ──
    private val _specialWorkDays = MutableStateFlow<List<SpecialWorkDayEntity>>(emptyList())
    val specialWorkDays: StateFlow<List<SpecialWorkDayEntity>> = _specialWorkDays

    private suspend fun reloadSpecialWorkDays() {
       _specialWorkDays.value = runCatching { specialWorkDayDao.getAll() }.getOrDefault(emptyList())
    }

    init {
       holidayScope.launch { reloadSpecialWorkDays() }
    }

    fun saveSpecialWorkDay(originDate: String, targetDate: String) {
       holidayScope.launch {
           specialWorkDayDao.upsert(SpecialWorkDayEntity(originDate = originDate, targetDate = targetDate))
           reloadSpecialWorkDays()
       }
    }

    fun deleteSpecialWorkDay(originDate: String) {
       holidayScope.launch {
           specialWorkDayDao.delete(originDate)
           reloadSpecialWorkDays()
       }
    }
    private val prefs = context.getSharedPreferences("pocket_xiaoli_prefs", Context.MODE_PRIVATE)
    private val appContext = context.applicationContext

    // ── Favorite schools ──
    private val gson = Gson()
    private val _favoriteSchoolIds = MutableStateFlow(loadFavoriteIds())
    val favoriteSchoolIds: StateFlow<Set<String>> = _favoriteSchoolIds

    private fun loadFavoriteIds(): Set<String> {
       val raw = prefs.getString("favorite_school_ids", null) ?: return emptySet()
       return try {
           gson.fromJson(raw, object : TypeToken<Set<String>>() {}.type) ?: emptySet()
       } catch (_: Exception) { emptySet() }
    }

    fun toggleFavoriteSchool(schoolId: String) {
       val current = _favoriteSchoolIds.value.toMutableSet()
       if (schoolId in current) current.remove(schoolId) else current.add(schoolId)
       _favoriteSchoolIds.value = current
       prefs.edit().putString("favorite_school_ids", gson.toJson(current)).apply()
    }

    // ── Custom user-defined schools ──
    private val _customSchools = MutableStateFlow(loadCustomSchools())
    val customSchools: StateFlow<List<School>> = _customSchools

    private fun loadCustomSchools(): List<School> {
       val raw = prefs.getString("custom_schools", null) ?: return emptyList()
       return try {
           gson.fromJson(raw, object : TypeToken<List<School>>() {}.type) ?: emptyList()
       } catch (_: Exception) { emptyList() }
    }

    fun addCustomSchool(school: School) {
       val current = _customSchools.value.toMutableList()
       current.removeAll { it.baseUrl.equals(school.baseUrl, ignoreCase = true) }
       current.add(0, school)
       _customSchools.value = current
       prefs.edit().putString("custom_schools", gson.toJson(current)).apply()
    }

    /** 更新自定义学校（编辑保存，按 id 定位） */
    fun updateCustomSchool(old: School, new: School) {
       val current = _customSchools.value.toMutableList()
       val idx = current.indexOfFirst { it.id == old.id }
       if (idx >= 0) current[idx] = new
       _customSchools.value = current
       prefs.edit().putString("custom_schools", gson.toJson(current)).apply()
    }

    /** 删除自定义学校 */
    fun removeCustomSchool(school: School) {
       val current = _customSchools.value.toMutableList()
       current.removeAll { it.id == school.id }
       _customSchools.value = current
       prefs.edit().putString("custom_schools", gson.toJson(current)).apply()
    }

    // ── Custom campus POIs (M3) ──
    private val _customPois = MutableStateFlow(loadCustomPois())
    val customPois: StateFlow<List<CampusPoi>> = _customPois

    private fun loadCustomPois(): List<CampusPoi> {
       val raw = prefs.getString("custom_pois", null)
       val existing: List<CampusPoi> = try {
           if (raw == null) emptyList()
           else gson.fromJson(raw, object : TypeToken<List<CampusPoi>>() {}.type) ?: emptyList()
       } catch (_: Exception) {
           emptyList()
       }

       // 内置 POI 播种：仅在首次启动或内置版本升级时合并一次，
       // 之后完全交给用户，被删除的内置项不会被重新写回。
       val seededVersion = prefs.getInt(KEY_POI_SEED_VERSION, 0)
       if (seededVersion >= BuiltinPois.VERSION) return existing

       val knownIds = existing.mapTo(HashSet()) { it.id }
       val merged = existing + BuiltinPois.all.filterNot { it.id in knownIds }
       prefs.edit()
           .putString("custom_pois", gson.toJson(merged))
           .putInt(KEY_POI_SEED_VERSION, BuiltinPois.VERSION)
           .apply()
       return merged
    }

    private fun persistCustomPois(items: List<CampusPoi>) {
       prefs.edit().putString("custom_pois", gson.toJson(items)).apply()
    }

    fun addCustomPoi(poi: CampusPoi) {
       _customPois.value = _customPois.value + poi
       persistCustomPois(_customPois.value)
    }

    fun updateCustomPoi(poi: CampusPoi) {
       _customPois.value = _customPois.value.map { if (it.id == poi.id) poi else it }
       persistCustomPois(_customPois.value)
    }

    fun removeCustomPoi(id: String) {
       _customPois.value = _customPois.value.filterNot { it.id == id }
       persistCustomPois(_customPois.value)
       _favoritePoiIds.value = _favoritePoiIds.value - id
       prefs.edit().putStringSet(KEY_FAVORITE_POI_IDS, _favoritePoiIds.value).apply()
    }

    // ── Favorite POIs（地图收藏）──
    private val _favoritePoiIds = MutableStateFlow(loadFavoritePoiIds())
    val favoritePoiIds: StateFlow<Set<String>> = _favoritePoiIds

    private fun loadFavoritePoiIds(): Set<String> {
       return prefs.getStringSet(KEY_FAVORITE_POI_IDS, null)?.toSet().orEmpty()
    }

    fun togglePoiFavorite(id: String) {
       val next = _favoritePoiIds.value.toMutableSet().apply {
           if (!add(id)) remove(id)
       }
       _favoritePoiIds.value = next
       prefs.edit().putStringSet(KEY_FAVORITE_POI_IDS, next).apply()
    }

    // ── 常用服务（用户可编辑；key = 服务标题）──
    private val _quickServiceKeys = MutableStateFlow(loadQuickServiceKeys())
    val quickServiceKeys: StateFlow<List<String>> = _quickServiceKeys

    private fun loadQuickServiceKeys(): List<String> {
       val raw = prefs.getString("quick_service_keys", null) ?: return DEFAULT_QUICK_SERVICE_KEYS
       return try {
           val parsed: List<String>? = gson.fromJson(raw, object : TypeToken<List<String>>() {}.type)
           parsed?.filter { it.isNotBlank() } ?: DEFAULT_QUICK_SERVICE_KEYS
       } catch (_: Exception) {
           DEFAULT_QUICK_SERVICE_KEYS
       }
    }

    fun setQuickServiceKeys(keys: List<String>) {
       val clean = keys.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(4)
       _quickServiceKeys.value = clean
       prefs.edit().putString("quick_service_keys", gson.toJson(clean)).apply()
    }

    fun resetQuickServiceKeys() {
       _quickServiceKeys.value = DEFAULT_QUICK_SERVICE_KEYS
       prefs.edit().remove("quick_service_keys").apply()
    }

    // ── 最近使用服务已移除 ──
    // 原本记录用户最近点过的 8 个服务并在服务页展示。产品决定去掉该区块后，
    // 记录逻辑（markServiceUsed / recentServiceKeys）也一并删除，避免留下无人读写的死状态。
    // 旧的 "recent_service_keys" prefs 键不再读取，残留数据无害，不做清理。
    // ── 服务隐藏（用户从服务页隐藏的入口，不删除数据）──
    private val _hiddenServiceKeys = MutableStateFlow(loadHiddenServiceKeys())
    val hiddenServiceKeys: StateFlow<List<String>> = _hiddenServiceKeys

    private fun loadHiddenServiceKeys(): List<String> {
       val raw = prefs.getString("hidden_service_keys", null) ?: return emptyList()
       return try {
           val parsed: List<String>? = gson.fromJson(raw, object : TypeToken<List<String>>() {}.type)
           parsed?.filter { it.isNotBlank() }?.distinct() ?: emptyList()
       } catch (_: Exception) {
           emptyList()
       }
    }

    fun toggleServiceHidden(key: String) {
       val cleanKey = key.trim()
       if (cleanKey.isEmpty()) return
       val current = _hiddenServiceKeys.value
       val next = if (cleanKey in current) current - cleanKey else current + cleanKey
       _hiddenServiceKeys.value = next
       prefs.edit().putString("hidden_service_keys", gson.toJson(next)).apply()
    }

    fun resetHiddenServices() {
       _hiddenServiceKeys.value = emptyList()
       prefs.edit().remove("hidden_service_keys").apply()
    }
    // ── 教室分布（只做「看分布」，无路径规划）──
    private val _roomCells = MutableStateFlow(loadRoomCells())
    val roomCells: StateFlow<List<RoomCell>> = _roomCells

    private fun loadRoomCells(): List<RoomCell> {
       val raw = prefs.getString(KEY_ROOM_CELLS, null)
       val parsed: List<RoomCell>? = if (raw == null) {
           emptyList()
       } else {
           try {
               gson.fromJson(raw, object : TypeToken<List<RoomCell>>() {}.type)
           } catch (_: Exception) {
               null
           }
       }
       // 归一化：Gson 用 Unsafe 分配实例，旧 JSON 缺失的新字段不会是 Kotlin 默认值
       val cells = (parsed ?: emptyList()).map { it.normalized() }
       // 内置教室播种（首次启动或内置版本升级时合并一次）—— 必须在 schema 升级之前，
       // 否则升级写回时会把刚播种的内容落下
       val seeded = seedBuiltinRoomCells(cells)
       upgradeRoomCellsSchemaIfNeeded(seeded)
       return seeded
    }

    /**
     * 内置教室播种 —— 与 BuiltinPois 同一策略：
     * **只在首次启动或内置版本升级时合并一次**，用户在应用内删掉的房间不会复活。
     */
    private fun seedBuiltinRoomCells(existing: List<RoomCell>): List<RoomCell> {
       val seededVersion = prefs.getInt(KEY_ROOM_CELL_SEED_VERSION, 0)
       if (seededVersion >= BuiltinRoomCells.VERSION) return existing
       val builtin = BuiltinRoomCells.all
       val knownIds = existing.mapTo(mutableSetOf()) { it.id }
       val merged = existing + builtin.filterNot { it.id in knownIds }
       prefs.edit()
           .putString(KEY_ROOM_CELLS, gson.toJson(merged))
           .putInt(KEY_ROOM_CELLS_SCHEMA, ROOM_CELLS_SCHEMA)
           .putInt(KEY_ROOM_CELL_SEED_VERSION, BuiltinRoomCells.VERSION)
           .apply()
       return merged
    }

    /**
     * 教室分布图 JSON 的一次性 schema 升级。
     *
     * 新增字段都带默认值，Gson 读旧 JSON 时已自动填好，这里只负责把版本号落盘，
     * 让后续版本能知道自己读到的是哪一代数据。
     */
    private fun upgradeRoomCellsSchemaIfNeeded(cells: List<RoomCell>) {
       val stored = prefs.getInt(KEY_ROOM_CELLS_SCHEMA, 1)
       if (stored >= ROOM_CELLS_SCHEMA) return
       if (cells.isEmpty()) {
           prefs.edit().putInt(KEY_ROOM_CELLS_SCHEMA, ROOM_CELLS_SCHEMA).apply()
       } else {
           persistRoomCells(cells)
       }
    }

    private fun persistRoomCells(items: List<RoomCell>) {
       prefs.edit()
           .putString(KEY_ROOM_CELLS, gson.toJson(items))
           .putInt(KEY_ROOM_CELLS_SCHEMA, ROOM_CELLS_SCHEMA)
           .apply()
    }

    /** 批量新增：按 楼+层+房间号 去重，返回实际新增数量 */
    fun addRoomCells(items: List<RoomCell>): Int {
       val list = _roomCells.value.toMutableList()
       val keys = list.mapTo(mutableSetOf()) { it.building + "/" + it.floor + "/" + it.room }
       var added = 0
       items.forEach { cell ->
           if (keys.add(cell.building + "/" + cell.floor + "/" + cell.room)) {
               list += cell
               added++
           }
       }
       if (added > 0) {
           _roomCells.value = list
           persistRoomCells(list)
       }
       return added
    }

    fun updateRoomCell(cell: RoomCell) {
       val next = _roomCells.value.map { if (it.id == cell.id) cell else it }
       _roomCells.value = next
       persistRoomCells(next)
    }

    fun removeRoomCell(id: String) {
       val next = _roomCells.value.filterNot { it.id == id }
       _roomCells.value = next
       persistRoomCells(next)
    }

    fun moveRoomCell(id: String, row: Int, col: Int) {
       val next = RoomDirectorySearch.moveCell(_roomCells.value, id, row, col)
       if (next != _roomCells.value) {
           _roomCells.value = next
           persistRoomCells(next)
       }
    }

    fun splitRoomCell(id: String) {
       val next = RoomDirectorySearch.splitCell(_roomCells.value, id)
       if (next != _roomCells.value) {
           _roomCells.value = next
           persistRoomCells(next)
       }
    }

    fun mergeRoomCell(id: String, direction: RoomMergeDirection) {
       val next = RoomDirectorySearch.mergeAdjacent(_roomCells.value, id, direction)
       if (next != _roomCells.value) {
           _roomCells.value = next
           persistRoomCells(next)
       }
    }

    /** 清空某一层（重新录入时用） */
    fun clearRoomFloor(campusId: String, building: String, floor: String) {
       val next = _roomCells.value.filterNot {
           it.campusId == campusId && it.building == building && it.floor == floor
       }
       _roomCells.value = next
       persistRoomCells(next)
    }

    // ── Recent import ──
    private val _lastImport = MutableStateFlow(loadLastImport())
    val lastImport: StateFlow<LastImportInfo?> = _lastImport

    private val _importHistory = MutableStateFlow(loadImportHistory())
    val importHistory: StateFlow<List<LastImportInfo>> = _importHistory

    // ⚠️ 两处读取都要过 `sanitized()`：这两个类在 prefs 里是 JSON，
    // 而 Gson 的反射赋值绕过构造器 —— 声明成 String 的字段可能真的是 null
    //（`LastImportInfo` 曾有三个裸字段，那段时间存下的记录里 `source` 就是 null，
    // 会在绑定信息页上直接显示成 `"null"`）。见 `LastImportInfo.sanitized()`。
    private fun loadLastImport(): LastImportInfo? = try {
       prefs.getString("last_import_info", null)?.let {
           gson.fromJson(it, LastImportInfo::class.java)?.sanitized()
       }
    } catch (_: Exception) {
       null
    }

    private fun loadImportHistory(): List<LastImportInfo> {
       val raw = prefs.getString("import_history", null) ?: return emptyList()
       return try {
           // 显式标注类型：`fromJson(String, Type)` 的返回是平台类型，
           // 直接链式调用会让编译器无法推断 T（报 "Cannot infer type for type parameter"）
           val parsed: List<LastImportInfo>? =
               gson.fromJson(raw, object : TypeToken<List<LastImportInfo>>() {}.type)
           // mapNotNull：JSON 数组里若混进 null 元素，不能让它把整段历史带崩
           parsed?.mapNotNull { it?.sanitized() } ?: emptyList()
       } catch (_: Exception) {
           emptyList()
       }
    }

    fun recordImport(source: String, courseCount: Int, success: Boolean, detail: String? = null) {
       val info = LastImportInfo(
           source = source,
           courseCount = courseCount,
           success = success,
           detail = detail
       )
       _lastImport.value = info
       val history = (listOf(info) + _importHistory.value).take(20)
       _importHistory.value = history
       prefs.edit()
           .putString("last_import_info", gson.toJson(info))
           .putString("import_history", gson.toJson(history))
           .apply()
       ClassReminderScheduler.rescheduleAsync(appContext)
       ClassWidgetProvider.refresh(appContext)
    }

    fun clearLastImport() {
       _lastImport.value = null
       prefs.edit().remove("last_import_info").apply()
    }

    fun clearImportHistory() {
       _importHistory.value = emptyList()
       prefs.edit().remove("import_history").apply()
    }


    // ── Credential memory (EncryptedSharedPreferences) ──
    private val credPrefs: SharedPreferences by lazy {
       val ctx = context.applicationContext
       val masterKey = MasterKey.Builder(ctx)
           .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
           .build()
       val encrypted = try {
           EncryptedSharedPreferences.create(
               ctx,
               "edu_credentials_encrypted",
               masterKey,
               EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
               EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
           )
       } catch (e: Exception) {
           Log.w("AppContainer", "EncryptedSharedPreferences unavailable, falling back to plain", e)
           ctx.getSharedPreferences("edu_credentials_encrypted", Context.MODE_PRIVATE)
       }
       migrateOldCredentials(ctx, encrypted)
       encrypted
    }

    private fun migrateOldCredentials(ctx: Context, target: SharedPreferences) {
       val marker = ctx.getSharedPreferences("_migration_marker", Context.MODE_PRIVATE)
       if (marker.getBoolean("creds_migrated", false)) return

       val old = ctx.getSharedPreferences("pocket_xiaoli_prefs", Context.MODE_PRIVATE)
       val keysToRemove = mutableListOf<String>()
       old.all.forEach { (key, value) ->
           if (key.startsWith("cred_user_") || key.startsWith("cred_pass_")) {
               target.edit().putString(key, value.toString()).apply()
               keysToRemove.add(key)
           }
       }
       if (keysToRemove.isNotEmpty()) {
           val edit = old.edit()
           keysToRemove.forEach { edit.remove(it) }
           edit.apply()
       }
       marker.edit().putBoolean("creds_migrated", true).apply()
    }

    fun saveCredential(baseUrl: String, username: String, password: String) {
       val key = baseUrl.trimEnd('/')
       credPrefs.edit()
           .putString("cred_user_$key", username)
           .putString("cred_pass_$key", password)
           .apply()
    }

    fun loadCredential(baseUrl: String): Pair<String, String>? {
       val key = baseUrl.trimEnd('/')
       val user = credPrefs.getString("cred_user_$key", null) ?: return null
       val pass = credPrefs.getString("cred_pass_$key", null) ?: return null
       return Pair(user, pass)
    }

    fun deleteCredential(baseUrl: String) {
       val key = baseUrl.trimEnd('/')
       credPrefs.edit()
           .remove("cred_user_$key")
           .remove("cred_pass_$key")
           .apply()
    }
    // ── Learning platform accounts ──
    fun saveLearningAccount(platform: LearningPlatform, account: String, password: String) {
       saveCredential("learning_${platform.key}", account, password)
    }

    fun loadLearningAccount(platform: LearningPlatform): Pair<String, String>? =
       loadCredential("learning_${platform.key}")

    fun deleteLearningAccount(platform: LearningPlatform) {
       deleteCredential("learning_${platform.key}")
    }

    // ── Learning platform cookie ──
    fun saveLearningCookie(platform: LearningPlatform, cookie: String) {
       credPrefs.edit().putString("learning_cookie_${platform.key}", cookie).apply()
    }

    fun loadLearningCookie(platform: LearningPlatform): String? =
       credPrefs.getString("learning_cookie_${platform.key}", null)

    fun hasLearningCookie(platform: LearningPlatform): Boolean =
       !loadLearningCookie(platform).isNullOrBlank()

    fun deleteLearningCookie(platform: LearningPlatform) {
       credPrefs.edit().remove("learning_cookie_${platform.key}").apply()
    }

    // ── 趣智校园（洗澡 / 热水器）──
    //
    // 与教务凭据分文件存（QzxySessionStore 自带一份 EncryptedSharedPreferences）：
    // 退出趣智校园账号只清它自己，不会碰到教务凭据与学习平台 cookie。
    val qzxySessionStore: QzxySessionStore by lazy { QzxySessionStore(context) }
    val qzxyValveStore: QzxyValveStore by lazy { QzxyValveStore(context) }
    val qzxyDeviceStore: QzxyDeviceStore by lazy { QzxyDeviceStore(context) }
    val qzxyRepository: QzxyRepository by lazy {
       QzxyRepository(qzxySessionStore, qzxyValveStore, qzxyDeviceStore)
    }

    // ── 今日校园（智慧校园统一认证）的账号 ──
    // 与教务分开存：两个系统的密码经常只改一边，共用一个字段会互相弄坏。

    fun loadCasCredential(): Pair<String, String>? = loadCredential(CasLogin.CREDENTIAL_KEY)

    fun saveCasCredential(studentId: String, password: String) =
       saveCredential(CasLogin.CREDENTIAL_KEY, studentId, password)

    fun deleteCasCredential() = deleteCredential(CasLogin.CREDENTIAL_KEY)

    // ── 校园卡（易校园）的账号 ──
    // 易校园用自己的账号体系（手机号 + 密码），与学号那两套都不同，所以单独存。

    fun loadCardCredential(): Pair<String, String>? =
       loadCredential(com.pocket.xiaoli.data.card.YxyCardRepository.CREDENTIAL_KEY)

    fun saveCardCredential(phone: String, password: String) =
       saveCredential(com.pocket.xiaoli.data.card.YxyCardRepository.CREDENTIAL_KEY, phone, password)

    fun deleteCardCredential() =
       deleteCredential(com.pocket.xiaoli.data.card.YxyCardRepository.CREDENTIAL_KEY)

    // ── User profile ──
    //
    // 原 `nickname` 流与 `setNickname()` 已删除（2026-10，随身份卡改版）：
    // 「昵称」这个概念的三个落点是 身份卡显示、绑定信息页标题、改名弹窗 ——
    // 前两个改成不显示/固定标题后，它只剩一个用户永远改不了的值，于是整个删掉。
    // 残留的 `nickname` 偏好键**不做清理**：删掉读它的代码就够了，动老用户的存档没必要。

    /**
     * 本机「用户编号」——**这个 App 的使用者**的编号，不是学号。
     *
     * 需求方要求个人信息卡上展示它（"按使用这个软件的用户来"），同时说明
     * **真正的用户体系等接入服务器后再做**。所以现在：首次运行生成一串稳定的本地编号，
     * 落盘后终身不变（卸载才会换），足够在同一台机器上把"我"认出来；
     * 接服务端时把这里换成服务端下发的编号即可，界面一处都不用改。
     */
    private val _userNo = MutableStateFlow(loadOrCreateUserNo())
    val userNo: StateFlow<String> = _userNo

    private fun loadOrCreateUserNo(): String {
       prefs.getString("user_no", null)?.takeIf { it.isNotBlank() }?.let { return it }
       val generated = "XY-" + buildString {
           repeat(8) { append("ABCDEFGHJKMNPQRSTUVWXYZ23456789".random()) }
       }
       prefs.edit().putString("user_no", generated).apply()
       return generated
    }

    private val _school = MutableStateFlow(prefs.getString("school", "") ?: "")
    val school: StateFlow<String> = _school

    fun setSchool(value: String) {
       _school.value = value; prefs.edit().putString("school", value).apply()
    }

    // ── Campus selection ──
    private val _selectedCampus = MutableStateFlow(
       CampusConfig.byId(prefs.getString("selected_campus_id", CampusConfig.defaultCampus.id))
    )
    val selectedCampus: StateFlow<CampusInfo> = _selectedCampus

    // ── 宿舍绑定（按校区本地保存）──
    private val _dormInfo = MutableStateFlow(loadDormInfo(_selectedCampus.value.id))
    val dormInfo: StateFlow<DormInfo?> = _dormInfo

    private fun dormPrefKey(campusId: String) = "dorm_$campusId"

    private fun loadDormInfo(campusId: String): DormInfo? {
       val raw = prefs.getString(dormPrefKey(campusId), null) ?: return null
       return runCatching { gson.fromJson(raw, DormInfo::class.java) }
           .getOrNull()
           // ⚠️ 必须先 sanitized() 再判 isBound：release 包经过 R8 之后，Gson 未必走得上
           // 「无参构造 + Kotlin 默认值」，老存档里没有的字段会是 null。
           // v1.0.93 的服务页崩溃就是这么来的（详见 DormInfo.sanitized 的说明）。
           ?.sanitized()
           ?.takeIf { it.isBound }
    }

    /** 指定校区的宿舍绑定（绑定页可在未切换校区时读取） */
    fun dormInfoFor(campusId: String): DormInfo? = loadDormInfo(campusId)

    /** 保存当前校区的宿舍绑定；两项都为空视为解绑 */
    fun setDormInfo(
       building: String,
       room: String,
       serviceCampusId: String? = null,
       dormCode: String = "",
    ) = setDormInfoFor(_selectedCampus.value.id, building, room, serviceCampusId, dormCode)

    /** 保存指定校区的宿舍绑定；[dormCode] 是缴费系统用的宿舍编号（见 [DormInfo.dormCode]） */
    fun setDormInfoFor(
       campusId: String,
       building: String,
       room: String,
       serviceCampusId: String? = null,
       dormCode: String = "",
    ) {
       val info = DormInfo(
           building = building.trim(),
           room = room.trim(),
           serviceCampusId = serviceCampusId?.trim()?.takeIf { it.isNotBlank() },
           dormCode = dormCode.trim(),
       )
       val edit = prefs.edit()
       if (info.isBound) edit.putString(dormPrefKey(campusId), gson.toJson(info))
       else edit.remove(dormPrefKey(campusId))
       edit.apply()
       if (campusId == _selectedCampus.value.id) {
           _dormInfo.value = info.takeIf { it.isBound }
       }
    }

    fun setSelectedCampus(id: String) {
       val campus = CampusConfig.byId(id)
       if (campus.underConstruction) return
       _selectedCampus.value = campus
       _dormInfo.value = loadDormInfo(campus.id)
       prefs.edit().putString("selected_campus_id", campus.id).apply()
    }

    // ── Campus-locked school (dedicated client) ──
    /** 当前校区锁定的教务学校；专用客户端只服务本校，不再使用全国学校列表。 */
    fun currentCampusSchool(): School? {
       val campus = selectedCampus.value
       // 1) 按 schoolId 查索引
       campus.schoolId?.let { schoolRegistry.getById(it) }?.let { return it }
       val baseUrl = campus.jwBaseUrl?.trimEnd('/') ?: return null
       if (baseUrl.isBlank()) return null
       // 2) 按教务地址再查一次（id 对不上时仍能锁定本校）
       schoolRegistry.allSchools.firstOrNull {
           it.baseUrl.trimEnd('/').equals(baseUrl, ignoreCase = true)
       }?.let { return it }
       // 3) 最后兜底：用校区配置直接构造，绝不让用户退回全国学校列表
       return School(
           id = campus.schoolId ?: campus.id,
           name = campus.schoolName ?: campus.name,
           city = "",
           jwType = when (campus.jwType?.uppercase()) {
               "QIANGZHI" -> JwSystemType.QIANGZHI
               "QINGGUO" -> JwSystemType.QINGGUO
               "CHAOXING" -> JwSystemType.CHAOXING
               "URP" -> JwSystemType.URP
               else -> JwSystemType.ZHENGFANG
           },
           baseUrl = baseUrl
       )
    }

    // ── Scheme management ──
    private val _activeSchemeId = MutableStateFlow(prefs.getLong("active_scheme_id", 0L))
    val activeSchemeId: StateFlow<Long> = _activeSchemeId
    private val _activeSchemeName = MutableStateFlow(prefs.getString("active_scheme_name", "默认课表") ?: "默认课表")
    val activeSchemeName: StateFlow<String> = _activeSchemeName

    fun setActiveScheme(id: Long, name: String) {
       _activeSchemeId.value = id
       _activeSchemeName.value = name
       prefs.edit().putLong("active_scheme_id", id).putString("active_scheme_name", name).apply()
    }

    // ── Display toggles ──
    private val _showTeacher = MutableStateFlow(prefs.getBoolean("show_teacher", true))
    val showTeacher: StateFlow<Boolean> = _showTeacher
    private val _showRoom = MutableStateFlow(prefs.getBoolean("show_room", true))
    val showRoom: StateFlow<Boolean> = _showRoom
    private val _showCampus = MutableStateFlow(prefs.getBoolean("show_campus", false))
    val showCampus: StateFlow<Boolean> = _showCampus
    private val _showSlotTime = MutableStateFlow(prefs.getBoolean("show_slot_time", false))
    val showSlotTime: StateFlow<Boolean> = _showSlotTime

    fun toggleShowTeacher() { setBool("show_teacher", !_showTeacher.value, _showTeacher) }
    fun toggleShowRoom() { setBool("show_room", !_showRoom.value, _showRoom) }
    fun toggleShowCampus() { setBool("show_campus", !_showCampus.value, _showCampus) }
    fun toggleShowSlotTime() { setBool("show_slot_time", !_showSlotTime.value, _showSlotTime) }

    // 值类型 setter（个性化配置页使用，避免先 toggle 再比较的竞态）
    fun setShowTeacher(value: Boolean) { setBool("show_teacher", value, _showTeacher) }
    fun setShowRoom(value: Boolean) { setBool("show_room", value, _showRoom) }
    fun setShowCampus(value: Boolean) { setBool("show_campus", value, _showCampus) }
    fun setShowSlotTime(value: Boolean) { setBool("show_slot_time", value, _showSlotTime) }

    // ── Theme switching (8 themes) ──

    // ── 界面风格（皮肤）：形状 / 密度 / 表面基调，与配色主题正交 ──
    // minimal = 小圆角 + 紧凑 + 灰底白卡；vivid = 大圆角 + 宽松 + 弥散阴影
    // ⚠️ 默认值是 "vivid"。历史上这里写的是 "classic" —— 该 key 已随枚举改名而废弃。
    //    SkinStyle.fromKey 仍兼容 classic（→ vivid），所以老存档不会失效；
    //    换成本处的字面量只是去掉一个已删除的 key，不改变任何用户的实际观感。
    private val _skinStyle = MutableStateFlow(prefs.getString("skin_style", "vivid") ?: "vivid")
    val skinStyle: StateFlow<String> = _skinStyle
    fun setSkinStyle(value: String) { setStr("skin_style", value, _skinStyle) }

    // ── Appearance mode: rainbow / pure ──
    private val _appearanceMode = MutableStateFlow(prefs.getString("appearance_mode", "rainbow") ?: "rainbow")
    val appearanceMode: StateFlow<String> = _appearanceMode
    fun setAppearanceMode(value: String) { setStr("appearance_mode", value, _appearanceMode) }

    // ── Campus layout mode: modern / toolbox ──
    private val _campusLayoutMode = MutableStateFlow(prefs.getString("campus_layout_mode", "modern") ?: "modern")
    val campusLayoutMode: StateFlow<String> = _campusLayoutMode
    fun setCampusLayoutMode(value: String) { setStr("campus_layout_mode", value, _campusLayoutMode) }

    // ── Card appearance ──
    private val _cornerRadius = MutableStateFlow(prefs.getInt("corner_radius", 4))
    val cornerRadius: StateFlow<Int> = _cornerRadius

    fun setCornerRadius(value: Int) { setInt("corner_radius", value, _cornerRadius) }

    // ── Custom accent color: 0 = default theme ──
    private val _customAccentColor = MutableStateFlow(
       prefs.getInt(KEY_CUSTOM_ACCENT_COLOR, CustomAccent.DEFAULT)
    )
    val customAccentColor: StateFlow<Int> = _customAccentColor

    fun setCustomAccentColor(argb: Int) {
       _customAccentColor.value = argb
       prefs.edit().putInt(KEY_CUSTOM_ACCENT_COLOR, argb).apply()
    }

    fun clearCustomAccentColor() = setCustomAccentColor(CustomAccent.DEFAULT)

    // ── Grid appearance ──
    private val _textCentered = MutableStateFlow(prefs.getBoolean("text_centered", true))
    val textCentered: StateFlow<Boolean> = _textCentered
    private val _gridHeight = MutableStateFlow(prefs.getInt("grid_height", 64))
    val gridHeight: StateFlow<Int> = _gridHeight
    private val _gridOpacity = MutableStateFlow(prefs.getFloat("grid_opacity", 1.0f))
    val gridOpacity: StateFlow<Float> = _gridOpacity
    private val _gridTextSize = MutableStateFlow(prefs.getInt("grid_text_size", 12))
    val gridTextSize: StateFlow<Int> = _gridTextSize

    fun setTextCentered(value: Boolean) { setBool("text_centered", value, _textCentered) }
    fun setGridHeight(value: Int) { setInt("grid_height", value, _gridHeight) }
    fun setGridOpacity(value: Float) {
       _gridOpacity.value = value; prefs.edit().putFloat("grid_opacity", value).apply()
    }
    fun setGridTextSize(value: Int) { setInt("grid_text_size", value, _gridTextSize) }
    fun setShowNonCurrentWeek(value: Boolean) { setBool("show_non_current_week", value, _showNonCurrentWeek) }
    fun setShowOddEven(value: Boolean) { setBool("show_odd_even", value, _showOddEven) }

    private val _verticalLayout = MutableStateFlow(prefs.getBoolean("vertical_layout", true))
    val verticalLayout: StateFlow<Boolean> = _verticalLayout
    fun setVerticalLayout(value: Boolean) { setBool("vertical_layout", value, _verticalLayout) }

    // ── Time slot defaults ──
    private val _defaultClassDuration = MutableStateFlow(prefs.getInt("class_duration", 45))
    val defaultClassDuration: StateFlow<Int> = _defaultClassDuration
    fun setDefaultClassDuration(value: Int) { setInt("class_duration", value, _defaultClassDuration) }

    private val _defaultBreakDuration = MutableStateFlow(prefs.getInt("break_duration", 10))
    val defaultBreakDuration: StateFlow<Int> = _defaultBreakDuration
    fun setDefaultBreakDuration(value: Int) { setInt("break_duration", value, _defaultBreakDuration) }

    // ── Semester / week settings ──
    // 学期已升成一级实体（TermEntity，见 data/entity/TermEntity.kt）。这里保留的是
    // 「当前生效学期的配置镜像」：通知 / 小组件 / 日历都直接读这两个 Flow 与 activeTermId，
    // 切换学期时由 TimetableViewModel 把目标学期的 startDate/totalWeeks 写回来。
    private val _semesterStart = MutableStateFlow(prefs.getString("semester_start", "") ?: "")
    val semesterStart: StateFlow<String> = _semesterStart
    fun setSemesterStart(value: String) {
        setStr("semester_start", value, _semesterStart)
        syncActiveTermSchedule()
    }

    private val _semesterWeeks = MutableStateFlow(prefs.getInt("semester_weeks", 20))
    val semesterWeeks: StateFlow<Int> = _semesterWeeks
    fun setSemesterWeeks(value: Int) {
        setInt("semester_weeks", value, _semesterWeeks)
        syncActiveTermSchedule()
    }

    /**
     * 把镜像回写到当前学期行。
     *
     * 「校历校准」「教务导入带回开学日期」「校历一键校准」三处入口都只写镜像、不碰学期行；
     * 不回写就会出现：学期页显示的开学日期是旧的，切一次学期又跳回去。
     * 学期行由 [TimetableViewModel] 维护，这里只做补写，写失败也只是镜像与行短暂不一致。
     */
    private val termMirrorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private fun syncActiveTermSchedule() {
        val start = _semesterStart.value
        if (start.isBlank()) return
        val termId = _activeTermId.value
        val weeks = _semesterWeeks.value
        termMirrorScope.launch { repository.patchTermSchedule(termId, start, weeks) }
    }

    /** 当前学期：[TermEntity.id]，默认 0（迁移生成的「默认学期」）。 */
    private val _activeTermId = MutableStateFlow(prefs.getLong("active_term_id", 0L))
    val activeTermId: StateFlow<Long> = _activeTermId
    fun setActiveTermId(id: Long) { setLong("active_term_id", id, _activeTermId) }

    // ── 导入后学期校准引导信号：课表页弹出「当前是第几周」校准（导入未带学期信息时置位）──
    private val _calibrationPending = MutableStateFlow(false)
    val calibrationPending: StateFlow<Boolean> = _calibrationPending

    fun requestSemesterCalibration() { _calibrationPending.value = true }
    fun clearSemesterCalibration() { _calibrationPending.value = false }

    // ── 校历 → 课表：点某天看那天的课 ──
    // 为什么不是给 "timetable" 路由加参数：`isTabRoute` 是拿 `currentDestination?.route`
    // 去和底栏那五个裸 route 比的，参数化路由会变成 "timetable?week={week}"，
    // 底栏立刻消失、tab 高亮也没了。所以沿用上面 `calibrationPending` 的形制：
    // 状态留在容器里，课表页自己来取。
    private val _pendingWeekJump = MutableStateFlow<Int?>(null)
    val pendingWeekJump: StateFlow<Int?> = _pendingWeekJump

    fun requestWeekJump(week: Int) { _pendingWeekJump.value = week }
    fun consumeWeekJump() { _pendingWeekJump.value = null }

    // ── Cross-tab navigation signal: Homework ──
    // ⚠️ 2026-10 已删除（`pendingShowHomework` / `requestShowHomework` / `consumeShowHomework`）：
    // 它是为「作业管理面板」（`HomeworkListDialog`）准备的跨 Tab 信号，而那个面板
    // 被用户"首页只保留一个新增入口、放右下角"的口径取代了 —— 信号**一个调用点都没有**，
    // HomeScreen 里那个消费它的 LaunchedEffect 也随之删掉。
    // 留着它会让人以为"从别处可以唤起待办面板"。

    /**
     * 重排考试提醒。
     *
     * 放在 AppContainer 而不是各 ViewModel：只有这里有 [appContext]，
     * 而且已在同类场景（上课提醒）沿用这个位置 —— 集中一处，避免每个调用点
     * 都要自己想办法拿 context。
     */
    fun rescheduleExamReminders() = ExamReminderScheduler.rescheduleAsync(appContext)

    fun autoCurrentWeek(): Int {
       val start = parseSemesterStart(_semesterStart.value) ?: return 1
       return currentWeekFrom(start, _semesterWeeks.value)
    }

    // ── General settings ──
    private val _darkMode = MutableStateFlow(prefs.getString("dark_mode", "light") ?: "light")
    val darkMode: StateFlow<String> = _darkMode
    fun setDarkMode(value: String) { setStr("dark_mode", value, _darkMode) }

    /**
     * 同步系统 uiMode（让 WebView 等非 Compose 内容也跟随深色设置）。
     * 仅 API 31+ 支持应用级夜间模式；更低版本由 Compose 主题兜底（不崩溃）。
     */
    fun applySystemNightMode(mode: String) {
       if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return
       try {
           val umm = app.applicationContext.getSystemService(Context.UI_MODE_SERVICE) as? android.app.UiModeManager ?: return
           umm.setApplicationNightMode(
               when (mode) {
                   "light" -> android.app.UiModeManager.MODE_NIGHT_NO
                   "dark" -> android.app.UiModeManager.MODE_NIGHT_YES
                   else -> android.app.UiModeManager.MODE_NIGHT_AUTO
               }
           )
       } catch (_: Exception) {
           // 部分 ROM 不支持，忽略
       }
    }

    // ── Feature toggles ──
    private val _showTreasureBox = MutableStateFlow(prefs.getBoolean("show_treasure_box", false))
    val showTreasureBox: StateFlow<Boolean> = _showTreasureBox
    fun toggleTreasureBox() { setBool("show_treasure_box", !_showTreasureBox.value, _showTreasureBox) }

    private val _showWidget = MutableStateFlow(prefs.getBoolean("show_widget", false))
    val showWidget: StateFlow<Boolean> = _showWidget
    fun toggleWidget() {
       setBool("show_widget", !_showWidget.value, _showWidget)
       ClassWidgetProvider.refresh(appContext)
    }

    private val _reminderEnabled = MutableStateFlow(prefs.getBoolean("reminder_enabled", false))
    val reminderEnabled: StateFlow<Boolean> = _reminderEnabled
    private val _reminderMinutes = MutableStateFlow(prefs.getInt("reminder_minutes", 15))
    val reminderMinutes: StateFlow<Int> = _reminderMinutes

    private val _showOddEven = MutableStateFlow(prefs.getBoolean("show_odd_even", true))
    val showOddEven: StateFlow<Boolean> = _showOddEven
    private val _showNonCurrentWeek = MutableStateFlow(prefs.getBoolean("show_non_current_week", false))
    val showNonCurrentWeek: StateFlow<Boolean> = _showNonCurrentWeek

    fun toggleReminder() {
       setBool("reminder_enabled", !_reminderEnabled.value, _reminderEnabled)
       ClassReminderScheduler.rescheduleAsync(appContext)
    }
    fun setReminderMinutes(value: Int) {
       setInt("reminder_minutes", value, _reminderMinutes)
       ClassReminderScheduler.rescheduleAsync(appContext)
    }
    fun toggleShowOddEven() { setBool("show_odd_even", !_showOddEven.value, _showOddEven) }
    fun toggleShowNonCurrentWeek() { setBool("show_non_current_week", !_showNonCurrentWeek.value, _showNonCurrentWeek) }

    // ── Vibration mode: 0=off, 1=light, 2=medium, 3=strong ──
    private val _vibrationMode = MutableStateFlow(prefs.getInt("vibration_mode", 1))
    val vibrationMode: StateFlow<Int> = _vibrationMode
    fun setVibrationMode(value: Int) { setInt("vibration_mode", value, _vibrationMode) }

    // ── Grid background color: -1=default(theme), 0..N=palette index ──
    private val _gridBgColor = MutableStateFlow(prefs.getInt("grid_bg_color", -1))
    val gridBgColor: StateFlow<Int> = _gridBgColor
    fun setGridBgColor(value: Int) { setInt("grid_bg_color", value, _gridBgColor) }

    // ── Non-current week opacity (0.05 .. 0.5) ──
    private val _otherWeekAlpha = MutableStateFlow(prefs.getFloat("other_week_alpha", 0.50f))
    val otherWeekAlpha: StateFlow<Float> = _otherWeekAlpha
    fun setOtherWeekAlpha(value: Float) {
       _otherWeekAlpha.value = value; prefs.edit().putFloat("other_week_alpha", value).apply()
    }

    // ── Wallpaper URI (empty = none) ──
    private val _wallpaperUri = MutableStateFlow(prefs.getString("wallpaper_uri", "") ?: "")
    val wallpaperUri: StateFlow<String> = _wallpaperUri
    fun setWallpaperUri(value: String) { setStr("wallpaper_uri", value, _wallpaperUri) }

    // ── 遗留的 `app_skin` 皮肤层：2026-10 **整组删除** ──
    //
    // 它原是"按皮肤 key 设置配色主题"的兼容入口（`_skin` / `skin` / `setSkin` / `app_skin` 偏好键）。
    // 其存在理由 —— `AppearanceSkinCatalog`（7 套皮肤的翻译目录）—— 早已删除，只剩一个转发壳。
    // 配色收敛为 `POCKET_SOFT` 单一真源、且「配色主题」页（它唯一的使用者）删掉之后，
    // 这套壳**没有任何调用点**了，故一并移除。
    //
    // ⚠️ 老存档里的 `app_skin` 字符串**刻意不清理**：删掉读它的代码就够了，
    //    动老用户的存档没有必要（与 `CLAUDE.md` 对 `nickname` 残留键的处置同策）。
    //
    // `color_theme` 这个偏好键同样**不再被读取** —— 见本文件顶部同类说明，此处只是墓碑。

    // ── AI 助手（鲤鱼）改名 + 运行状态 ──
    //
    // 历史：这里原本是**宠物系统**（4 个角色 index + 可改名 + 各自 drawable）。
    // 宠物是从别的软件带过来的概念，与「口袋小鲤」的 AI 定位不符，已整组移除。
    // 现在只保留两件事：
    //   1. AI 的名字（用户可改，原 petName 的偏好键做兼容读取）
    //   2. Agent 运行状态 —— 供「我的」页的头像跟随 Agent 状态变化

    /** AI 助手名字。旧版本存在 pet_name 键里，首次读取时回退到它以免用户改过的名字丢失。 */
    private val _aiName = MutableStateFlow(
       prefs.getString("ai_name", null)
           ?: prefs.getString("pet_name", null)?.takeIf { it.isNotBlank() }
           ?: AI_DEFAULT_NAME
    )
    val aiName: StateFlow<String> = _aiName
    fun setAiName(value: String) {
       _aiName.value = value
       prefs.edit().putString("ai_name", value).apply()
    }

    /**
     * 鲤鱼 Agent 的当前阶段，驱动「我的」页头像的光环颜色与脉冲。
     *
     * 由 [com.pocket.xiaoli.ui.assistant.AssistantChatViewModel] 在对话过程中写入 ——
     * 用一个全局 StateFlow 而不是把状态塞进 ViewModel，是因为
     * 「我的」页与「鲤鱼」页是**两个不同的 Tab / ViewModel 实例**，
     * 需要一处共享的位置来传递状态。
     *
     * ⚠️ 类型是 [AgentPhase]（住在 `data/agent`），不是某个 UI 类型 ——
     * 这里是 DI 容器，只该依赖 data 侧。此前它用的是 `ui/theme` 里的同名枚举，
     * 导致容器被反向依赖了 UI 层。
     */
    private val _aiAvatarPhase = MutableStateFlow(AgentPhase.IDLE)
    val aiAvatarPhase: StateFlow<AgentPhase> = _aiAvatarPhase

    /** 唯一写入口。⚠️ 收尾复位必须走**非挂起**调用 —— 取消路径下再挂起会立刻再抛。 */
    fun setAiAvatarPhase(phase: AgentPhase) {
        _aiAvatarPhase.value = phase
    }

    /**
     * 「让鲤鱼帮忙分析」的待处理请求。
     *
     * ## 为什么需要这个信箱
     *
     * 发起方是「我的」页（导出日志入口），消费方是「鲤鱼」页，两者是**不同的
     * NavBackStackEntry + 各自的 ViewModel 实例**，彼此拿不到对方的引用。
     * 早期做法是让「我的」页直接构造 ViewModel，但那会分裂出第二个会话实例 ——
     * 用户在鲤鱼页看到的对话和「分析结果」不在同一个会话里，工具调用的上下文也断了。
     *
     * 所以这里放一个 **一次性信箱**：谁都能投递，鲤鱼页取走即清空。
     *
     * ## 为什么要 nonce
     *
     * 鲤鱼页是 `saveState/restoreState` 的底部 Tab，ViewModel 在切换 Tab 时**不会重建**，
     * 因此不能靠 `init {}` 消费（切回来时 init 不会再跑）。改由界面 `LaunchedEffect`
     * 监听这里；而 `LaunchedEffect` 在重进页面时会重新执行，只靠"内容非空"判断
     * 会把同一条请求重复发送。nonce 让每次投递都是**新 key**，
     * 界面端只处理比自己记录过的更大的 nonce，就能做到恰好消费一次。
     */
    data class AiAnalyzeRequest(val nonce: Long, val text: String)

    private val _pendingAiAnalyze = MutableStateFlow<AiAnalyzeRequest?>(null)
    val pendingAiAnalyze: StateFlow<AiAnalyzeRequest?> = _pendingAiAnalyze

    /** 投递一条待分析文本；nonce 自增，保证与上一条不同。 */
    fun requestAiAnalyze(text: String) {
       _pendingAiAnalyze.value = AiAnalyzeRequest(
           nonce = (_pendingAiAnalyze.value?.nonce ?: 0L) + 1L,
           text = text,
       )
    }

    /** 取走后清空，避免下次进鲤鱼页又发一遍。 */
    fun consumeAiAnalyze() { _pendingAiAnalyze.value = null }

    /**
     * 跨 Tab 跳转请求。
     *
     * 与 [requestAiAnalyze] 同样的理由：由「我的」页发起、[com.pocket.xiaoli.ui.navigation.AppNavigation]
     * 消费。之前导航动线都靠参数层层下传（`onOpenXxx` 回调），一个入口就要改 3~4 层签名；
     * 这种"从任意页面跳到某个 Tab"的需求用信箱更省事，且不会漏改中间层。
     */
    data class NavRequest(val nonce: Long, val route: String)

    private val _pendingNav = MutableStateFlow<NavRequest?>(null)
    val pendingNav: StateFlow<NavRequest?> = _pendingNav

    /** 请求跳到某个底部 Tab（route 取 bottomNavItems 的 route）。 */
    fun requestNav(route: String) {
       _pendingNav.value = NavRequest(
           nonce = (_pendingNav.value?.nonce ?: 0L) + 1L,
           route = route,
       )
    }

    fun consumeNav() { _pendingNav.value = null }

    private val _borderStyle = MutableStateFlow(prefs.getInt("border_style", 0))
    val borderStyle: StateFlow<Int> = _borderStyle
    fun setBorderStyle(value: Int) { setInt("border_style", value, _borderStyle) }

    // ── helpers（单一事实来源：SharedPreferences + StateFlow）──
    private fun setBool(key: String, value: Boolean, flow: MutableStateFlow<Boolean>) {
       flow.value = value; prefs.edit().putBoolean(key, value).apply()
    }

    private fun setInt(key: String, value: Int, flow: MutableStateFlow<Int>) {
       flow.value = value; prefs.edit().putInt(key, value).apply()
    }

    private fun setStr(key: String, value: String, flow: MutableStateFlow<String>) {
       flow.value = value; prefs.edit().putString(key, value).apply()
    }

    private fun setLong(key: String, value: Long, flow: MutableStateFlow<Long>) {
       flow.value = value; prefs.edit().putLong(key, value).apply()
    }

    // ── 开发者模式（默认完全隐藏，需在「关于」连点版本号解锁）──
    private val _developerUnlocked = MutableStateFlow(prefs.getBoolean(KEY_DEVELOPER_UNLOCKED, false))
    val developerUnlocked: StateFlow<Boolean> = _developerUnlocked

    fun unlockDeveloper() {
       _developerUnlocked.value = true
       prefs.edit().putBoolean(KEY_DEVELOPER_UNLOCKED, true).apply()
    }

    fun lockDeveloper() {
       _developerUnlocked.value = false
       prefs.edit().putBoolean(KEY_DEVELOPER_UNLOCKED, false).apply()
    }

    // ── 功能开关（Feature Flags）──
    private val _featureFlags = MutableStateFlow(loadFeatureFlags())
    val featureFlags: StateFlow<Set<String>> = _featureFlags

    private fun loadFeatureFlags(): Set<String> {
       // getStringSet 返回的集合由 prefs 持有，必须拷贝一份再用
       val stored = prefs.getStringSet(KEY_FEATURE_FLAGS, null)?.toSet()
       return stored ?: FeatureFlag.defaultOnKeys
    }

    fun isFlagOn(flag: FeatureFlag): Boolean = flag.key in _featureFlags.value

    fun setFlag(flag: FeatureFlag, on: Boolean) {
       val next = _featureFlags.value.toMutableSet().apply {
           if (on) add(flag.key) else remove(flag.key)
       }
       _featureFlags.value = next
       prefs.edit().putStringSet(KEY_FEATURE_FLAGS, next).apply()
    }

    // ── 高级课表设置：着色规则 ──
    private val _courseColorMode = MutableStateFlow(
       CourseColorMode.fromKey(prefs.getString(KEY_COURSE_COLOR_MODE, null))
    )
    val courseColorMode: StateFlow<CourseColorMode> = _courseColorMode

    fun setCourseColorMode(mode: CourseColorMode) {
       _courseColorMode.value = mode
       prefs.edit().putString(KEY_COURSE_COLOR_MODE, mode.key).apply()
    }

    // ── 自定义首页 ──
    private val _homeModules = MutableStateFlow(loadHomeModules())
    val homeModules: StateFlow<List<HomeModule>> = _homeModules

    private val _hiddenHomeModules = MutableStateFlow(loadHiddenHomeModules())
    val hiddenHomeModules: StateFlow<Set<String>> = _hiddenHomeModules

    private fun loadHomeModules(): List<HomeModule> {
       val raw = prefs.getString(KEY_HOME_MODULE_ORDER, null).orEmpty()
       val keys = if (raw.isBlank()) emptyList() else raw.split(",").map { it.trim() }
       return HomeLayout.normalize(keys)
    }

    private fun loadHiddenHomeModules(): Set<String> {
       return prefs.getStringSet(KEY_HIDDEN_HOME_MODULES, null)?.toSet().orEmpty()
    }

    private fun persistHomeModules(modules: List<HomeModule>) {
       _homeModules.value = modules
       prefs.edit().putString(KEY_HOME_MODULE_ORDER, modules.joinToString(",") { it.key }).apply()
    }

    fun setHomeModuleEnabled(module: HomeModule, enabled: Boolean) {
       val next = _hiddenHomeModules.value.toMutableSet().apply {
           if (enabled) remove(module.key) else add(module.key)
       }
       _hiddenHomeModules.value = next
       prefs.edit().putStringSet(KEY_HIDDEN_HOME_MODULES, next).apply()
    }

    fun moveHomeModule(from: Int, to: Int) {
       val keys = _homeModules.value.map { it.key }
       val moved = HomeLayout.move(keys, from, to)
       if (moved != keys) persistHomeModules(HomeLayout.normalize(moved))
    }

    fun resetHomeLayout() {
       _hiddenHomeModules.value = emptySet()
       persistHomeModules(HomeLayout.defaultOrder)
       prefs.edit().remove(KEY_HIDDEN_HOME_MODULES).apply()
    }

    // ── 本地学习笔记（后续接服务器公开笔记）──
    private val _notes = MutableStateFlow(loadNotes())
    val notes: StateFlow<List<NoteItem>> = _notes

    private fun loadNotes(): List<NoteItem> {
       val raw = prefs.getString(KEY_NOTES, null) ?: return emptyList()
       return runCatching {
           val type = object : TypeToken<List<NoteItem>>() {}.type
           gson.fromJson<List<NoteItem>>(raw, type)
       }.getOrDefault(emptyList())
    }

    fun saveNote(note: NoteItem) {
       val next = _notes.value.filterNot { it.id == note.id } + note
       val sorted = next.sortedByDescending { it.updatedAt }
       _notes.value = sorted
       prefs.edit().putString(KEY_NOTES, gson.toJson(sorted)).apply()
    }

    fun deleteNote(id: String) {
       val next = _notes.value.filterNot { it.id == id }
       _notes.value = next
       prefs.edit().putString(KEY_NOTES, gson.toJson(next)).apply()
    }

    // ── Mock 数据模式（开发者实验室）──
    private val _mockScenario = MutableStateFlow(
       MockScenario.fromKey(prefs.getString(KEY_MOCK_SCENARIO, null))
    )
    val mockScenario: StateFlow<MockScenario> = _mockScenario

    fun setMockScenario(scenario: MockScenario) {
       _mockScenario.value = scenario
       prefs.edit().putString(KEY_MOCK_SCENARIO, scenario.key).apply()
    }


    // ── 缓存清理 / 重置（开发者实验室）──

    /** 清除 WebView 缓存、Cookie、LocalStorage 与 CookieStore 持久化登录态。 */
    fun clearWebCacheAndCookies() {
       runCatching {
           val webView = android.webkit.WebView(appContext)
           webView.clearCache(true)
           webView.clearHistory()
           webView.destroy()

           android.webkit.CookieManager.getInstance().removeAllCookies(null)
           android.webkit.CookieManager.getInstance().flush()
           android.webkit.WebStorage.getInstance().deleteAllData()
           CookieStore.clear()
       }
    }

    /**
     * 清空**所有**本地用户数据。
     *
     * 覆盖：Room 全部表、`shared_prefs/` 下的每一个 SharedPreferences 文件（含加密凭据）、
     * Cookie、网络日志、外部存储里的崩溃日志与其它缓存文件。
     *
     * ⚠️ **必须在 IO 线程调用**；调用后建议重启进程，避免内存中的 StateFlow 继续展示旧值。
     * WebView 的缓存要另调 [clearWebCacheAndCookies]（WebView 只能在主线程碰）。
     *
     * ⚠️ 这里**刻意不逐个硬编码 SharedPreferences 文件名**。历史实现只清了
     * `pocket_xiaoli_prefs` 与 `edu_credentials_encrypted`，结果漏掉了
     * `egg_rice_cookies`（首次导入的教务登录态）、`web_import_urls`（导入 URL 历史）、
     * `egg_rice_treasure`（服务页收藏）、`campus_location`（校区定位）、
     * 今日校园 Cookie、校园卡会话、趣智洗浴设备/阀门/会话、知识库缓存、
     * 节假日缓存、正方导入记忆等 11 个文件 —— 用户点了「重置」，洗澡与校园卡的凭据
     * 仍然留在设备上。现在改为枚举目录，**以后新增的存储自动被覆盖**。
     */
    fun resetAllData() {
       // 番茄钟要**先停**再清：它挂着前台服务与常驻通知，
       // 只清 prefs 的话服务还在跑，下一次状态变更会把快照重新写回磁盘，
       // 用户会看到"重置完了通知栏还挂着一个倒计时"。
       // 这里不落库、不记 session —— 数据正在被抹掉，记了也白记。
       runCatching {
          FocusTimerService.stop(app)
          FocusNotifier.cancelTimer(app)
          focusStore.clear()
       }
       app.database.clearAllTables()
       clearAllSharedPreferences()
       CookieStore.clear()
       NetworkLogStore.clear()
       deleteCrashLogs()
       deleteExternalCache()
    }

    /**
     * 清空 `shared_prefs/` 目录下的每一个 SharedPreferences 文件。
     *
     * 只 `clear()` 不删文件：删文件会让已经持有实例的调用方（那些 `by lazy` 单例）
     * 与磁盘状态不一致，而 `clear()` 会同时更新内存与磁盘。
     */
    private fun clearAllSharedPreferences() {
       val dir = java.io.File(app.applicationInfo.dataDir, "shared_prefs")
       val names = dir.listFiles { file -> file.isFile && file.name.endsWith(".xml") }
           ?.map { it.name.removeSuffix(".xml") }
           .orEmpty()
       names.forEach { name ->
          runCatching {
             app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
          }
       }
    }

    /**
     * 删除外部存储里的崩溃日志目录。
     *
     * ⚠️ 崩溃日志是**唯一写在 Room 与 SharedPreferences 之外的**用户数据
     *（`getExternalFilesDir(DOWNLOADS)/crash_logs/crash_*.txt`，含机型、系统版本、
     * 堆栈与可能的真实文件路径）。`clearAllTables()` 与清空 SharedPreferences
     * 都碰不到它，所以必须显式删 —— 否则「删除后不可恢复」这条承诺不成立。
     */
    fun deleteCrashLogs() {
       runCatching { app.crashHandler.getLogDir()?.deleteRecursively() }
    }

    /** 删除 `getExternalFilesDir(null)` 下的全部内容（崩溃日志、导出中间产物等）。 */
    private fun deleteExternalCache() {
       runCatching {
          app.getExternalFilesDir(null)?.listFiles()?.forEach { it.deleteRecursively() }
       }
    }

}

/** SharedPreferences key：内置 POI 已播种到的版本 */
private const val KEY_POI_SEED_VERSION = "poi_seed_version"

/** SharedPreferences key：地图收藏的 POI id 集合 */
private const val KEY_FAVORITE_POI_IDS = "favorite_poi_ids"

/** 常用服务默认项（按服务标题匹配） */
// 「校园助手」已从服务目录移除，快速服务默认值同步去掉它（否则会解析成空项占位）
// 2026-10 从 3 个提到 5 个。⚠️ 这五个 key 必须与 `ServiceEntry.title` **逐字相同** ——
// `TreasureBoxScreen` 用 `mapNotNull { key -> allServiceEntries.firstOrNull { it.key == key } }`
// 解析，**不匹配的 key 会被静默丢掉**（那一格直接不显示，不报错）。
val DEFAULT_QUICK_SERVICE_KEYS = listOf("校园卡", "校园网", "空教室查询", "宿舍电费", "图书馆")

/** SharedPreferences key：教室分布图数据（JSON） */
private const val KEY_ROOM_CELLS = "room_cells"

/** SharedPreferences key：教室分布图 JSON 的 schema 版本 */
private const val KEY_ROOM_CELLS_SCHEMA = "room_cells_schema_v"

/**
 * 教室分布图数据当前 schema 版本。
 *
 * - v1：仅有 row / col 等基础字段（历史数据不带版本号，视为 v1）
 * - v2：新增 rowSpan / colSpan / type / capacity
 *
 * 这段数据是 SharedPreferences 里的 JSON，**没有 Room 迁移可用**，
 * 所以靠这个版本号在读取时做一次性升级。
 */
private const val ROOM_CELLS_SCHEMA = 2

/** SharedPreferences key：内置教室数据已播种到的版本 */
private const val KEY_ROOM_CELL_SEED_VERSION = "room_cell_seed_version"

/** SharedPreferences key：开发者模式是否已解锁 */
private const val KEY_DEVELOPER_UNLOCKED = "developer_unlocked"

/** SharedPreferences key：功能开关状态集合 */
private const val KEY_FEATURE_FLAGS = "feature_flags"

/** SharedPreferences key：开发者实验室 Mock 场景 */
private const val KEY_MOCK_SCENARIO = "mock_scenario"

/** SharedPreferences key：首页模块顺序 */
/** SharedPreferences key：课程着色规则 */
private const val KEY_COURSE_COLOR_MODE = "course_color_mode"

/** SharedPreferences key：自定义主题色（0 = 默认主题色） */
private const val KEY_CUSTOM_ACCENT_COLOR = "custom_accent_color"

private const val KEY_HOME_MODULE_ORDER = "home_module_order"

/** SharedPreferences key：首页隐藏模块 key 集合 */
private const val KEY_HIDDEN_HOME_MODULES = "hidden_home_modules"
private const val KEY_NOTES = "notes_json"
