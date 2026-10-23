package com.pocket.xiaoli.data.electricity

import android.webkit.CookieManager
import com.pocket.xiaoli.data.model.DormInfo
import com.pocket.xiaoli.data.model.DormRoomNumber
import com.pocket.xiaoli.network.CookieStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

data class DormElectricityBalance(
    val balance: String,
    val accountNo: String,
    val address: String,
    val accountId: String? = null,
    val itemId: String? = null,
    /**
     * true 表示这一条是**按宿舍编号**认出来的（用了 [DormInfo.dormCode]），
     * false 表示按「楼号 + 房间号」匹配地址认出来的。
     *
     * 之所以要把它带出来给人看：「这真的是我这一间吗」是电费页最要紧的问题，
     * 而两种认人方式的可靠度不一样 —— 编号是唯一标识，地址是在猜写法。
     */
    val matchedByCode: Boolean = false,
    /** 本结果获取时间；0 表示未知（例如单测构造）。 */
    val fetchedAtMillis: Long = 0L,
    /** true 表示直接来自内存缓存，未发起网络请求。 */
    val fromCache: Boolean = false,
)

/**
 * 烟大南校后勤缴费系统余额查询。
 *
 * payItemList 会返回当前会话下的缴费账户列表；拿宿舍楼号和房间号去匹配
 * 用户地址，匹配到后取实时余额。只用于「我的」页 / 电费原生页展示，
 * 不缓存敏感姓名。
 *
 * 优化点：
 * - 5 分钟内直接返回内存缓存，避免来回切页重复打接口；
 * - 支持 forceRefresh 手动刷新；
 * - 统一给出缴费直达链接，避免多个页面各拼一次 URL；
 * - 房间号匹配增加数字边界，避免 602 错匹配到 1602。
 */
object DormElectricityRepository {

    private const val LIST_URL = "https://ydny.ytu.edu.cn/pms/wechat/ytdx/payItemList"
    private const val ADD_URL = "https://ydny.ytu.edu.cn/pms/wechat/ytdx/payItemAdd"
    private const val PAY_URL = "https://ydny.ytu.edu.cn/pms/wechat/ytdx/payItemIndex"
    private const val CACHE_TTL_MS = 5 * 60 * 1000L

    private var cachedBalance: DormElectricityBalance? = null
    private var cachedDormKey: String? = null

    /** 兼容旧调用：有有效缓存时返回余额字符串。 */
    fun cachedBalance(dorm: DormInfo): String? = cachedSnapshot(dorm)?.balance

    /** 返回带缓存标记 / 时间戳的完整快照；过期返回 null。 */
    fun cachedSnapshot(dorm: DormInfo?): DormElectricityBalance? {
        if (dorm == null) return null
        if (cachedDormKey != dormKey(dorm)) return null
        val snapshot = cachedBalance ?: return null
        if (snapshot.fetchedAtMillis > 0L &&
            System.currentTimeMillis() - snapshot.fetchedAtMillis > CACHE_TTL_MS
        ) {
            return null
        }
        return snapshot.copy(fromCache = true)
    }

    fun clearCache() {
        cachedBalance = null
        cachedDormKey = null
    }

    private fun rememberBalance(dorm: DormInfo, balance: DormElectricityBalance) {
        cachedDormKey = dormKey(dorm)
        cachedBalance = balance
    }

    private fun dormKey(dorm: DormInfo): String =
        dorm.building + "/" + dorm.room + "/" + dorm.serviceCampusId.orEmpty() + "/" + dorm.dormCode

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * 查询余额。
     *
     * @param forceRefresh true 时绕过内存缓存；false 时优先命中 5 分钟内的缓存。
     */
    suspend fun query(
        dorm: DormInfo,
        forceRefresh: Boolean = false,
    ): Result<DormElectricityBalance?> = withContext(Dispatchers.IO) {
        try {
            if (!dorm.isBound) return@withContext Result.success(null)
            if (!forceRefresh) {
                cachedSnapshot(dorm)?.let { return@withContext Result.success(it) }
            }

            val builder = Request.Builder().url(LIST_URL).get()
            val cookie = runCatching {
                CookieManager.getInstance().getCookie(LIST_URL)
            }.getOrNull() ?: CookieStore.getServiceCookies(LIST_URL)
            if (!cookie.isNullOrBlank()) builder.header("Cookie", cookie)

            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("缴费系统返回 HTTP ${response.code}")
                    )
                }
                val html = response.body?.string().orEmpty()
                val balance = parse(html, dorm)?.copy(
                    fetchedAtMillis = System.currentTimeMillis(),
                    fromCache = false,
                )
                if (balance != null) rememberBalance(dorm, balance)
                Result.success(balance)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 有匹配户号时直达本人缴费页；否则进入新增缴费项页。 */
    fun paymentUrl(detail: DormElectricityBalance?): String {
        return matchedPaymentUrl(detail) ?: ADD_URL
    }

