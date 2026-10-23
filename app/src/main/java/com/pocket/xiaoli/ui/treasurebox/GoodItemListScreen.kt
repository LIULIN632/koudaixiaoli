package com.pocket.xiaoli.ui.treasurebox

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocket.xiaoli.data.entity.GoodItemEntity
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private data class PresetGoodItem(
    val name: String,
    val category: String,
    val reason: String,
    val description: String,
    val referencePrice: String
)

private val PRESET_GOOD_ITEMS = listOf(
    // ── 数码 ──
    PresetGoodItem("充电宝", "数码", "手机没电时的救命稻草", "大学外出时间长，上课、图书馆、社团活动，手机一天根本撑不住，早买早踏实", "¥80-150"),
    PresetGoodItem("蓝牙耳机", "数码", "图书馆/宿舍刷课必备", "宿舍集体生活需要安静，听网课、听歌、打电话都方便，降噪款体验翻倍", "¥100-500"),
    PresetGoodItem("平板", "数码", "无纸化学习效率翻倍", "记笔记、看PDF、刷网课比手机舒服太多，配支笔直接告别纸质笔记本", "¥2000-5000"),
    PresetGoodItem("显示器", "数码", "笔记本外接大屏生产力拉满", "写论文、敲代码、看网课多窗口并行，宿舍桌面空间换效率，早用早享受", "¥600-1500"),
    PresetGoodItem("机械键盘", "数码", "打字手感质的飞跃", "大学四年敲键盘时间超乎想象，一把好键盘护手腕、提效率，室友也会感谢你选静音轴", "¥200-600"),
    PresetGoodItem("U盘/移动硬盘", "数码", "资料备份和传输的生命线", "作业、论文、课件随身带，小组作业互相传文件必备，坏了哭都来不及建议买两个", "¥30-300"),
    PresetGoodItem("拓展坞", "数码", "笔记本接口不够用的救星", "轻薄本通常只有1-2个Type-C口，接U盘、显示器、网线全靠它，开会投屏也方便", "¥80-300"),

    // ── 生活 ──
    PresetGoodItem("床帘", "生活", "宿舍里唯一属于你的私密空间", "集体生活最需要的就是隐私感，换衣服、睡觉遮光、深夜开灯不打扰室友", "¥50-150"),
    PresetGoodItem("坐垫", "生活", "拯救你的老腰", "宿舍椅子普遍硬且不舒服，四年坐下来的损伤不可逆，好坐垫是健康投资", "¥30-100"),
    PresetGoodItem("台灯", "生活", "熄灯后的光明自由", "宿舍统一熄灯后想学习或刷手机就靠它，选充电款停电也能用，护眼款更不易疲劳", "¥50-200"),
    PresetGoodItem("洗衣机", "生活", "告别排队洗衣烦恼", "公共洗衣机卫生堪忧且要抢，宿舍合资买个小洗衣机，四年洗衣自由", "¥300-800"),
    PresetGoodItem("小冰箱", "生活", "夏天冷饮自由不是梦", "夏天冰西瓜冰饮料、储存水果酸奶，尤其南方的同学，早买早享受", "¥300-600"),
    PresetGoodItem("保温杯", "生活", "随时喝热水养生又省钱", "教室饮水机排队费时间，自带保温杯随时喝，冬天暖手夏天保冷，告别买矿泉水", "¥50-150"),
    PresetGoodItem("收纳箱/收纳架", "生活", "宿舍空间翻倍的秘密", "宿舍桌子柜子空间有限，合理收纳让物品一目了然，找东西不再翻箱倒柜", "¥20-80"),
    PresetGoodItem("小风扇/挂脖风扇", "生活", "夏天教室没空调的续命神器", "很多大学教室和宿舍没空调或制冷差，一个小风扇让你上课睡觉都舒服", "¥20-100"),
    PresetGoodItem("电蚊香/蚊帐", "生活", "南方同学夏天的护身符", "蚊子多到怀疑人生的时候才知道这有多重要，蚊帐防蚊电蚊香灭蚊双管齐下", "¥15-60"),

    // ── 学习 ──
    PresetGoodItem("打印机", "学习", "打印资料不用跑打印店", "期末复习资料、申请材料、作业报告随时打印，尤其考研党打印量巨大", "¥300-800"),
    PresetGoodItem("护眼灯", "学习", "长时间学习不伤眼", "普通台灯频闪伤眼，好的护眼灯无频闪、可调色温，四年用眼健康值得投资", "¥150-500"),
    PresetGoodItem("书架", "学习", "桌面整洁学习更有动力", "教材教辅越来越多，桌上堆不下时才知道书架有多香，分层收纳一目了然", "¥30-80"),
    PresetGoodItem("错题本", "学习", "学霸标配，复习效率翻倍", "整理错题是最有效的学习方法之一，手写印象更深，纸质版不受设备限制", "¥10-30"),
    PresetGoodItem("笔记本支架", "学习", "颈椎救星，低头党的福音", "长时间低头看笔记本脖子酸痛是通病，支架抬高屏幕平视，坐姿改善立竿见影", "¥30-150"),
    PresetGoodItem("荧光笔/记号笔", "学习", "期末划重点的神器", "教材和笔记用不同颜色标注重点，复习时一目了然，效率提升不止一倍", "¥10-30"),
    PresetGoodItem("计划本/手账本", "学习", "告别拖延症的第一步", "把DDL、考试、社团活动写下来，重要事情不再忘记，自律从记录开始", "¥10-40")
)

