package com.pocket.xiaoli.ui.import_

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.model.LastImportInfo
import com.pocket.xiaoli.ui.components.AppCard
import com.pocket.xiaoli.ui.components.SectionCaption
import com.pocket.xiaoli.ui.theme.LocalDarkMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.ServiceAccent
import com.pocket.xiaoli.ui.theme.serviceAccentColor
import com.pocket.xiaoli.ui.theme.serviceAccentContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「导入课表」的入口页：选一种方式，或直接看上一次导入的结果。
 *
 * ## 2026-10 改版的三个决定
 *
 * 1. **先分组再列卡**：网页导入 / 教务直连属于"自动"，文件导入是"兜底"。
 *    旧版三张卡同权重平铺，用户要读完三行描述才知道该点哪个。
 * 2. **每张卡给图标与语义色**：网页=语言、直连=闪电、文件=文件夹。
 *    颜色仍走 `ServiceAccent`，不新增硬编码色（跟随 8 套配色与深浅色）。
 * 3. **最近导入变成状态卡**：成功/失败用语义色 + 图标表达，成功时直接给「去课表」。
 *    没有记录时也明确说"这里以后会显示什么"，而不是只写"还没有导入记录"。
 *
 * ## 学号不再明文展示
 *
 * 旧版在锁定提示里直接拼了学号（`学号 2023xxxx 将自动填入`）。本页是会被截图分享的
 * 入口页，学号没有必要出现 —— 现在只说"学号已自动填入"，账号本身仍在预填逻辑里。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableImportEntryScreen(
    onBack: () -> Unit,
    onWebImport: () -> Unit,
    onDirectImport: () -> Unit,
    onFileImport: (() -> Unit)? = null,
    lastImport: LastImportInfo? = null,
    onOpenTimetable: (() -> Unit)? = null,
    onOpenHistory: (() -> Unit)? = null,

    lockedSchoolName: String? = null,
    boundStudentId: String? = null,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Scaffold(
        containerColor = colors.surfaceBase,
        topBar = {
            TopAppBar(
                title = { Text("导入课表", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surfaceCard)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = skin.spaceLg, vertical = skin.spaceMd),
            verticalArrangement = Arrangement.spacedBy(skin.spaceMd)
        ) {
            ImportHeroCard(
                lockedSchoolName = lockedSchoolName,
                hasBoundStudentId = !boundStudentId.isNullOrBlank(),
            )

            SectionCaption("自动导入")
            ImportOptionCard(
                icon = Icons.Outlined.Language,
                title = "网页导入",
                description = if (lockedSchoolName != null) {
                    "$lockedSchoolName · 登录后在页面里自动识别课表"
                } else {
                    "打开教务网页登录后，自动识别本学期课表"
                },
                tag = "通用",
                accent = ServiceAccent.QUERY,
                onClick = onWebImport,
            )
            ImportOptionCard(
                icon = Icons.Outlined.Bolt,
                title = "教务直连导入",
                description = if (lockedSchoolName != null) {
                    "$lockedSchoolName · 账号已自动填入"
                } else {
                    "用绑定的教务账号直连，少走一层网页登录"
                },
                tag = "更快",
                accent = ServiceAccent.ACADEMIC,
                onClick = onDirectImport,
            )

            if (onFileImport != null) {
                Spacer(Modifier.height(skin.spaceMicro))
                SectionCaption("手动兜底")
                ImportOptionCard(
                    icon = Icons.Outlined.FolderOpen,
                    title = "文件导入",
                    description = "Excel / HTML / CSV / JSON / PDF 都能试",
                    tag = "兜底",
                    accent = ServiceAccent.LIFE,
                    onClick = onFileImport,
                )
            }

            Spacer(Modifier.height(skin.spaceMicro))
            SectionCaption("最近一次")
            RecentImportCard(
                lastImport = lastImport,
                onOpenTimetable = onOpenTimetable,
                onOpenHistory = onOpenHistory
            )
        }
    }
}

/** 顶部说明卡：这一页只做一件事 —— 把课表搬进来。 */
@Composable
private fun ImportHeroCard(
    lockedSchoolName: String?,
    hasBoundStudentId: Boolean,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val isDark = LocalDarkMode.current
    val accent = serviceAccentColor(ServiceAccent.ACADEMIC)

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = serviceAccentContainer(ServiceAccent.ACADEMIC),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(skin.spaceLg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(accent.copy(alpha = if (isDark) 0.22f else 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.FileDownload,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(skin.spaceMd))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "把课表搬进来",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "登录一次教务，之后打开就是最新的",
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                )
                if (lockedSchoolName != null) {
                    Spacer(Modifier.height(skin.spaceSm))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = colors.accentMain,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = buildString {
                                append("已锁定 ").append(lockedSchoolName)
                                if (hasBoundStudentId) append(" · 学号已自动填入")
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.accentMain,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** 一种导入方式（入口页与文件页共用）：图标块 + 标题/说明 + 小标签。 */
@Composable
internal fun ImportOptionCard(
    icon: ImageVector,
    title: String,
    description: String,
    tag: String,
    accent: ServiceAccent,
    onClick: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val isDark = LocalDarkMode.current
    val tint = serviceAccentColor(accent)

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .padding(horizontal = skin.spaceMd, vertical = skin.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(tint.copy(alpha = if (isDark) 0.24f else 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(skin.spaceSm))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(6.dp))
                    MethodTag(text = tag, tint = tint)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(skin.spaceXs))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun MethodTag(text: String, tint: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(tint.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 1.dp),
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = tint,
            maxLines = 1,
        )
    }
}

/** 最近一次导入：成功/失败都是**状态**，要一眼看出来。 */
@Composable
private fun RecentImportCard(
    lastImport: LastImportInfo?,
    onOpenTimetable: (() -> Unit)?,
    onOpenHistory: (() -> Unit)?,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val isDark = LocalDarkMode.current
    val info = lastImport?.sanitized()

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = skin.spaceMd, vertical = skin.spaceMd),
        ) {
            if (info == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surfaceAlt),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = null,
                            tint = colors.textTertiary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(skin.spaceSm))
                    Column {
                        Text("还没有导入记录", fontSize = 14.sp, color = colors.textSecondary)
                        Text(
                            "导入成功后，这里会显示来源与时间",
                            fontSize = 11.sp,
                            color = colors.textTertiary,
                        )
                    }
                }
                return@Column
            }

            val success = info.success
            val statusTint = if (success) colors.success else colors.danger
            val time = remember(info.timestamp) {
                if (info.timestamp <= 0L) {
                    "时间未知"
                } else {
                    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(info.timestamp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(statusTint.copy(alpha = if (isDark) 0.24f else 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (success) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = statusTint,
                        modifier = Modifier.size(21.dp),
                    )
                }
                Spacer(Modifier.width(skin.spaceSm))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = info.source.ifBlank { "课表导入" },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(6.dp))
                        MethodTag(text = if (success) "成功" else "失败", tint = statusTint)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = when {
                            success -> "导入 ${info.courseCount} 门课程"
                            !info.detail.isNullOrBlank() -> info.detail.orEmpty()
                            else -> "这次没有导入成功，可换一种方式再试"
                        },
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = time,
                        fontSize = 11.sp,
                        color = colors.textTertiary,
                    )
                }
            }

            val showHistory = onOpenHistory != null
            val showTimetable = success && onOpenTimetable != null
            if (showHistory || showTimetable) {
                Spacer(Modifier.height(skin.spaceMicro))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (showHistory) {
                        TextButton(
                            onClick = onOpenHistory,
                            modifier = Modifier.heightIn(min = 44.dp),
                        ) {
                            Text("查看全部", color = colors.textSecondary, fontSize = 13.sp)
                        }
                    }
                    if (showTimetable) {
                        TextButton(
                            onClick = onOpenTimetable,
                            modifier = Modifier.heightIn(min = 44.dp),
                        ) {
                            Text(
                                "去课表",
                                color = colors.accentMain,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}
