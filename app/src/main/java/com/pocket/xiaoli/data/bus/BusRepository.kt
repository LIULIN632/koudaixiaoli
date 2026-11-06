package com.pocket.xiaoli.data.bus

import android.content.Context
import com.pocket.xiaoli.core.bus.BusArrivalRules
import com.pocket.xiaoli.network.NetworkLogInterceptor
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * 「公交到站」的数据门面：只打**一个明文接口**。
 *
 * ## 数据源与为什么只走它
 *
 * 参考实现 5151561/Trackr（MIT）有两条实时通道：车来了与掌上公交。车来了那条要
 * `cryptoSign`（参数 MD5）+ AES-ECB 解响应体，密钥只能从别处抓包拿来 ——
 * 与本项目「不猜加密签名」的既有边界冲突（学习通的 enc 签名我们同样不猜）。
 * 掌上公交只有一个 `POST https://h5.mygolbs.com/ApiData.do`、
 * `x-www-form-urlencoded`、**明文 JSON、无 key 无签名**，所以这一页只走它，并在界面上写明数据源。
 *
 * 2026-11-04 实测（烟台市）：
 * - CMD=105 本校站返回 9 条线路，`routeBean.hasReal = "1"`，`neartext` 形如
 *   `2站 [开往 蓝色智谷]` / `已到站 [到达 烟台理工学院]` / `等待发车`（此时 `nearnum = 100`）；
 * - CMD=106 按学校中心坐标返回 16 个附近站，带真实经纬度与直线距离（最近 205 米）。
 *
 * ## 为什么包内不预置线路
 *
 * 线路会改道、会调价。把某天的静态线路打进 APK，离线时它就会以「事实」的口吻讲一件
 * 可能已经不成立的事 —— `memory/01-constraints.md` §2.4 那条教训写的就是
 * 「占位样例随包发布等于对所有用户宣称那是真的」。所以包内只钉两个不常变的事实：
 * 本校站名与学校中心坐标；线路与站点一律现取，成功后落缓存，下次离线显示缓存并明写
 * 「上次刷新时间」。
 *
 * ## 不申请定位权限
 *
 * 距离一律以**学校中心点**为锚（[CAMPUS_LAT] / [CAMPUS_LNG]），不是用户当前位置。
 * 换来的是：不要 `ACCESS_FINE_LOCATION`、不上传位置、口径稳定。
 * 代价是：宿舍在另一角的同学看到的「直线 205 米」是锚点到站点的距离，不是到他本人的 ——
 * 所以文案写「到校中心直线」，不写「离你 X 米」。
 *
 * ## 抓取纪律
 *
 * 上游是给人用的 H5 页面接口，Trackr 的免责声明也是「不要高频抓取」。这里守三条：
 * 同一屏 30 秒内不重复请求（[REALTIME_TTL_MILLIS]）、并发用 [fetchMutex] 合流
 * （连点刷新只发一次）、附近站列表一天一次（[STATIONS_TTL_MILLIS]）。
 */
