package com.pocket.xiaoli.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocket.xiaoli.PocketXiaoliApplication
import com.pocket.xiaoli.ui.components.AppCard
import com.pocket.xiaoli.ui.theme.*

data class VersionLog(
    val version: String,
    val date: String,
    val features: List<String> = emptyList(),
    val fixes: List<String> = emptyList(),
    val improvements: List<String> = emptyList()
)

/**
 * 手写的更新日志。
 *
 * ⚠️ **只写到当前版本为止**，更早的版本不再追补（v1.x 那一批是改名前的历史，
 * 需要时去 git 历史里翻）。最新一版的说明由 `announce/feed.json` 的 `notes` 下发，
 * 显示在本页顶部（`ReleaseNotesCard`）—— 那份是发版时填的，不需要改代码。
 *
 * 规矩：**发版时优先改 feed.json，这里只在“这一版的意义值得长期留档”时补一条。**
 * 两个地方都写也不算错，但 feed 那份是用户真正看到的“这一版改了什么”。
 */
val changelog = listOf(
    VersionLog(
        version = "v0.1.41",
        date = "2026-10-17",
        fixes = listOf(
            "修：通知抽屉一直显示「正在检查…」——拉取失败被当成了“还在加载”，现在会明确说「检查失败」",
            "修：失败时给出具体原因（哪个镜像、HTTP 几、什么异常），抽屉底部还有「重试」",
            "改：「自动更新校园数据」从更新页移到「数据与诊断」，更新页只留版本与公告一条链"
        )
    ),
    VersionLog(
        version = "v0.1.40",
        date = "2026-10-17",
        features = listOf(
            "新增更新与公告：打开 App 自动检查，首页铃铛里能看到新版本与公告",
            "后台每 15 分钟检查一次，有新版本会发通知（设置 → 更新 里可单独关掉）",
            "支持应用内下载并安装，下载前校验文件大小、文件头与 SHA-256",
            "重要公告与强制更新会弹必须确认的对话框，点外面关不掉",
            "更新说明由 feed.json 下发，改文案不必重新发版"
        )
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogScreen(onBack: () -> Unit) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    // 最新一版的说明来自更新源（feed.json 的 notes），下面手写的 changelog 只留历史。
    // 这样「发版说明」只有一处真源，不用在打包前再抄一遍进这个文件。
    val context = LocalContext.current
    val container = remember { (context.applicationContext as PocketXiaoliApplication).appContainer }
    val feedState by container.appFeedRepository.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("更新日志", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surfaceCard)
            )
        },
        containerColor = colors.surfaceBase
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(skin.spaceMd)
        ) {
            if (feedState.notes.isNotEmpty()) {
                item {
                    ReleaseNotesCard(
                        versionName = feedState.latestVersionName,
                        notes = feedState.notes,
                    )
                }
            }
            itemsIndexed(changelog) { index, log ->
                VersionCard(log, isFirst = index == 0)
            }
        }
    }
}

@Composable
internal fun ReleaseNotesCard(versionName: String, notes: List<String>) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current

    AppCard {
        Column(modifier = Modifier.padding(skin.spaceLg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(skin.radiusMicro),
                    color = colors.accentMain
                ) {
                    Text(
                        text = versionName.ifBlank { "最新版" },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.surfaceCard,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "来自发布说明",
                    fontSize = 13.sp,
                    color = colors.textSecondary
                )
            }

            Spacer(Modifier.height(skin.spaceMd))

            notes.take(10).forEach { note ->
                Row(modifier = Modifier.padding(vertical = 3.dp)) {
                    Text("·", fontSize = 14.sp, color = colors.accentMain)
                    Spacer(Modifier.width(8.dp))
                    Text(note, fontSize = 14.sp, color = colors.textSecondary)
                }
            }
        }
    }
}

@Composable
internal fun VersionCard(log: VersionLog, isFirst: Boolean) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current

    AppCard {
        Column(modifier = Modifier.padding(skin.spaceLg)) {
            // Version header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(skin.radiusMicro),
                    color = if (isFirst) colors.accentMain else colors.surfaceHighlight
                ) {
                    Text(
                        text = log.version,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isFirst) colors.surfaceCard else colors.accentMain,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = log.date,
                    fontSize = 13.sp,
                    color = colors.textSecondary
                )
            }

            Spacer(Modifier.height(skin.spaceMd))

            // Features
            if (log.features.isNotEmpty()) {
                SectionTag("新增功能", SuccessGreen)
                Spacer(Modifier.height(skin.spaceXs))
                log.features.forEach { Bullet(it) }
            }

            // Fixes
            if (log.fixes.isNotEmpty()) {
                if (log.features.isNotEmpty()) Spacer(Modifier.height(skin.spaceSmall))
                SectionTag("修复BUG", colors.danger)
                Spacer(Modifier.height(skin.spaceXs))
                log.fixes.forEach { Bullet(it) }
            }

            // Improvements
            if (log.improvements.isNotEmpty()) {
                if (log.features.isNotEmpty() || log.fixes.isNotEmpty()) Spacer(Modifier.height(skin.spaceSmall))
                SectionTag("优化改进", OrangeAccent)
                Spacer(Modifier.height(skin.spaceXs))
                log.improvements.forEach { Bullet(it) }
            }
        }
    }
}

@Composable
private fun SectionTag(label: String, color: Color) {
    val skin = LocalSkin.current
    Surface(
        shape = RoundedCornerShape(skin.radiusMicro),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = skin.spaceSm, vertical = 2.dp)
        )
    }
}

@Composable
private fun Bullet(text: String) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Row(
        modifier = Modifier.padding(start = skin.spaceXs, top = 2.dp, bottom = 2.dp)
    ) {
        Text(
            text = "·",
            fontSize = 14.sp,
            color = colors.textTertiary
        )
        Spacer(Modifier.width(skin.spaceSmall))
        Text(
            text = text,
            fontSize = 13.sp,
            color = colors.textSecondary,
            lineHeight = 18.sp
        )
    }
}