    /**
     * 只在**认出了本人账户**时才返回直达缴费页，否则返回 null。
     *
     * 与 [paymentUrl] 的区别很重要：`paymentUrl` 认不出人时会退到 `payItemAdd`
     * （"增绑新用户"页）。而缴费这一步**只能由微信完成**（见 `WeChatUrlLauncher`
     * 的 KDoc），把用户送进 `payItemAdd` 只会让他看到绑定界面 ——
     * 那正是"点缴费跳到绑定界面"这个问题的表象。
     *
     * 所以界面需要能区分这两种情况：有 `matchedPaymentUrl` 就送微信直达本人那一间，
     * 没有就说明还没认出人，应当先解决认人（填宿舍编号），而不是去绑新账户。
     */
    fun matchedPaymentUrl(detail: DormElectricityBalance?): String? {
        val accountId = detail?.accountId
        val itemId = detail?.itemId
        return if (!accountId.isNullOrBlank() && !itemId.isNullOrBlank()) {
            "$PAY_URL?accountId=$accountId&itemType=METER&itemId=$itemId"
        } else {
            null
        }
    }

    internal fun parse(html: String, dorm: DormInfo): DormElectricityBalance? {
        if (html.isBlank() || !dorm.isBound) return null
        val code = normalizeCode(dorm.dormCode)
        // `orEmpty()`：这些字段来自 SharedPreferences 的 JSON，老存档里可能是 null
        // （详见 DormInfo.sanitized）。查询路径上崩一次就是"进电费页闪退"。
        val buildingNumber = Regex("(\\d{1,2})").find(dorm.building.orEmpty())
            ?.groupValues?.getOrNull(1)
        val room = dorm.room.orEmpty().trim()
        // 两套认人方式至少要有一套能用：编号，或者「楼号 + 房间号」
        if (code.isBlank() && (buildingNumber == null || room.isBlank())) return null

        val document = Jsoup.parse(html)
        val panels = document.select("div.weui-panel")

        // ① 填了宿舍编号：按编号**精确认人**（缴费系统里「用户编号」就是那串编号）。
        //    这一步不看地址 —— 地址写法五花八门（北校10楼南602 / 南校区10栋602），
        //    而编号是唯一的，这也是这个字段存在的理由。
        if (code.isNotBlank()) {
            panels.asSequence()
                .mapNotNull { parsePanelByCode(it, code) }
                .firstOrNull()
                ?.let { return it }
        }

        // ② 没填编号（或编号没对上）：退回原来的「楼号 + 房间号」匹配地址
        if (buildingNumber == null || room.isBlank()) return null
        panels.asSequence()
            .mapNotNull { parsePanel(it, buildingNumber, room) }
            .firstOrNull()
            ?.let { return it }
        return parseTextFallback(document, buildingNumber, room)
    }

    /**
     * 按宿舍编号认人：大小写、空格、`-`、`_`、`#` 都不敏感（用户从网页上抄过来的写法各异）。
     *
     * 参数收 `String?`：宿舍是存在 JSON 里的，老存档没有这一栏时它是 null 而不是空串
     * （详见 `DormInfo.sanitized`），归一化顺手把这个也吃掉，省得每个调用点各写一次判断。
     */
    private fun normalizeCode(raw: String?): String =
        raw.orEmpty()
            .trim()
            .uppercase()
            .replace(" ", "")
            .replace("　", "")
            .replace("-", "")
            .replace("_", "")
            .replace("#", "")

    private fun parsePanelByCode(panel: Element, code: String): DormElectricityBalance? {
        val address = firstCellText(panel, "用户地址", "房间地址", "地址")
        val balance = firstCellText(panel, "实时余额", "电费余额", "账户余额", "余额")
        val accountNo = firstCellText(panel, "用户编号", "户号", "账户编号")
        if (address.isBlank() || balance.isBlank() || accountNo.isBlank()) return null
        if (normalizeCode(accountNo) != code) return null
        return balanceOf(panel, accountNo, address, balance, matchedByCode = true)
    }

