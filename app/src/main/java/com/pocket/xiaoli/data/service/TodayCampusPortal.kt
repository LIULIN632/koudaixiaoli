package com.pocket.xiaoli.data.service

import android.webkit.CookieManager
import com.google.gson.Gson
import com.pocket.xiaoli.network.CookieStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/** 门户里的一项服务。来自门户自己的服务列表，**不是**我们写死的清单。 */
data class PortalService(
    val appId: String,
    val name: String,
    val category: String? = null,
)

/** 门户状态快照：登录态 + 当前账号能看到的服务。 */
data class PortalSnapshot(
    val loggedIn: Boolean,
    val services: List<PortalService>,
)

/**
 * 今日校园（智慧校园门户 `ehall.yitsd.edu.cn`）的**只读**状态接口。
 *
 * ## 它解决了什么（2026-09-28 校外实测）
 *
 * 之前 `docs/ServiceAudit.md` 的结论是：「7 项校园服务需要登录态，公网匿名探测**拿不到
 * serviceId / 深层 URL**，不伪造深层入口」——所以「今日校园」只能把用户丢到门户首页自己找。
 *
 * 实测发现**不需要在校内也能拿到机制**，只是要在**登录之后**去问门户自己：
 *
 * | 接口 | 返回 | 用途 |
 * |---|---|---|
 * | `jsonp/serviceCenterData.json` | `{"searchResult":[{appId,appName,categoryList,…}],"hasLogin":bool}` | 登录态 + 该账号能用的服务 |
 * | `new/mobileAppShow?appId=<appId>` | 302 到统一认证，登录后直达该服务 | 服务直达地址 |
 *
 * 两个都是**门户自己下发**的：服务清单会随学校调整自动更新，我们不需要手抄 7 个深层 URL，
 * 也不会因为学校改了菜单而变成死链。未登录时 `hasLogin=false`，此时**不展示服务列表**
 * （匿名只返回「学生请销假 / 站群管理」这类与同学无关的条目）。
 *
 * ## 数据来源的可验证性
 *
 * `TodayCampusPortalTest` 里用的就是**那天抓下来的真实响应体**（逐字节），
 * 不是按想象编的样本 —— 这类解析最怕「照着自己猜的格式写测试」，测过了照样在真机上不对。
 */
object TodayCampusPortal {

    /** 门户源。与 `CampusConfig` 的 `SMART_CAMPUS` 同源（那里是 `/new/index.html`）。 */
    const val ORIGIN = "http://ehall.yitsd.edu.cn"

    private const val SERVICE_CENTER_URL = "$ORIGIN/jsonp/serviceCenterData.json"
    private const val CACHE_TTL_MS = 60 * 1000L

    private val gson = Gson()

    /**
     * 服务直达地址。
     *
     * 校外实测：未登录时 302 到 `authserver.yitsd.edu.cn`，登录后直接进该服务 ——
     * 也就是说它天然是「未登录先登录、登录完回原目标」的 CAS 语义，App 不需要自己拼登录跳转。
     */
    fun serviceUrl(appId: String): String = "$ORIGIN/new/mobileAppShow?appId=$appId"

    private var cached: PortalSnapshot? = null
    private var cachedAt = 0L

    /** 内存缓存快照；过期或没拉过时返回 null（切页回来不必重复打接口）。 */
    fun cachedSnapshot(): PortalSnapshot? {
        val snapshot = cached ?: return null
        if (System.currentTimeMillis() - cachedAt > CACHE_TTL_MS) return null
        return snapshot
    }

    fun clearCache() {
        cached = null
        cachedAt = 0L
    }

    /**
     * 在门户的服务清单里按名字找一项。
     *
     * 用途：把「校园卡」接到门户里的**易校园**入口上 —— 用户没装易校园 App 时，
     * 与其把他丢到易校园官网（那里办不了任何事），不如直接进门户里那条已联邦认证的入口。
     *
     * 只认**已登录**的清单：未登录时门户只返回与同学无关的管理条目，按名字找会找错。
     */
    suspend fun findApp(vararg nameHints: String): PortalService? {
        val snapshot = cachedSnapshot() ?: load().getOrNull() ?: return null
        if (!snapshot.loggedIn) return null
        val hints = nameHints.filter { it.isNotBlank() }
        if (hints.isEmpty()) return null
        return snapshot.services.firstOrNull { service ->
            hints.any { hint -> service.name.contains(hint, ignoreCase = true) }
        }
    }

    /**
     * 取门户当前登录态的 cookie。
     *
     * 先问 WebView（用户刚在内置网页里登录过），再退回 [CookieStore] 落盘的那份
     * —— 部分门户只下发会话 cookie，进程重启后 WebView 会丢，落盘那份是兜底。
     */
    fun currentCookie(): String? {
        val live = runCatching { CookieManager.getInstance().getCookie(ORIGIN) }.getOrNull()
        return live?.takeIf { it.isNotBlank() } ?: CookieStore.getServiceCookies(ORIGIN)
    }

    suspend fun load(forceRefresh: Boolean = false): Result<PortalSnapshot> =
        withContext(Dispatchers.IO) {
            try {
                if (!forceRefresh) {
                    cachedSnapshot()?.let { return@withContext Result.success(it) }
                }
                val builder = Request.Builder().url(SERVICE_CENTER_URL).get()
                currentCookie()?.let { builder.header("Cookie", it) }
                client.newCall(builder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            IllegalStateException("门户返回 HTTP ${response.code}")
                        )
                    }
                    val body = response.body?.string().orEmpty()
                    val snapshot = parse(body)
                        ?: return@withContext Result.failure(
                            IllegalStateException("门户返回了无法解析的内容")
                        )
                    cached = snapshot
                    cachedAt = System.currentTimeMillis()
                    Result.success(snapshot)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // ── 解析 ──
    //
    // 所有字段都收成可空：Gson 反序列化不走 Kotlin 默认值，JSON 里没有的字段在 release 包里
    // 就是 null（`CLAUDE.md` 里那条"给 JSON 类加字段"的教训）。字段名照抄门户的真实响应，
    // 见 TodayCampusPortalTest 里那份逐字节抓下来的样本。

    internal data class ResponseDto(
        val searchResult: List<AppDto>? = null,
        val hasLogin: Boolean? = null,
    )

    internal data class AppDto(
        val appId: String? = null,
        val appName: String? = null,
        val categoryList: List<CategoryDto>? = null,
        /** 门户的"是否上架"标记；null（字段缺失）与 true 都当作可用。 */
        val opening: Boolean? = null,
    )

    internal data class CategoryDto(val categoryName: String? = null)

    /** 纯函数，便于单测；内容不是 JSON 或结构完全对不上时返回 null。 */
    internal fun parse(body: String): PortalSnapshot? = runCatching {
        val dto = gson.fromJson(body, ResponseDto::class.java) ?: return null
        PortalSnapshot(
            loggedIn = dto.hasLogin == true,
            services = dto.searchResult.orEmpty().mapNotNull { app ->
                val id = app.appId?.trim().orEmpty()
                val name = app.appName?.trim().orEmpty()
                if (id.isBlank() || name.isBlank()) return@mapNotNull null
                if (app.opening == false) return@mapNotNull null
                PortalService(
                    appId = id,
                    name = name,
                    category = app.categoryList
                        ?.firstNotNullOfOrNull { it.categoryName?.trim() }
                        ?.takeIf { it.isNotBlank() },
                )
            },
        )
    }.getOrNull()
}
