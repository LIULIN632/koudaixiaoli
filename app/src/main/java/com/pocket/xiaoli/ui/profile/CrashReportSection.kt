package com.pocket.xiaoli.ui.profile

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pocket.xiaoli.data.feedback.CrashReportClient
import com.pocket.xiaoli.data.feedback.FeedbackFormatter
import com.pocket.xiaoli.data.feedback.SubmitOutcome
import com.pocket.xiaoli.data.feedback.FeedbackStore
import com.pocket.xiaoli.ui.components.AppCard
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「闪退了？把日志发给我」那一段。
 *
 * 位置在反馈页、设备信息卡下面 —— 顺序是刻意的：用户要么是来提意见的（上面那块），
 * 要么是**刚崩过**来抱怨的（这块），后者一眼就能看到「上次崩溃」而不是让他自己去描述现象。
 *
 * 只在**确实是崩溃日志、且这台设备配了服务器通道**时出现；两个条件任一不满足就整块隐藏，
 * 不做"点了没反应"的假按钮（与 [FeedbackSendSection] 同一条纪律）。
 */
@Composable
internal fun ColumnScope.CrashReportSection(
    remoteReady: Boolean,
    onSent: () -> Unit,
) {
    val context = LocalContext.current
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val scope = rememberCoroutineScope()
    val store = remember { FeedbackStore(context) }

    var pending by remember { mutableStateOf(CrashReportClient.pending(context)) }
    var sending by remember { mutableStateOf<String?>(null) }
    var lastResult by remember { mutableStateOf<String?>(null) }

    if (!remoteReady || pending.isEmpty()) return

    Spacer(Modifier.height(skin.spaceMd))
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = colors.surfaceAlt,
    ) {
        Column(Modifier.padding(skin.spaceMd), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.BugReport,
                    contentDescription = null,
                    tint = colors.accentMain,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "上次崩溃的日志",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${pending.size} 份待发",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }
            Text(
                "发之前会把本机路径里带名字的那段抹掉，只留版本、机型、堆栈。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )

            pending.take(3).forEach { file ->
                CrashLogRow(
                    file = file,
                    busy = sending == file.name,
                    onSend = {
                        if (sending != null) return@CrashLogRow
                        scope.launch {
                            sending = file.name
                            val now = System.currentTimeMillis()
                            val ticket = FeedbackFormatter.ticketId(now, file.name.hashCode() and 0xFFFFFF)
                            val outcome = CrashReportClient.send(
                                context = context,
                                file = file,
                                note = "",
                                anonId = store.anonId(),
                                ticket = ticket,
                            )
                            sending = null
                            when (outcome) {
                                is SubmitOutcome.Sent -> {
                                    lastResult = "已发送 · 工单 $ticket"
                                    pending = CrashReportClient.pending(context)
                                    onSent()
                                }

                                is SubmitOutcome.Retryable ->
                                    lastResult = outcome.reason

                                is SubmitOutcome.Rejected ->
                                    lastResult = outcome.reason
                            }
                            Toast.makeText(context, lastResult, Toast.LENGTH_LONG).show()
                        }
                    },
                )
            }

            if (lastResult != null) {
                Text(
                    lastResult.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }
        }
    }

    // 页面停留期间又崩了的话，下次进来列表要重新读一次——这里只在首帧同步一次，
    // 真正的刷新由上面的发送回调触发，避免每帧读目录。
    LaunchedEffect(Unit) { pending = CrashReportClient.pending(context) }
}

@Composable
private fun CrashLogRow(
    file: File,
    busy: Boolean,
    onSend: () -> Unit,
) {
    val colors = LocalPocketXiaoliColors.current
    val whenText = remember(file) {
        SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(file.lastModified()))
    }
    val headline = remember(file) {
        runCatching { CrashReportClient.headline(file.readText()) }.getOrDefault("（读不出来）")
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                headline,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textPrimary,
                maxLines = 2,
            )
            Text(
                "$whenText · ${(file.length() / 1024).coerceAtLeast(1)} KB",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        }
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = colors.accentMain,
            )
        } else {
            TextButton(onClick = onSend, shape = RoundedCornerShape(10.dp)) {
                Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("发送", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
