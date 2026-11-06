package com.pocket.xiaoli.data.electricity

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pocket.xiaoli.data.model.DormInfo
import java.time.LocalDate
import java.time.ZoneId

/**
 * 电费余额的**本机每日采样**存储 —— [usageStatsOf] 的原料从这里来。
 *
 * ## 为什么必须自己存
 *
 * 后勤缴费系统只给当前余额，没有任何历史接口（见 [DormElectricityRepository] 的字段说明）。
 * 想要"用了多少 / 还能用几天"，唯一的诚实来源就是**这台设备上自己攒的读数**。
 *
 * ## 三条硬约束
 *
 * 1. **只存本机**：这是一个 SharedPreferences 文件，不上传、不进日志、不进崩溃包。
 *    电费余额属于"这间宿舍"的财务信息，虽然敏感度不高，但没有理由出门。
 * 2. **按认人键分开存**：键用 [DormInfo.balanceIdentity]（校区 + 楼栋 + 房间 + 宿舍编号）。
 *    换宿舍之后的采样绝不能和旧宿舍连成一条线 —— 那会把别人的用电曲线画给他，
 *    也会让"还能用几天"完全失真。这一条与首页低余额提醒用的是同一个键，两处必须一致。
 * 3. **按天只留一条**：一天里查十次也只算一个读数（取最后一次），
 *    否则图上会出现一堆同日的点，日均的分母也会被"天"数骗过。
 *
 * 保留窗口 [RETENTION_DAYS] 天：再往前的采样对"最近用多少"没意义，
 * 而一个人的大学很长 —— 不设上限就是往本机里无限堆财务读数。
 */
class ElectricityUsageStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val gson = Gson()

    /** 今天（设备本地时区）—— 与 [record] 用同一个时区口径，避免跨天判定漂移。 */
    private fun todayEpochDay(): Long = LocalDate.now(ZoneId.systemDefault()).toEpochDay()

    /**
     * 记一条采样。
     *
     * @param rawBalance 上游给的余额原文（字符串）。**解析不出来就直接不记** ——
     *   存一条猜出来的数字比少一条记录危险得多：它会进入日均、进入"还能用几天"。
     * @return 是否写入了一条新/更新的采样
     */
    fun record(dorm: DormInfo, rawBalance: String?): Boolean {
        val balance = parseBalance(rawBalance) ?: return false
        if (!dorm.isBound) return false
        val key = dorm.balanceIdentity
        val day = todayEpochDay()
        return synchronized(lock) {
            val all = readAll()
            val series = (all[key] ?: mutableListOf()).toMutableList()
            // 同一天覆盖，不追加：一天只算一个读数
            val trimmed = series.filter { it.epochDay != day }.toMutableList()
            trimmed += BalanceSample(day, balance)
            val cut = day - RETENTION_DAYS
            all[key] = trimmed.filter { it.epochDay >= cut }.sortedBy { it.epochDay }.toMutableList()
            writeAll(all)
            true
        }
    }

    /** 这间宿舍的全部采样（按天升序）。没有记录时是空表。 */
    fun samples(dorm: DormInfo?): List<BalanceSample> {
        if (dorm == null) return emptyList()
        return readAll()[dorm.balanceIdentity]?.sortedBy { it.epochDay } ?: emptyList()
    }

    /** 清空这间宿舍的记录（用户在统计卡上主动点「清空」）。 */
    fun clear(dorm: DormInfo) {
        synchronized(lock) {
            val all = readAll()
            all.remove(dorm.balanceIdentity)
            writeAll(all)
        }
    }

    /**
     * 读全部采样。坏数据（解析失败、余额不是有限数）一律丢掉而不是将就用 ——
     * 采样会进日均、进"还能用几天"，一条脏数据就是把一个可信的错数字摆到用户面前。
     */
    private fun readAll(): MutableMap<String, MutableList<BalanceSample>> {
        val raw = prefs.getString(KEY_SAMPLES, null) ?: return mutableMapOf()
        val decoded: MutableMap<String, MutableList<BalanceSample>>? = runCatching {
            gson.fromJson<MutableMap<String, MutableList<BalanceSample>>>(
                raw,
                object : TypeToken<MutableMap<String, MutableList<BalanceSample>>>() {}.type,
            )
        }.getOrNull()
        return decoded
            ?.mapNotNull { (key, series) ->
                val clean = series.orEmpty().filter { it.balance.isFinite() && it.epochDay > 0L }
                if (clean.isEmpty()) null else key to clean.toMutableList()
            }
            ?.toMap()
            ?.toMutableMap()
            ?: mutableMapOf()
    }

    private fun writeAll(all: Map<String, List<BalanceSample>>) {
        prefs.edit().putString(KEY_SAMPLES, gson.toJson(all)).apply()
    }

    companion object {

        private val lock = Any()

        private const val PREFS = "pocket_electricity_usage"
        private const val KEY_SAMPLES = "samples"

        /** 只保留最近这么多天的采样。 */
        const val RETENTION_DAYS = 120

        /**
         * 把上游的余额原文读成数字。
         *
         * 容忍「21.86 元」「 21.86」与千分位，但**不做任何猜测**：
         * 读不出来就是 null，宁可少一条记录。
         */
        fun parseBalance(raw: String?): Double? = raw
            ?.trim()
            ?.replace("元", "")
            ?.replace(",", "")
            ?.replace(" ", "")
            ?.toDoubleOrNull()
            ?.takeIf { it.isFinite() }
    }
}
