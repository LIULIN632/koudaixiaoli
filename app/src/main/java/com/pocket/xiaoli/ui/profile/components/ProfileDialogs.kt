package com.pocket.xiaoli.ui.profile.components

import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import com.pocket.xiaoli.ui.components.MiuiMenuMaxHeight
import com.pocket.xiaoli.ui.components.MiuiMenuPopup
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.PocketXiaoliApplication
import com.pocket.xiaoli.data.backup.BackupCrypto
import com.pocket.xiaoli.data.entity.CourseEntity
import com.pocket.xiaoli.data.model.CourseBackup
import com.pocket.xiaoli.ui.theme.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*


// ═══════════════════════════════════════════
//  Menu Dialogs
// ═══════════════════════════════════════════

// 「备份与分享」已迁到 ShareMenuSheet.kt（底部抽屉 + 分组），此处不再保留旧对话框。

// ═══════════════════════════════════════════
//  General Dialogs
// ═══════════════════════════════════════════

@Composable
internal fun EditTextDialog(
    title: String,
    currentValue: String,
    hint: String,
    maxLength: Int = 20,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val skin = LocalSkin.current
    var text by remember { mutableStateOf(currentValue) }
    val colors = LocalPocketXiaoliColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(skin.radiusCard),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Badge,
                    contentDescription = null,
                    tint = colors.accentMain,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(skin.spaceSm))
                Text(title, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= maxLength) text = it },
                    placeholder = { Text(hint, color = colors.textTertiary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(skin.radiusChip),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accentMain,
                        cursorColor = colors.accentMain,
                        focusedContainerColor = colors.surfaceBase.copy(alpha = 0.5f),
                        unfocusedContainerColor = colors.surfaceBase.copy(alpha = 0.5f)
                    )
                )
                if (maxLength < Int.MAX_VALUE) {
                    Spacer(Modifier.height(skin.spaceXs))
                    Text(
                        text = "${text.length}/$maxLength",
                        fontSize = 11.sp,
                        color = colors.textTertiary,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text); onDismiss() },
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentMain),
                shape = RoundedCornerShape(skin.radiusChip)
            ) {
                Text("确定", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = colors.textSecondary) }
        }
    )
}

@Composable
internal fun <T> OptionsDialog(
    title: String,
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalPocketXiaoliColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (label, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value); onDismiss() }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected == value,
                            onClick = { onSelect(value); onDismiss() },
                            colors = RadioButtonDefaults.colors(selectedColor = colors.accentMain)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label, fontSize = 15.sp, color = colors.textPrimary)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}