private val CATEGORIES = listOf("全部", "数码", "生活", "学习", "自定义")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoodItemListScreen(
    repository: CourseRepository,
    schemeId: Long,
    onBack: () -> Unit
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // WithLifecycle 版本：参数里内联构造的 Room 冷 Flow 每次重组都会重新注册观察者
    val items by repository.getGoodItemsByScheme(schemeId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("全部") }
    var searchQuery by remember { mutableStateOf("") }

    // Initialize preset items on first load — use .first() to read real DB state
    LaunchedEffect(Unit) {
        val existing = repository.getGoodItemsByScheme(schemeId).first()
        if (existing.isEmpty()) {
            PRESET_GOOD_ITEMS.forEachIndexed { idx, preset ->
                repository.insertGoodItem(
                    GoodItemEntity(
                        name = preset.name,
                        category = preset.category,
                        reason = preset.reason,
                        description = preset.description,
                        referencePrice = preset.referencePrice,
                        sortOrder = idx,
                        schemeId = schemeId
                    )
                )
            }
        }
    }

    val filteredItems = remember(items, selectedCategory, searchQuery) {
        items.filter { item ->
            val matchCategory = selectedCategory == "全部" || item.category == selectedCategory
            val matchSearch = searchQuery.isBlank() ||
                item.name.contains(searchQuery, ignoreCase = true) ||
                item.reason.contains(searchQuery, ignoreCase = true) ||
                item.description.contains(searchQuery, ignoreCase = true)
            matchCategory && matchSearch
        }
    }

    val purchasedCount = items.count { it.purchased }
    val purchasedProgress = if (items.isEmpty()) 0f else purchasedCount.toFloat() / items.size

    Scaffold(
        containerColor = colors.surfaceBase,
        topBar = {
            ServicePageTopBar(
                title = "大学好物清单",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Filled.Add, "添加好物", tint = colors.accentMain)
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
            // ── 搜索：从顶栏里搬出来，做成常驻输入框 ──
            // 原先藏在标题栏的放大镜开关里：点一下才能搜、搜完得再点一下才关，
            // 而且输入框挤在标题位置宽度不够。常驻之后少一次点击，也让"能搜"这件事可见。
            // 它也是本页**唯一**留在滚动之外的控件（输入框滚出屏幕会丢焦点）。
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("搜索好物名称或推荐理由……", color = colors.textTertiary) },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Search,
                        null,
                        tint = colors.textTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(32.dp).invisibleTouchTarget()) {
                            Icon(
                                Icons.Filled.Close,
                                "清空搜索",
                                tint = colors.textTertiary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = skin.spaceLg, vertical = skin.spaceSm),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accentMain,
                    unfocusedBorderColor = colors.borderDivider,
                    cursorColor = colors.accentMain,
                    focusedContainerColor = colors.surfaceCard,
                    unfocusedContainerColor = colors.surfaceCard,
                ),
                shape = RoundedCornerShape(skin.radiusCard),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
            )

            // ⚠️ 2026-10-22：hero、分类 chips、小节标题与条目列表现在同处一条滚动。
            // 以前它们固定在外层 Column、列表是内嵌 LazyColumn，往下滑时那张进度大卡
            // 一直挂在顶上，第一件好物从它下沿滑出来被切 —— 用户报的「卡片悬浮遮挡」。
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
                // ── 主视觉：入手进度（原来是一块白板里的 40dp 小方块，读不出"进度"这件事）──
                if (items.isNotEmpty()) {
                    item(key = "hero") {
                        ServiceHeroCard(accent = ServiceAccent.INFO) {
                            Row(verticalAlignment = Alignment.Top) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "已入手",
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                    Spacer(Modifier.height(skin.spaceXs))
                                    ServiceHeroCount("$purchasedCount", "/ ${items.size} 件")
                                }
                                ServiceHeroBadge(Icons.Outlined.Star)
                            }

                            Spacer(Modifier.height(skin.spaceLg))

                            LinearProgressIndicator(
                                progress = { purchasedProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(skin.radiusMicro)),
                                color = Color.White,
                                trackColor = Color.White.copy(alpha = 0.22f),
                            )

                            Spacer(Modifier.height(skin.spaceSm))

                            Text(
                                when {
                                    purchasedCount == 0 -> "早买早享受，从第一件开始"
                                    purchasedCount >= items.size -> "全部入手，宿舍装备毕业了"
                                    else -> "还剩 ${items.size - purchasedCount} 件等你解锁"
                                },
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }

                // ── Category filter chips ──
                item(key = "chips") {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)
                        // 外层 LazyColumn 的 contentPadding 已经给了左右留白，
                        // 这里再 pad 一次 chips 会额外右移 16dp。
                    ) {
                        items(CATEGORIES, key = { it }) { cat ->
                            val count = when (cat) {
                                "全部" -> items.size
                                else -> items.count { it.category == cat }
                            }
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = {
                                    Text(
                                        "$cat ($count)",
                                        fontSize = 11.sp,
                                        fontWeight = if (selectedCategory == cat) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                shape = RoundedCornerShape(skin.radiusChip),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.accentMain,
                                    selectedLabelColor = onAccentColor(),
                                    containerColor = colors.surfaceCard,
                                    labelColor = colors.textSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = colors.borderDivider,
                                    enabled = true,
                                    selected = false,
                                ),
                            )
                        }
                    }
                }

                item(key = "label") {
                    ServiceSectionLabel(
                        title = "清单",
                        accent = ServiceAccent.INFO,
                        modifier = Modifier.padding(top = skin.spaceXs, bottom = skin.spaceXs),
                        trailing = {
                            Text(
                                "${filteredItems.size} 件",
                                fontSize = 12.sp,
                                color = colors.textTertiary
                            )
                        }
                    )
                }

                // ── Item list ──
                if (filteredItems.isEmpty()) {
                    item(key = "empty") {
                        ServiceEmptyCard(
                            icon = if (searchQuery.isNotBlank() || selectedCategory != "全部")
                                Icons.Outlined.SearchOff
                            else
                                Icons.Outlined.Star,
                            title = if (searchQuery.isNotBlank()) "没找到相关好物"
                            else if (selectedCategory != "全部") "这个分类还空着"
                            else "清单还是空的",
                            message = if (searchQuery.isNotBlank())
                                "名称和推荐理由都会搜，试试只填两个字，比如「桌」"
                            else if (selectedCategory != "全部")
                                "换个分类看看，或者把这件好物加进来"
                            else
                                "把想要的设备加进清单，标好预算，入手一件勾一件",
                            accent = ServiceAccent.INFO,
                            actionLabel = "添加好物",
                            onAction = { showAddDialog = true },
                        )
                    }
                } else {
                    // Unpurchased first, then purchased
                    val sorted = filteredItems.sortedWith(compareBy({ it.purchased }, { it.sortOrder }))
                    items(sorted, key = { it.id }) { item ->
                        GoodItemCard(
                            item = item,
                            onToggle = {
                                scope.launch {
                                    repository.updateGoodItem(item.copy(purchased = !item.purchased))
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    repository.deleteGoodItem(item.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddGoodItemDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, category, reason, description, price ->
                scope.launch {
                    val sortOrder = (items.maxOfOrNull { it.sortOrder } ?: -1) + 1
                    repository.insertGoodItem(
                        GoodItemEntity(
                            name = name.trim(),
                            category = category,
                            reason = reason.trim(),
                            description = description.trim(),
                            referencePrice = price.trim(),
                            sortOrder = sortOrder,
                            schemeId = schemeId
                        )
                    )
                }
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun GoodItemCard(
    item: GoodItemEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val accentTint = serviceAccentColor(ServiceAccent.INFO)
    var expanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        // 已入手的卡降一档到 surfaceAlt（比页底深一点的灰）：
        // 用 surfaceBase 会和页底同色，卡片边界直接消失，看着像渲染坏了而不是"已完成"。
        containerColor = if (item.purchased) colors.surfaceAlt else null,
        // 整卡可点展开详情（AppCard 自带 MIUIX 按压回弹），比裸 clickable 更有回应。
        onClick = { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 一行里塞了图标衬底 / 勾选框 / 名称+价格 / 详情 / 删除五件事，
                // 上下再不给够呼吸就挤成一坨（spaceXs 时卡片几乎连成一片）。
                .padding(horizontal = skin.spaceMd, vertical = skin.spaceSm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 分类图标衬底：一行一件，扫视时靠形状而不是靠读字定位。
                // 颜色统一用本页语义色，不给每个分类再配一个色值。
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(skin.radiusChip))
                        .background(accentTint.copy(alpha = if (item.purchased) 0.10f else 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        goodItemCategoryIcon(item.category),
                        null,
                        tint = accentTint.copy(alpha = if (item.purchased) 0.45f else 1f),
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(Modifier.width(skin.spaceSm))
                Checkbox(
                    checked = item.purchased,
                    onCheckedChange = { onToggle() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = colors.accentMain,
                        uncheckedColor = colors.textTertiary,
                        checkmarkColor = onAccentColor()
                    )
                )
                Spacer(Modifier.width(skin.spaceXs))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (item.purchased)
                                colors.textTertiary.copy(alpha = 0.7f)
                            else
                                colors.textPrimary,
                            textDecoration = if (item.purchased) TextDecoration.LineThrough else TextDecoration.None,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (item.referencePrice.isNotEmpty()) {
                            Spacer(Modifier.width(skin.spaceSm))
                            Surface(
                                shape = RoundedCornerShape(skin.radiusMicro),
                                color = colors.surfaceAlt
                            ) {
                                Text(
                                    item.referencePrice,
                                    fontSize = 11.sp,
                                    color = colors.textSecondary,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = skin.spaceSmall, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    if (item.reason.isNotEmpty()) {
                        Spacer(Modifier.height(skin.spaceMicro))
                        Text(
                            item.reason,
                            fontSize = 12.sp,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // 展开提示：详情藏在卡片里，没有这个标记用户不知道能点。
                Text(
                    if (expanded) "收起" else "详情",
                    fontSize = 11.sp,
                    color = colors.textTertiary,
                )
                IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(28.dp).invisibleTouchTarget()) {
                    Icon(
                        Icons.Outlined.Close, "删除",
                        tint = colors.textTertiary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            // Expanded detail section
            if (expanded && item.description.isNotEmpty()) {
                Spacer(Modifier.height(skin.spaceSm))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(skin.radiusChip),
                    color = colors.surfaceAlt
                ) {
                    Row(
                        modifier = Modifier.padding(skin.spaceMd),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Outlined.Lightbulb,
                            null,
                            tint = accentTint,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(skin.spaceSmall))
                        Text(
                            item.description,
                            fontSize = 12.sp,
                            color = colors.textSecondary,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除好物") },
            text = { Text("确定要删除「${item.name}」吗？") },
            confirmButton = {
                Button(
                    onClick = { onDelete(); showDeleteConfirm = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.danger,
                        contentColor = Color.White
                    )
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } }
        )
    }
}

/**
 * 分类 → 图标。只承担"区分"，颜色统一用本页语义色 ——
 * 好物清单原本已经在同一屏里出现 accentMain / surfaceHighlight / 表情符号三套强调手段。
 */
private fun goodItemCategoryIcon(category: String): androidx.compose.ui.graphics.vector.ImageVector =
    when (category) {
        "数码" -> Icons.Outlined.DeveloperMode
        "生活" -> Icons.Outlined.Weekend
        "学习" -> Icons.Outlined.School
        else -> Icons.Outlined.Category
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddGoodItemDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, category: String, reason: String, description: String, price: String) -> Unit
) {
    val colors = LocalPocketXiaoliColors.current
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("自定义") }
    var reason by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var catExpanded by remember { mutableStateOf(false) }
    val categories = listOf("数码", "生活", "学习", "自定义")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加好物", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 20) name = it },
                    label = { Text("物品名称 *") },
                    placeholder = { Text("如：电动牙刷") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colors.accentMain, cursorColor = colors.accentMain)
                )
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
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colors.accentMain)
                    )
                    ExposedDropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                        categories.forEach { c ->
                            DropdownMenuItem(text = { Text(c) }, onClick = { category = c; catExpanded = false })
                        }
                    }
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { if (it.length <= 30) reason = it },
                    label = { Text("推荐理由") },
                    placeholder = { Text("如：宿舍生活幸福感来源") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colors.accentMain, cursorColor = colors.accentMain)
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= 60) description = it },
                    label = { Text("为什么早买早享受") },
                    placeholder = { Text("如：四年下来能省很多时间……") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colors.accentMain, cursorColor = colors.accentMain)
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { if (it.length <= 15) price = it },
                    label = { Text("参考价格") },
                    placeholder = { Text("如：¥100-300") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colors.accentMain, cursorColor = colors.accentMain)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, category, reason, description, price) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentMain)
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
