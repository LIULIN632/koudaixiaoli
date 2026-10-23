package com.pocket.xiaoli.ui.import_

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.model.LastImportInfo
import com.pocket.xiaoli.di.AppContainer
import com.pocket.xiaoli.ui.components.uniqueKeys
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.pocket.xiaoli.ui.theme.LocalSkin
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportHistoryScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val colors = LocalPocketXiaoliColors.current
    val history by container.importHistory.collectAsStateWithLifecycle()
    var showClearConfirm by remember { mutableStateOf(false) }

    Scaffold(
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
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "还没有导入记录",
                    fontSize = 14.sp,
                    color = colors.textTertiary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // key 必须唯一：同一秒内连点两次导入会拿到同一个 `时间戳+来源`，
                // 而 LazyColumn 遇到重复 key 是直接崩
                val keys = uniqueKeys(history) { it.timestamp.toString() + it.source }
                itemsIndexed(history, key = { index, _ -> keys[index] ?: index.toString() }) { _, item ->
                    ImportHistoryRow(item = item)
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空导入记录") },
            text = { Text("将删除本机保存的全部导入记录,不影响已导入的课表。") },
            confirmButton = {
                TextButton(onClick = {
                    container.clearImportHistory()
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
private fun ImportHistoryRow(item: LastImportInfo) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val time = remember(item.timestamp) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(item.timestamp))
    }
    val statusColor = if (item.success) colors.accentMain else colors.danger
    val statusText = when {
        item.success -> "成功导入 ${item.courseCount} 门课程"
        !item.detail.isNullOrBlank() -> "导入失败：${item.detail}"
        else -> "导入失败"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusCard),
        color = colors.surfaceCard
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(skin.spaceCompact),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (item.success) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(skin.spaceMd))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.source,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                Spacer(Modifier.height(skin.spaceMicro))
                Text(text = statusText, fontSize = 12.sp, color = colors.textSecondary)
                Spacer(Modifier.height(skin.spaceMicro))
                Text(text = time, fontSize = 11.sp, color = colors.textTertiary)
            }
        }
    }
}