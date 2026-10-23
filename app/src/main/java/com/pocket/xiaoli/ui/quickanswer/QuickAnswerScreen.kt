package com.pocket.xiaoli.ui.quickanswer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.pocket.xiaoli.data.quickanswer.QuickAnswerCourse
import com.pocket.xiaoli.data.quickanswer.QuickAnswerHit
import com.pocket.xiaoli.data.quickanswer.QuickAnswerQuestion
import com.pocket.xiaoli.data.quickanswer.QuickAnswerRepository
import com.pocket.xiaoli.data.quickanswer.QuickAnswerStore
import com.pocket.xiaoli.ui.components.AppCard
import com.pocket.xiaoli.ui.components.MiuixChoice
import com.pocket.xiaoli.ui.components.MiuixChoiceRow
import com.pocket.xiaoli.ui.components.MiuixSettingsScaffold
import com.pocket.xiaoli.ui.components.SectionCaption
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 快问快答：**老师每章会问什么、怎么答**。
 *
 * ## 为什么按「课程 → 章 → 题」而不是按时间流
 *
 * 课堂提问是跟着章节走的：今天讲到第 3 章，学生要的就是第 3 章那几问。
 * 所以不做"最近更新"瀑布流，只做章内清单 —— 点开一行看答案要点，看完标「会了」。
 *
 * ## 为什么标记只在本机
 *
 * 「哪几章还不会」能反推你在挂哪门课，比课表更私密。所以标记只写 SharedPreferences，
 * 不联网、不上报；换手机丢了就丢了，可以接受。
 *
 * @param repository 题库来源（先内置，再被远端覆盖）。
 * @param store 会 / 不会 的本地账本。
 */
@Composable
internal fun QuickAnswerScreen(
    repository: QuickAnswerRepository,
    store: QuickAnswerStore,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val state by repository.state.collectAsState()

    var subject by remember { mutableStateOf("") }
    var courseId by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var knownIds by remember { mutableStateOf(store.knownIds()) }
    var unknownIds by remember { mutableStateOf(store.unknownIds()) }

    // 缓存先上（离线也有内容），再后台刷新；两件事都只跑一次。
    LaunchedEffect(Unit) {
        repository.applyCache()
        repository.refresh()
    }

    val bank = state.bank
    // 选了学科就只在该学科的课里挑；没选就取全库第一门。
    // 用 `coursesOf` 而不是 `course(id)`：换学科后旧的 courseId 可能不在新学科里。
    val course = courseId?.let { id -> bank.courses.firstOrNull { it.id == id } }
        ?: bank.coursesOf(subject.takeIf { it.isNotBlank() }).firstOrNull()
    val visibleCourses = bank.coursesOf(subject.takeIf { it.isNotBlank() })

    QuickAnswerContent(
        ui = QuickAnswerUi(
            courses = visibleCourses,
            course = course,
            subjects = bank.subjects(),
            subject = subject,
            hits = remember(query, courseId, bank) { bank.search(query, courseId) },
            query = query,
            expandedId = expandedId,
            knownIds = knownIds,
            unknownIds = unknownIds,
            totalQuestions = course?.questionCount ?: bank.courses.sumOf { it.questionCount },
            fromRemote = state.fromRemote,
            error = state.error,
        ),
        onQueryChange = { query = it },
        onSelectSubject = { picked ->
            subject = picked
            // 换学科要跟着换课，否则停在上一科的课上，看着像「这科没题」。
            courseId = null
            expandedId = null
        },
        onSelectCourse = { id ->
            courseId = id
            expandedId = null
        },
        onToggle = { questionId ->
            expandedId = if (expandedId == questionId) null else questionId
        },
        onMark = { questionId, known ->
            store.mark(questionId, known)
            knownIds = store.knownIds()
            unknownIds = store.unknownIds()
        },
        onRefresh = { scope.launch { repository.refresh(force = true) } },
        onBack = onBack,
    )
}

/**
 * 一页快问快答的**全部可见状态**。
 *
 * 与 `LibraryUi` 同一个理由：截图测试不联网、不碰 SharedPreferences，
 * 只把「章 / 题 / 会与不会」摊在桌面上看版式。
 */
