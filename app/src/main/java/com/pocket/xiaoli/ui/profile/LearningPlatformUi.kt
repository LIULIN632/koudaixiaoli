package com.pocket.xiaoli.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import com.pocket.xiaoli.data.entity.HomeworkEntity
import com.pocket.xiaoli.data.learning.ChaoxingClient
import com.pocket.xiaoli.data.learning.LearningHomework
import com.pocket.xiaoli.data.learning.ZhihuishuClient
import kotlinx.coroutines.launch
import com.pocket.xiaoli.data.learning.LearningPlatform
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.LocalAppearanceMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin


@Composable
private fun LearningPlatformTile(
    platform: LearningPlatform,
    account: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val isPure = LocalAppearanceMode.current == AppearanceMode.PURE
    val bound = !account.isNullOrBlank()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(skin.radiusCard))
            .background(if (isPure) colors.surfaceHighlight else if (bound) Color(0xFFDDF3EA) else Color(0xFFEEF2F7))
            .clickable(onClick = onClick)
            .padding(skin.spaceCompact)
    ) {
        Text(
            text = platform.label,
            color = colors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(skin.spaceXs))
        Text(
            text = if (bound) "已绑定 ${maskLearningAccount(account.orEmpty())}" else "未绑定",
            color = if (bound) colors.accentMain else colors.textSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)

@Composable
fun LearningManualImportDialog(
    onConfirm: (type: String, text: String) -> Unit,
    onDismiss: () -> Unit
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    var type by remember { mutableStateOf("exam") }
    var text by remember { mutableStateOf("") }
    val hint = when (type) {
        "exam" -> "每行一条：课程名,考试名称,截止日期\n例如：高等数学,期中考试,2025-11-20"
        "homework" -> "每行一条：课程名,作业标题,截止日期\n例如：高等数学,第三章作业,2025-11-20"
        else -> "每行一条：标题,内容,时间\n例如：选课通知,下学期选课开始,2025-11-18"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("同步学习平台内容") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                    FilterChip(
                        selected = type == "exam",
                        onClick = { type = "exam" },
                        label = { Text("考试") }
                    )
                    FilterChip(
                        selected = type == "homework",
                        onClick = { type = "homework" },
                        label = { Text("作业") }
                    )
                    FilterChip(
                        selected = type == "inbox",
                        onClick = { type = "inbox" },
                        label = { Text("收件箱") }
                    )
                }
                Text(
                    text = hint,
                    color = colors.textSecondary,
                    fontSize = 11.sp
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    label = { Text("粘贴内容") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(type, text) },
                enabled = text.isNotBlank(),
                shape = RoundedCornerShape(50)
            ) {
                Text("加入待办")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 学习账号脱敏。
 *
 * ⚠️ 实现委托给 [com.pocket.xiaoli.util.maskStudentId] —— 保留这个名字只是因为
 * 这处遮的是**学习平台账号**、不是学号（名字即语义）；**规则必须是同一套**
 *（只保留末 4 位），否则同一个账号在两个页面会被遮成不同样子。详见 StudentIdMask.kt。
 */
private fun maskLearningAccount(value: String): String =
    com.pocket.xiaoli.util.maskStudentId(value)

/**
 * 学习平台自动同步对话框。
 * 走的是真实拉取：cookie -> 课程列表 -> 课程页作业入口(带 enc) -> 作业列表。
 * 失败时保留「手动导入」兜底入口。
 */
@Composable
fun LearningSyncDialog(
    platform: LearningPlatform,
    cookie: String?,
    existing: List<HomeworkEntity>,
    onPersist: suspend (List<LearningHomework>) -> Int,
    onNeedLogin: () -> Unit,
    onOpenManualImport: () -> Unit,
    onDismiss: () -> Unit
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<List<String>>(emptyList()) }
    val sessionKeys = remember { mutableSetOf<String>() }
    val loggedIn = !cookie.isNullOrBlank()

    fun startSync() {
        val current = cookie
        if (current.isNullOrBlank() || running) return
        running = true
        message = null
        detail = emptyList()
        scope.launch {
            val client = if (platform == LearningPlatform.CHAOXING) {
                ChaoxingClient()
            } else {
                ZhihuishuClient()
            }
            client.fetchHomework(current).fold(
                onSuccess = { list ->
                    val known = existing.mapTo(mutableSetOf()) {
                        it.courseName + "|" + it.content + "|" + it.dueDate
                    }
                    val fresh = list.filter { item ->
                        val key = item.courseName + "|" + item.title + "|" + item.dueDate
                        known.add(key) && sessionKeys.add(key)
                    }
                    val added = if (fresh.isEmpty()) 0 else onPersist(fresh)
                    running = false
                    message = when {
                        list.isEmpty() -> "已扫描全部课程，没有发现待办作业"
                        added == 0 -> "拉取 " + list.size + " 条，都已存在，无需重复导入"
                        else -> "拉取 " + list.size + " 条，新增 " + added + " 条待办"
                    }
                    detail = list.take(8).map { item ->
                        item.courseName + " · " + item.title +
                            if (item.dueDate.isBlank()) "" else "（截止 " + item.dueDate + "）"
                    }
                },
                onFailure = { e ->
                    running = false
                    message = e.message ?: "同步失败，请稍后重试"
                }
            )
        }
    }

    AlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        title = { Text("同步" + platform.label + "作业", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (loggedIn) {
                        "读取你账号下各门课程的作业列表，去重后加入待办。当前支持作业，考试与通知待接入。"
                    } else {
                        "还没有登录" + platform.label + "，请先完成网页登录再同步。"
                    },
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )
                if (running) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = colors.accentMain
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("正在拉取课程与作业…", fontSize = 13.sp, color = colors.textSecondary)
                    }
                }
                message?.let { text ->
                    Text(
                        text = text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary
                    )
                }
                if (detail.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(skin.spaceTiny)) {
                        detail.forEach { line ->
                            Text(
                                text = "· " + line,
                                fontSize = 11.sp,
                                color = colors.textTertiary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (loggedIn) {
                Button(
                    onClick = { startSync() },
                    enabled = !running,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(if (running) "同步中…" else "开始同步")
                }
            } else {
                Button(onClick = onNeedLogin, shape = RoundedCornerShape(50)) {
                    Text("去登录")
                }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onOpenManualImport) { Text("手动导入", fontSize = 13.sp) }
                TextButton(onClick = onDismiss, enabled = !running) { Text("关闭") }
            }
        }
    )
}
