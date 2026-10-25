package com.pocket.xiaoli.ui.treasurebox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.mcd.McProteinCatalog
import com.pocket.xiaoli.data.mcd.McProteinCatalogParser
import com.pocket.xiaoli.data.mcd.McProteinItem
import com.pocket.xiaoli.data.mcd.McProteinPlanner
import com.pocket.xiaoli.data.mcd.McProteinSlot
import com.pocket.xiaoli.ui.components.AppCard
import com.pocket.xiaoli.ui.components.PocketField
import com.pocket.xiaoli.ui.components.ServiceEmptyCard
import com.pocket.xiaoli.ui.components.ServiceHeroCard
import com.pocket.xiaoli.ui.components.ServiceHeroChip
import com.pocket.xiaoli.ui.components.ServicePageTopBar
import com.pocket.xiaoli.ui.components.ServiceSectionLabel
import com.pocket.xiaoli.ui.components.campusui.CampusSegmentedTabs
import com.pocket.xiaoli.ui.theme.LocalDarkMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.ServiceAccent
import com.pocket.xiaoli.ui.theme.serviceAccentColor
import java.time.LocalTime
import java.util.Locale

/**
 * 「麦当劳蛋白榜」：把官方菜单的价格与蛋白质对齐，按**元/克蛋白**排序。
 *
 * ## 数据从哪来（为什么不实时）
 *
 * 数据是开源项目 `hework1110/mcd-protein-index`（MIT）在 2026-10-24 采的一份快照：
 * 同一家店、早餐/正餐两套菜单、官方到店自取价 + 官方营养表。放进 assets 是为了
 * **离线可用、无 Token、无网络依赖**；页面底部写清采样门店与日期。
 *
 * 如果以后要接官方 MCP 实时价，只需要把 `McProteinCatalog` 的来源换掉，页面不用动。
 *
 * ## 三块内容
 *
 * 1. **榜单**：元/克蛋白升序，只排单品（套餐要逐项相加，容易低估）；
 * 2. **预算凑蛋白**：无界背包求"预算内最省钱地吃够目标蛋白"；
 * 3. **时段**：按门店时钟 10:30 自动选早餐/正餐，可手动切（不同门店可能差几分钟）。
 */
@Composable
fun McProteinIndexPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val catalog = remember {
        runCatching {
            context.assets.open("mcd_protein.json").bufferedReader().use { it.readText() }
        }.getOrNull()?.let(McProteinCatalogParser::parse)
    }
    val initialSlot = remember {
        val now = LocalTime.now()
        McProteinSlot.forClock(now.hour, now.minute)
    }
    McProteinIndexContent(catalog = catalog, initialSlot = initialSlot, onBack = onBack)
}

@Composable
internal fun McProteinIndexContent(
    catalog: McProteinCatalog?,
    initialSlot: McProteinSlot,
    onBack: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    // 键上 initialSlot：快照测试会在同一组合里换时段/主题，不键住的话会一直停在第一次的选择。
    var slot by remember(initialSlot) { mutableStateOf(initialSlot) }

    Scaffold(
        topBar = { ServicePageTopBar(title = "麦当劳蛋白榜", onBack = onBack) },
        containerColor = colors.surfaceBase,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = skin.spaceLg,
                end = skin.spaceLg,
                top = skin.spaceSm,
                bottom = skin.spaceXxl,
            ),
            verticalArrangement = Arrangement.spacedBy(skin.spaceMd),
        ) {
            if (catalog == null) {
                item(key = "empty") {
                    ServiceEmptyCard(
                        icon = Icons.Outlined.Fastfood,
                        title = "榜单数据没带进来",
                        message = "安装包里的 mcd_protein.json 缺失或格式不对，重装一次试试。",
                        accent = ServiceAccent.LIFE,
                    )
                }
                return@LazyColumn
            }

            item(key = "hero") { Hero(catalog) }

            item(key = "slot") {
                Column {
                    CampusSegmentedTabs(
                        options = McProteinSlot.entries.map { it.label to it },
                        selected = slot,
                        onSelect = { slot = it },
                        scrollable = false,
                    )
                    Spacer(Modifier.height(skin.spaceMicro))
                    Text(
                        "按当前时间自动选中；10:30 前算早餐（不同门店可能差几分钟）",
                        fontSize = 11.sp,
                        color = colors.textTertiary,
                    )
                }
            }

            item(key = "planner") { PlannerCard(items = catalog.ranking(slot)) }

            item(key = "section") {
                ServiceSectionLabel(
                    title = "元/克蛋白 · 越前越划算",
                    accent = ServiceAccent.LIFE,
                )
            }

            item(key = "ranking") { RankingCard(items = catalog.ranking(slot)) }

            item(key = "footer") { Footer(catalog) }
        }
    }
}

