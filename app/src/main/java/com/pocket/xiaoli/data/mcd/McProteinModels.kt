package com.pocket.xiaoli.data.mcd

import com.google.gson.Gson
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * 麦当劳菜单的「每 1 元能买多少克蛋白」榜单。
 *
 * 数据来自开源项目 `hework1110/mcd-protein-index`（MIT · Copyright (c) 2026 Jackson）：
 * 它用麦当劳中国官方 MCP 在同一家店采了「早餐 / 正餐」两套菜单的到店自取价，
 * 并与官方营养成分表对齐。本模块只做**离线搬运与算术**，运行时不联网、不接 MCP Token。
 *
 * ⚠️ 价格随门店与时段浮动，快照只代表它被采样的那一刻（见 [McProteinCatalog.sampledAt]）。
 * 另外官方营养表只有能量/蛋白/脂肪/碳水/钠/钙，没有糖与膳食纤维 —— 所以这里**不做控糖分析**。
 */
enum class McProteinSlot(val wire: String, val label: String) {
    BREAKFAST("breakfast", "早餐"),
    DINNER("dinner", "正餐"),
    ;

    companion object {
        fun fromWire(raw: String?): McProteinSlot? = entries.firstOrNull { it.wire == raw }

        /**
         * 按门店时钟选时段：**10:30 前算早餐**。
         *
         * 这是数据采集时的实测分界（10:30 会切到正餐菜单），不同门店可能差几分钟；
         * 页面允许手动切换，不把这个推断当硬事实。
         */
        fun forClock(hour: Int, minute: Int): McProteinSlot =
            if (hour * 60 + minute < 10 * 60 + 30) BREAKFAST else DINNER
    }
}

data class McProteinItem(
    val name: String,
    val priceYuan: Double,
    val proteinGram: Double,
    val kcal: Int,
    val sodiumMg: Int,
) {
    /** 元/克蛋白。蛋白为 0 的项（理论上不该有）排到最后，不参与除法。 */
    val yuanPerGram: Double
        get() = if (proteinGram > 0) priceYuan / proteinGram else Double.MAX_VALUE
}

data class McProteinCatalog(
    val storeName: String,
    val city: String,
    val sampledAt: String,
    val source: String,
    val license: String,
    private val rankingBySlot: Map<McProteinSlot, List<McProteinItem>>,
) {
    /** 该时段的完整榜单，已按「元/克蛋白」升序。 */
    fun ranking(slot: McProteinSlot): List<McProteinItem> = rankingBySlot[slot].orEmpty()
}

object McProteinCatalogParser {

    fun parse(raw: String): McProteinCatalog? = runCatching {
        val wire = Gson().fromJson(raw, WireCatalog::class.java) ?: return@runCatching null
        val grouped = wire.items.orEmpty()
            .mapNotNull { row ->
                val slot = McProteinSlot.fromWire(row.slot) ?: return@mapNotNull null
                val name = row.name?.trim().orEmpty()
                val price = row.price ?: return@mapNotNull null
                val protein = row.protein ?: return@mapNotNull null
                if (name.isEmpty() || price <= 0 || protein <= 0) return@mapNotNull null
                slot to McProteinItem(
                    name = name,
                    priceYuan = price,
                    proteinGram = protein,
                    kcal = (row.kcal ?: 0).coerceAtLeast(0),
                    sodiumMg = (row.sodium ?: 0).coerceAtLeast(0),
                )
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, list) -> list.sortedBy { it.yuanPerGram } }

        if (grouped.isEmpty()) return@runCatching null
        McProteinCatalog(
            storeName = wire.store?.trim().orEmpty(),
            city = wire.city?.trim().orEmpty(),
            sampledAt = wire.date?.trim().orEmpty(),
            source = wire.source?.trim().orEmpty(),
            license = wire.license?.trim().orEmpty(),
            rankingBySlot = grouped,
        )
    }.getOrNull()

    private class WireCatalog(
        val store: String? = null,
        val city: String? = null,
        val date: String? = null,
        val source: String? = null,
        val license: String? = null,
        val items: List<WireItem>? = null,
    )

    private class WireItem(
        val slot: String? = null,
        val name: String? = null,
        val price: Double? = null,
        val protein: Double? = null,
        val kcal: Int? = null,
        val sodium: Int? = null,
    )
}

/**
 * 「预算内凑够目标蛋白」：不限次数地挑单品，求最省钱的组合。
 *
 * 用**无界背包**而不是贪心：按元/克蛋白从低到高一件件加，遇到"差 2 克蛋白、
 * 再点一份便宜的大件就超预算"时不如多拿一杯牛奶；DP 才能给出真正最省钱的组合。
 *
 * 蛋白按 0.1 克为单位离散化（数据本身是整数克，保留一位是为了以后接小数不再改结构）。
 */
object McProteinPlanner {

    data class Part(val item: McProteinItem, val count: Int)

    data class Plan(
        val parts: List<Part>,
        val totalPriceYuan: Double,
        val totalProteinGram: Double,
        val budgetYuan: Double,
    ) {
        val withinBudget: Boolean get() = totalPriceYuan <= budgetYuan + 1e-6
        val overBudgetYuan: Double get() = (totalPriceYuan - budgetYuan).coerceAtLeast(0.0)
    }

    fun cheapest(items: List<McProteinItem>, targetGram: Double, budgetYuan: Double): Plan? {
        if (targetGram <= 0) return null
        val candidates = items.filter { it.priceYuan > 0 && it.proteinGram > 0 }
        if (candidates.isEmpty()) return null

        val target = ceil(targetGram * 10).toInt().coerceIn(1, 3000)
        val inf = Double.MAX_VALUE / 4
        val cost = DoubleArray(target + 1) { inf }
        val prevTenths = IntArray(target + 1) { -1 }
        val prevItem = IntArray(target + 1) { -1 }
        cost[0] = 0.0

        for (tenths in 1..target) {
            candidates.forEachIndexed { index, item ->
                val protein = (item.proteinGram * 10).roundToInt().coerceAtLeast(1)
                val from = (tenths - protein).coerceAtLeast(0)
                val next = cost[from] + item.priceYuan
                if (next < cost[tenths] - 1e-9) {
                    cost[tenths] = next
                    prevTenths[tenths] = from
                    prevItem[tenths] = index
                }
            }
        }
        if (cost[target] >= inf) return null

        val counts = LinkedHashMap<Int, Int>()
        var cursor = target
        var guard = 0
        while (cursor > 0 && guard++ <= target) {
            val index = prevItem[cursor]
            if (index < 0) return null
            counts[index] = (counts[index] ?: 0) + 1
            cursor = prevTenths[cursor]
        }

        val parts = counts.entries
            .map { Part(candidates[it.key], it.value) }
            .sortedWith(compareBy({ it.item.yuanPerGram }, { it.item.name }))
        return Plan(
            parts = parts,
            totalPriceYuan = parts.sumOf { it.item.priceYuan * it.count },
            totalProteinGram = parts.sumOf { it.item.proteinGram * it.count },
            budgetYuan = budgetYuan,
        )
    }

    /**
     * 结果文案的纯函数（页面与测试共用）。
     *
     * 组合与金额分两行：正餐的组合名很长，挤在一行会把「42 g 蛋白」里的单位折到下一行，
     * 读起来像断句错误。
     */
    fun describe(plan: Plan): String = buildString {
        append(plan.parts.joinToString(" + ") { "${it.count} × ${it.item.name}" })
        append('\n')
        append(String.format(Locale.CHINA, "¥%.2f · %.0f g 蛋白", plan.totalPriceYuan, plan.totalProteinGram))
    }
}
