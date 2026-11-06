package com.pocket.xiaoli.ui.bus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocket.xiaoli.core.bus.BusArrivalRules
import com.pocket.xiaoli.core.bus.BusArrivalTone
import com.pocket.xiaoli.data.bus.BusLine
import com.pocket.xiaoli.data.bus.BusRepository
import com.pocket.xiaoli.data.bus.BusStation
import com.pocket.xiaoli.ui.components.AppCard
import com.pocket.xiaoli.ui.components.MiuixChoice
import com.pocket.xiaoli.ui.components.MiuixChoiceRow
import com.pocket.xiaoli.ui.components.MiuixSettingsScaffold
import com.pocket.xiaoli.ui.components.SectionCaption
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.PocketXiaoliColors
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「公交到站」：本校站点上每条线路下一班车还有几站。
 *
 * 形态照参考项目 5151561/Trackr 的「站点详情 · 一起等多条线路」那一屏做，
 * 数据只走掌上公交明文接口（[BusRepository]）。
 *
 * 与 [com.pocket.xiaoli.ui.library.LibraryScreen] 同样的分层：这一层负责把仓库的
 * [BusRepository.State] 变成一份**只含展示值**的 [BusArrivalUi]，
 * [BusArrivalContent] 只吃数据、只吐回调 —— 快照测试因此能凭空造出一屏，
 * 不需要网络、也不许读真时钟。
 */
@Composable
internal fun BusArrivalScreen(
    repository: BusRepository,
    onBack: () -> Unit,
) {
    val state by repository.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { repository.load() }

    BusArrivalContent(
        ui = BusArrivalUi(
            stationName = state.stationName,
            stations = state.stations,
            lines = state.lines,
            loading = state.loading,
            error = state.error,
            updatedText = BusRepository.capturedLabel(state.capturedAtMillis),
            fromNetwork = state.fromNetwork,
        ),
        onSelectStation = { name -> scope.launch { repository.selectStation(name) } },
        onRefresh = { scope.launch { repository.load(force = true) } },
        onBack = onBack,
    )
}

/**
 * 一屏的全部可见状态。
 *
 * ⚠️ [updatedText] 是**已经格式化好的字符串**，不是时间戳：
 * 基准图里不许出现会随每次运行漂的值（`memory/04-ui-verification.md` §八·补）。
 */
internal data class BusArrivalUi(
    val stationName: String = "",
    val stations: List<BusStation> = emptyList(),
    val lines: List<BusLine> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val updatedText: String = "",
    /** true = 刚联网拉的；false = 上次缓存或还没成功过，文案要跟着改口。 */
    val fromNetwork: Boolean = false,
)

@Composable
internal fun BusArrivalContent(
    ui: BusArrivalUi,
    onSelectStation: (String) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current

    MiuixSettingsScaffold(
        title = "公交到站",
        onBack = onBack,
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "刷新到站信息",
                    tint = colors.textSecondary,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MiuixTheme.colorScheme.surface)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = skin.spaceLg, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(skin.spaceWide),
        ) {
            StationBlock(ui = ui, onSelectStation = onSelectStation)

            when {
                ui.loading && ui.lines.isEmpty() -> InfoCard(
                    title = "正在查询实时公交…",
                    body = "这一页只查一次接口、不申请定位权限：站点距离按学校中心点算，" +
                        "查到的到站信息会留在本机，下次离线也能先看上次的。",
                )

                ui.lines.isEmpty() -> InfoCard(
                    title = if (ui.error != null) "暂时没有到站信息" else "这个站没查到线路",
                    body = "线路与站点由掌上公交按站名现查。换附近的站点看看，" +
                        "或者过一会儿再刷新 —— 末班车收完之后这里就是空的。",
                )

                else -> ui.lines.forEach { line ->
                    LineCard(line)
                }
            }

            Footer(ui)
        }
    }
}

/**
 * 站名 + 附近站切换。
 *
 * 附近站列表没拿到时只画当前站名 —— 不画一排只有一个是真的的假 chip。
 */
@Composable
private fun StationBlock(
    ui: BusArrivalUi,
    onSelectStation: (String) -> Unit,
) {
    val colors = LocalPocketXiaoliColors.current
    val skin = LocalSkin.current
    Column(verticalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = ui.stationName,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(skin.spaceSmall))
            if (ui.updatedText.isNotBlank()) {
                Text(
                    text = ui.updatedText,
                    style = MiuixTheme.textStyles.footnote2,
                    color = colors.textTertiary,
                )
            }
        }
        if (ui.stations.size > 1) {
            SectionCaption("附近站点 · 到校中心直线距离")
            MiuixChoiceRow(
                choices = ui.stations.map { station ->
                    MiuixChoice(
                        id = station.name,
                        label = station.name + BusArrivalRules.distanceText(station.distanceMeters)
                            .let { if (it.isEmpty()) "" else " · $it" },
                    )
                },
                selectedId = ui.stationName,
                onSelect = { id -> id?.let(onSelectStation) },
            )
        }
    }
}

