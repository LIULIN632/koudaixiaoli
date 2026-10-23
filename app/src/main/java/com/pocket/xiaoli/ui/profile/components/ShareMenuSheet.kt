package com.pocket.xiaoli.ui.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.ui.components.SectionCaption
import com.pocket.xiaoli.ui.theme.LocalDarkMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.ServiceAccent
import com.pocket.xiaoli.ui.theme.miuixPress
import com.pocket.xiaoli.ui.theme.serviceAccentColor
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/**
 * 「备份与分享」：把原来一条七项的平铺菜单按**用途**拆成三组。
 *
 * ## 为什么改
 *
 * 旧实现是 `AlertDialog` 里七行同权重的文字（无副标题）：
 * 「导出备份文件」和「导出完整备份」只差两个字，用户没法判断该点哪个；
 * 「恢复完整备份」紧挨在最后，看起来与导出同级，但它会**覆盖当前数据**。
 *
 * 现在：
 * - 分组告诉用户"这是分享 / 这是导出 / 这是整机备份"；
 * - 每行都有副标题，说明产物是什么、用在哪；
 * - 恢复项用警示色，与危险操作同语义；
 * - 外壳换成 [WindowBottomSheet]，长菜单不再挤在对话框里。
 *
 * ⚠️ 恢复项**不做二次确认**：调用方 `ProfileDialogHost` 在选完之后会弹出
 * 口令输入框，并再次说明会覆盖当前数据 —— 真正的闸门在那里，不重复设卡。
 */
@Composable
internal fun ShareMenuDialog(
    onDismiss: () -> Unit,
    onGenerateCode: () -> Unit,
    onExportBackup: () -> Unit,
    onExportCsv: () -> Unit,
    onExportIcs: () -> Unit = {},
    onExportFullBackup: () -> Unit = {},
    onRestoreFullBackup: () -> Unit = {},
    onExportImage: () -> Unit = {}
) {
    WindowBottomSheet(
        show = true,
        title = "备份与分享",
        onDismissRequest = onDismiss,
    ) {
        ShareMenuBody(
            onGenerateCode = onGenerateCode,
            onExportImage = onExportImage,
            onExportBackup = onExportBackup,
            onExportCsv = onExportCsv,
            onExportIcs = onExportIcs,
            onExportFullBackup = onExportFullBackup,
            onRestoreFullBackup = onRestoreFullBackup,
        )
    }
}

/**
 * 抽屉正文（与窗口外壳分开，只为了能单独截图）。
 *
 * `WindowBottomSheet` 是独立窗口，JVM 快照抓不到它 —— 与 `RecommendSheetBody`
 * / `AppUpdateSheetBody` / `SchemeManagerBody` 同一条限制。
 */
@Composable
internal fun ShareMenuBody(
    onGenerateCode: () -> Unit,
    onExportBackup: () -> Unit,
    onExportCsv: () -> Unit,
    onExportIcs: () -> Unit,
    onExportFullBackup: () -> Unit,
    onRestoreFullBackup: () -> Unit,
    onExportImage: () -> Unit,
) {
    val skin = LocalSkin.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        SectionCaption("分享给同学")
        Spacer(Modifier.height(skin.spaceSm))
        ShareGroup {
            ShareActionRow(
                icon = Icons.Outlined.Tag,
                title = "生成分享口令",
                subtitle = "同学粘贴口令就能导入这张课表",
                accent = ServiceAccent.ACADEMIC,
                onClick = onGenerateCode,
            )
            GroupDivider()
            ShareActionRow(
                icon = Icons.Outlined.Image,
                title = "导出课表图片",
                subtitle = "一张 PNG，发群里或存相册",
                accent = ServiceAccent.QUERY,
                onClick = onExportImage,
            )
        }

        Spacer(Modifier.height(skin.spaceLg))
        SectionCaption("导出课表")
        Spacer(Modifier.height(skin.spaceSm))
        ShareGroup {
            ShareActionRow(
                icon = Icons.Outlined.Backup,
                title = "课表备份文件",
                subtitle = "JSON 格式，在本 App 里恢复",
                accent = ServiceAccent.ACADEMIC,
                onClick = onExportBackup,
            )
            GroupDivider()
            ShareActionRow(
                icon = Icons.Outlined.TableChart,
                title = "Excel / CSV",
                subtitle = "用电脑打开、打印或排课",
                accent = ServiceAccent.LIFE,
                onClick = onExportCsv,
            )
            GroupDivider()
            ShareActionRow(
                icon = Icons.Outlined.CalendarMonth,
                title = ".ics 日历文件",
                subtitle = "导入手机系统日历，课前自动提醒",
                accent = ServiceAccent.INFO,
                onClick = onExportIcs,
            )
        }

        Spacer(Modifier.height(skin.spaceLg))
        SectionCaption("完整备份")
        Spacer(Modifier.height(skin.spaceMicro))
        Text(
            text = "课表、成绩存档、待办与笔记一起打包",
            fontSize = 11.sp,
            color = LocalPocketXiaoliColors.current.textTertiary,
        )
        Spacer(Modifier.height(skin.spaceSm))
        ShareGroup {
            ShareActionRow(
                icon = Icons.Outlined.Backup,
                title = "导出完整备份",
                subtitle = "全量数据，需要设置备份口令",
                accent = ServiceAccent.STUDY,
                onClick = onExportFullBackup,
            )
            GroupDivider()
            ShareActionRow(
                icon = Icons.Outlined.Restore,
                title = "恢复完整备份",
                subtitle = "用备份文件覆盖当前数据",
                accent = ServiceAccent.INFO,
                danger = true,
                onClick = onRestoreFullBackup,
            )
        }
        Spacer(Modifier.height(skin.spaceSm))
    }
}

@Composable
private fun ShareGroup(content: @Composable ColumnScope.() -> Unit) {
    val skin = LocalSkin.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(skin.radiusCard))
            .background(LocalPocketXiaoliColors.current.surfaceAlt),
        content = content,
    )
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(
        color = LocalPocketXiaoliColors.current.borderDivider,
        modifier = Modifier.padding(start = 62.dp),
    )
}

@Composable
private fun ShareActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: ServiceAccent,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalPocketXiaoliColors.current
    val isDark = LocalDarkMode.current
    val skin = LocalSkin.current
    val tint = if (danger) colors.danger else serviceAccentColor(accent)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .miuixPress(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = skin.spaceMd, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = if (isDark) 0.24f else 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(skin.spaceSm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (danger) colors.danger else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}
