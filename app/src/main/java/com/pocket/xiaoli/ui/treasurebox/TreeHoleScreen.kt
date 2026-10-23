package com.pocket.xiaoli.ui.treasurebox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocket.xiaoli.data.entity.TreeHoleEntity
import com.pocket.xiaoli.data.repository.CourseRepository
import com.pocket.xiaoli.ui.components.AppCard
import com.pocket.xiaoli.ui.components.ServiceEmptyCard
import com.pocket.xiaoli.ui.components.ServiceHeroBadge
import com.pocket.xiaoli.ui.components.ServiceHeroCard
import com.pocket.xiaoli.ui.components.ServiceHeroCount
import com.pocket.xiaoli.ui.components.ServiceHeroChip
import com.pocket.xiaoli.ui.components.ServicePageTopBar
import com.pocket.xiaoli.ui.components.ServiceSectionLabel
import com.pocket.xiaoli.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TreeHoleScreen(
    repository: CourseRepository,
    schemeId: Long,
    onBack: () -> Unit
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // WithLifecycle 版本：参数里内联构造的 Room 冷 Flow 每次重组都会重新注册观察者
    val messages by repository.getTreeHolesByScheme(schemeId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    // FAB 的**实测高度**，用来推算列表底部要让出的净空（见下面的 contentPadding）。
    // 初值就按规格给的 56dp 填，避免首帧少让一段、最后一张卡闪一下被压住。
    val density = LocalDensity.current
    var fabHeight by remember { mutableStateOf(56.dp) }

    Scaffold(
        containerColor = colors.surfaceBase,
        topBar = {
            ServicePageTopBar(title = "留声树洞", onBack = onBack)
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = colors.accentMain,
                contentColor = onAccentColor(),
                shape = RoundedCornerShape(skin.radiusCard),
                icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                text = { Text("写一条", fontWeight = FontWeight.Bold) },
                modifier = Modifier.onSizeChanged { size ->
                    fabHeight = with(density) { size.height.toDp() }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(colors.surfaceBase)
        ) {
            if (messages.isEmpty()) {
                Spacer(Modifier.height(skin.spaceLg))
                ServiceEmptyCard(
                    icon = Icons.Outlined.Park,
                    title = "树洞里还很安静",
                    message = "把想说的话放进来：开心、委屈、说不出口的都可以。留空昵称就是匿名。",
                    accent = ServiceAccent.INFO,
                    modifier = Modifier.padding(horizontal = skin.spaceLg),
                    actionLabel = "写下第一条",
                    onAction = { showAddDialog = true },
                )
            } else {
                // ⚠️ 2026-10-22：hero 与小节标题**都在下面这条滚动里**。
                //
                // 之前它们固定在外层 Column、列表只占剩下的部分，于是往下滑时
                // 这张彩色大卡一直挂在顶上，第一张留言从它下沿"滑出来"被切掉一截 ——
                // 用户报的「整个卡片始终悬浮在内容上，有点遮挡内容」就是这个。
                // 放进 `contentPadding` 支撑的同一条列表后，卡片跟着内容一起滚走。
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    // 底部净空 = **FAB 的实测高度** + 它到列表的间距 + 导航栏那一段的保守量。
                    // FAB 会压住最后一两张卡，列表要能把它滚到 FAB 下面。
                    // 原来直接写死 88dp，FAB 换尺寸/换文案就不成立了；现在这个数跟着 FAB 走。
                    contentPadding = PaddingValues(
                        start = skin.spaceLg,
                        end = skin.spaceLg,
                        top = skin.spaceSm,
                        bottom = fabHeight + 32.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(skin.spaceSm)
                ) {
                    // 一张语义色 hero 说清"收下了多少"与"这里的规矩"；
                    // 条数只在这里讲一次，下面的小节标题不再重复。
                    item(key = "hero") {
                        ServiceHeroCard(accent = ServiceAccent.INFO) {
                            Row(verticalAlignment = Alignment.Top) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "树洞已经收下",
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.9f),
                                    )
                                    ServiceHeroCount(messages.size.toString(), "条")
                                }
                                ServiceHeroBadge(icon = Icons.Outlined.Park)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                                ServiceHeroChip("留空昵称就是匿名")
                                ServiceHeroChip("最新的在最上面")
                            }
                        }
                    }
                    item(key = "label") {
                        ServiceSectionLabel(
                            title = "树洞里的悄悄话",
                            accent = ServiceAccent.INFO,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    items(messages, key = { it.id }) { msg ->
                        TreeHoleCard(
                            message = msg,
                            onDelete = {
                                scope.launch { repository.deleteTreeHole(msg.id) }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddTreeHoleDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { content, author ->
                scope.launch {
                    repository.insertTreeHole(TreeHoleEntity(content = content.trim(), author = author.trim(), schemeId = schemeId))
                }
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun TreeHoleCard(
    message: TreeHoleEntity,
    onDelete: () -> Unit
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // ⚠️ locale 取可观察的那个，并把它作为 remember 的 key。
    //
    // 原来写的是 `remember { SimpleDateFormat(..., Locale.getDefault()) }`：
    // `Locale.getDefault()` 在 Composable 里不可观察，而且 remember 会**缓存**结果 ——
    // 系统语言变了格式化器也不会重建（Compose lint NonObservableLocale 命中）。
    // 加上 locale 这个 key 之后，配置一变就会重新构造。
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { SimpleDateFormat("yyyy/MM/dd HH:mm", locale) }
    val accentTint = serviceAccentColor(ServiceAccent.INFO)

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(skin.spaceLg)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 首字头像：让每一条留言在列表里有一个"作者锚点"，
                // 而不是所有卡片都从同一位置起一行灰字。
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(accentTint.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = message.author.trim().firstOrNull()?.toString() ?: "匿",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentTint,
                    )
                }
                Spacer(Modifier.width(skin.spaceSm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        message.author.ifBlank { "匿名同学" },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textSecondary,
                    )
                    Text(
                        dateFormat.format(Date(message.createdAt)),
                        fontSize = 11.sp,
                        color = colors.textTertiary.copy(alpha = 0.7f),
                    )
                }
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.size(28.dp).invisibleTouchTarget()
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        "删除",
                        tint = colors.textTertiary.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(skin.spaceSm))

            Text(
                message.content,
                fontSize = 15.sp,
                color = colors.textPrimary,
                lineHeight = 23.sp
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除留言") },
            text = { Text("确定要删除这条留言吗？") },
            confirmButton = {
                Button(
                    onClick = { onDelete(); showDeleteConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentMain)
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTreeHoleDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    var content by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("写给树洞", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = author,
                    onValueChange = { if (it.length <= 8) author = it },
                    placeholder = { Text("你的昵称（留空则为匿名）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accentMain,
                        cursorColor = colors.accentMain
                    )
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { if (it.length <= 500) content = it },
                    placeholder = { Text("在这里写下你想说的话……") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    maxLines = 6,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accentMain,
                        cursorColor = colors.accentMain
                    )
                )
                Text(
                    "${content.length}/500",
                    fontSize = 11.sp,
                    color = colors.textTertiary.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = skin.spaceXs),
                    textAlign = TextAlign.End
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(content, author) },
                enabled = content.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentMain)
            ) { Text("放入树洞") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
