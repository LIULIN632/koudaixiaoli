package com.pocket.xiaoli.data.knowledge

import com.pocket.xiaoli.data.library.LibraryDoc
import com.pocket.xiaoli.data.library.LibraryFile
import com.pocket.xiaoli.data.quickanswer.QuickAnswerBank
import com.pocket.xiaoli.di.AppContainer

/**
 * 「资料库」的**四类内容来源**。
 *
 * ## 为什么要一张表而不是四个 if
 *
 * 检索结果只回 `documentId`（见 `KeywordRetrieverCore.search` 的 `KnowledgeSnippet`），
 * 要靠 id 前缀反查"这条来自哪、界面上标什么"。前缀写在两个地方就会漂：查的时候用
 * `"qa:"`、展示的时候忘了带，来源条就变成一串没有出处的文字。
 *
 * @param prefix id 前缀，**改它等于改历史数据的可读性**，别动。
 * @param label 来源条与回答里的中文名（用户看得到）。
 * @param marker 引用编号的前缀：正文里写 `[K1]`，来源条也是 `K1`。
 */
enum class ContentSource(val prefix: String, val label: String, val marker: String) {
    /** 校园知识库（Obsidian 维护的学校事实）。 */
    KNOWLEDGE("kb:", "校园知识库", "K"),

    /** 快问快答（每章课堂提问与答案要点）。 */
    QUICK_ANSWER("qa:", "快问快答", "Q"),

    /** 内置 App 使用帮助（LocalFaqKnowledgeSource）。 */
    FAQ("faq:", "口袋小鲤使用帮助", "F"),

    /** 资料站（可下载文件与手工转录的正文条目）。 */
    LIBRARY("lib:", "资料站", "L"),
    ;

    companion object {
        /** 按 id 前缀反查；未知前缀按知识库对待（宁可标签不对，也不要丢掉来源）。 */
        fun of(documentId: String): ContentSource =
            entries.firstOrNull { documentId.startsWith(it.prefix) } ?: KNOWLEDGE
    }
}

/**
 * 把四个来源压成同一份 [KnowledgeDocument] 列表，交给**唯一**的检索器
 * （`core/knowledge/KeywordRetrieverCore.kt`，与 Windows 桌面端同一份打分）。
 *
 * ## 三条纪律
 *
 * 1. **不在这里过滤时效**。过期判断依赖"今天是哪天"，必须每次检索现算 ——
 *    由 [AssistantTools] 在合并后调 `ExpiryPolicy.apply`（见 `ExpiryPolicy` 类注释）。
 * 2. **不在这里联网**。四个来源取的都是内存里已有的东西：知识库（`ensureLoaded` 读缓存/内置）、
 *    快问快答（`QuickAnswerRepository.state`，启动时已从内置 + 缓存载入）、资料索引
 *    （`LibraryRepository.state`，同上）、内置 FAQ（编译进 APK）。
 *    用户问一句就发四个请求是硬纪律问题，不是性能问题。
 * 3. **id 前缀只在这里加**。`kb:` / `qa:` / `faq:` / `lib:` 一次成型，
 *    下游（`ContentSource.of`）只读不拼。
 */
object UnifiedContent {

    /** 资料站文件条目的 id 前缀后缀：`lib:file:<id>`。 */
    private const val FILE_ID = "lib:file:"

    /** 资料站正文条目的 id 前缀后缀：`lib:doc:<id>`。 */
    private const val DOC_ID = "lib:doc:"

    /**
     * `LibraryFile.id` → 检索用的文档 id。
     *
     * 拼前缀的地方**只有这里**：界面要从命中反查回 `LibraryFile`（拿 kinds/size/urls
     * 去渲染资料卡），两处各拼一遍迟早漂成"卡片上永远没有可下载的文件"。
     */
    internal fun fileDocumentId(fileId: String): String = FILE_ID + fileId

    /** `LibraryDoc.id` → 检索用的文档 id。 */
    internal fun docDocumentId(docId: String): String = DOC_ID + docId

