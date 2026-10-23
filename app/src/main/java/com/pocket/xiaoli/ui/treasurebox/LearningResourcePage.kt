package com.pocket.xiaoli.ui.treasurebox

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocket.xiaoli.ui.components.AppCard
import com.pocket.xiaoli.ui.components.ServiceEmptyCard
import com.pocket.xiaoli.ui.components.ServiceHeroBadge
import com.pocket.xiaoli.ui.components.ServiceHeroCard
import com.pocket.xiaoli.ui.components.ServiceHeroCount
import com.pocket.xiaoli.ui.components.ServiceHeroChip
import com.pocket.xiaoli.ui.components.ServicePageTopBar
import com.pocket.xiaoli.ui.components.ServiceSectionLabel
import com.pocket.xiaoli.util.DeepLinks
import com.pocket.xiaoli.ui.theme.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningResourcePage(
    onBack: () -> Unit,
    onImportCourse: (courseName: String, teacher: String, room: String, day: Int, startSlot: Int, endSlot: Int) -> Unit
) {
    val skin = LocalSkin.current
    val context = LocalContext.current
    val app = context.applicationContext as android.app.Application
    val viewModel: TreasureBoxViewModel = viewModel(factory = TreasureBoxViewModel.Factory(app))
    val resources by viewModel.resources.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val colors = LocalPocketXiaoliColors.current

    var selectedSubject by remember { mutableStateOf("全部") }
    var showAddDialog by remember { mutableStateOf(false) }
    var showFavoritesOnly by remember { mutableStateOf(false) }

    val subjects = remember(resources) {
        listOf("全部") + (resources.map { it.subject }.distinct().filter { it != "自定义" })
    }

    val filteredResources = remember(resources, selectedSubject, showFavoritesOnly, favorites) {
        val base = if (selectedSubject == "全部") resources
        else resources.filter { it.subject == selectedSubject }
        if (showFavoritesOnly) base.filter { it.id in favorites } else base
    }

    Scaffold(
        containerColor = colors.surfaceBase,
        topBar = {
            ServicePageTopBar(
                title = "学习资源",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Filled.Add, "添加资源", tint = colors.accentMain)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(colors.surfaceBase)
        ) {
            // ⚠️ 2026-10-22：hero、学科 chips、「资源列表」标题与资源卡现在同处一条滚动。
            // 以前它们固定在外层 Column、列表是内嵌 LazyColumn，往下滑时那张渐变大卡
            // 一直挂在顶上，第一门课从它下沿滑出来被切 —— 用户报的「卡片悬浮遮挡内容」。
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(
                    start = skin.spaceLg,
                    end = skin.spaceLg,
                    top = skin.spaceSm,
                    bottom = skin.spaceXxl,
                ),
                verticalArrangement = Arrangement.spacedBy(skin.spaceSm)
            ) {
                // ── 主视觉：这页到底攒了多少资源 ──
                if (resources.isNotEmpty()) {
                    item(key = "hero") {
                        ServiceHeroCard(accent = ServiceAccent.STUDY) {
                            Row(verticalAlignment = Alignment.Top) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "已收录课程",
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                    Spacer(Modifier.height(skin.spaceXs))
                                    ServiceHeroCount(resources.size.toString(), "门")
                                }
                                ServiceHeroBadge(Icons.AutoMirrored.Outlined.MenuBook)
                            }
                            Spacer(Modifier.height(skin.spaceLg))
                            Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                                ServiceHeroChip("已收藏 ${favorites.size}")
                                ServiceHeroChip("${subjects.size - 1} 个学科")
                                ServiceHeroChip("可导入课表")
                            }
                        }
                    }
                }

                // Subject + favorite filter chips
                // 原来这行套在一块 surfaceCard 白板里 —— 页底浅灰、这条浅白，
                // 分层分得毫无意义，只是把顶栏的白继续往下拖。去掉那层底。
                item(key = "chips") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(skin.spaceSm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LazyRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)
                        ) {
                            items(subjects, key = { it }) { subject ->
                                FilterChip(
                                    selected = selectedSubject == subject,
                                    onClick = { selectedSubject = subject },
                                    label = { Text(subject, fontSize = 12.sp) },
                                    shape = RoundedCornerShape(skin.radiusChip),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = colors.accentMain,
                                        selectedLabelColor = onAccentColor(),
                                        containerColor = colors.surfaceCard,
                                        labelColor = colors.textSecondary,
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        borderColor = colors.borderDivider,
                                        enabled = true,
                                        selected = false,
                                    ),
                                )
                            }
                        }
                        FilterChip(
                            selected = showFavoritesOnly,
                            onClick = { showFavoritesOnly = !showFavoritesOnly },
                            label = { Text("已收藏", fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(
                                    if (showFavoritesOnly) Icons.Filled.Star else Icons.Outlined.Star,
                                    null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            shape = RoundedCornerShape(skin.radiusChip),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.accentMain,
                                selectedLabelColor = onAccentColor(),
                                containerColor = colors.surfaceCard,
                                labelColor = colors.textSecondary,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = colors.borderDivider,
                                enabled = true,
                                selected = false,
                            ),
                        )
                    }
                }

                if (filteredResources.isEmpty()) {
                    item(key = "empty") {
                        ServiceEmptyCard(
                            icon = Icons.AutoMirrored.Outlined.MenuBook,
                            title = if (resources.isEmpty()) "还没有学习资源"
                            else if (showFavoritesOnly) "收藏夹还是空的"
                            else "这个学科下还没有课",
                            message = if (resources.isEmpty())
                                "添加一门你在跟的网课，之后可以直接导入课表"
                            else if (showFavoritesOnly)
                                "点卡片上的「收藏」，这里就会留下你常看的课"
                            else
                                "换个学科看看，或者自己加一门",
                            accent = ServiceAccent.STUDY,
                            actionLabel = "添加资源",
                            onAction = { showAddDialog = true },
                        )
                    }
                } else {
                    item(key = "label") {
                        ServiceSectionLabel(
                            title = "资源列表",
                            accent = ServiceAccent.STUDY,
                            modifier = Modifier.padding(top = skin.spaceXs),
                            trailing = {
                                Text(
                                    "${filteredResources.size} 门",
                                    fontSize = 12.sp,
                                    color = colors.textTertiary
                                )
                            }
                        )
                    }

                    items(filteredResources, key = { it.id }) { resource ->
                        val isFav = resource.id in favorites
                        ResourceCard(
                            resource = resource,
                            isFavorite = isFav,
                            onOpenUrl = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(resource.videoUrl))
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onToggleFavorite = { viewModel.toggleFavorite(resource.id) },
                            onImport = {
                                onImportCourse(
                                    resource.courseName,
                                    resource.blogger,
                                    "${resource.subject}课",
                                    resource.dayOfWeek,
                                    resource.startSlot,
                                    resource.endSlot
                                )
                                Toast.makeText(context, "已添加到课表！", Toast.LENGTH_SHORT).show()
                            },
                            onShare = {
                                val shareText = "【学习资源推荐】\n课程：${resource.courseName}\n博主：${resource.blogger}\n简介：${resource.description}\n链接：${resource.videoUrl}\n\n—— 来自「口袋小鲤」百宝箱"
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                                context.startActivity(Intent.createChooser(intent, "分享学习资源"))
                            },
                            onDelete = if (resource.isCustom) {
                                { viewModel.deleteResource(resource.id) }
                            } else null
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddResourceDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { resource ->
                viewModel.addResource(resource)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun ResourceCard(
    resource: LearningResource,
    isFavorite: Boolean,
    onOpenUrl: () -> Unit,
    onToggleFavorite: () -> Unit,
    onImport: () -> Unit,
    onShare: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val subjectTint = serviceAccentColor(subjectAccentOf(resource.subject))

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(skin.cardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Subject badge
                Surface(
                    shape = RoundedCornerShape(skin.radiusMicro),
                    color = subjectTint.copy(alpha = if (LocalDarkMode.current) 0.26f else 0.14f)
                ) {
                    Text(
                        resource.subject,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = subjectTint,
                        modifier = Modifier.padding(horizontal = skin.spaceSmall, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.width(skin.spaceSm))
                Text(
                    resource.courseName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // Favorite indicator
                if (isFavorite) {
                    Icon(
                        Icons.Filled.Star, "已收藏",
                        tint = colors.warning,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(skin.spaceXs))
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp).invisibleTouchTarget()) {
                        Icon(Icons.Outlined.Close, "删除", tint = colors.textTertiary, modifier = Modifier.size(14.dp))
                    }
                }
            }

            Spacer(Modifier.height(skin.spaceSmall))

            // 博主这行是"谁讲的"，属于次要信息；原来用 accentMain 上色的结果是
            // 每行都在抢标题的注意力，主操作按钮反而不显眼了。
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Person, null, tint = colors.textTertiary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(skin.spaceXs))
                Text(resource.blogger, fontSize = 12.sp, color = colors.textSecondary, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(skin.spaceXs))
            Text(resource.description, fontSize = 12.sp, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Jump to URL button
                //
                // 只在链接**看起来可用**时才给按钮。内置的 12 条资源原本填的是
                // `https://b23.tv/gaoshu_songhao` 这种编造短链，点开必然打不开；
                // 与其给一个必定失败的按钮让用户怀疑自己手机，不如明确显示"暂无链接"。
                // 判据见 [DeepLinks.looksUsable]（有单测锁住那 12 条占位符）。
                //
                // 刻意**保留按钮但禁用**而不是直接隐藏：隐藏会让"怎么没有跳转"变成疑问，
                // 灰掉并写"暂无链接"是明确的信息 —— 这条资源确实没有可跳转的地址。
                val hasLink = DeepLinks.looksUsable(resource.videoUrl)
                OutlinedButton(
                    onClick = onOpenUrl,
                    enabled = hasLink,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp).invisibleTouchTarget()
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(skin.spaceXs))
                    Text(if (hasLink) "跳转" else "暂无链接", fontSize = 11.sp)
                }
                Spacer(Modifier.width(skin.spaceSmall))
                // Favorite button
                FilledTonalButton(
                    onClick = onToggleFavorite,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp).invisibleTouchTarget(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        // 收藏态原来是三处写死的金色（0xFFF0E8D0 / 0xFFB8A060 / 0xFF9A8040），
                        // 换任何配色主题它们都不动。改用语义 warning 档（同为暖金），
                        // 图标与文字直接吃 contentColor，不再各写一遍。
                        containerColor = if (isFavorite)
                            colors.warning.copy(alpha = if (LocalDarkMode.current) 0.26f else 0.16f)
                        else
                            colors.surfaceAlt,
                        contentColor = if (isFavorite) colors.warning else colors.textSecondary,
                    )
                ) {
                    Icon(
                        if (isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                        null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(skin.spaceXs))
                    Text(if (isFavorite) "已收藏" else "收藏", fontSize = 11.sp)
                }
                Spacer(Modifier.width(skin.spaceSmall))
                // Import to timetable button
                FilledTonalButton(
                    onClick = onImport,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp).invisibleTouchTarget(),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = colors.surfaceHighlight)
                ) {
                    Icon(Icons.Outlined.AddCircle, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(skin.spaceXs))
                    Text("导入", fontSize = 11.sp)
                }
                Spacer(Modifier.width(skin.spaceSmall))
                // Share button —— 没有链接就没有可分享的内容，直接不显示
                // （分享文案里含链接，分享一个打不开的链接等于把问题扩散出去）
                if (hasLink) {
                    FilledTonalButton(
                        onClick = onShare,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp).invisibleTouchTarget(),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = colors.surfaceHighlight)
                    ) {
                        Icon(Icons.Outlined.Share, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(skin.spaceXs))
                        Text("分享", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/**
 * 学科 → 语义色类别。
 *
 * 这里原本写死 6 个十六进制（`Color(0xFF8B94B8)`…）：换配色主题时它们不动，
 * 于是"马卡龙粉"这类主题下这一页会同时出现 6 个不属于主题的颜色。
 * 改为复用 [ServiceAccent] 的 6 个类别 —— 它们本来就是为了"6 类之间可辨识、
 * 又都跟着主题走"而设计的，正好是这里要的东西。
 */
private fun subjectAccentOf(subject: String): ServiceAccent = when (subject) {
    "数学" -> ServiceAccent.ACADEMIC
    "编程" -> ServiceAccent.STUDY
    "英语" -> ServiceAccent.QUERY
    "考研" -> ServiceAccent.LIFE
    "设计" -> ServiceAccent.INFO
    "通识" -> ServiceAccent.PAYMENT
    else -> ServiceAccent.STUDY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddResourceDialog(
    onDismiss: () -> Unit,
    onConfirm: (LearningResource) -> Unit
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    var subject by remember { mutableStateOf("自定义") }
    var courseName by remember { mutableStateOf("") }
    var blogger by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var videoUrl by remember { mutableStateOf("") }
    var dayOfWeek by remember { mutableIntStateOf(1) }
    var startSlot by remember { mutableIntStateOf(1) }
    var endSlot by remember { mutableIntStateOf(2) }

    var subjectExpanded by remember { mutableStateOf(false) }
    val weekdays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加学习资源", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(skin.spaceSm)
            ) {
                // Subject dropdown
                ExposedDropdownMenuBox(
                    expanded = subjectExpanded,
                    onExpandedChange = { subjectExpanded = it }
                ) {
                    OutlinedTextField(
                        value = subject,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("学科分类") },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subjectExpanded) },
                        singleLine = true
                    )
                    ExposedDropdownMenu(expanded = subjectExpanded, onDismissRequest = { subjectExpanded = false }) {
                        SUBJECTS.filter { it != "自定义" }.forEach { s ->
                            DropdownMenuItem(text = { Text(s) }, onClick = { subject = s; subjectExpanded = false })
                        }
                        DropdownMenuItem(text = { Text("自定义") }, onClick = { subject = "自定义"; subjectExpanded = false })
                    }
                }

                OutlinedTextField(value = courseName, onValueChange = { courseName = it }, label = { Text("课程名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = blogger, onValueChange = { blogger = it }, label = { Text("推荐博主") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("视频简介") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                // 链接可以留空（那就只用于「导入课表」，卡片上不显示跳转/分享）。
                // 但如果填了却明显是拼凑的占位符，就地提示 —— 不要等用户点开才发现打不开。
                val linkLooksFake = videoUrl.isNotBlank() && !DeepLinks.looksUsable(videoUrl)
                OutlinedTextField(
                    value = videoUrl,
                    onValueChange = { videoUrl = it },
                    label = { Text("视频链接（可留空）") },
                    placeholder = { Text("https://b23.tv/BV1xx411c7mD") },
                    singleLine = true,
                    isError = linkLooksFake,
                    supportingText = if (linkLooksFake) {
                        { Text("这个链接看起来不是有效地址，保存后将无法跳转", fontSize = 11.sp) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                    var dayExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = dayExpanded, onExpandedChange = { dayExpanded = it }, modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = weekdays[dayOfWeek - 1], onValueChange = {}, readOnly = true,
                            label = { Text("星期") }, singleLine = true,
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                        )
                        ExposedDropdownMenu(expanded = dayExpanded, onDismissRequest = { dayExpanded = false }) {
                            weekdays.forEachIndexed { i, d -> DropdownMenuItem(text = { Text(d) }, onClick = { dayOfWeek = i + 1; dayExpanded = false }) }
                        }
                    }
                    var sExp by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = sExp, onExpandedChange = { sExp = it }, modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = "第${startSlot}节", onValueChange = {}, readOnly = true,
                            label = { Text("开始节") }, singleLine = true,
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                        )
                        ExposedDropdownMenu(expanded = sExp, onDismissRequest = { sExp = false }) {
                            (1..12).forEach { s -> DropdownMenuItem(text = { Text("第${s}节") }, onClick = { startSlot = s; if (endSlot < s) endSlot = s; sExp = false }) }
                        }
                    }
                    var eExp by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = eExp, onExpandedChange = { eExp = it }, modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = "第${endSlot}节", onValueChange = {}, readOnly = true,
                            label = { Text("结束节") }, singleLine = true,
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                        )
                        ExposedDropdownMenu(expanded = eExp, onDismissRequest = { eExp = false }) {
                            (startSlot..12).forEach { s -> DropdownMenuItem(text = { Text("第${s}节") }, onClick = { endSlot = s; eExp = false }) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(LearningResource(
                        subject = subject, courseName = courseName, blogger = blogger,
                        description = description, videoUrl = videoUrl,
                        dayOfWeek = dayOfWeek, startSlot = startSlot, endSlot = endSlot
                    ))
                },
                // 链接不再是必填：留空也能添加，只是不能跳转/分享
                enabled = courseName.isNotBlank() && blogger.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentMain)
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
