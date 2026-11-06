package com.pocket.xiaoli.data.knowledge

import com.pocket.xiaoli.data.academic.AcademicSnapshot
import com.pocket.xiaoli.data.entity.SavedGradeEntity
import com.pocket.xiaoli.data.library.LibraryDoc
import com.pocket.xiaoli.data.library.LibraryFile
import com.pocket.xiaoli.data.map.RoomCell
import com.pocket.xiaoli.data.map.RoomCellType
import com.pocket.xiaoli.data.service.CampusServiceSpec
import com.pocket.xiaoli.data.service.CampusServiceType
import com.pocket.xiaoli.ui.treasurebox.isPassed
import com.pocket.xiaoli.ui.treasurebox.isShortOfCredit
import com.pocket.xiaoli.ui.treasurebox.isUntaken
import java.time.Instant
import java.time.ZoneId

/**
 * 助手工具的回答排版 —— 纯函数，不依赖 Android / 数据库，便于单测。
 * 由 [AssistantTools] 取数后调用。
 */
object AssistantFormatters {

    private const val MAX_HITS = 5

    /**
     * 成绩问答：
     * - 问题里包含某门课名 → 返回这门课的成绩（可多学期）
     * - 否则返回整体统计（门数 / 平均分 / 平均绩点 / 不及格门数）
     *
     * ⚠️ 返回值里的 [ToolOutcome.text] 是**给模型读的**，措辞不要为了排版去动它；
     * 界面要的数字与明细在 [ToolOutcome.card] 里，两者在**同一次取数**里生成 ——
     * 这是"卡片说 41 门、模型说 38 门"这类双口径唯一的防法。
     */
    fun grades(grades: List<SavedGradeEntity>, query: String): ToolOutcome {
        if (grades.isEmpty()) {
            return ToolOutcome(
                "本地还没有保存成绩。可以到「服务 → 成绩查询」查询后点「全部存档」，" +
                    "或在「我的课程」里一键同步。"
            )
        }

        val matched = grades.filter { courseMatches(it.courseName, query) }
        if (matched.isNotEmpty()) {
            val lines = matched.take(MAX_HITS).joinToString("\n") { g ->
                "· ${g.courseName}：" + g.totalScore.ifBlank { "—" } + " 分" +
                    (if (g.termLabel.isNotBlank()) "（${g.termLabel}）" else "")
            }
            val more = if (matched.size > MAX_HITS) "\n（共 ${matched.size} 条，仅显示前 $MAX_HITS 条）" else ""
            return ToolOutcome(
                text = "查到 ${matched.size} 条成绩：\n$lines$more",
                card = AssistantCard.Grades(
                    rows = matched.take(MAX_HITS).map { g ->
                        AssistantCard.GradeRow(
                            course = g.courseName,
                            term = g.termLabel,
                            score = g.totalScore.ifBlank { "—" },
                            gpa = g.gpa,
                        )
                    },
                ),
            )
        }

        val scores = grades.mapNotNull { it.totalScore.trim().toFloatOrNull() }
        val gpas = grades.mapNotNull { it.gpa.trim().toFloatOrNull() }
        val failed = scores.count { it < 60f }
        val text = buildString {
            append("已存档 ").append(grades.size).append(" 门课程")
            scores.takeIf { it.isNotEmpty() }?.average()?.let { append("，平均分 ").append("%.1f".format(it)) }
            gpas.takeIf { it.isNotEmpty() }?.average()?.let { append("，平均绩点 ").append("%.2f".format(it)) }
            if (failed > 0) append("，其中 ").append(failed).append(" 门低于 60 分")
            append("。可以问我「高数多少分」这样具体课程。")
        }
        return ToolOutcome(
            text = text,
            card = AssistantCard.Grades(
                stats = AssistantCard.Grades.Stats(
                    total = grades.size,
                    averageScore = scores.takeIf { it.isNotEmpty() }?.average(),
                    averageGpa = gpas.takeIf { it.isNotEmpty() }?.average(),
                    failedCount = failed,
                ),
            ),
        )
    }

