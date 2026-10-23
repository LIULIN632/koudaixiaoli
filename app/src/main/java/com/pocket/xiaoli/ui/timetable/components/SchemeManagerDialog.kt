package com.pocket.xiaoli.ui.timetable.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.entity.SchemeEntity
import com.pocket.xiaoli.ui.theme.LocalDarkMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.rememberSinkIndication
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/**
 * 「课表管理」：多方案切换 + 新建 / 重命名 / 删除。
 *
 * ## 为什么从 AlertDialog 换成底部抽屉
 *
 * 旧实现把方案列表塞进 `AlertDialog.text`，后果有三个：
 * 1. 新建入口是标题栏一枚没有文字的「+」，用户得先猜它是干什么的；
 * 2. 重命名 / 删除是 32dp 的小图标，挤在行尾，手指经常点错；
 * 3. 对话框内容区宽度由 Material3 默认值决定，长方案名与「使用中」状态没有位置，
 *    选中与否只能靠一行浅色底 —— 一眼看不出当前用的是哪张。
 *
 * 换成 [WindowBottomSheet]（与「发现新版本」「推荐给同学」同一套外壳）后：
 * 宽度随屏幕、可滚动、标题有位置；新建做成底部主按钮，方案行有选中态与徽标。
 *
 * ## 状态语义
 *
 * 方案行用 `selectable(role = RadioButton)` 而不是 `clickable`：
 * 读屏会念出「已选中 / 未选中」，这正是这组列表的实际语义（单选）。
 */
@Composable
fun SchemeManagerDialog(
    schemes: List<SchemeEntity>,
    activeSchemeId: Long,
    onSwitchScheme: (SchemeEntity) -> Unit,
    onCreateScheme: (String) -> Unit,
    onRenameScheme: (SchemeEntity, String) -> Unit,
    onDeleteScheme: (SchemeEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<SchemeEntity?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<SchemeEntity?>(null) }

    WindowBottomSheet(
        show = true,
        title = "课表管理",
        onDismissRequest = onDismiss,
    ) {
        SchemeManagerBody(
            schemes = schemes,
            activeSchemeId = activeSchemeId,
            onSwitch = onSwitchScheme,
            onCreate = { showCreateDialog = true },
            onRename = { showRenameDialog = it },
            onDelete = { showDeleteConfirm = it },
        )
    }

    if (showCreateDialog) {
        SchemeEditDialog(
            title = "新建方案",
            initialName = "",
            onConfirm = { name ->
                onCreateScheme(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    showRenameDialog?.let { scheme ->
        SchemeEditDialog(
            title = "重命名方案",
            initialName = scheme.name,
            onConfirm = { name ->
                onRenameScheme(scheme, name)
                showRenameDialog = null
            },
            onDismiss = { showRenameDialog = null }
        )
    }

    showDeleteConfirm?.let { scheme ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("删除方案", fontWeight = FontWeight.Bold) },
            text = { Text("确定要删除「${scheme.name}」吗？该方案下的所有课程数据将被永久删除，此操作不可撤销。") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteScheme(scheme)
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LocalPocketXiaoliColors.current.danger
                    )
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("取消") }
            }
        )
    }
}

/**
 * 抽屉正文（与窗口外壳分开，只为了能单独截图）。
 *
 * `WindowBottomSheet` 是独立窗口，JVM 快照抓不到它 —— 与
 * `RecommendSheetBody` / `AppUpdateSheetBody` 同一条限制。
 */
@Composable
internal fun SchemeManagerBody(
    schemes: List<SchemeEntity>,
    activeSchemeId: Long,
    onSwitch: (SchemeEntity) -> Unit,
    onCreate: () -> Unit,
    onRename: (SchemeEntity) -> Unit,
    onDelete: (SchemeEntity) -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "切换后，课表与首页会立刻使用选中的方案",
            fontSize = 12.sp,
            color = colors.textTertiary,
        )
        Spacer(Modifier.height(skin.spaceMd))

        if (schemes.isEmpty()) {
            EmptySchemeCard()
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                schemes.forEach { scheme ->
                    SchemeRow(
                        scheme = scheme,
                        active = scheme.id == activeSchemeId,
                        onSwitch = { onSwitch(scheme) },
                        onRename = { onRename(scheme) },
                        onDelete = { onDelete(scheme) },
                    )
                }
            }
        }

        Spacer(Modifier.height(skin.spaceLg))
        Button(
            onClick = onCreate,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp),
            shape = RoundedCornerShape(skin.radiusChip),
            colors = ButtonDefaults.buttonColors(containerColor = colors.accentMain),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("新建方案", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(skin.spaceSm))
        Text(
            text = "每个方案独立保存课程，可分别导入",
            fontSize = 11.sp,
            color = colors.textTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(skin.spaceSm))
    }
}

@Composable
private fun EmptySchemeCard() {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(skin.radiusCard))
            .background(colors.surfaceAlt)
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.FolderOpen,
            contentDescription = null,
            tint = colors.textTertiary.copy(alpha = 0.35f),
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(skin.spaceSm))
        Text("还没有课表方案", fontSize = 14.sp, color = colors.textSecondary)
        Spacer(Modifier.height(2.dp))
        Text("新建一个方案，再导入课表", fontSize = 12.sp, color = colors.textTertiary)
    }
}

@Composable
private fun SchemeRow(
    scheme: SchemeEntity,
    active: Boolean,
    onSwitch: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val isDark = LocalDarkMode.current
    val shape = RoundedCornerShape(skin.radiusCard)
    val canDelete = scheme.id != 0L

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (active) {
                    colors.accentMain.copy(alpha = if (isDark) 0.18f else 0.10f)
                } else {
                    colors.surfaceAlt
                }
            )
            .then(
                if (active) {
                    Modifier.border(1.dp, colors.accentMain.copy(alpha = 0.35f), shape)
                } else {
                    Modifier
                }
            )
            .selectable(
                selected = active,
                role = Role.RadioButton,
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberSinkIndication(),
                onClick = onSwitch,
            )
            .heightIn(min = 56.dp)
            .padding(start = skin.spaceMd, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (active) Icons.Outlined.CheckCircle else Icons.Outlined.Circle,
            contentDescription = null,
            tint = if (active) colors.accentMain else colors.textTertiary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(skin.spaceSm))
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = scheme.name,
                fontSize = 15.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (active) {
                Spacer(Modifier.width(skin.spaceSm))
                ActivePill()
            }
        }
        IconButton(
            onClick = onRename,
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = "重命名「${scheme.name}」",
                tint = colors.textTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
        if (canDelete) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "删除「${scheme.name}」",
                    tint = colors.danger,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            // 默认方案不可删除：留出同样宽度的占位，避免两行的标题起点不一样。
            Box(modifier = Modifier.size(44.dp))
        }
    }
}

@Composable
private fun ActivePill() {
    val colors = LocalPocketXiaoliColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(colors.accentMain.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = "使用中",
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = colors.accentMain,
            maxLines = 1,
        )
    }
}

@Composable
private fun SchemeEditDialog(
    title: String,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    val colors = LocalPocketXiaoliColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("方案名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accentMain,
                    cursorColor = colors.accentMain
                )
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentMain)
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