class BusRepository(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(NetworkLogInterceptor)
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build(),
) {

    data class State(
        val loading: Boolean = false,
        val stationName: String = DEFAULT_STATION,
        /** 附近站（按到锚点的直线距离由近到远），用于顶部切换。 */
        val stations: List<BusStation> = emptyList(),
        /** 当前站的线路看板，已按到站紧迫度排好序。 */
        val lines: List<BusLine> = emptyList(),
        val capturedAtMillis: Long = 0L,
        val error: String? = null,
        /** 看板是刚拉的（true）还是上次缓存的（false）。 */
        val fromNetwork: Boolean = false,
    ) {
        val isEmpty: Boolean get() = lines.isEmpty() && stations.isEmpty()
    }

    /** 缓存读一次就够（进页面第一件事就是拿它当初值），顺带把附近站的时间戳带出来。 */
    private val cached = loadCache()

    private val _state = MutableStateFlow(cached.first)
    val state: StateFlow<State> = _state.asStateFlow()

    /** 上次抓到附近站列表的时间；只在内存里，写缓存时一起落盘。 */
    private var stationsCapturedAt: Long = cached.second

    /** 同一时刻只放一个抓取在跑；连点刷新不会变成对上游的轰炸。 */
    private val fetchMutex = Mutex()

    /**
     * 拉当前站的看板（附近站列表过期时顺带补一发）。
     *
     * @param force 手动刷新传 true 跳过 TTL；页面首次进入传 false。
     */
    suspend fun load(force: Boolean = false): State = fetchMutex.withLock {
        val current = _state.value
        if (!force && !isStale(current.capturedAtMillis)) return@withLock current
        _state.value = current.copy(loading = true, error = null)
        val board = withContext(Dispatchers.IO) { fetchBoard(current.stationName) }
        finish(current, board)
    }

    /**
     * 切换站点。
     *
     * ⚠️ 换站名之后立刻把看板清空：抓取失败时**留着上一个站的数字**比空白更糟，
     * 那等于把 A 站的到站时间挂到 B 站名下。
     */
    suspend fun selectStation(name: String): State {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed == _state.value.stationName) return _state.value
        return fetchMutex.withLock {
            val current = _state.value.copy(stationName = trimmed, lines = emptyList(), error = null)
            _state.value = current
            val board = withContext(Dispatchers.IO) { fetchBoard(trimmed) }
            finish(current, board)
        }
    }

    /** 抓取失败时保留上一次的数据与时间戳，只加一句人话说明。 */
    private fun finish(previous: State, board: BusBoard?): State {
        val next = when {
            board == null -> previous.copy(
                loading = false,
                fromNetwork = false,
                error = if (previous.lines.isEmpty()) {
                    "没能连上实时公交，检查网络后再刷新"
                } else {
                    "没能连上实时公交，下面是上次刷新到的到站信息"
                },
            )

            else -> {
                val rejected = acceptReason(board, previous)
                if (rejected != null) {
                    previous.copy(loading = false, fromNetwork = false, error = rejected)
                } else {
                    val fresh = board.copy(
                        lines = sortLines(board.lines),
                        stations = board.stations.ifEmpty { previous.stations },
                    )
                    writeCached(fresh)
                    fresh.copy(fromNetwork = true).toState()
                }
            }
        }
        _state.value = next
        return next
    }

    /** 一次抓取 = CMD=105（本站线路 + 实时）；附近站过期时再补一发 CMD=106。 */
    private fun fetchBoard(stationName: String): BusBoard? {
        val raw = post(stationBoardForm(stationName)) ?: return null
        val lines = parseLines(raw)
        if (lines.isEmpty()) {
            // 接口正常应答但这个站查不到线路：给一张空板子，让界面说实话，
            // 而网络异常（拿不到 status）走 null 分支
            val status = runCatching { JSONObject(raw).optString("status") }.getOrDefault("")
            return if (status == "1") BusBoard(stationName, emptyList(), emptyList(), nowMillis(), true) else null
        }
        val stations = if (stationsCapturedAt <= 0L ||
            nowMillis() - stationsCapturedAt > STATIONS_TTL_MILLIS
        ) {
            fetchStations()?.also { stationsCapturedAt = nowMillis() } ?: _state.value.stations
        } else {
            _state.value.stations
        }
        return BusBoard(stationName, lines, stations, nowMillis(), true)
    }

    /** CMD=106：按学校锚点取附近站。失败返回 null（保留上次列表，不清空）。 */
    private fun fetchStations(): List<BusStation>? {
        val raw = post(nearbyStationForm()) ?: return null
        return parseStations(raw).ifEmpty { null }
    }

    private fun post(form: Map<String, String>): String? = runCatching {
        val body = FormBody.Builder().apply {
            form.forEach { (key, value) -> add(key, value) }
        }.build()
        val request = Request.Builder()
            .url(API_URL)
            .post(body)
            .header("Origin", ORIGIN)
            .header("Referer", ORIGIN + "/")
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.string()
        }
    }.getOrNull()

    private fun cacheFile(): File = File(context.filesDir, "bus/yantai.json")

    /**
     * 只落**被接受过**的看板：坏数据永远不许覆盖好缓存（与
     * [com.pocket.xiaoli.data.library.LibraryRepository] 同一条纪律，更新链上踩过）。
     */
    private fun writeCached(board: BusBoard) {
        runCatching {
            cacheFile().parentFile?.mkdirs()
            cacheFile().writeText(boardToJson(board).toString(), Charsets.UTF_8)
        }
    }

    private fun boardToJson(board: BusBoard): JSONObject = board.toJson()
        .put(STATIONS_CAPTURED_KEY, stationsCapturedAt)

    /**
     * 读缓存 → (初值状态, 附近站时间)。
     *
     * 没有缓存或读坏了都是空状态，界面显示加载态而不是旧数据。
     * ⚠️ 缓存里存的仍是上游原始字段，[BusLine.arrival] 每次访问现算，
     * 所以不可能出现「缓存里躺着一个十分钟前的倒计时」。
     */
    private fun loadCache(): Pair<State, Long> {
        val text = runCatching {
            cacheFile().takeIf { it.isFile }?.readText(Charsets.UTF_8)
        }.getOrNull() ?: return (State() to 0L)
        val parsed = runCatching {
            val root = JSONObject(text)
            BusBoard.fromJson(root) to root.optLong(STATIONS_CAPTURED_KEY, 0L)
        }.getOrNull() ?: return (State() to 0L)
        val (board, stationsAt) = parsed
        if (board.stationName.isEmpty() && board.lines.isEmpty() && board.stations.isEmpty()) {
            return State() to stationsAt
        }
        return State(
            stationName = board.stationName.ifEmpty { DEFAULT_STATION },
            stations = board.stations,
            lines = sortLines(board.lines),
            capturedAtMillis = board.capturedAtMillis,
            fromNetwork = false,
        ) to stationsAt
    }

    private fun isStale(capturedAt: Long): Boolean =
        capturedAt <= 0L || nowMillis() - capturedAt > REALTIME_TTL_MILLIS

    private fun BusBoard.toState(): State = State(
        loading = false,
        stationName = stationName,
        stations = stations,
        lines = lines,
        capturedAtMillis = capturedAtMillis,
        error = null,
        fromNetwork = fromNetwork,
    )

    companion object {

        /** 上游只有一个端点，靠 `CMD` 分流。 */
        const val API_URL = "https://h5.mygolbs.com/ApiData.do"

        /** 上游的 H5 页面域；Origin/Referer 必须与它一致，否则被挡。 */
        internal const val ORIGIN = "https://h5.mygolbs.com"

        internal const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Mobile Safari/537.36"

        /** 上游要求带行政区划后缀的城市名（`烟台市`，不是 `烟台`）。 */
        const val CITY_NAME = "烟台市"

        /** 本校站名（2026-11-04 CMD=110 实测存在；另有「烟台理工学校」一站，不是本校这个门）。 */
        const val DEFAULT_STATION = "烟台理工学院"

        /**
         * 学校中心锚点。**与 `assets/campus_map/ytlg_main/campus_3d.json` 的 `center` 同源**
         * （WGS-84，OpenStreetMap 校园轮廓的中心，ODbL 1.0）。
         * 由 `BusCampusAnchorTest` 直接读资产文件钉住，防止两处漂开。
         */
        const val CAMPUS_LAT = 37.453823
        const val CAMPUS_LNG = 121.471607

        /** 到站数据 30 秒就旧了，但连点刷新不该每次都打上游。 */
        const val REALTIME_TTL_MILLIS = 30_000L

        /** 附近站列表一天查一次。 */
        const val STATIONS_TTL_MILLIS = 24L * 60L * 60L * 1000L

        /** 缓存里「附近站是什么时候查的」这个键；不属于 [BusBoard] 数据模型，所以单独读写。 */
        internal const val STATIONS_CAPTURED_KEY = "stationsCapturedAtMillis"

        /** 时钟留一个口子，只为了给 TTL 判据写可重复的单测。 */
        internal fun nowMillis(): Long = System.currentTimeMillis()

        /**
         * CMD=105（本站线路 + 实时）的表单。
         *
         * ⚠️ `MYLAT` / `MYLNG` **留空是刻意的**：这两个字段是上游收「用户当前坐标」的位置。
         * 本页不申请定位权限、不读设备位置，所以必须传空串 ——
         * 哪天有人"顺手"把手机坐标填进来，就等于往第三方接口传用户位置，
         * 直接撞 `memory/01-constraints.md` §2.2 那条红线。由 `MygolbsParseTest` 钉住。
         */
        internal fun stationBoardForm(stationName: String): Map<String, String> = mapOf(
            "CMD" to "105",
            "CITYNAME" to CITY_NAME,
            "STATIONNAME" to stationName,
            "MYLAT" to "",
            "MYLNG" to "",
            "CITYKEY" to "",
        )

        /**
         * CMD=106（附近站）的表单。
         *
         * 两处坐标字段（`LAT/LNG` 与 `MYLAT/MYLNG`）**都填学校中心锚点**：
         * 于是"离查询点"和"离我"在语义上统一成"离学校"，不牵扯任何个人位置。
         */
        internal fun nearbyStationForm(): Map<String, String> = mapOf(
            "CMD" to "106",
            "CITYNAME" to CITY_NAME,
            "LAT" to CAMPUS_LAT.toString(),
            "LNG" to CAMPUS_LNG.toString(),
            "MYLAT" to CAMPUS_LAT.toString(),
            "MYLNG" to CAMPUS_LNG.toString(),
        )

        /**
         * 票价文案。
         *
         * ⚠️ 上游的 `comments` **不止票价**：本校站 105 路那条实测是
         * `票价:1元` + 换行 + 一整段发车时刻（`发车时间：石家疃6:00、6:40、7:35…`，两百多字）。
         * 整段进卡片会把「开往 X」挤成三行，所以只取第一行；
         * 万一上游哪天不给「票价:」前缀，也只拿第一行并截到 12 字兜底。
         */
        internal fun fareOf(comments: String): String {
            val firstLine = comments.lineSequence().firstOrNull().orEmpty().trim()
            return if (firstLine.startsWith("票价:")) {
                firstLine.removePrefix("票价:").trim()
            } else {
                firstLine.take(12)
            }
        }

        /**
         * 上游的一条线路 → [BusLine]。
         *
         * ⚠️ `hasReal` 上游给的是**字符串** `"1"`（不是布尔、也不是数字 1），
         * 所以统一转字符串再比。写成 `optInt("hasReal") == 1` 的话实时线路会被全部
         * 误判成「未开通实时」—— 那比没有实时更糟，因为它在说假话。
         */
        internal fun lineFrom(item: JSONObject): BusLine {
            val route = item.optJSONObject("routeBean") ?: JSONObject()
            return BusLine(
                lineName = item.optString("lineName"),
                direction = item.optString("upperOrDown", "1").toIntOrNull() ?: 1,
                from = item.optString("from"),
                to = item.optString("to"),
                firstDeparture = item.optString("beginTime"),
                lastDeparture = item.optString("endTime"),
                fare = fareOf(item.optString("comments")),
                nearText = item.optString("neartext"),
                nearNum = item.optInt("nearnum", 0),
                hasRealtime = route.opt("hasReal")?.toString()?.trim() == "1",
                runState = route.optInt("runState", 0),
                nextBusText = item.optString("nexttext"),
            )
        }

        /** CMD=105 响应 → 线路列表；`status != 1` 或结构不对一律当失败（返回空）。 */
        internal fun parseLines(raw: String): List<BusLine> = runCatching {
            val root = JSONObject(raw)
            if (root.optString("status") != "1") return@runCatching emptyList()
            val data = root.optJSONArray("data") ?: return@runCatching emptyList()
            (0 until data.length()).mapNotNull(data::optJSONObject).map(::lineFrom)
        }.getOrDefault(emptyList())

        /** CMD=106 响应 → 附近站，按直线距离由近到远。 */
        internal fun parseStations(raw: String): List<BusStation> =
            runCatching {
                val root = JSONObject(raw)
                if (root.optString("status") != "1") return@runCatching emptyList()
                val data = root.optJSONArray("data") ?: return@runCatching emptyList()
                (0 until data.length()).mapNotNull(data::optJSONObject).map {
                    BusStation(
                        name = it.optString("name"),
                        lat = it.optString("lat").toDoubleOrNull() ?: 0.0,
                        // ⚠️ 上游这一版用 `lon`，另一版（CMD=110）用 `station_lon`，别抄错
                        lng = it.optString("lon").toDoubleOrNull() ?: 0.0,
                        distanceMeters = it.optInt("dis", -1),
                    )
                }
            }.getOrDefault(emptyList()).sortedBy { it.distanceMeters }

        /**
         * 看板排序：能等的在前（已到站 → 站数少 → 等发车 → 没实时/停运），
         * 同档按线路号自然序（2 路排在 17 路前面，跟站牌一致）。
         */
        internal fun sortLines(lines: List<BusLine>): List<BusLine> = lines.sortedWith(
            compareBy<BusLine> { BusArrivalRules.sortRank(it.arrival) }
                .thenComparator { a, b -> BusArrivalRules.compareLineNames(a.lineName, b.lineName) }
        )

        /**
         * 这份新数据能不能替换当前状态？返回 null = 可以，否则返回一句给人看的话。
         *
         * 只有一道闸：上游正常应答但当前站查不到线路时，不许把已有看板冲掉。
         */
        internal fun acceptReason(incoming: BusBoard, current: State): String? {
            if (incoming.lines.isEmpty() && current.lines.isNotEmpty() &&
                incoming.stationName == current.stationName
            ) {
                return "这个站暂时没查到线路，先显示上次刷新到的信息"
            }
            return null
        }

        /**
         * 「更新于 13:42」这类标签。
         *
         * 只格式化给定的毫秒，**不读当前时间** —— 快照测试传固定毫秒就拿到固定文案。
         */
        fun capturedLabel(millis: Long): String =
            if (millis <= 0L) "" else "更新于 " + SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(millis))
    }
}