    /**
     * 培养方案进度问答（数据来自「服务 → 修课情况」查询时落下的本地快照）。
     *
     * 三种问法各有归属：
     * - 问某门课（"形势与政策修了没"）→ 列那门课的状态/成绩/建议修读学期；
     * - 问整体（"我还差多少学分"）→ 总要求/已获/还差 + **缺学分的类型**（哪块缺、缺多少）；
     * - 问"哪些课没修" → 由 `text` 里点名，卡片只给统计（课程可能有几十门，卡片列不下）。
     *
     * ⚠️ 与 [grades] 同一条规矩：`text` 给模型、`card` 给界面，**同一次取数**里生成。
     * ⚠️ `updatedLabel` 一定要说出口：培养方案每学期会调，不写"更新于"就是把旧要求当现行要求。
     */
    fun academicProgress(snapshot: AcademicSnapshot?, query: String): ToolOutcome {
        if (snapshot == null || snapshot.types.isEmpty()) {
            return ToolOutcome(
                "本地还没有培养方案数据。可以到「服务 → 修课情况」查一次（需要登录教务），" +
                    "查成功后我就能回答还差多少学分、哪些课没修。"
            )
        }

        val courses = snapshot.courses
        // 总进度只看根节点（「主修」）：两级树把所有节点加起来会**重复计算**（同 CreditProgress）
        val root = snapshot.types.firstOrNull { it.parentId.isBlank() && it.requiredCredit.isNotBlank() }
        // ⚠️ 根节点**不算**"缺学分的类型"：它就是总计本身，列进去会出现
        // 「主修 缺 75 学分」与「通识必修模块 缺 6 学分」并列 —— 一个总数混在明细里，
        // 用户会以为"这块缺 75"。它已经在上面的三格 stat 里了。
        val shortTypes = snapshot.types.filter { it.isShortOfCredit() && it.id != root?.id }
        val untaken = courses.filter { it.isUntaken() }
        val matched = courses.filter { courseMatches(it.courseName, query) }
        val updated = snapshot.updatedLabel()

        // ① 问某门课
        if (matched.isNotEmpty()) {
            val lines = matched.take(MAX_HITS).joinToString("\n") { c ->
                buildString {
                    append("· ").append(c.courseName)
                    append("：").append(c.status.ifBlank { if (c.score.isNotBlank()) "有成绩" else "未修" })
                    if (c.maxGrade.isNotBlank()) append("，成绩 ").append(c.maxGrade)
                    if (c.credit.isNotBlank()) append("，").append(c.credit).append(" 学分")
                    if (c.typeName.isNotBlank()) append("（").append(c.typeName).append("）")
                    if (c.term.isNotBlank()) append("，").append(c.term)
                    if (c.suggestedTerm.isNotBlank() && c.suggestedTerm != c.term) {
                        append("，建议 ").append(c.suggestedTerm).append(" 修")
                    }
                }
            }
            val more = if (matched.size > MAX_HITS) "\n（共 ${matched.size} 条，仅显示前 $MAX_HITS 条）" else ""
            return ToolOutcome(
                text = "培养方案里有 ${matched.size} 门匹配的课（$updated）：\n$lines$more",
                card = AssistantCard.AcademicProgress(
                    stats = statsOf(root, shortTypes.size, untaken.size),
                    rows = matched.take(MAX_HITS).map { c ->
                        AssistantCard.AcademicProgress.Row(
                            title = c.courseName,
                            detail = listOfNotNull(
                                c.typeName.takeIf { it.isNotBlank() },
                                c.term.takeIf { it.isNotBlank() },
                                c.maxGrade.takeIf { it.isNotBlank() }?.let { "成绩 $it" },
                            ).joinToString(" · "),
                            tag = c.status.ifBlank { if (c.score.isNotBlank()) "已修" else "未修" },
                        )
                    },
                    updatedLabel = updated,
                ),
            )
        }

        // ② 问整体 / 问没修的课
        val text = buildString {
            append("培养方案进度（").append(updated).append("）：")
            if (root != null) {
                append("总要求 ").append(root.requiredCredit).append(" 学分，")
                append("已获 ").append(root.earnedCredit).append(" 学分")
                if (root.missedCredit.isNotBlank()) append("，还差 ").append(root.missedCredit).append(" 学分")
            } else {
                append("已获 ").append(courses.count { it.isPassed() }).append(" 门已通过课程")
            }
            append("。缺学分的类型 ").append(shortTypes.size).append(" 个")
            append("，未修/未过课程 ").append(untaken.size).append(" 门")
            if (shortTypes.isNotEmpty()) {
                append("。缺得最多的是：")
                append(
                    shortTypes.sortedByDescending { it.missedCredit.toFloatOrNull() ?: 0f }
                        .take(3)
                        .joinToString("、") { "${it.name}（还差 ${it.missedCredit} 学分）" }
                )
            }
            if (untaken.isNotEmpty()) {
                append("。还没修的课：")
                append(untaken.take(5).joinToString("、") { it.courseName })
                if (untaken.size > 5) append(" 等 ${untaken.size} 门")
            }
            append("。")
        }
        return ToolOutcome(
            text = text,
            card = AssistantCard.AcademicProgress(
                stats = statsOf(root, shortTypes.size, untaken.size),
                // 问"还差多少"时用户真正想看的下一层是"哪块缺" → 行给缺学分的类型
                rows = shortTypes.sortedByDescending { it.missedCredit.toFloatOrNull() ?: 0f }
                    .take(MAX_HITS)
                    .map { t ->
                        AssistantCard.AcademicProgress.Row(
                            title = t.name,
                            detail = "已获 ${t.earnedCredit.ifBlank { "—" }} / 要求 ${t.requiredCredit.ifBlank { "—" }}",
                            tag = "缺 ${t.missedCredit} 学分",
                        )
                    },
                updatedLabel = updated,
            ),
        )
    }

