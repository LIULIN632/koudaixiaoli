package com.pocket.xiaoli.ui.dev

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.database.PocketXiaoliDatabase
import com.pocket.xiaoli.ui.components.ErrorCard
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.pocket.xiaoli.ui.theme.LocalSkin

/**
 * Room 数据查看器。
 *
 * 开发者实验室里的只读数据库浏览器：
 * - 列出所有业务表与行数；
 * - 选表后分页预览 JSON；
 * - 支持复制当前页 JSON；
 * - 支持导出**整表**（全部行）为 JSON 文本分享。
 *
 * 为什么不做删除 / 编辑：真实用户数据不值得在排障工具里提供误触入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomDataViewerScreen(
    database: PocketXiaoliDatabase,
    onBack: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tables by remember { mutableStateOf<List<RoomTableInfo>>(emptyList()) }
    var selectedTable by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableStateOf<RoomTablePage?>(null) }
    var loadingTables by remember { mutableStateOf(true) }
    var loadingRows by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun loadTables() {
        scope.launch {
            loadingTables = true
            runCatching {
                withContext(Dispatchers.IO) {
                    RoomDataViewer.listTables(database.openHelper.readableDatabase)
                }
            }.onSuccess { loaded ->
                tables = loaded
                selectedTable = selectedTable?.takeIf { name -> loaded.any { it.name == name } }
                    ?: loaded.firstOrNull()?.name
                error = null
            }.onFailure { throwable ->
                error = throwable.message ?: "读取数据库表失败"
            }
            loadingTables = false
        }
    }

    fun loadRows(tableName: String) {
        scope.launch {
            loadingRows = true
            runCatching {
                withContext(Dispatchers.IO) {
                    RoomDataViewer.readPage(database.openHelper.readableDatabase, tableName)
                }
            }.onSuccess { loaded ->
                page = loaded
                error = null
            }.onFailure { throwable ->
                error = throwable.message ?: "读取表数据失败"
                page = null
            }
            loadingRows = false
        }
    }

    LaunchedEffect(Unit) { loadTables() }
    LaunchedEffect(selectedTable) {
        val tableName = selectedTable ?: return@LaunchedEffect
        loadRows(tableName)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Room 数据查看器", fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(skin.spaceSm))

            if (loadingTables) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = colors.accentMain) }
                return@Column
            }

            if (tables.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = error ?: "没有可查看的业务表。",
                        color = if (error == null) colors.textTertiary else colors.danger,
                        fontSize = 13.sp,
                    )
                }
                return@Column
            }

            Text(
                text = "选表查看 · 只读模式",
                fontSize = 12.sp,
                color = colors.textTertiary,
            )
            Spacer(Modifier.height(skin.spaceSm))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                items(tables, key = { it.name }) { table ->
                    FilterChip(
                        selected = table.name == selectedTable,
                        onClick = { selectedTable = table.name },
                        label = { Text("${table.name} (${table.rowCount})", fontSize = 12.sp) },
                    )
                }
            }

            Spacer(Modifier.height(skin.spaceMd))

            val currentPage = page
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedTable.orEmpty(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = currentPage?.let {
                            "共 ${it.totalRows} 行，预览前 ${RoomDataViewer.PREVIEW_LIMIT} 行"
                        } ?: "读取中…",
                        fontSize = 11.sp,
                        color = colors.textTertiary,
                    )
                }
                TextButton(
                    onClick = {
                        currentPage?.let {
                            copyText(context, RoomDataJson.pretty(it.columns, it.rows), "当前页 JSON")
                        }
                    },
                    enabled = currentPage != null && currentPage.rows.isNotEmpty(),
                ) { Text("复制 JSON", fontSize = 12.sp) }
                TextButton(
                    onClick = {
                        val tableName = selectedTable ?: return@TextButton
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    RoomDataViewer.exportJson(database.openHelper.readableDatabase, tableName)
                                }
                            }.onSuccess { json -> shareText(context, tableName, json) }
                                .onFailure { throwable ->
                                    Toast.makeText(
                                        context,
                                        "导出失败：${throwable.message ?: "未知错误"}",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                        }
                    },
                    enabled = currentPage != null && currentPage.rows.isNotEmpty(),
                ) { Text("导出", fontSize = 12.sp) }
            }

            Spacer(Modifier.height(skin.spaceXs))
            HorizontalDivider(color = colors.borderDivider)
            Spacer(Modifier.height(skin.spaceXs))

            if (loadingRows) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = colors.accentMain) }
            } else if (error != null) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    // 统一错误卡片（此前是一行裸红字，12sp 无行距、无出路）
                    ErrorCard(
                        title = "读取失败",
                        message = error!!,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            } else if (currentPage == null || currentPage.rows.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("这张表还没有数据。", color = colors.textTertiary, fontSize = 13.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    itemsIndexed(currentPage.rows) { index, row ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                        ) {
                            Text(
                                text = "#$index",
                                fontSize = 10.sp,
                                color = colors.textTertiary,
                                fontFamily = FontFamily.Monospace,
                            )
                            Spacer(Modifier.height(skin.spaceMicro))
                            Text(
                                text = RoomDataJson.prettyRow(currentPage.columns, row),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = colors.textSecondary,
                            )
                        }
                        HorizontalDivider(color = colors.borderDivider)
                    }
                }
            }
        }
    }
}

private fun copyText(context: Context, text: String, label: String) {
    val ok = runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
        true
    }.getOrDefault(false)
    Toast.makeText(
        context,
        if (ok) "$label 已复制" else "复制失败",
        Toast.LENGTH_SHORT,
    ).show()
}

private fun shareText(context: Context, tableName: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_SUBJECT, "$tableName.json")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "导出 $tableName"))
    }.onFailure {
        Toast.makeText(context, "没有可用的导出应用", Toast.LENGTH_SHORT).show()
    }
}

