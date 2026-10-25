package com.pocket.xiaoli.data.contribution

import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 共建榜的 HTTP 客户端。
 *
 * 与 [com.pocket.xiaoli.telemetry.TelemetryUploader] 一样，**它不管开关、不管队列**，
 * 只回答一个问题：这条请求还能不能再试。
 *
 * 状态码口径：
 * - 2xx → 收下了
 * - 4xx（含 429 每日上限）→ 丢掉，今天再发也不会成功
 * - 5xx / 断网 / 超时 → 留着下次再发
 */
class ContributionClient(
    private val baseUrl: String,
    private val appKey: String,
    private val client: OkHttpClient = defaultClient(),
    private val gson: Gson = Gson(),
) {

    val configured: Boolean get() = baseUrl.isNotBlank() && appKey.isNotBlank()

    enum class Delivery { ACCEPTED, DROP, RETRY }

    fun report(cid: String, nickname: String, action: ContributionAction, eid: String): Delivery {
        if (!configured) return Delivery.RETRY
        val request = Request.Builder()
            .url(url(PATH_REPORT))
            .header(KEY_HEADER, appKey)
            .post(reportBody(cid, nickname, action, eid).toRequestBody(JSON))
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> Delivery.ACCEPTED
                    response.code in 400..499 -> Delivery.DROP
                    else -> Delivery.RETRY
                }
            }
        } catch (_: Exception) {
            Delivery.RETRY
        }
    }

    /** 拉不到就返回 null —— 由仓库决定「用缓存」还是「显示空态」。 */
    fun leaderboard(
        category: ContributionCategory,
        period: ContributionPeriod,
        cid: String?,
        nowMs: Long = System.currentTimeMillis(),
    ): ContributionBoard? {
        if (!configured) return null
        val request = Request.Builder()
            .url(url(leaderboardPath(category, period, cid)))
            .header(KEY_HEADER, appKey)
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val raw = response.body?.string().orEmpty()
                parseBoard(raw, category, period, nowMs)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun leave(cid: String): Boolean {
        if (!configured) return false
        val body = JsonObject().apply { addProperty("cid", cid) }.toString()
        val request = Request.Builder()
            .url(url(PATH_LEAVE))
            .header(KEY_HEADER, appKey)
            .post(body.toRequestBody(JSON))
            .build()
        return try {
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    /** 改名只影响榜单上显示的名字，不产生新的记录。失败也不影响本地下次上报时带上新名字。 */
    fun rename(cid: String, nickname: String): Boolean {
        if (!configured) return false
        val body = JsonObject().apply {
            addProperty("cid", cid)
            addProperty("nickname", nickname)
        }.toString()
        val request = Request.Builder()
            .url(url(PATH_NICKNAME))
            .header(KEY_HEADER, appKey)
            .post(body.toRequestBody(JSON))
            .build()
        return try {
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    // ── 纯函数（可单测）────────────────────────────────────────────────

    internal fun reportBody(cid: String, nickname: String, action: ContributionAction, eid: String): String =
        JsonObject().apply {
            addProperty("v", 1)
            addProperty("cid", cid)
            addProperty("nickname", nickname)
            addProperty("eid", eid)
            addProperty("action", action.wire)
        }.toString()

    internal fun leaderboardPath(
        category: ContributionCategory,
        period: ContributionPeriod,
        cid: String?,
    ): String = buildString {
        append(PATH_LEADERBOARD)
        append("?category=").append(category.wire)
        append("&period=").append(period.wire)
        append("&limit=").append(LIMIT)
        if (!cid.isNullOrBlank()) append("&cid=").append(cid)
    }

    internal fun parseBoard(
        raw: String,
        category: ContributionCategory,
        period: ContributionPeriod,
        nowMs: Long,
    ): ContributionBoard? = runCatching {
        val wire = gson.fromJson(raw, WireBoard::class.java) ?: return@runCatching null
        val entries = wire.rows.orEmpty().mapNotNull { row ->
            val name = row.nickname?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            ContributionEntry(rank = 0, nickname = name, mine = row.mine == true)
        }
        val me = wire.me?.let { m ->
            val name = m.nickname?.trim().orEmpty()
            if (name.isEmpty()) null else ContributionMe(name)
        }
        // 服务端已按昵称排好；这里再排一次见 [ContributionOrder] 的 KDoc（旧缓存 / 旧服务端）。
        val sorted = ContributionOrder.sortByName(entries)
        ContributionBoard(
            category = category,
            period = period,
            entries = sorted,
            me = me,
            fetchedAt = nowMs,
            // 旧服务端不带 total：至少不能比看到的列表还小。
            total = (wire.total ?: 0).coerceAtLeast(sorted.size),
        )
    }.getOrNull()

    private fun url(path: String): String = baseUrl.trimEnd('/') + path

    private class WireBoard(
        val rows: List<WireRow>? = null,
        val me: WireMe? = null,
        val total: Int? = null,
    )

    /** 只认昵称与 `mine`；`rank` / `points` 即使还在响应里也不再解读。 */
    private class WireRow(
        val nickname: String? = null,
        val mine: Boolean? = null,
    )

    private class WireMe(
        val nickname: String? = null,
    )

    companion object {
        const val PATH_REPORT = "/v1/contribution/report"
        const val PATH_LEADERBOARD = "/v1/contribution/leaderboard"
        const val PATH_LEAVE = "/v1/contribution/leave"
        const val PATH_NICKNAME = "/v1/contribution/nickname"
        const val LIMIT = 50

        private const val KEY_HEADER = "X-App-Key"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