    private fun statsOf(
        root: com.pocket.xiaoli.network.AcademicTypeInfo?,
        shortTypeCount: Int,
        untakenCount: Int,
    ) = AssistantCard.AcademicProgress.Stats(
        requiredCredit = root?.requiredCredit.orEmpty(),
        earnedCredit = root?.earnedCredit.orEmpty(),
        missedCredit = root?.missedCredit.orEmpty(),
        shortTypeCount = shortTypeCount,
        untakenCourseCount = untakenCount,
    )

    /**
     * 教室问答：从问题里反向匹配房间号（如 A101），返回楼栋 / 楼层 / 类型。
     * 未命中时给出可操作提示。
     */
    /**
     * 课程名匹配：问句直接包含课名（「高等数学多少分」），
     * 或问句与课名至少共享 2 个字、**且占到课名的一半**（「我高数多少分」→「高等数学」= 2/4）。
     * 启发式足够覆盖中文简称，避免引入分词依赖。
     *
     * ⚠️ 后半句那个比例约束是 2026-10 补的。只有"共享 ≥2 字"时，**长问句会误命中长课名**：
     * 问「形势与政策修了没」，课名「思想道德修养与法律基础」因为恰好共享「与」「修」两个字
     * 也被选中 —— 卡片于是列出两门毫不相干的课（培养方案那批课名都很长，这个坑很密）。
     * 加上比例后：简称场景（2/4 = 50%）照旧成立，长课名的巧合（2/11 = 18%）被排掉。
     */
    private fun courseMatches(courseName: String, query: String): Boolean {
        val name = courseName.trim()
        if (name.isEmpty()) return false
        if (query.contains(name)) return true
        val q = query.lowercase()
        val n = name.lowercase()
        val hits = q.toCharArray().distinct().count { it in n }
        return hits >= 2 && hits * 2 >= n.toCharArray().distinct().size
    }
    /** 校园服务问答：从服务目录里按名称 / 服务商匹配。 */
    fun services(specs: List<CampusServiceSpec>, query: String): String {
        if (specs.isEmpty()) return "当前校区还没有配置校园服务入口。"
        val q = query.trim()
        val matched = specs.filter { spec ->
            q.contains(spec.name, ignoreCase = true) ||
                spec.name.contains(q, ignoreCase = true) ||
                (!spec.provider.isNullOrBlank() && (
                    q.contains(spec.provider!!, ignoreCase = true) ||
                        spec.provider!!.contains(q, ignoreCase = true)
                    ))
        }.take(MAX_HITS)
        if (matched.isEmpty()) {
            return "没找到对应服务。可以到「服务」页搜索「$q」。"
        }
        val lines = matched.joinToString("\n") { spec ->
            buildString {
                append("· ").append(spec.name)
                if (!spec.provider.isNullOrBlank()) append("：").append(spec.provider)
                append("（").append(channelLabel(spec.type)).append("）")
            }
        }
        val more = if (matched.size > MAX_HITS) "\n（仅显示前 $MAX_HITS 个）" else ""
        return "找到 ${matched.size} 个服务：\n$lines$more"
    }

