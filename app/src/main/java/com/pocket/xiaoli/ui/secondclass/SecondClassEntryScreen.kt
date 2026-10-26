package com.pocket.xiaoli.ui.secondclass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.ui.components.AppCard
import com.pocket.xiaoli.ui.components.ServiceHeroBadge
import com.pocket.xiaoli.ui.components.ServiceHeroCard
import com.pocket.xiaoli.ui.components.ServiceHeroChip
import com.pocket.xiaoli.ui.components.ServicePageTopBar
import com.pocket.xiaoli.ui.components.ServiceSectionLabel
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.ServiceAccent

/**
 * 「到梦空间」在手机上能给出的那一屏。
 *
 * ## 为什么不再直接打开网页
 *
 * 官网 `www.5idream.net` 的登录**只有扫码**一条路（`scan_login/getSecurityId`
 * 出二维码 → 3 秒轮询 `rollPoling` → 写 cookie `dmkj_web_token`，取证见
 * `docs/superpowers/specs/2026-10-05-secondclass-architecture-design.md` §11.6），
 * 而二维码就显示在这台手机上 —— **手机扫不到自己的屏幕**。再加上它的 CSS 是
 * `min-width: 1200px`，手机上打开就是一个缩小三倍的桌面页：看得见、用不了。
 *
 * 所以这一页的职责不是"替用户登录"（做不到，也不该绕厂商的保护），
 * 而是把**唯一走得通的那条路**说清楚：在电脑上扫码登录，手机这边负责
 * 复制网址、开桌面版网页、去装官方 App。
 */
data class SecondClassEntryState(
    /** 网页版地址（给"复制"和"打开"用）。 */
    val webUrl: String,
    /** 当前学校/校区名，用来说明"这是给谁看的"。 */
    val schoolLabel: String,
)

@Composable
fun SecondClassEntryScreen(
    state: SecondClassEntryState,
    onBack: () -> Unit,
    onOpenWeb: (url: String, title: String) -> Unit,
    onCopyUrl: () -> Unit,
    onOpenStore: () -> Unit,
) {
    Scaffold(
        topBar = { ServicePageTopBar(title = "到梦空间", onBack = onBack) },
    ) { padding ->
        SecondClassEntryContent(
            state = state,
            onOpenWeb = onOpenWeb,
            onCopyUrl = onCopyUrl,
            onOpenStore = onOpenStore,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalSkin.current.spaceLg, vertical = LocalSkin.current.spaceMd),
        )
    }
}

/**
 * 纯内容层：不读 ViewModel、不碰剪贴板，快照测试直接渲它。
 * 三个动作都从外面传进来，"点了没反应"和"没接线"在测试里就能分开。
 */
@Composable
fun SecondClassEntryContent(
    state: SecondClassEntryState,
    onOpenWeb: (url: String, title: String) -> Unit,
    onCopyUrl: () -> Unit,
    onOpenStore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(skin.spaceMd),
    ) {
        ServiceHeroCard(accent = ServiceAccent.STUDY) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    // hero 是分类色深底：文字必须白，用 textPrimary 会深字压深底
                    Text("第二课堂 · 活动报名", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(skin.spaceXs))
                    Text(
                        "${state.schoolLabel} · 学分与活动记录都在里面",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ServiceHeroBadge(Icons.Outlined.EmojiEvents)
            }
            Spacer(Modifier.height(skin.spaceSm))
            // 把"为什么打不开"直接写在主视觉上，而不是让人点进去才发现
            ServiceHeroChip("登录只支持扫码，请在电脑上打开")
        }

        ServiceSectionLabel(title = "怎么进去", accent = ServiceAccent.STUDY)
        AppCard {
            Text("网页版只能在电脑浏览器里登录", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            Spacer(Modifier.height(skin.spaceXs))
            Text(
                "到梦空间网页版没有账号密码登录，只有二维码：电脑浏览器打开登录页 → " +
                    "用手机上的「到梦空间」App 扫那个码 → 手机 App 里就能看活动、报名、查学分。" +
                    "手机浏览器自己打开时，二维码显示在同一块屏幕上，扫不了。",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = colors.textSecondary,
            )
        }

        ServiceSectionLabel(title = "这台手机上能做的", accent = ServiceAccent.STUDY)
        AppCard {
            EntryActionRow(
                icon = Icons.Outlined.ContentCopy,
                title = "复制网页地址",
                detail = state.webUrl,
                onClick = onCopyUrl,
            )
            EntryActionRow(
                icon = Icons.AutoMirrored.Outlined.OpenInNew,
                title = "在浏览器打开（桌面版）",
                detail = "网页是按 1200px 宽度排的，开桌面版才能看清点得准",
                onClick = { onOpenWeb(state.webUrl, "到梦空间") },
            )
            EntryActionRow(
                icon = Icons.Outlined.Storefront,
                title = "装官方 App",
                detail = "扫码登录与报名都在官方 App 里完成",
                last = true,
                onClick = onOpenStore,
            )
        }

        Text(
            "本应用不代登录、不代报名，也不读取你的第二课堂成绩；活动与学分以系统内显示为准。",
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = colors.textTertiary,
        )
    }
}

@Composable
private fun EntryActionRow(
    icon: ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit,
    last: Boolean = false,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(skin.radiusChip))
                .clickable(onClick = onClick)
                .padding(horizontal = skin.spaceSm, vertical = skin.spaceMd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(skin.spaceSm),
        ) {
            Icon(icon, contentDescription = null, tint = colors.accentMain, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary)
                Text(
                    detail,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = colors.textTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!last) {
            HorizontalDivider(color = colors.borderDivider)
        }
    }
}
