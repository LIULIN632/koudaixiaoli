package com.pocket.xiaoli.ui.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.pocket.xiaoli.data.library.LibraryFile
import com.pocket.xiaoli.data.library.LibraryRepository
import com.pocket.xiaoli.ui.components.AppCard
import com.pocket.xiaoli.ui.components.MiuixChoice
import com.pocket.xiaoli.ui.components.MiuixChoiceRow
import com.pocket.xiaoli.ui.components.MiuixSettingsScaffold
import com.pocket.xiaoli.ui.components.MiuixSettingsSubSection
import com.pocket.xiaoli.ui.components.SectionCaption
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import java.io.File
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 资料站：**课件、历年卷、习题答案**按课程分发。
 *
 * ## 为什么没有「上传」按钮
 *
 * 这一版是**作者单向分发**：索引 `library/index.json` 与文件本体都跟我们发版走同一条链路
 *（Gitee Release 附件，免备案、零成本）。学生只能看和下载。
 * 真要做上传，就得有后端、审核、备案 —— 那是另一个产品，见 `docs/资料站与快问快答.md`。
 *
 * ## 三个状态必须说人话
 *
 * 1. 索引里有、文件还没传：显示「待上传」，点一下给一句说明，不装死；
 * 2. 网络挂了但有缓存：列表照常显示，底部一行小字说明「没能连上」；
 * 3. 一份都没有：给一张卡解释资料从哪来，而不是空白屏。
 */
@Composable
internal fun LibraryScreen(
    repository: LibraryRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by repository.state.collectAsState()

    var query by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var busyId by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0) }
    var note by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { repository.refresh() }

    val files = remember(query, state.files) { state.index.search(query) }

    LibraryContent(
        ui = LibraryUi(
            files = files,
            query = query,
            subjects = state.index.subjects(),
            subject = subject,
            loading = state.loading,
            error = state.error,
            busyId = busyId,
            progress = progress,
            note = note,
        ),
        onQueryChange = { query = it },
        onSelectSubject = { subject = it },
        onRefresh = { scope.launch { repository.refresh(force = true) } },
        onFileClick = { file ->
            if (!file.downloadable) {
                note = "《${file.name}》还没传上来，等作者补上"
            } else if (busyId == null) {
                scope.launch {
                    busyId = file.id
                    progress = 0
                    val result = repository.download(file) { progress = it }
                    busyId = null
                    result.fold(
                        onSuccess = { saved ->
                            note = "已下载：${saved.name}"
                            openDownloaded(context, saved, file.extension)
                        },
                        onFailure = { note = it.message ?: "下载失败" },
                    )
                }
            }
        },
        onBack = onBack,
    )
}

/**
 * 一页资料站的**全部可见状态**。
 *
 * 为什么把 UI 与 [LibraryRepository] 拆开：截图测试跑在 Robolectric 里，没有网、
 * 也不该去碰 Gitee；而"列表长什么样"恰恰是这次最需要看的东西。
 * 拆完之后 Content 只吃数据、只吐回调，测试就能凭空造出一页。
 */
internal data class LibraryUi(
    val files: List<LibraryFile> = emptyList(),
    val query: String = "",
    /** 出现过的学科，按索引顺序；只有一科时不画 chip 行。 */
    val subjects: List<String> = emptyList(),
    /** 当前选中的学科；空串 = 全部。 */
    val subject: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val busyId: String? = null,
    val progress: Int = 0,
    val note: String? = null,
)