    private fun channelLabel(type: CampusServiceType): String = when (type) {
        CampusServiceType.NATIVE -> "原生页"
        CampusServiceType.WEBVIEW -> "内置网页"
        CampusServiceType.MINIPROGRAM -> "微信小程序"
        CampusServiceType.ALIPAY -> "支付宝"
        CampusServiceType.EXTERNAL_APP -> "第三方 App"
        CampusServiceType.API -> "API"
    }
    /**
     * 校园知识库问答。
     *
     * 输出格式对齐 `docs/AssistantContract.md` §8 的约定：
     * `[K1] 标题 / 更新时间 / 正文片段` —— 这样模型能照着 `[K1]` 在回答里做引用，
     * 用户也能顺着来源自己去核实。
     *
     * 两种退化情况**必须区分文案**：
     * - 库里为空（[totalDocuments] == 0）：告知"暂无内容"，别让用户以为是自己问错了；
     * - 有内容但没命中：告知"没查到"并给官方入口（§8 要求），而不是假装查过。
     *
     * 界面要的 [ToolOutcome.sources] 与 [ToolOutcome.text] 在这里**同一次映射**：
     * `[K1]` 既是回灌给模型的编号，也是来源条上的编号。分开生成迟早会出现
     * "正文引用 K2、来源条只有 K1"的错位。
     */
    fun knowledge(hits: List<KnowledgeHit>, totalDocuments: Int, query: String): ToolOutcome =
        contentOutcome(hits, totalDocuments, query, KNOWLEDGE_SCOPE)

    /**
     * 资料库问答（四源合一后的检索结果）。
     *
     * 与 [knowledge] 的差别只有三处，**共用同一段渲染**：
     * 1. 引用编号按来源分前缀（`K` 知识库 / `Q` 快问快答 / `F` 使用帮助 / `L` 资料站），
     *    用户一眼能看出这句话是靠什么答的；
     * 2. 每条标题后多一段「来源：X」，模型据此说"根据资料站的历年卷…"；
     * 3. 退化文案说的是「资料库」而不是「知识库」。
     *
     * [files] 只用来给资料卡补文件元信息（学科/课程/类型/大小/可下载），
     * [docs] 只用来给**正文条目**补提供者署名；
     * 传空表也照常工作（卡片就没有资料行 / 没有署名），**它们是纯展示输入，不参与打分**。
     */
    fun library(
        hits: List<KnowledgeHit>,
        totalDocuments: Int,
        query: String,
        files: List<LibraryFile> = emptyList(),
        docs: List<LibraryDoc> = emptyList(),
    ): ToolOutcome = contentOutcome(hits, totalDocuments, query, UNIFIED_SCOPE) { hitList, markers ->
        materialCard(hitList, markers, files, docs)
    }

    /** [contentOutcome] 的两个口径。加新口径=新增一个实例，别复制渲染逻辑。 */
    private class ContentScope(
        val emptyText: String,
        val missText: (String) -> String,
        /**
         * 一次把引用编号**全算出来**：正文里的 `[Q1]` 与来源条上的 `Q1` 读的是同一个列表，
         * 分开算迟早出现"正文引用 K2、来源条只有 K1"。
         */
        val markers: (List<KnowledgeHit>) -> List<String>,
        /** 是否在标题后写「来源：X」。知识库单源时不必写（整篇都是知识库）。 */
        val showOrigin: Boolean,
    )

    private val KNOWLEDGE_SCOPE = ContentScope(
        emptyText = "知识库暂无内容（尚未发布校园知识笔记）。" +
            "可以问我 App 怎么用，或到「服务」页找对应入口。",
        missText = { q ->
            "知识库没有查到与「$q」相关的内容。可以换个说法再问，或到「服务」页找对应入口；" +
                "制度类问题建议直接咨询教务处或图书馆。"
        },
        markers = { hits -> hits.indices.map { "K${it + 1}" } },
        showOrigin = false,
    )

