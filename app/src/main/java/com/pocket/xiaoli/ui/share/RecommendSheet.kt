package com.pocket.xiaoli.ui.share

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/**
 * 「推荐给同学」气泡：**轻点入口**看到的两样东西 —— 一个能扫的码、两条能复制的链接。
 *
 * 长按入口那一路不发这个气泡（直接起系统分享面板），所以本组件只管"看"与"复制"。
 *
 * 外壳用 MIUIX `WindowBottomSheet`，与「发现新版本」气泡同一套（2026-10-17 用户反馈
 * Material3 的 ModalBottomSheet 会撑到半屏，哪怕内容只有三行）。
 */
@Composable
fun RecommendSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    onShareApk: () -> Unit,
) {
    if (!show) return
    val context = LocalContext.current
    val qr = rememberQrCode(AppShare.GITEE_URL, sizePx = 512)

    WindowBottomSheet(
        show = show,
        title = "推荐给同学",
        onDismissRequest = onDismiss,
    ) {
        RecommendSheetBody(
            qr = qr,
            onCopyLink = { url ->
                AppShare.copy(context, "口袋小鲤下载地址", url)
                Toast.makeText(context, "链接已复制", Toast.LENGTH_SHORT).show()
            },
            onOpenLink = { url -> AppShare.openUrl(context, url) },
            onShareApk = onShareApk,
        )
    }
}

/**
 * 气泡的**内容**（与窗口外壳分开，只为了能单独截图）。
 *
 * 这里没有 `show`、没有窗口、也不读 Context —— 复制/打开/分享都由调用方给。
 * 拆出来的唯一理由是基准图：`WindowBottomSheet` 是独立窗口，JVM 截图抓不到它；
 * 而这一块的排版（二维码大小、两行链接的高度、按钮顺序）恰恰是最容易改坏的部分。
 */
@Composable
fun RecommendSheetBody(
    qr: android.graphics.Bitmap?,
    onCopyLink: (String) -> Unit,
    onOpenLink: (String) -> Unit,
    onShareApk: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── 二维码 ──
        // 白底是**功能需要**，不是主题选择：深色底 + 浅色码块，不少相机扫不动。
        if (qr != null) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(148.dp)
                        .clip(RoundedCornerShape(skin.radiusCard))
                        .background(Color.White)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = qr.asImageBitmap(),
                        contentDescription = "下载地址二维码",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "同学扫码打开下载页",
                    fontSize = 12.sp,
                    color = colors.textTertiary,
                )
            }
            Spacer(Modifier.height(14.dp))
        }

        LinkRow(
            label = "Gitee（国内，推荐）",
            url = AppShare.GITEE_URL,
            onCopy = { onCopyLink(AppShare.GITEE_URL) },
            onOpen = { onOpenLink(AppShare.GITEE_URL) },
        )
        Spacer(Modifier.height(8.dp))
        LinkRow(
            label = "GitHub（备用）",
            url = AppShare.GITHUB_URL,
            onCopy = { onCopyLink(AppShare.GITHUB_URL) },
            onOpen = { onOpenLink(AppShare.GITHUB_URL) },
        )

        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onShareApk,
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.accentMain),
        ) {
            Text("分享安装包", fontSize = 14.sp)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            "直接把你手机里这个版本发过去，对方点开就能装",
            fontSize = 11.sp,
            color = colors.textTertiary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        TextButton(
            onClick = { onCopyLink(AppShare.GITEE_URL) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
        ) {
            Text("复制链接", fontSize = 13.sp, color = colors.textSecondary)
        }
        Spacer(Modifier.height(8.dp))
        Spacer(Modifier.padding(bottom = 4.dp))
    }
}

/**
 * 一行链接：左列「标签 + 网址」，右侧「打开」。
 *
 * 点整行 = 复制，点「打开」= 跳浏览器 —— 这正是「点一下展示下载链接」的语义：
 * 链接摆出来、想复制就复制，**不会因为手滑直接跳走**。
 */
@Composable
private fun LinkRow(
    label: String,
    url: String,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(skin.radiusCard))
            .background(colors.surfaceAlt)
            .clickable(onClick = onCopy)
            .heightIn(min = 52.dp)
            .padding(start = skin.spaceMd, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 12.sp, color = colors.textTertiary)
            Spacer(Modifier.height(1.dp))
            Text(
                url,
                fontSize = 11.sp,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(
            onClick = onOpen,
            modifier = Modifier.heightIn(min = 44.dp),
        ) {
            Text("打开", fontSize = 12.sp, color = colors.accentMain, fontWeight = FontWeight.Medium)
        }
    }
}
