package com.pocket.xiaoli.ui.import_

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocket.xiaoli.data.model.LastImportInfo
import com.pocket.xiaoli.di.AppContainer
import com.pocket.xiaoli.ui.components.AppCard
import com.pocket.xiaoli.ui.components.uniqueKeys
import com.pocket.xiaoli.ui.theme.LocalDarkMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「导入记录」：最近 20 次导入的结果。
 *
 * ## 2026-10 改版
 *
 * 旧实现是一列 `Surface` 卡片，每张卡三行灰字：来源 / 结果 / 时间。问题：
 * 1. 成功与失败只靠一枚小图标区分，扫一眼整页看不出来；
 * 2. 失败原因和「成功导入 N 门课程」是同一层级、同一字号 —— 用户得逐行读；
 * 3. 空态只有一句「还没有导入记录」，没有下一步动作。
 *
 * 现在与入口页的「最近一次」卡同一套语言：状态色图标块 + 成功/失败小标签，
 * 成功行显示课程数，失败行直接显示原因；空态给「去导入课表」。
 *
 * ⚠️ 记录的读取已在 `AppContainer.loadImportHistory()` 里过 `sanitized()`
 * （Gson 反射赋值可能让 `source` 真的是 null），这里不再重复清洗。
 */
@Composable
fun ImportHistoryScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val history by container.importHistory.collectAsStateWithLifecycle()
    ImportHistoryPage(
        history = history,
        onBack = onBack,
        onClear = container::clearImportHistory,
    )
}

/** 页面正文（与 `AppContainer` 解耦，只为能单独截图与测语义）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImportHistoryPage(
    history: List<LastImportInfo>,
    onBack: () -> Unit,
    onClear: () -> Unit,
) {
    val colors = LocalPocketXiaoliColors.current
    val skin = LocalSkin.current
    var showClearConfirm by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = colors.surfaceBase,
        topBar = {
            TopAppBar(
                title = { Text("导入记录", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirm = true }) {
                            Icon(Icons.Outlined.DeleteOutline, "清空记录", tint = colors.textTertiary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surfaceCard)
            )
        }
    ) { padding ->
        if (history.isEmpty()) {
            EmptyHistory(
                modifier = Modifier.fillMaxSize().padding(padding),
                onGoImport = onBack,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(
                    horizontal = skin.spaceLg,
                    vertical = skin.spaceMd,
                ),
                verticalArrangement = Arrangement.spacedBy(skin.spaceSm),
            ) {
                item {
                    Text(
                        text = "共 ${history.size} 条 · 只保存在本机",
                        fontSize = 12.sp,
                        color = colors.textTertiary,
                    )
                }
                // key 必须唯一：同一秒内连点两次导入会拿到同一个 `时间戳+来源`，
                // 而 LazyColumn 遇到重复 key 是直接崩
                val keys = uniqueKeys(history) { it.timestamp.toString() + it.source }
                itemsIndexed(history, key = { index, _ -> keys[index] ?: index.toString() }) { _, item ->
                    HistoryRow(item = item)
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空导入记录") },
            text = { Text("将删除本机保存的全部导入记录，不影响已导入的课表。") },
            confirmButton = {
                TextButton(onClick = {
                    onClear()
                    showClearConfirm = false
                }) { Text("清空", color = colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun EmptyHistory(
    modifier: Modifier = Modifier,
    onGoImport: () -> Unit,
) {
    val colors = LocalPocketXiaoliColors.current
    val skin = LocalSkin.current
    Column(
        modifier = modifier.padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surfaceAlt),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.History,
                contentDescription = null,
                tint = colors.textTertiary.copy(alpha = 0.6f),
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.height(skin.spaceMd))
        Text(
            text = "还没有导入记录",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(skin.spaceXs))
        Text(
            text = "从「导入课表」导入一次后，来源、结果和时间会记在这里",
            fontSize = 12.sp,
            color = colors.textTertiary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(skin.spaceSm))
        TextButton(onClick = onGoImport) {
            Text("去导入课表", color = colors.accentMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun HistoryRow(item: LastImportInfo) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val isDark = LocalDarkMode.current
    val time = remember(item.timestamp) {
        if (item.timestamp <= 0L) {
            "时间未知"
        } else {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(item.timestamp))
        }
    }
    val success = item.success
    val statusTint = if (success) colors.success else colors.danger
    val statusText = when {
        success -> "导入 ${item.courseCount} 门课程"
        !item.detail.isNullOrBlank() -> item.detail.orEmpty()
        else -> "这次没有导入成功，可换一种方式再试"
    }

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = skin.spaceMd, vertical = skin.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(statusTint.copy(alpha = if (isDark) 0.24f else 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (success) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = statusTint,
                    modifier = Modifier.size(21.dp),
                )
            }
            Spacer(Modifier.width(skin.spaceSm))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.source.ifBlank { "课表导入" },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(skin.spaceSm))
                    Text(
                        text = time,
                        fontSize = 11.sp,
                        color = colors.textTertiary,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ImportTag(text = if (success) "成功" else "失败", tint = statusTint)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = statusText,
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