    suspend fun collect(container: AppContainer): List<KnowledgeDocument> = buildList {
        // ① 校园知识库 —— 只有这一类是"远端的"（`requireLoaded` 会读缓存，必要时回内置）
        container.knowledgeStore.ensureLoaded().forEach { document ->
            add(document.copy(id = ContentSource.KNOWLEDGE.prefix + document.id))
        }

        // ② 快问快答：一道题 = 一条，题干当标题，答案要点拼成正文。
        addAll(quickAnswers(container.quickAnswerRepository.state.value.bank))

        // ③ 内置 App 使用帮助：本来就已经是 KnowledgeDocument
        LocalFaqKnowledgeSource.documents.forEach { document ->
            add(document.copy(id = ContentSource.FAQ.prefix + document.id))
        }

        // ④ 资料站：可下载文件（只读 summary）+ 正文条目（读全文）
        val index = container.libraryRepository.state.value.index
        addAll(libraryFiles(index.files))
        addAll(libraryDocs(index.docs))
    }

    // ---------------------------------------------------------------- 快问快答

    internal fun quickAnswers(bank: QuickAnswerBank): List<KnowledgeDocument> = buildList {
        bank.courses.forEach { course ->
            course.chapters.forEach { chapter ->
                chapter.questions.forEach { question ->
                    val body = buildString {
                        question.answerPoints.forEach { append("- ").append(it).append('\n') }
                        if (question.hint.isNotBlank()) append("老师的口头习惯：").append(question.hint).append('\n')
                    }.trim()
                    // 没有答案要点的题不进来：模型只能读到这里，一条空答案等于让它编。
                    if (body.isEmpty()) return@forEach
                    val heading = listOfNotNull(
                        course.name.takeIf { it.isNotBlank() },
                        chapter.title.takeIf { it.isNotBlank() }?.let { chapter.displayTitle },
                    ).joinToString(" · ")
                    add(
                        KnowledgeDocument(
                            id = ContentSource.QUICK_ANSWER.prefix + question.id,
                            title = question.question,
                            body = if (heading.isBlank()) body else "$heading\n\n$body",
                            sourceLabel = ContentSource.QUICK_ANSWER.label +
                                course.name.takeIf { it.isNotBlank() }?.let { "（$it）" }.orEmpty(),
                            category = course.subject,
                            tags = question.tags,
                        ),
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------------- 资料站

    internal fun libraryFiles(files: List<LibraryFile>): List<KnowledgeDocument> =
        files.mapNotNull { file ->
            // 摘要必填（设计 §5）；没写的条目对 AI 不可见，但界面上照样能下载 ——
            // 这比让模型从文件名硬猜内容安全。
            val summary = file.summary.trim()
            if (summary.isEmpty()) return@mapNotNull null
            KnowledgeDocument(
                id = fileDocumentId(file.id),
                title = file.name,
                body = buildString {
                    append(summary)
                    append("\n\n这是一份可下载的资料：")
                    append(file.name)
                    listOf(
                        file.kind.takeIf { it.isNotBlank() },
                        file.course.takeIf { it.isNotBlank() },
                        file.sizeText.takeIf { it.isNotBlank() },
                    ).filterNotNull().forEach { append("，").append(it) }
                    append("。入口在「服务 → 资料站」。")
                },
                sourceLabel = ContentSource.LIBRARY.label,
                category = file.subject,
                tags = file.tags,
            )
        }

    internal fun libraryDocs(docs: List<LibraryDoc>): List<KnowledgeDocument> =
        docs.mapNotNull { doc ->
            val content = doc.content
            if (content.isBlank()) return@mapNotNull null
            KnowledgeDocument(
                id = docDocumentId(doc.id),
                title = doc.title,
                body = content,
                sourceLabel = ContentSource.LIBRARY.label,
                category = doc.subject,
                tags = doc.tags + listOfNotNull(doc.course.takeIf { it.isNotBlank() }, doc.kind.takeIf { it.isNotBlank() }),
            )
        }
}