    private val UNIFIED_SCOPE = ContentScope(
        emptyText = "资料库暂无内容（知识笔记、题库、使用帮助都还没就位）。" +
            "可以到「服务」页找对应入口，或问我课表、成绩这类本地数据。",
        missText = { q ->
            "资料库没有查到与「$q」相关的内容。可以换个说法再问；" +
                "课件与历年卷在「服务 → 资料站」，制度类问题建议直接咨询教务处或图书馆。"
        },
        markers = { hits ->
            // 每个来源各自从 1 数：Q1、K1、L1 同屏出现也不会撞号。
            val counters = mutableMapOf<ContentSource, Int>()
            hits.map { hit ->
                val source = ContentSource.of(hit.document.id)
                val next = (counters[source] ?: 0) + 1
                counters[source] = next
                source.marker + next
            }
        },
        showOrigin = true,
    )

    private fun contentOutcome(
        hits: List<KnowledgeHit>,
        totalDocuments: Int,
        query: String,
        scope: ContentScope,
        /** 卡片与正文在同一次映射里生成；给它 `null` 表示这个口径不出卡片。 */
        cardOf: ((List<KnowledgeHit>, List<String>) -> AssistantCard?)? = null,
    ): ToolOutcome {
        val q = query.trim()

        if (totalDocuments == 0) return ToolOutcome(scope.emptyText)
        if (hits.isEmpty()) return ToolOutcome(scope.missText(q))

        val markers = scope.markers(hits)

        val text = hits.mapIndexed { index, hit ->
            buildString {
                append('[').append(markers[index]).append("] ").append(hit.document.title)
                if (scope.showOrigin) append(" / 来源：").append(ContentSource.of(hit.document.id).label)
                hit.document.updatedAt?.let { append(" / 更新于 ").append(formatEpochDay(it)) }
                hit.document.effectiveFrom?.let { append(" / 自 ").append(it).append(" 起生效") }
                if (hit.document.owner.isNotBlank()) append(" / 责任人：").append(hit.document.owner)
                append("\n").append(hit.chunk.text)
            }
        }.joinToString("\n\n")

        return ToolOutcome(
            text = text,
            card = cardOf?.invoke(hits, markers),
            sources = hits.mapIndexed { index, hit ->
                val document = hit.document
                // 分成「来源」与「时效」两行：拼成一行时手机宽度下会在
                // 「责任人：图 / 书馆」这种词中间硬折，读起来像排版事故。
                val freshness = listOfNotNull(
                    document.updatedAt?.let { "更新于 ${formatEpochDay(it)}" },
                    document.effectiveFrom?.let { "自 $it 起生效" },
                    document.owner.takeIf { it.isNotBlank() }?.let { "责任人：$it" },
                ).joinToString(" · ")
                AssistantSource(
                    marker = markers[index],
                    title = document.title,
                    meta = listOfNotNull(
                        document.sourceLabel.takeIf { it.isNotBlank() }?.let { "来源：$it" },
                        freshness.takeIf { it.isNotBlank() },
                    ).joinToString("\n"),
                    // 来源条只留两行，先把换行/连排空白压平；text 里的原样片段不动。
                    snippet = hit.chunk.text.trim().replace(Regex("\\s+"), " "),
                    url = document.sourceUrl?.trim()?.takeIf {
                        it.startsWith("https://") || it.startsWith("http://")
                    },
                )
            },
        )
    }

