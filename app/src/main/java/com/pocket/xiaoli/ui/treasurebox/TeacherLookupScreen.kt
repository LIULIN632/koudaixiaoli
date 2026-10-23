package com.pocket.xiaoli.ui.treasurebox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocket.xiaoli.data.entity.TeacherEntity
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
fun TeacherLookupScreen(
    onBack: () -> Unit,
    repository: CourseRepository,
    schemeId: Long
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val scope = rememberCoroutineScope()
    // WithLifecycle 版本：参数里内联构造的 Room 冷 Flow 每次重组都会重新注册观察者
    val teachers by repository.getAllTeachers()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val distinctNames by repository.getDistinctTeacherNames(schemeId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingTeacher by remember { mutableStateOf<TeacherEntity?>(null) }
    var confirmingDelete by remember { mutableStateOf<String?>(null) }

    // Auto-sync teachers from course names on first load
    var hasAutoSynced by remember { mutableStateOf(false) }
    LaunchedEffect(distinctNames, hasAutoSynced) {
        if (!hasAutoSynced && distinctNames.isNotEmpty()) {
            val existingNames = teachers.map { it.name }.toSet()
            val toInsert = distinctNames.filter { it !in existingNames }
            if (toInsert.isNotEmpty()) {
                scope.launch {
                    toInsert.forEach { name ->
                        repository.insertTeacher(TeacherEntity(name = name.trim()))
                    }
                }
            }
            hasAutoSynced = true
        }
    }

    val filteredTeachers = remember(searchQuery, teachers) {
        if (searchQuery.isBlank()) teachers
        else teachers.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        containerColor = colors.surfaceBase,
        topBar = {
            ServicePageTopBar(
                title = "查询老师办公室",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Filled.Add, "添加老师", tint = colors.accentMain)
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
            // Search bar —— 只有它留在滚动之外（输入框滚出屏幕会丢焦点）。
            // ⚠️ 2026-10-22：hero 与小节标题**移进列表**。原先它们固定在外层 Column、
            // 列表是内嵌 LazyColumn，往下滑时那张大卡一直挂在顶上、第一张老师卡
            // 从它下沿滑出来被切 —— 用户报的「卡片悬浮遮挡内容」就是它。
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("搜索老师姓名……", color = colors.textTertiary) },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Outlined.Search, null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
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
                shape = RoundedCornerShape(skin.radiusCard)
            )

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
                // ── 主视觉：这页有多少条真数据，用户一眼要知道 ──
                item(key = "hero") {
                    val withOffice = teachers.count { it.office.isNotBlank() }
                    ServiceHeroCard(accent = ServiceAccent.INFO) {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "已收录老师",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                                Spacer(Modifier.height(skin.spaceXs))
                                ServiceHeroCount(teachers.size.toString(), "位")
                            }
                            ServiceHeroBadge(Icons.Outlined.WorkOutline)
                        }
                        Spacer(Modifier.height(skin.spaceLg))
                        Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                            ServiceHeroChip("已填办公室 $withOffice")
                            ServiceHeroChip("点卡片看办公时间")
                        }
                    }
                }

                item(key = "label") {
                    ServiceSectionLabel(
                        title = if (searchQuery.isBlank()) "老师名录" else "搜索结果",
                        accent = ServiceAccent.INFO,
                        modifier = Modifier.padding(top = skin.spaceSm, bottom = skin.spaceXs),
                        trailing = {
                            Text(
                                "${filteredTeachers.size} 位",
                                fontSize = 12.sp,
                                color = colors.textTertiary
                            )
                        }
                    )
                }

                if (filteredTeachers.isEmpty()) {
                    item(key = "empty") {
                        ServiceEmptyCard(
                            icon = Icons.Outlined.PersonSearch,
                            title = if (teachers.isEmpty()) "名录还是空的" else "没找到这位老师",
                            message = if (teachers.isEmpty())
                                "手动添加，或先从课表里把任课老师同步过来"
                            else
                                "姓名只支持包含匹配，试试只填姓，比如「王」",
                            accent = ServiceAccent.INFO,
                            actionLabel = if (teachers.isEmpty()) "添加老师" else null,
                            onAction = if (teachers.isEmpty()) {{ showAddDialog = true }} else null,
                        )
                    }
                } else {
                    items(filteredTeachers, key = { it.name }) { teacher ->
                        TeacherCard(
                            teacher = teacher,
                            onEdit = { editingTeacher = teacher },
                            onDelete = { confirmingDelete = teacher.name }
                        )
                    }
                }
            }
        }
    }

    // Add/Edit dialog
    if (showAddDialog || editingTeacher != null) {
        TeacherEditDialog(
            initial = editingTeacher,
            onDismiss = { showAddDialog = false; editingTeacher = null },
            onSave = { teacher ->
                scope.launch {
                    repository.insertTeacher(teacher)
                }
                showAddDialog = false
                editingTeacher = null
            }
        )
    }

    // Delete confirm dialog
    if (confirmingDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = null },
            title = { Text("删除老师信息") },
            text = { Text("确定要删除「${confirmingDelete}」的信息吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            confirmingDelete?.let { name ->
                                repository.deleteTeacher(name)
                            }
                        }
                        confirmingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.danger)
                ) {
                    Text("删除", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun TeacherCard(
    teacher: TeacherEntity,
    onEdit: (TeacherEntity) -> Unit,
    onDelete: (TeacherEntity) -> Unit
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val isDark = LocalDarkMode.current
    val accentTint = serviceAccentColor(ServiceAccent.INFO)
    var expanded by remember { mutableStateOf(false) }

    // 用 AppCard 的 onClick：整张卡可点并且有 MIUIX 按压回弹，
    // 比裸 clickable（无水波纹、无手感）更明确"这张卡会展开"。
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded },
    ) {
        Column(modifier = Modifier.padding(skin.cardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 首字头像：一列名字长度不一的卡片，没有它就没有对齐的视觉锚点
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(accentTint.copy(alpha = if (isDark) 0.26f else 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        teacher.name.firstOrNull()?.toString() ?: "师",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentTint
                    )
                }
                Spacer(Modifier.width(skin.spaceMd))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        teacher.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    if (teacher.title.isNotEmpty()) {
                        Text(
                            teacher.title,
                            fontSize = 13.sp,
                            color = accentTint,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Text(
                    if (expanded) "收起" else "详情",
                    fontSize = 11.sp,
                    color = colors.textTertiary,
                )
                Row {
                    IconButton(onClick = { onEdit(teacher) }, modifier = Modifier.size(36.dp).invisibleTouchTarget()) {
                        Icon(Icons.Filled.Edit, "编辑", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { onDelete(teacher) }, modifier = Modifier.size(36.dp).invisibleTouchTarget()) {
                        Icon(Icons.Filled.Delete, "删除", tint = colors.danger, modifier = Modifier.size(18.dp))
                    }
                }
            }

            if (teacher.office.isNotEmpty()) {
                Spacer(Modifier.height(skin.spaceSmall))
                InfoRow(Icons.Outlined.WorkOutline, "办公室", teacher.office, colors)
            }

            if (expanded) {
                Spacer(Modifier.height(skin.spaceXs))
                HorizontalDivider(color = colors.borderDivider, thickness = 0.5.dp)
                Spacer(Modifier.height(skin.spaceSm))

                if (teacher.officeHours.isNotEmpty()) {
                    InfoRow(Icons.Outlined.WatchLater, "办公时间", teacher.officeHours, colors)
                    Spacer(Modifier.height(skin.spaceSmall))
                }
                if (teacher.phone.isNotEmpty()) {
                    InfoRow(Icons.Outlined.MailOutline, "联系电话", teacher.phone, colors)
                    Spacer(Modifier.height(skin.spaceSmall))
                }
                if (teacher.lastUpdated > 0) {
                    Text(
                        // ⚠️ locale 必须取**可观察**的那个。`Locale.getDefault()` 在 Composable 里
                        // 不建立订阅，系统语言变了这一行不会重算 —— Compose 的 lint 规则
                        // NonObservableLocale 就是抓这个的（本项目首跑即命中这类问题）。
                        "更新于 " + SimpleDateFormat("yyyy-MM-dd", LocalConfiguration.current.locales[0])
                            .format(Date(teacher.lastUpdated)),
                        fontSize = 11.sp,
                        color = colors.textTertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, colors: PocketXiaoliColors) {
    val skin = LocalSkin.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = colors.textTertiary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(skin.spaceSmall))
        Text(label, fontSize = 12.sp, color = colors.textTertiary)
        Spacer(Modifier.width(skin.spaceSm))
        Text(value, fontSize = 13.sp, color = colors.textPrimary)
    }
}

@Composable
private fun TeacherEditDialog(
    initial: TeacherEntity?,
    onDismiss: () -> Unit,
    onSave: (TeacherEntity) -> Unit
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var office by remember { mutableStateOf(initial?.office ?: "") }
    var officeHours by remember { mutableStateOf(initial?.officeHours ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var title by remember { mutableStateOf(initial?.title ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initial != null) "编辑老师信息" else "添加老师",
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("姓名") }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentColor(), cursorColor = accentColor()))
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("职称") }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentColor(), cursorColor = accentColor()))
                OutlinedTextField(value = office, onValueChange = { office = it }, label = { Text("办公室") }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentColor(), cursorColor = accentColor()))
                OutlinedTextField(value = officeHours, onValueChange = { officeHours = it }, label = { Text("办公时间") }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentColor(), cursorColor = accentColor()))
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("联系电话") }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentColor(), cursorColor = accentColor()))
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(TeacherEntity(
                            name = name.trim(),
                            office = office.trim(),
                            officeHours = officeHours.trim(),
                            phone = phone.trim(),
                            title = title.trim()
                        ))
                    }
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor())
            ) { Text("保存", color = Color.White) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