@Composable
internal fun LibraryContent(
    ui: LibraryUi,
    onQueryChange: (String) -> Unit,
    onSelectSubject: (String) -> Unit,
    onRefresh: () -> Unit,
    onFileClick: (LibraryFile) -> Unit,
    onBack: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current

    MiuixSettingsScaffold(
        title = "资料站",
        onBack = onBack,
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "重新拉取索引",
                    tint = colors.textSecondary,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MiuixTheme.colorScheme.surface)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = skin.spaceLg, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(skin.spaceWide),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .border(1.dp, colors.accentMain.copy(alpha = 0.18f), RoundedCornerShape(26.dp))
            ) {
                SearchBar(
                    inputField = {
                        InputField(
                            query = ui.query,
                            onQueryChange = onQueryChange,
                            onSearch = { },
                            expanded = false,
                            onExpandedChange = { },
                            label = "搜课程、课件、历年卷",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    expanded = false,
                    onExpandedChange = { },
                    insideMargin = DpSize(0.dp, 0.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) { }
            }

            // 第一层：学科。只有一科时不画，省一行高度。
            if (ui.subjects.size > 1) {
                MiuixChoiceRow(
                    choices = ui.subjects.map { MiuixChoice(it, it) },
                    selectedId = ui.subject.takeIf { it.isNotBlank() },
                    onSelect = { onSelectSubject(it ?: "") },
                    allLabel = "全部",
                )
            }

            when {
                ui.loading && ui.files.isEmpty() -> HintCard(
                    title = "正在拉取资料索引…",
                    body = "第一次打开要联网拿一份索引，之后就离线也能看。",
                    accent = colors.accentMain,
                )

                ui.files.isEmpty() -> HintCard(
                    title = "还没有资料",
                    body = "资料由作者上传后出现在这里：课件、历年卷、习题答案都会按课程排好。" +
                        "现在可以先去「快问快答」看老师每章会问什么。",
                    accent = colors.accentMain,
                )

                else -> {
                    val visible = if (ui.subject.isBlank()) ui.files else ui.files.filter { it.subjectGroup == ui.subject }
                    visible.map { it.subjectGroup }.distinct().forEach { subject ->
                        SectionCaption(subject)
                        visible.filter { it.subjectGroup == subject }.groupBy { it.group }.forEach { (course, courseFiles) ->
                            MiuixSettingsSubSection(course)
                            courseFiles.forEach { file ->
                                AppCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { onFileClick(file) },
                                ) {
                                    LibraryRow(
                                        file = file,
                                        busy = ui.busyId == file.id,
                                        progress = if (ui.busyId == file.id) ui.progress else 0,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 有内容时把索引状态缩成一行小字，不抢主内容的注意力。
            val statusLine = when {
                ui.loading -> "正在核对索引…"
                ui.error != null -> ui.error
                ui.files.isNotEmpty() -> "索引已就绪"
                else -> null
            }
            ui.note?.let { text -> HintLine(text, colors.accentMain) }
            statusLine?.let { text -> HintLine(text, colors.textSecondary) }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun LibraryRow(
    file: LibraryFile,
    busy: Boolean,
    progress: Int,
) {
    val colors = LocalPocketXiaoliColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.accentMain.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Description,
                contentDescription = null,
                tint = colors.accentMain,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
            val meta = listOfNotNull(
                file.kind.takeIf { it.isNotBlank() },
                file.sizeText.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = meta,
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.textSecondary,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        when {
            busy -> Text(
                text = "$progress%",
                style = MiuixTheme.textStyles.footnote1,
                color = colors.accentMain,
                fontWeight = FontWeight.Medium,
            )

            !file.downloadable -> Text(
                text = "待上传",
                style = MiuixTheme.textStyles.footnote2,
                color = colors.textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.surfaceAlt)
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            )

            else -> Icon(
                imageVector = Icons.Outlined.CloudDownload,
                contentDescription = "下载",
                tint = colors.accentMain,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun HintCard(
    title: String,
    body: String,
    accent: Color,
) {
    val colors = LocalPocketXiaoliColors.current
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = body,
                style = MiuixTheme.textStyles.footnote1,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun HintLine(text: String, color: Color) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote2,
        color = color,
    )
}

/** 用系统应用打开刚下载的文件；没有能打开的 App 就老老实实说一句。 */
private fun openDownloaded(context: Context, file: File, extension: String) {
    val uri: Uri = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull() ?: run {
        Toast.makeText(context, "文件已下载，但打不开分享通道", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeOf(extension))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val opened = runCatching { context.startActivity(intent) }.isSuccess
    if (!opened) {
        Toast.makeText(context, "已下载到手机，没找到能打开这种文件的 App", Toast.LENGTH_LONG).show()
    }
}

/**
 * 扩展名 → MIME。
 *
 * 不查 `MimeTypeMap` 是因为它对 `.docx` / `.pptx` 在部分 ROM 上返回 null，
 * 而这几类正是资料站的主力。写死一张小表比查系统表更可预测。
 */
internal fun mimeOf(extension: String): String = when (extension.lowercase()) {
    "pdf" -> "application/pdf"
    "ppt" -> "application/vnd.ms-powerpoint"
    "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    "doc" -> "application/msword"
    "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    "xls" -> "application/vnd.ms-excel"
    "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    "zip" -> "application/zip"
    "txt" -> "text/plain"
    "md" -> "text/markdown"
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "mp4" -> "video/mp4"
    else -> "application/octet-stream"
}