    private fun parsePanel(
        panel: Element,
        buildingNumber: String,
        room: String,
    ): DormElectricityBalance? {
        val address = firstCellText(panel, "用户地址", "房间地址", "地址")
        val balance = firstCellText(panel, "实时余额", "电费余额", "账户余额", "余额")
        val accountNo = firstCellText(panel, "用户编号", "户号", "账户编号")
        if (address.isBlank() || balance.isBlank() || accountNo.isBlank()) return null
        if (!matchesDorm(address, buildingNumber, room)) return null
        return balanceOf(panel, accountNo, address, balance, matchedByCode = false)
    }

    /** 两种认人方式共用的收尾：把面板上的缴费链接参数一起取出来。 */
    private fun balanceOf(
        panel: Element,
        accountNo: String,
        address: String,
        balance: String,
        matchedByCode: Boolean,
    ): DormElectricityBalance {
        val href = panel.selectFirst("a[href*=payItemIndex]")?.attr("href")
            ?: panel.selectFirst("a[href*=payItemAdd]")?.attr("href")
            ?: ""
        return DormElectricityBalance(
            balance = balance,
            accountNo = accountNo,
            address = address,
            accountId = queryParam(href, "accountId"),
            itemId = queryParam(href, "itemId"),
            matchedByCode = matchedByCode,
        )
    }

    private fun firstCellText(panel: Element, vararg labels: String): String {
        labels.forEach { label ->
            val value = panel.selectFirst("div.weui-cell:contains($label) div.weui-cell__ft")
                ?.text()
                ?.trim()
                .orEmpty()
            if (value.isNotBlank()) return value
        }
        return ""
    }

    private fun parseTextFallback(
        document: Document,
        buildingNumber: String,
        room: String,
    ): DormElectricityBalance? {
        val text = document.text()
        val accountNo = Regex("(?:用户编号|户号|账户编号)[：:]?\\s*([A-Za-z0-9]+)")
            .find(text)?.groupValues?.getOrNull(1).orEmpty()
        val address = Regex("(?:用户地址|房间地址|地址)[：:]?\\s*([^\\s，,]{2,30})")
            .find(text)?.groupValues?.getOrNull(1).orEmpty()
        val balance = Regex("(?:实时余额|电费余额|账户余额|余额)[：:]?\\s*(-?\\d+(?:\\.\\d+)?)")
            .find(text)?.groupValues?.getOrNull(1).orEmpty()
        if (accountNo.isBlank() || address.isBlank() || balance.isBlank()) return null
        if (!matchesDorm(address, buildingNumber, room)) return null

        val href = document.selectFirst("a[href*=payItemIndex]")?.attr("href").orEmpty()
        return DormElectricityBalance(
            balance = balance,
            accountNo = accountNo,
            address = address,
            accountId = queryParam(href, "accountId"),
            itemId = queryParam(href, "itemId"),
            matchedByCode = false,
        )
    }

    private fun queryParam(href: String, key: String): String? {
        val raw = Regex("[?&]" + Regex.escape(key) + "=([^&#]+)")
            .find(href)?.groupValues?.getOrNull(1)
            ?: return null
        return runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
    }

    private fun matchesDorm(address: String, buildingNumber: String, room: String): Boolean {
        val compact = address.replace(" ", "").replace("　", "").uppercase()
        val buildingLabels = listOf(
            "${buildingNumber}楼",
            "${buildingNumber}栋",
            "${buildingNumber}号",
            "北校$buildingNumber",
            "南校$buildingNumber",
        )
        if (buildingLabels.none { compact.contains(it) }) return false

        val roomCompact = room.replace(" ", "").replace("　", "").uppercase()
        if (roomCompact.isBlank()) return false

        // ⚠️ 房号有**多种等价写法**，由 DormRoomNumber 统一展开（`n602` = `N602` = `北602` = `602`）：
        // 缴费系统的地址是后勤手写的，同一间房见过「北校10楼南602」（带中文方位）、
        // 「南校区10栋602」（不带方位）两种。只认房号那一种写法会让用户"明明填对了却查不到电费"。
        //
        // 逐形态匹配的规则保持不变：
        //   · 含字母（如 A114 / N602 / 北602）→ 要求地址里**原样包含**；
        //   · 纯数字（如 602）→ 加数字边界，避免 602 命中 1602。
        return DormRoomNumber.matchForms(room).any { form ->
            val needle = form.uppercase()
            if (needle.any { it.isLetter() }) {
                compact.contains(needle)
            } else {
                Regex("(?<![0-9])" + Regex.escape(needle) + "(?![0-9])").containsMatchIn(compact)
            }
        }
    }
}