internal data class QuickAnswerUi(
    val courses: List<QuickAnswerCourse> = emptyList(),
    val course: QuickAnswerCourse? = null,
    val hits: List<QuickAnswerHit> = emptyList(),
    val query: String = "",
    /** 出现过的学科；只有一科时不画 chip 行。 */
    val subjects: List<String> = emptyList(),
    /** 当前选中的学科；空串 = 全部。 */
    val subject: String = "",
    val expandedId: String? = null,
    val knownIds: Set<String> = emptySet(),
    val unknownIds: Set<String> = emptySet(),
    val totalQuestions: Int = 0,
    val fromRemote: Boolean = false,
    val error: String? = null,
)

@Composable
internal fun QuickAnswerContent(
    ui: QuickAnswerUi,
    onQueryChange: (String) -> Unit,
    onSelectSubject: (String) -> Unit,
    onSelectCourse: (String) -> Unit,
    onToggle: (String) -> Unit,
    onMark: (String, Boolean?) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current

    MiuixSettingsScaffold(
        title = "快问快答",
        onBack = onBack,
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "重新拉取题库",
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
            SearchBar(
                inputField = {
                    InputField(
                        query = ui.query,
                        onQueryChange = onQueryChange,
                        onSearch = { },
                        expanded = false,
                        onExpandedChange = { },
                        label = "搜题干、关键词",
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                expanded = false,
                onExpandedChange = { },
                insideMargin = DpSize(0.dp, 0.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { }

            // 第一层：学科；第二层：课程。两层都用横滚 chip，只占两行。
            if (ui.subjects.size > 1) {
                MiuixChoiceRow(
                    choices = ui.subjects.map { MiuixChoice(it, it) },
                    selectedId = ui.subject.takeIf { it.isNotBlank() },
                    onSelect = { onSelectSubject(it ?: "") },
                    allLabel = "全部",
                )
            }

            if (ui.courses.size > 1) {
                CourseChips(
                    courses = ui.courses,
                    selectedId = ui.course?.id,
                    onSelect = onSelectCourse,
                )
            }

            when {
                ui.courses.isEmpty() -> InfoCard(
                    title = "题库还没上架",
                    body = "题库是作者按课程整理的：每章几条老师真会问的问题 + 能照着说的答案要点。",
                    accent = colors.accentMain,
                )

                ui.course == null -> InfoCard(
                    title = "这门课还没有题目",
                    body = "换一门课看看，或者用上面的搜索框。",
                    accent = colors.accentMain,
                )

                ui.query.isNotBlank() -> {
                    SectionCaption("搜索结果 · ${ui.hits.size}")
                    if (ui.hits.isEmpty()) {
                        InfoCard(
                            title = "没搜到",
                            body = "试试更短的关键词：一个术语就行，比如「中序」「凹凸」。",
                            accent = colors.accentMain,
                        )
                    } else {
                        ui.hits.forEach { hit ->
                            QuestionCard(
                                question = hit.question,
                                meta = "${hit.course.name} · ${hit.chapter.displayTitle}",
                                expanded = ui.expandedId == hit.question.id,
                                known = hit.question.id in ui.knownIds,
                                unknown = hit.question.id in ui.unknownIds,
                                onClick = { onToggle(hit.question.id) },
                                onMark = { known -> onMark(hit.question.id, known) },
                            )
                        }
                    }
                }

                else -> ui.course.chapters.forEach { chapter ->
                    SectionCaption(chapter.displayTitle)
                    chapter.questions.forEach { question ->
                        QuestionCard(
                            question = question,
                            meta = null,
                            expanded = ui.expandedId == question.id,
                            known = question.id in ui.knownIds,
                            unknown = question.id in ui.unknownIds,
                            onClick = { onToggle(question.id) },
                            onMark = { known -> onMark(question.id, known) },
                        )
                    }
                }
            }

            ProgressLine(
                known = ui.knownIds.size,
                total = ui.totalQuestions,
                remote = ui.fromRemote,
                error = ui.error,
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun CourseChips(
    courses: List<QuickAnswerCourse>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    // 课程也用横滚 chip（与学科同一套），不再 3 个一行：课一多就会占三四行。
    MiuixChoiceRow(
        choices = courses.map { MiuixChoice(it.id, it.name) },
        selectedId = selectedId,
        onSelect = { id -> if (id != null) onSelect(id) },
    )
}

/**
 * 一道题的卡片外壳。
 *
 * 展开态必须和收起态**看起来不是同一张卡**：只靠箭头旋转的话，
 * 一屏里同时有七八张同色卡片，用户视线找不到刚点开的那一张。
 */
@Composable
private fun QuestionCard(
    question: QuickAnswerQuestion,
    meta: String?,
    expanded: Boolean,
    known: Boolean,
    unknown: Boolean,
    onClick: () -> Unit,
    onMark: (Boolean?) -> Unit,
) {
    val accent = LocalPocketXiaoliColors.current.accentMain
    // 展开态用主色微染 + 细描边区分；收起态就是全站统一的白卡（皮肤决定底色与阴影）。
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        containerColor = if (expanded) accent.copy(alpha = 0.07f) else null,
        border = if (expanded) BorderStroke(1.dp, accent.copy(alpha = 0.18f)) else null,
    ) {
        QuestionRow(
            question = question,
            meta = meta,
            expanded = expanded,
            known = known,
            unknown = unknown,
            onMark = onMark,
        )
    }
}

/**
 * 一道题的卡片：**收起只显示问题**，点开才给答案要点。
 *
 * 收起是刻意的：一屏能放下五六问，先扫一遍「今天老师可能问哪些」，
 * 不会一进来就被答案淹没。
 */
@Composable
private fun QuestionRow(
    question: QuickAnswerQuestion,
    meta: String?,
    expanded: Boolean,
    known: Boolean,
    unknown: Boolean,
    onMark: (Boolean?) -> Unit,
) {
    val colors = LocalPocketXiaoliColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = question.question,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            if (known || unknown) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (known) "会了" else "还不会",
                    style = MiuixTheme.textStyles.footnote2,
                    color = if (known) colors.accentMain else colors.warning,
                )
            }
            // 折叠态必须留一个「能点开」的记号：只写题干的话整行看着像纯文本，
            // 学生不会想到去点它（190 基线第一版就是这个毛病）。
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "收起答案" else "展开答案",
                tint = colors.textSecondary,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(if (expanded) 180f else 0f),
            )
        }

        if (meta != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = meta,
                style = MiuixTheme.textStyles.footnote2,
                color = colors.textSecondary,
            )
        }

        if (expanded) {
            if (question.answerPoints.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                question.answerPoints.forEach { point ->
                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                        Text(
                            text = "·",
                            style = MiuixTheme.textStyles.footnote1,
                            color = colors.accentMain,
                            modifier = Modifier.width(10.dp),
                        )
                        Text(
                            text = point,
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            if (question.hint.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.warning.copy(alpha = 0.10f))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lightbulb,
                        contentDescription = null,
                        tint = colors.warning,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = question.hint,
                        style = MiuixTheme.textStyles.footnote2,
                        color = colors.warning,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = known,
                    onClick = { onMark(if (known) null else true) },
                    label = { Text("会了", style = MiuixTheme.textStyles.footnote2) },
                    // 未选中必须与「还不会」同款中性面：这一对是**状态**不是主次按钮，
                    // 给"会了"预先染上 accent 底色会让它看着已经选中（190 基线拍到了）。
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = colors.surfaceCard,
                        labelColor = colors.textSecondary,
                        selectedContainerColor = colors.accentMain,
                        selectedLabelColor = colors.surfaceBase,
                    ),
                )
                FilterChip(
                    selected = unknown,
                    onClick = { onMark(if (unknown) null else false) },
                    label = { Text("还不会", style = MiuixTheme.textStyles.footnote2) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = colors.surfaceCard,
                        labelColor = colors.textSecondary,
                        selectedContainerColor = colors.warning,
                        selectedLabelColor = colors.surfaceBase,
                    ),
                )
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String, accent: androidx.compose.ui.graphics.Color) {
    val colors = LocalPocketXiaoliColors.current
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "·",
                    style = MiuixTheme.textStyles.footnote1,
                    color = accent,
                    modifier = Modifier.width(10.dp),
                )
                Text(
                    text = body,
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun ProgressLine(known: Int, total: Int, remote: Boolean, error: String?) {
    val colors = LocalPocketXiaoliColors.current
    val parts = buildList {
        add("已标会 $known / 共 $total 问")
        add(if (remote) "题库来自在线更新" else "题库为内置版本")
        if (error != null) add(error)
    }
    Text(
        text = parts.joinToString(" · "),
        style = MiuixTheme.textStyles.footnote2,
        color = if (error != null) colors.warning else colors.textSecondary,
    )
}


