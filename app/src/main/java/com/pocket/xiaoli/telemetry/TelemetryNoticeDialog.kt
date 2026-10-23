package com.pocket.xiaoli.telemetry

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * 首启一次性告知。
 *
 * 为什么是"告知 + 当场可拒"，而不是"默默开着 + 设置里藏个开关"：
 * 默认开这种东西，用户只有在**第一次知道它存在**的时候才谈得上同意。
 * 关掉之后 `Telemetry` 连队列都不写（见 `TelemetryStore` 的注释），不是发不发的问题，
 * 是本机不再产生这类数据。
 *
 * 只在真正第一次启动时出现一次（`noticeSeen`），之后不再打扰 —— 想改主意去
 * 「设置 → 关于口袋小鲤 → 隐私」。
 */
@Composable
fun TelemetryNoticeDialog() {
    val context = LocalContext.current
    var visible by remember { mutableStateOf(Telemetry.shouldShowNotice(context)) }
    if (!visible) return

    fun close() {
        Telemetry.markNoticeShown(context)
        visible = false
    }

    AlertDialog(
        onDismissRequest = { close() },
        title = { Text("关于匿名统计") },
        text = {
            Text(
                "为了知道有多少人在用、更新提示有没有人点，App 会发送几条匿名事件：" +
                    "启动、更新提示、点击更新、下载完成、崩溃次数，以及版本号。\n\n" +
                    "不上传设备型号、学号、课表，也不上传任何内容。标识符是随机生成的，" +
                    "每 30 天换一个新的，没有任何接口能按它查到你。\n\n" +
                    "随时可以在「设置 → 关于口袋小鲤 → 隐私」里关掉。"
            )
        },
        confirmButton = {
            TextButton(onClick = { close() }) { Text("知道了") }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    Telemetry.setEnabled(context, false)
                    close()
                }
            ) { Text("不要统计") }
        },
    )
}