@Composable
private fun Hero(catalog: McProteinCatalog) {
    val skin = LocalSkin.current
    ServiceHeroCard(accent = ServiceAccent.LIFE) {
        Text(
            "每 1 元，能买多少克蛋白？",
            fontSize = 21.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
        )
        Spacer(Modifier.height(skin.spaceXs))
        Text(
            "同一家店凑 27 g 蛋白：早餐 ¥18，正餐 ¥23。",
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = Color.White.copy(alpha = 0.9f),
        )
        Spacer(Modifier.height(skin.spaceSm))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ServiceHeroChip(catalog.storeName)
            ServiceHeroChip(catalog.sampledAt)
        }
    }
}

@Composable
private fun PlannerCard(items: List<McProteinItem>) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    var budgetText by remember { mutableStateOf("40") }
    var targetText by remember { mutableStateOf("40") }

    val budget = budgetText.toDoubleOrNull()
    val target = targetText.toDoubleOrNull()
    val plan = remember(items, budget, target) {
        if (budget == null || target == null) null
        else McProteinPlanner.cheapest(items, target, budget)
    }

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(skin.cardPaddingLarge)) {
            Text(
                "预算凑蛋白",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "同一件可以点多份；按下面榜单里的快照价算。",
                fontSize = 11.sp,
                color = colors.textTertiary,
            )
            Spacer(Modifier.height(skin.spaceSm))
            Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                PocketField(
                    label = "预算（元）",
                    value = budgetText,
                    onValueChange = { budgetText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    placeholder = "40",
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                PocketField(
                    label = "目标蛋白（g）",
                    value = targetText,
                    onValueChange = { targetText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    placeholder = "40",
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
            Spacer(Modifier.height(skin.spaceSm))

            if (plan == null) {
                Text(
                    "填一个预算和目标，我算最省钱的组合。",
                    fontSize = 12.sp,
                    color = colors.textTertiary,
                )
            } else {
                Text(
                    McProteinPlanner.describe(plan),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(4.dp))
                if (plan.withinBudget) {
                    Text(
                        String.format(
                            Locale.CHINA,
                            "在预算内，还剩 ¥%.2f",
                            (plan.budgetYuan - plan.totalPriceYuan).coerceAtLeast(0.0),
                        ),
                        fontSize = 11.sp,
                        color = colors.success,
                    )
                } else {
                    Text(
                        String.format(Locale.CHINA, "最低要 ¥%.2f，超出预算 ¥%.2f", plan.totalPriceYuan, plan.overBudgetYuan),
                        fontSize = 11.sp,
                        color = colors.danger,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "按 ${items.size} 个单品算；实际价格以门店为准。",
                fontSize = 10.sp,
                color = colors.textTertiary,
            )
        }
    }
}

@Composable
private fun RankingCard(items: List<McProteinItem>) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val isDark = LocalDarkMode.current
    val tint = serviceAccentColor(ServiceAccent.LIFE)

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            items.forEachIndexed { index, item ->
                val rank = index + 1
                val top = rank <= 3
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (top) tint.copy(alpha = if (isDark) 0.16f else 0.08f) else Color.Transparent)
                        .padding(horizontal = skin.spaceMd, vertical = skin.spaceSm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        rank.toString(),
                        modifier = Modifier.width(24.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (top) tint else colors.textTertiary,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.name,
                            fontSize = 14.sp,
                            fontWeight = if (top) FontWeight.Bold else FontWeight.Medium,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            String.format(
                                Locale.CHINA,
                                "¥%.1f · %.0f g 蛋白 · %d kcal",
                                item.priceYuan,
                                item.proteinGram,
                                item.kcal,
                            ),
                            fontSize = 11.sp,
                            color = colors.textTertiary,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            String.format(Locale.CHINA, "¥%.2f", item.yuanPerGram),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (top) tint else colors.textSecondary,
                        )
                        Text("/ 克蛋白", fontSize = 10.sp, color = colors.textTertiary)
                    }
                }
                if (rank != items.size) {
                    HorizontalDivider(
                        color = colors.borderDivider,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = skin.spaceMd),
                    )
                }
            }
        }
    }
}

@Composable
private fun Footer(catalog: McProteinCatalog) {
    val colors = LocalPocketXiaoliColors.current
    Text(
        "只排单品：套餐要把子项相加，容易低估，所以不排。\n" +
            "价格是 ${catalog.storeName} · ${catalog.sampledAt} 的到店自取价，会随门店与时段变化。\n" +
            "数据来自 ${catalog.source}（${catalog.license}）。本页只做算术，不构成健康、医疗或减重建议。",
        fontSize = 11.sp,
        lineHeight = 17.sp,
        color = colors.textTertiary,
    )
}