@Composable
internal fun FaqDialog(onDismiss: () -> Unit) {
    val faqs = listOf(
        "如何导入课表？" to "进入导入页会自动锁定本校，登录正方教务即可拉取课表。也支持分享口令、Excel / HTML 文件等方式。",
        // ⚠️ 必须与产品范围一致：本 App 是烟台理工专用，导入页锁校（campusOnly），
        // 用户看不到其他学校。这里曾写"支持正方/强智/青果/超星，不在列表可申请适配" ——
        // 那是导入**引擎**的能力而非产品范围，用户在应用里根本选不到别的学校。
        "支持哪些学校？" to "本应用为烟台理工学院专用，只对接本校教务（烟台理工本校区 · 正方教务 v8）。烟大南校区用于后勤能源缴费等服务。",
        "课表数据存储在哪里？" to "课表、作业、待办、成绩等只存在手机本地，不上传服务器。应用不会自动上传任何数据；崩溃日志也只写在本机，要交出去得你自己在「设置 → 关于」里导出。",
        "如何分享课表？" to "在课程页面长按课程即可生成分享口令，好友可通过口令导入您的课表。",
        "如何切换深色模式？" to "在「我的 → 设置 → 深色/浅色模式」里可以选择浅色、深色或跟随系统三种模式。"
    )
    val colors = LocalPocketXiaoliColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("常见问题") },
        text = {
            Column(modifier = Modifier.heightIn(max = 400.dp)) {
                faqs.forEach { (q, a) ->
                    Text(q, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(a, fontSize = 13.sp, color = colors.textSecondary, lineHeight = 18.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
internal fun SchoolRequestDialog(
    context: android.content.Context,
    onDismiss: () -> Unit,
    initialName: String = ""
) {
    var schoolName by remember { mutableStateOf(initialName) }
    var systemType by remember { mutableStateOf("未知") }
    var expanded by remember { mutableStateOf(false) }
    // 气泡菜单贴在输入框下沿弹出，位置由这个锚点算
    val systems = listOf("未知", "正方", "强智", "青果", "超星", "其他")
    val colors = LocalPocketXiaoliColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("反馈学校信息") },
        text = {
            Column {
                Text(
                    "本应用目前只服务烟台理工学院。如果你不是本校学生，可以把学校信息填在下面反馈。" +
                        "注意：暂时无法承诺适配其他学校。",
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = schoolName,
                    onValueChange = { schoolName = it },
                    placeholder = { Text("学校全称", color = colors.textTertiary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accentMain,
                        cursorColor = colors.accentMain
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box {
                    OutlinedTextField(
                        value = systemType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("教务系统类型") },
                        trailingIcon = {
                            IconButton(onClick = { expanded = true }) {
                                Icon(Icons.Filled.ArrowDropDown, "选择")
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accentMain
                        )
                    )
                    MiuiMenuPopup(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        options = systems,
                        selectedIndex = systems.indexOf(systemType),
                        maxHeight = MiuiMenuMaxHeight,
                        onSelect = { index -> systems.getOrNull(index)?.let { systemType = it }; expanded = false },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (schoolName.isNotBlank()) {
                        try {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:pocketxiaoyan@outlook.com")
                                putExtra(Intent.EXTRA_SUBJECT, "口袋小鲤 - 学校信息反馈")
                                putExtra(Intent.EXTRA_TEXT, "学校：${schoolName}\n教务系统：${systemType}\n请协助适配该校教务系统")
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "申请已提交，感谢您的支持！", Toast.LENGTH_SHORT).show()
                        }
                        onDismiss()
                    }
                }
            ) {
                Text("发送反馈", color = colors.accentMain)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
internal fun ShareExportDialog(gson: Gson, app: PocketXiaoliApplication, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var shareCode by remember { mutableStateOf("") }
    var generated by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val courses = app.repository.allCourses.firstOrNull() ?: emptyList()
            val backup = CourseBackup(
                exportTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                courses = courses
            )
            val json = gson.toJson(backup)
            shareCode = Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            generated = true
        }
    }

    val colors = LocalPocketXiaoliColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("分享口令") },
        text = {
            Column {
                if (!generated) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = colors.accentMain, strokeWidth = 2.dp)
                } else {
                    Text("复制以下口令分享给好友：", fontSize = 13.sp, color = colors.textSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = shareCode,
                        onValueChange = {},
                        readOnly = true,
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("share_code", shareCode))
                            Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentMain),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("复制口令") }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

/**
 * 已删除：`AiNameDialog`（「鲤鱼的名字」改名对话框）。
 *
 * 删它不是因为难看，是因为**它承诺的事是假的**：
 *
 * - 对话框正文写着"这是它在你手机上的称呼，用来在对话里称呼你"，但
 *   `AssistantLlmConfig.defaultSystemPrompt()` 是写死的（"你是「鲤鱼」…"），
 *   `AssistantChatViewModel` 也**从不把 aiName 或用户昵称发给模型** ——
 *   改完名字，AI 在对话里既不会换称呼，也不会知道用户叫什么。
 * - 它唯一的写入方 `container.setAiName()` 除自己以外没有任何读取方
 *   （`AppContainer.aiName` 这个 StateFlow 只有本对话框在读）。
 *
 * 而且它一直**没有入口**：`showPetDialog` 从没有一处置为 true，
 * 是"宠物"概念整组下线时留下的最后一个孤儿。
 *
 * 要恢复这个能力，正确做法是**先让提示词真的用上它**（把称呼拼进
 * `defaultSystemPrompt`），再把入口加回设置页；现在直接复活对话框等于把一个
 * 看着能用、实际无效的开关还给用户。
 */

@Composable
internal fun AboutDialog(
    onDismiss: () -> Unit,
    context: android.content.Context,
    /** 连点版本号 7 次后回调；已解锁时为 null（不再重复触发） */
    onUnlockDeveloper: (() -> Unit)? = null,
) {
    val versionName = try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "未知" } catch (_: Exception) { "未知" }
    // 隐藏入口：连点版本号 7 次解锁开发者模式。
    // 用本地计数即可 —— 用户中途关掉对话框就重新数，符合"连点"的直觉。
    var tapCount by remember { mutableStateOf(0) }
    val canUnlock = onUnlockDeveloper != null
    val colors = LocalPocketXiaoliColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("关于口袋小鲤") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(colors.surfaceHighlight), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.School, null, tint = colors.accentMain, modifier = Modifier.size(32.dp))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("口袋小鲤", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary)
                Text(
                    text = "v$versionName",
                    fontSize = 14.sp,
                    color = colors.textSecondary,
                    modifier = Modifier.clickable(enabled = canUnlock) {
                        tapCount += 1
                        if (tapCount >= 7) {
                            tapCount = 0
                            onUnlockDeveloper?.invoke()
                        }
                    }
                )
                if (canUnlock && tapCount in 3..6) {
                    Text("再点 ${7 - tapCount} 次开启开发者模式", fontSize = 11.sp, color = colors.textTertiary)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("纯净无广告 · 开源 · 免费", fontSize = 13.sp, color = colors.textTertiary)
                Text("烟台理工学院专用版", fontSize = 12.sp, color = colors.textTertiary)
                Spacer(modifier = Modifier.height(4.dp))
                Text("所有数据仅存储在手机本地", fontSize = 12.sp, color = colors.textTertiary)
                Spacer(modifier = Modifier.height(10.dp))
                Text("个人开发的第三方工具，非烟台理工学院官方应用", fontSize = 11.sp, color = colors.textTertiary)
                Text("与学校无隶属关系，未获学校授权或背书", fontSize = 11.sp, color = colors.textTertiary)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

// ═══════════════════════════════════════════
//  完整备份口令
// ═══════════════════════════════════════════

/**
 * 完整备份的口令输入框。
 *
 * ## 为什么要有这一层
 *
 * 导出的完整备份是**全量用户数据**（课表、成绩存档、待办、笔记、绑定的教务账号标识）。
 * 明文导出的文件一旦被同步到网盘、发到微信、留在电脑桌面，就等于把这些数据公开了。
 * 发布门禁要求「加密备份」，这个框就是加密的入口。
 *
 * ## 两条必须说清楚的约束（都直接写在界面上）
 *
 * 1. **口令不保存在 App 里**，也不进 keystore —— 忘了就真的解不开（这是我们能提供的
 *    唯一保证：没有后门，也就没有「帮用户找回」的可能）。所以导出要求输两次。
 * 2. 恢复时除格式外不做任何校验：解不开就是口令不对或文件损坏，不给更细的提示
 *    （区分二者会泄露信息）。
 *
 * @param confirmNeeded true = 导出流程（要求输两次且一致）；false = 恢复流程（输一次即可）
 * @param onConfirm 校验通过后回调口令明文
 */
@Composable
internal fun BackupPassphraseDialog(
    title: String,
    helper: String,
    confirmNeeded: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(skin.radiusCard),
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(helper, fontSize = 12.sp, color = colors.textSecondary, lineHeight = 18.sp)
                Spacer(Modifier.height(skin.spaceSm))
                PassphraseField(
                    value = first,
                    hint = if (confirmNeeded) "设置口令（至少 8 位）" else "输入备份口令",
                    onValueChange = { first = it; error = null },
                )
                if (confirmNeeded) {
                    Spacer(Modifier.height(skin.spaceSm))
                    PassphraseField(
                        value = second,
                        hint = "再输一次",
                        onValueChange = { second = it; error = null },
                    )
                }
                error?.let {
                    Spacer(Modifier.height(skin.spaceSm))
                    Text(it, fontSize = 12.sp, color = colors.danger)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val problem = BackupCrypto.passphraseProblem(first.toCharArray())
                    error = when {
                        problem != null -> problem
                        confirmNeeded && first != second -> "两次输入的口令不一致"
                        else -> null
                    }
                    if (error == null) onConfirm(first)
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentMain),
                shape = RoundedCornerShape(skin.radiusChip),
            ) {
                Text("确定", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = colors.textSecondary) }
        }
    )
}

@Composable
private fun PassphraseField(
    value: String,
    hint: String,
    onValueChange: (String) -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(hint, color = colors.textTertiary) },
        singleLine = true,
        // 口令必须遮起来：这个框很可能是用户在教室里打开的。
        // 用共享的 PocketPasswordTransformation，不要在调用点内联新建（见 PocketField.kt:
        // InlinePasswordTransformationTest —— 每次重组新建实例会让中文输入法组合区发抖、丢字）。
        visualTransformation = com.pocket.xiaoli.ui.components.PocketPasswordTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusChip),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.accentMain,
            cursorColor = colors.accentMain,
            focusedContainerColor = colors.surfaceBase.copy(alpha = 0.5f),
            unfocusedContainerColor = colors.surfaceBase.copy(alpha = 0.5f),
        )
    )
}
