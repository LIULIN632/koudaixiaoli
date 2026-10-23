package com.pocket.xiaoli.ui.legal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.legal.AgreementText
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin

/**
 * 用户协议与免责声明的两种用法。
 *
 * | 模式 | 谁在用 | 底部 |
 * |---|---|---|
 * | [READ] | 设置 → 关于 → 用户协议 | 只有返回 |
 * | [CONSENT] | 首次启动（或条款改版后的第一次启动） | 「不同意，退出」+「同意并继续」 |
 *
 * 为什么首启是**整页**而不是弹窗（聚在工大是弹窗）：这份文本有十条、滚三屏，
 * 塞进 `AlertDialog` 会被压成碎句，等于没有让人"真正读到"。整页只有一个代价 ——
 * 不能点空白关掉，而这恰好是我们要的：同意是一个动作，不是滑一下。
 *
 * 视觉与设置页同一套令牌（`LocalSkin` 间距 + `LocalPocketXiaoliColors` 色），
 * 不另起一套字号，避免"协议页像是另一个 App"。
 */
internal enum class AgreementMode { READ, CONSENT }

@Composable
internal fun UserAgreementScreen(
    mode: AgreementMode,
    onAccept: () -> Unit = {},
    onDecline: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val colors = LocalPocketXiaoliColors.current
    val skin = LocalSkin.current

    // 首启模式：系统返回键 = 不同意（否则会退到一个"没同意也能用"的空白页）
    // 阅读模式：系统返回键 = 关掉这一页（与左上角返回一致，不能让它穿透到设置页）
    if (mode == AgreementMode.CONSENT) {
        BackHandler { onDecline() }
    } else {
        BackHandler { onBack() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceBase)
            .statusBarsPadding(),
    ) {
        // ── 顶栏 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (mode == AgreementMode.READ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = colors.textSecondary,
                    )
                }
            } else {
                Spacer(Modifier.padding(start = 12.dp))
            }
            Text(
                text = "用户协议与免责声明",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )
        }

        // ── 正文（唯一可滚的区域）──
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = skin.spaceLg),
            verticalArrangement = Arrangement.spacedBy(skin.spaceMd),
        ) {
            Text(
                text = AgreementText.PREAMBLE.replace("**", ""),
                fontSize = 13.sp,
                lineHeight = 21.sp,
                color = colors.textSecondary,
            )
            Text(
                text = "版本 v${AgreementText.VERSION} · 生效日期 ${AgreementText.EFFECTIVE_DATE}",
                fontSize = 12.sp,
                color = colors.textTertiary,
            )
            AgreementText.SECTIONS.forEach { section ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (section.title.isNotEmpty()) {
                        Text(
                            text = section.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                        )
                    }
                    section.paragraphs.forEach { paragraph ->
                        EmphasizedText(paragraph, colors.textSecondary)
                    }
                }
            }
            Spacer(Modifier.height(skin.spaceWide))
        }

        // ── 底部动作 ──
        if (mode == AgreementMode.CONSENT) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = skin.spaceLg, vertical = skin.spaceMd),
                verticalArrangement = Arrangement.spacedBy(skin.spaceSm),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(skin.spaceSm),
                ) {
                    OutlinedButton(
                        onClick = onDecline,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("不同意，退出", fontSize = 14.sp)
                    }
                    Button(
                        onClick = onAccept,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentMain),
                    ) {
                        Text("同意并继续", fontSize = 14.sp, color = Color.White)
                    }
                }
                Text(
                    text = "同意后可在「我的 → 设置 → 关于 → 隐私」里随时查看本协议",
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 11.sp,
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * 把正文里的 `**强调**` 渲染成粗体。
 *
 * 协议文本用 Markdown 的星号标记重点，而这里没有 Markdown 渲染器 ——
 * 用一个 5 行的分段器就够了：按 `**` 切开，奇数段加粗。**别直接 `replace("**","")`**，
 * 那样"不上传"这类关键承诺会和普通文字一样轻，失去存在的意义。
 */
@Composable
private fun EmphasizedText(text: String, color: Color) {
    val annotated = buildAnnotatedString {
        text.split("**").forEachIndexed { index, part ->
            if (index % 2 == 1) {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = color)) { append(part) }
            } else {
                append(part)
            }
        }
    }
    Text(text = annotated, fontSize = 13.sp, lineHeight = 21.sp, color = color)
}
