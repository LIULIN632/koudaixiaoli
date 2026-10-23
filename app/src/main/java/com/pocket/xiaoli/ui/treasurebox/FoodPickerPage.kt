package com.pocket.xiaoli.ui.treasurebox

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import com.pocket.xiaoli.ui.components.ServiceHeroChip
import com.pocket.xiaoli.ui.components.ServicePageTopBar
import com.pocket.xiaoli.ui.components.ServiceSectionLabel
import com.pocket.xiaoli.ui.theme.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodPickerPage(onBack: () -> Unit) {
    val skin = LocalSkin.current
    val context = LocalContext.current
    val app = context.applicationContext as android.app.Application
    val viewModel: TreasureBoxViewModel = viewModel(factory = TreasureBoxViewModel.Factory(app))
    val pickedFood by viewModel.pickedFood.collectAsStateWithLifecycle()
    val isRolling by viewModel.isRolling.collectAsStateWithLifecycle()
    val foodOptions by viewModel.foodOptions.collectAsStateWithLifecycle()
    val colors = LocalPocketXiaoliColors.current

    var showAddDialog by remember { mutableStateOf(false) }
    var editingFood by remember { mutableStateOf<FoodOption?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("全部") }

    // 分类 chips **从实际数据推导**，不再硬编码。
    //
    // 原本写的是 listOf("全部", "一食堂", "二食堂", "三食堂", "外卖")：
    // 前三个食堂名在这批数据里**一条都筛不出来**（内置分类是 食堂/面食/小吃/快餐/外卖），
    // 用户点「一食堂」得到空白页，只会以为 App 坏了。
    // 而且反过来，数据里真实存在的 食堂(9) / 面食(6) / 小吃(4) / 快餐(2) 又没有对应按钮，
    // 等于 21 条数据只能靠「全部」看。
    //
    // 现在按"数据里出现过哪些分类"生成，自洽且不会再腐烂：
    // 用户导入带新分类的菜品后，chips 会自动多出来。
    val categories = remember(foodOptions) {
        listOf("全部") + foodOptions
            .map { it.category }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    val filteredFood = remember(foodOptions, selectedCategory) {
        if (selectedCategory == "全部") foodOptions
        else foodOptions.filter { it.category == selectedCategory }
    }

    val scaleAnim by animateFloatAsState(
        targetValue = if (pickedFood != null) 1f else 0.9f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f)
    )

    Scaffold(
        containerColor = colors.surfaceBase,
        topBar = {
            ServicePageTopBar(
                title = "今天吃什么",
                onBack = onBack,
                actions = {
                    // Export
                    IconButton(onClick = {
                        val json = viewModel.exportFoodOptionsJson(includeDefaults = false)
                        if (json == "[]") {
                            Toast.makeText(context, "暂无自定义菜单可导出", Toast.LENGTH_SHORT).show()
                        } else {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, json)
                                putExtra(Intent.EXTRA_SUBJECT, "食堂菜单分享")
                            }
                            context.startActivity(Intent.createChooser(intent, "分享菜单"))
                        }
                    }) {
                        Icon(Icons.Outlined.Share, "导出", tint = colors.accentMain)
                    }
                    // Import
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(Icons.Outlined.FileDownload, "导入", tint = colors.accentMain)
                    }
                    // Add
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Filled.Add, "添加菜品", tint = colors.accentMain)
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
            // ⚠️ 2026-10-22：结果卡、区块标题、分类 chips 与菜品列表现在同处一条滚动，
            // 只有「开始抽取 / 换一个」这个主操作固定在底部。
            // 以前结果卡与按钮都固定在外层 Column、列表是内嵌 LazyColumn，
            // 往下滑时那张绿色结果卡一直挂在顶上，第一道菜从它下沿滑出来被切 ——
            // 用户报的「卡片悬浮遮挡内容」就是它。按钮挪到底部则不会再压住内容。
            val listState = rememberLazyListState()
            LaunchedEffect(pickedFood?.name) {
                if (pickedFood != null && listState.layoutInfo.totalItemsCount > 0) {
                    listState.animateScrollToItem(0)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(
                    start = skin.spaceLg,
                    end = skin.spaceLg,
                    top = skin.spaceSm,
                    bottom = skin.spaceSm,
                ),
                verticalArrangement = Arrangement.spacedBy(skin.spaceSm)
            ) {
                // ── 抽取结果：整张卡就是"结果"本身 ──
                // 原来是一块白板里再嵌一个 accentMain 渐变圆 —— 两层容器把主视觉压小了，
                // 也把主题强调色和 LIFE 绿挤在同一屏里。现在只有一层：绿色深渐变卡 + 白字。
                item(key = "hero") {
                    ServiceHeroCard(accent = ServiceAccent.LIFE) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                val picked = pickedFood
                                Text(
                                    text = if (picked == null) "还没想好？摇一下" else "就决定是你了！",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                                Spacer(Modifier.height(skin.spaceSm))
                                Text(
                                    text = when {
                                        isRolling -> "抽取中……"
                                        picked != null -> picked.name
                                        else -> "？？？"
                                    },
                                    fontSize = if (picked != null && !isRolling) 26.sp else 22.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.scale(scaleAnim)
                                )
                                if (picked != null && !isRolling) {
                                    Spacer(Modifier.height(skin.spaceMd))
                                    Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                                        if (picked.windowName.isNotBlank()) ServiceHeroChip(picked.windowName)
                                        if (picked.price.isNotBlank()) ServiceHeroChip("¥${picked.price}")
                                        if (picked.category.isNotBlank()) ServiceHeroChip(picked.category)
                                    }
                                }
                            }
                            Spacer(Modifier.width(skin.spaceLg))
                            ServiceHeroBadge(Icons.Outlined.Restaurant)
                        }
                    }
                }

                // ── 区块标题 ──
                item(key = "label") {
                    ServiceSectionLabel(
                        title = "我的菜单",
                        accent = ServiceAccent.LIFE,
                        modifier = Modifier.padding(top = skin.spaceMd),
                        trailing = {
                            Text(
                                "${filteredFood.size} 道",
                                fontSize = 12.sp,
                                color = colors.textTertiary
                            )
                        }
                    )
                }

                // ── Category filter ──
                item(key = "chips") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(skin.spaceSmall)
                    ) {
                        items(categories, key = { it }) { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.accentMain,
                                    selectedLabelColor = onAccentColor(),
                                    containerColor = colors.surfaceCard,
                                    labelColor = colors.textSecondary,
                                ),
                                shape = RoundedCornerShape(skin.radiusChip),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = colors.borderDivider,
                                    enabled = true,
                                    selected = false,
                                ),
                            )
                        }
                    }
                }

                // ── Food list（空态是页面的正式状态，不是屏幕正中一行浅灰字）──
                if (filteredFood.isEmpty()) {
                    item(key = "empty") {
                        ServiceEmptyCard(
                            icon = Icons.Outlined.Restaurant,
                            title = if (foodOptions.isEmpty()) "菜单还是空的" else "这个分类下还没有菜",
                            message = if (foodOptions.isEmpty())
                                "添加几道常吃的菜，或者直接导入同学分享的菜单"
                            else
                                "换个分类看看，也可以在这里添一道新菜",
                            accent = ServiceAccent.LIFE,
                            actionLabel = "添加菜品",
                            onAction = { showAddDialog = true },
                        )
                    }
                } else {
                    items(filteredFood, key = { it.id }) { food ->
                        FoodCard(
                            food = food,
                            onEdit = if (food.isCustom) {{ editingFood = food }} else null,
                            onDelete = if (food.isCustom) {{ viewModel.deleteFoodOption(food.id) }} else null
                        )
                    }
                }
            }

            // ── 抽取按钮：主操作固定在这条滚动之外（页面底部），不再压住列表 ──
            // 主操作仍用 accentMain（页面身份用绿色，动作不跟着换色）
            Button(
                onClick = { viewModel.pickRandomFood() },
                enabled = !isRolling,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = skin.spaceLg, vertical = skin.spaceSm)
                    .height(48.dp),
                shape = RoundedCornerShape(skin.radiusCard),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentMain,
                    contentColor = onAccentColor()
                )
            ) {
                if (isRolling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = onAccentColor(),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(skin.spaceSm))
                    Text("抽选中……", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Outlined.Casino, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(skin.spaceSmall))
                    Text(
                        if (pickedFood == null) "开始抽取" else "换一个",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // ── Dialogs ──
    if (showAddDialog) {
        FoodFormDialog(
            title = "添加菜品",
            onDismiss = { showAddDialog = false },
            onConfirm = { food ->
                viewModel.addFoodOption(food)
                showAddDialog = false
                Toast.makeText(context, "已添加「${food.name}」", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (editingFood != null) {
        FoodFormDialog(
            title = "编辑菜品",
            initial = editingFood,
            onDismiss = { editingFood = null },
            onConfirm = { food ->
                viewModel.updateFoodOption(food)
                editingFood = null
                Toast.makeText(context, "已更新「${food.name}」", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showImportDialog) {
        ImportFoodDialog(
            onDismiss = { showImportDialog = false },
            onImport = { json ->
                val count = viewModel.importFoodOptionsJson(json)
                showImportDialog = false
                if (count > 0) {
                    Toast.makeText(context, "成功导入 $count 个菜品", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "未导入新菜品（可能已存在或格式错误）", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

// ═══════════════════════════════════════════
//  Food card item
// ═══════════════════════════════════════════

@Composable
private fun FoodCard(
    food: FoodOption,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = skin.spaceSm, vertical = skin.spaceXs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji icon
            val emoji = when {
                food.category == "一食堂" -> "🍽️"
                food.category == "二食堂" -> "🍴"
                food.category == "三食堂" -> "🥢"
                food.category == "外卖" -> "🥡"
                food.category.contains("面") -> "🍜"
                food.category.contains("快餐") -> "🍔"
                food.category.contains("小吃") -> "🍢"
                else -> "🍚"
            }
            // 表情直接裸在卡上会显得零散（大小不一、左边缘不齐）；给它一枚语义色衬底，
            // 一行卡片才有对得上的视觉起点。
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(skin.radiusChip))
                    .background(serviceAccentContainer(ServiceAccent.LIFE)),
                contentAlignment = Alignment.Center,
            ) {
                Text(emoji, fontSize = 20.sp)
            }
            Spacer(Modifier.width(skin.spaceSm))

            // Food info
            Column(Modifier.weight(1f)) {
                Text(
                    food.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitleParts = mutableListOf<String>()
                if (food.windowName.isNotEmpty()) subtitleParts.add(food.windowName)
                if (food.price.isNotEmpty()) subtitleParts.add("¥${food.price}")
                if (subtitleParts.isNotEmpty()) {
                    Text(
                        subtitleParts.joinToString(" · "),
                        fontSize = 11.sp,
                        color = colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (food.category.isNotEmpty()) {
                    Spacer(Modifier.height(skin.spaceMicro))
                    val categoryTint = serviceAccentColor(ServiceAccent.LIFE)
                    Surface(
                        shape = RoundedCornerShape(skin.radiusMicro),
                        color = categoryTint.copy(alpha = if (LocalDarkMode.current) 0.24f else 0.14f)
                    ) {
                        Text(
                            food.category,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = categoryTint,
                            modifier = Modifier.padding(horizontal = skin.spaceSmall, vertical = 1.dp)
                        )
                    }
                }
            }

            // Edit/Delete buttons (custom items only)
            if (onEdit != null) {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp).invisibleTouchTarget()) {
                    Icon(Icons.Outlined.Edit, "编辑", tint = colors.textTertiary, modifier = Modifier.size(16.dp))
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp).invisibleTouchTarget()) {
                    Icon(Icons.Outlined.Close, "删除", tint = colors.textTertiary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ═══════════════════════════════════════════
//  Add / Edit food dialog
// ═══════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoodFormDialog(
    title: String,
    initial: FoodOption? = null,
    onDismiss: () -> Unit,
    onConfirm: (FoodOption) -> Unit
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: "一食堂") }
    var windowName by remember { mutableStateOf(initial?.windowName ?: "") }
    var price by remember { mutableStateOf(initial?.price ?: "") }
    var catExpanded by remember { mutableStateOf(false) }

    val categories = listOf("一食堂", "二食堂", "三食堂", "外卖")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("菜品名称") },
                    placeholder = { Text("如：红烧牛肉面") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                    OutlinedTextField(
                        value = windowName,
                        onValueChange = { windowName = it },
                        label = { Text("窗口") },
                        placeholder = { Text("如：二楼6号") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = price,
                        onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) price = it },
                        label = { Text("价格") },
                        placeholder = { Text("如：15") },
                        singleLine = true,
                        modifier = Modifier.weight(0.7f)
                    )
                }
                ExposedDropdownMenuBox(
                    expanded = catExpanded,
                    onExpandedChange = { catExpanded = it }
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("分类") },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpanded) },
                        singleLine = true
                    )
                    ExposedDropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                        categories.forEach { c ->
                            DropdownMenuItem(text = { Text(c) }, onClick = { category = c; catExpanded = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val food = FoodOption(
                        id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                        name = name.trim(),
                        category = category,
                        windowName = windowName.trim(),
                        price = price.trim(),
                        isCustom = true
                    )
                    onConfirm(food)
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentMain)
            ) { Text(if (initial != null) "保存" else "添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// ═══════════════════════════════════════════
//  Import dialog
// ═══════════════════════════════════════════

@Composable
private fun ImportFoodDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    var jsonText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入菜单", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                Text(
                    "粘贴同学分享的菜单 JSON 文本，重复菜品将自动跳过。",
                    fontSize = 12.sp,
                    color = colors.textTertiary
                )
                OutlinedTextField(
                    value = jsonText,
                    onValueChange = { jsonText = it },
                    label = { Text("菜单 JSON") },
                    placeholder = { Text("粘贴菜单内容……") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(jsonText.trim()) },
                enabled = jsonText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentMain)
            ) { Text("导入") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