/**
 * 一条线路一张卡：左边线路号 + 方向与首末班，右边到站档位。
 *
 * ⚠️ 右侧文字**只说上游报的东西**：能等到的是「N 站 / 已到站」，
 * 等不到的是「等待发车 / 未开通实时 / 停运 / 暂无实时」，
 * 没有分钟数 —— 上游不给秒级 ETA，编一个就是把预测说成实时。
 */
@Composable
private fun LineCard(line: BusLine) {
    val colors = LocalPocketXiaoliColors.current
    val skin = LocalSkin.current
    val arrival = line.arrival
    val tone = BusArrivalRules.toneOf(arrival)
    val toneColor = toneColor(tone, colors)

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(skin.radiusChip))
                    .background(toneColor.copy(alpha = if (tone == BusArrivalTone.MUTED) 0.10f else 0.16f))
                    // 线路名有「67路文登整骨区间」这种长名：不限宽就会把标题与右侧档位挤没。
                    // 但**不能截断** —— 「67路文登整…」看不出是哪条区间车，等错车是要出事的，
                    // 所以限宽之后允许它自己折成两行。
                    .widthIn(max = 92.dp)
                    .padding(horizontal = 9.dp, vertical = 6.dp),
            ) {
                Text(
                    text = line.lineName,
                    style = MiuixTheme.textStyles.footnote1,
                    color = toneColor,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                )
            }
            Spacer(Modifier.width(skin.spaceSmall))
            Column(Modifier.weight(1f)) {
                Text(
                    text = line.directionText.ifBlank { "开往终点方向" },
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                // 元信息只有首末班与票价两段。上游那句 `车辆 开往 蓝色智谷` 不显示，
                // 理由写在 BusArrival.StopsAway 的注释上：它和标题的「开往 烟台人才市场」
                // 会在同一张卡里打架，而本机没法核实它到底指什么。
                if (line.metaText.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = line.metaText,
                        style = MiuixTheme.textStyles.footnote2,
                        color = colors.textSecondary,
                        maxLines = 2,
                    )
                }
                if (line.nextBusText.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "下一班 ${line.nextBusText}",
                        style = MiuixTheme.textStyles.footnote2,
                        color = colors.textTertiary,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.width(skin.spaceSm))
            Text(
                text = BusArrivalRules.chipText(arrival),
                style = MiuixTheme.textStyles.body1,
                color = toneColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                // 「未开通实时」这类说明比「8 站」长得多，给个下限宽度让它们别挤标题；
                // 线路名 chip 同时限宽，长名（67路文登整骨区间）自己截断
                modifier = Modifier.widthIn(min = 62.dp),
            )
        }
    }
}

private fun toneColor(tone: BusArrivalTone, colors: PocketXiaoliColors): Color = when (tone) {
    BusArrivalTone.IMMEDIATE -> colors.warning
    BusArrivalTone.NORMAL -> colors.accentMain
    BusArrivalTone.MUTED -> colors.textTertiary
}

/**
 * 底部两行：状态说明 + 数据源。
 *
 * ⚠️ 第一行**只出现一次**：错误文案本身已经说了「上次刷新到的到站信息」，
 * 再补一行「离线显示：更新于 …」就是同一件事说两遍（复述性小字）。
 */
@Composable
private fun Footer(ui: BusArrivalUi) {
    val colors = LocalPocketXiaoliColors.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val status = when {
            ui.error != null -> ui.error
            !ui.fromNetwork && ui.lines.isNotEmpty() && ui.updatedText.isNotBlank() ->
                "离线显示：${ui.updatedText} 刷新到的到站信息，实时状态以现场为准"
            else -> null
        }
        status?.let { text -> FootnoteLine(text, if (ui.error != null) colors.warning else colors.textTertiary) }
        FootnoteLine(
            text = "到站信息来自掌上公交公开接口，仅供参考；线路与首末班以站牌为准",
            color = colors.textTertiary,
        )
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun FootnoteLine(text: String, color: Color) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote2,
        color = color,
    )
}

@Composable
private fun InfoCard(
    title: String,
    body: String,
) {
    val colors = LocalPocketXiaoliColors.current
    val skin = LocalSkin.current
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = skin.spaceMd)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = colors.accentMain,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(skin.spaceXs))
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = body,
                style = MiuixTheme.textStyles.footnote1,
                color = colors.textSecondary,
            )
        }
    }
}