    /**
     * 资料卡：只列**资料站**的命中（理由见 [AssistantCard.MaterialList] 的 KDoc）。
     *
     * 文件行与正文行走同一条路，差别在元信息从哪来：
     * - 文件行（`lib:file:`）从 [files] 反查 `LibraryFile`，拿到 类型/大小/`downloadable`；
     *   反查不到（索引刚换过、这一轮没传 files）就当"不可下载"，但**行不能丢** ——
     *   丢行会让正文里的 `[L1]` 变成没有卡片的编号。
     * - 正文行（`lib:doc:`）没有文件可下，`downloadable = false`，摘要用命中的 chunk。
     * - 署名（[AssistantCard.MaterialList.Row.provider]）文件行取 `LibraryFile.provider`，
     *   取不到再退到同名 `LibraryDoc.provider` —— 一份资料既有文件又有转录正文时，
     *   作者可能只在一处写了名字。
     */
    private fun materialCard(
        hits: List<KnowledgeHit>,
        markers: List<String>,
        files: List<LibraryFile>,
        docs: List<LibraryDoc>,
    ): AssistantCard? {
        val filesById = files.associateBy { UnifiedContent.fileDocumentId(it.id) }
        val docsById = docs.associateBy { UnifiedContent.docDocumentId(it.id) }
        val rows = hits.mapIndexedNotNull { index, hit ->
            if (ContentSource.of(hit.document.id) != ContentSource.LIBRARY) return@mapIndexedNotNull null
            val document = hit.document
            val file = filesById[document.id]
            // 分两行：文件属性一行、「更新于」一行。拼成一行时窄屏会在「更新 / 于」之间硬折，
            // 看着像排版事故（基线 61-turn-material-card 第一版就是这样）。
            val about = listOfNotNull(
                // 学科优先取索引里的 subject，退化到文档的 category（两者本就同源）
                file?.subject?.takeIf { it.isNotBlank() }
                    ?: document.category.takeIf { it.isNotBlank() },
                file?.course?.takeIf { it.isNotBlank() },
                file?.kind?.takeIf { it.isNotBlank() },
                file?.sizeText?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            val freshness = document.updatedAt?.let { "更新于 ${formatEpochDay(it)}" }.orEmpty()
            val meta = listOf(about, freshness).filter { it.isNotBlank() }.joinToString("\n")
            AssistantCard.MaterialList.Row(
                marker = markers[index],
                title = document.title,
                meta = meta,
                // 文件类摘要就是作者手写的那句（= 文档正文首行）；正文类用命中的片段。
                summary = file?.summary?.trim()?.takeIf { it.isNotEmpty() }
                    ?: hit.chunk.text.trim().replace(Regex("\\s+"), " "),
                downloadable = file?.downloadable ?: false,
                fileId = file?.id.orEmpty(),
                provider = file?.provider?.trim().orEmpty()
                    .ifBlank { docsById[document.id]?.provider?.trim().orEmpty() },
            )
        }
        if (rows.isEmpty()) return null

        val pending = rows.count { it.fileId.isNotBlank() && !it.downloadable }
        return AssistantCard.MaterialList(
            header = if (rows.size == 1) "找到 1 份相关资料" else "找到 ${rows.size} 份相关资料",
            rows = rows,
            hint = when {
                // 只统计"登记了文件却没传上去"的行：正文条目本来就没有文件可下，
                // 把它算进"还没传上去"会让用户以为有人漏传。
                pending == 0 -> "在「服务 → 资料站」可下载原文"
                pending == rows.size -> "这些资料还没传上去，暂不可下载"
                else -> "有 $pending 份还没传上去；其余在「服务 → 资料站」下载"
            },
        )
    }

    /** epoch 毫秒 → `YYYY-MM-DD`（按设备本地时区，与时效判断口径一致）。 */
    private fun formatEpochDay(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    /** 把课程教室号解析成「楼栋 楼层」，用于课程问答补充位置。 */
    fun roomBrief(cells: List<RoomCell>, roomNo: String): String? {
        if (roomNo.isBlank()) return null
        val cell = cells.firstOrNull {
            it.room.isNotBlank() && roomNo.contains(it.room.trim(), ignoreCase = true)
        } ?: return null
        return listOf(cell.building, cell.floor)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { null }
    }
    fun rooms(cells: List<RoomCell>, query: String): String {
        if (cells.isEmpty()) {
            return "还没有教室分布数据。可以在「服务 → 教室分布图」里录入，之后就能问我「A101 在哪」。"
        }

        val matched = cells.filter { c ->
            c.room.isNotBlank() && query.contains(c.room.trim(), ignoreCase = true)
        }
        if (matched.isEmpty()) {
            return "没找到对应教室。可以说得更具体一点，比如「A101 在哪」；教室可在「服务 → 教室分布图」查看，校园地点可在「服务 → 地图导航」的 3D 沙盘里找。"
        }

        val lines = matched.take(MAX_HITS).joinToString("\n") { c ->
            buildString {
                append("· ").append(c.room)
                if (c.name.isNotBlank()) append("（").append(c.name).append("）")
                append("：").append(c.building)
                if (c.floor.isNotBlank()) append(" ").append(c.floor)
                append(" · ").append(RoomCellType.fromKey(c.type).label)
                if (c.capacity > 0) append(" · 可坐 ").append(c.capacity).append(" 人")
            }
        }
        val more = if (matched.size > MAX_HITS) "\n（共 ${matched.size} 个，仅显示前 $MAX_HITS 个）" else ""
        return "查到 ${matched.size} 个匹配：\n$lines$more"
    }
}
