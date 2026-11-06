package com.pocket.xiaoli.data.knowledge

import com.pocket.xiaoli.data.library.LibraryDoc
import com.pocket.xiaoli.data.library.LibraryFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `search_library` 的资料卡（[AssistantCard.MaterialList]）。
 *
 * ## 为什么这些断言值得写
 *
 * 卡片与来源条是**两套渲染、一份数据**：来源条列全部命中（`kb/qa/faq/lib` 都有），
 * 卡片只列资料站。这个"只列一部分"的决定一旦漏掉，界面会把同一份知识库笔记
 * 在卡片和来源条里各显示一遍；反过来漏掉资料行，正文里的 `[L1]` 就成了没有卡片的孤号。
 *
 * 另外三条退化路径没有别的地方能守：
 * 1. 索引里登记了文件但 `urls` 还是空 → 必须**标不可下载**，不能给假按钮；
 * 2. 卡片构建时拿不到 `LibraryFile`（索引刚换、或调用方没传 files）→ 行不能丢；
 * 3. 完全不是资料站命中 → `card` 必须是 null，界面才不渲染空卡片。
 */
class AssistantFormattersMaterialCardTest {

    private fun document(
        id: String,
        title: String,
        body: String,
        updatedAt: Long? = null,
        category: String = "",
    ) = KnowledgeDocument(
        id = id,
        title = title,
        body = body,
        sourceLabel = ContentSource.of(id).label,
        updatedAt = updatedAt,
        category = category,
    )

    private fun hit(document: KnowledgeDocument, text: String = document.body) = KnowledgeHit(
        chunk = KnowledgeChunk(
            id = document.id + "#0",
            documentId = document.id,
            order = 0,
            text = text,
        ),
        document = document,
        score = 1,
        rankScore = 1.0,
    )

    private fun cardOf(outcome: ToolOutcome): AssistantCard.MaterialList? =
        outcome.card as? AssistantCard.MaterialList

    // ------------------------------------------------------------ 正常路径

    @Test
    fun `资料站文件命中进卡片并带上文件元信息`() {
        val file = LibraryFile(
            id = "f1",
            name = "数据结构期末卷 2023.pdf",
            subject = "计算机",
            course = "数据结构",
            kind = "历年卷",
            sizeBytes = 1_887_436L,
            urls = listOf("https://example.com/f1.pdf"),
            summary = "2023 秋期末，7 道大题，图与排序占 60 分。",
        )
        val document = document(
            id = UnifiedContent.fileDocumentId(file.id),
            title = file.name,
            body = file.summary,
            category = file.subject,
        )

        val outcome = AssistantFormatters.library(
            hits = listOf(hit(document)),
            totalDocuments = 1,
            query = "期末卷",
            files = listOf(file),
        )
        val card = cardOf(outcome)

        assertNotNull("资料站命中必须出卡片", card)
        assertEquals(1, card!!.rows.size)
        val row = card.rows.first()
        assertEquals("编号与正文里的 [L1] 同源", "L1", row.marker)
        assertEquals(file.name, row.title)
        // 元信息四件：学科 / 课程 / 类型 / 大小；缺一件用户就看不出这是哪门课的什么卷
        assertTrue(row.meta, row.meta.contains("计算机"))
        assertTrue(row.meta, row.meta.contains("数据结构"))
        assertTrue(row.meta, row.meta.contains("历年卷"))
        assertTrue(row.meta, row.meta.contains(file.sizeText))
        // 摘要用作者手写那句，而不是拼出来的"这是一份可下载的资料：…"
        assertEquals("作者写的摘要优先", file.summary, row.summary)
        assertTrue("有 urls 就该是可下载", row.downloadable)
        assertEquals(file.id, row.fileId)
        assertTrue("有可下载文件时提示要指向资料站", card.hint.contains("资料站"))
    }

    @Test
    fun `署名取索引里的 provider 缺了才退到正文条目`() {
        val file = LibraryFile(
            id = "f9",
            name = "线性代数期中卷 2024.pdf",
            subject = "数学",
            course = "线性代数",
            kind = "历年卷",
            provider = "22 级数学 张同学",
            urls = listOf("https://example.com/f9.pdf"),
        )
        val document = document(
            id = UnifiedContent.fileDocumentId(file.id),
            title = file.name,
            body = "矩阵与向量空间。",
        )
        val withFile = cardOf(
            AssistantFormatters.library(
                hits = listOf(hit(document)),
                totalDocuments = 1,
                query = "线性代数",
                files = listOf(file),
            ),
        )
        assertEquals("索引里的署名", "22 级数学 张同学", withFile!!.rows.first().provider)

        // 文件条目没写 provider，但同 id 的正文条目写了 → 仍然显示（正文条目也能署名）
        val doc = LibraryDoc(id = "transcript-1", title = "线性代数期中卷 2024（转录）", body = "矩阵与向量空间。", provider = "23 级数学 李同学")
        val docDocument = document(
            id = UnifiedContent.docDocumentId(doc.id),
            title = doc.title,
            body = doc.body,
        )
        val withDoc = cardOf(
            AssistantFormatters.library(
                hits = listOf(hit(docDocument)),
                totalDocuments = 1,
                query = "线性代数",
                docs = listOf(doc),
            ),
        )
        assertEquals("正文条目自己的署名", "23 级数学 李同学", withDoc!!.rows.first().provider)

        // 谁都没写 → 空串，界面据此不渲染这一行（不能印出「由  提供」）
        val anonymous = cardOf(
            AssistantFormatters.library(
                hits = listOf(hit(docDocument)),
                totalDocuments = 1,
                query = "线性代数",
            ),
        )
        assertEquals("", anonymous!!.rows.first().provider)
    }

    @Test
    fun `文件登记了但没传上去时标不可下载`() {        val file = LibraryFile(
            id = "f2",
            name = "高等数学提纲.docx",
            subject = "数学",
            kind = "课程大纲",
            summary = "第 1~7 章的考核范围。",
            urls = emptyList(),
        )
        val document = document(
            id = UnifiedContent.fileDocumentId(file.id),
            title = file.name,
            body = file.summary,
        )

        val outcome = AssistantFormatters.library(
            hits = listOf(hit(document)),
            totalDocuments = 1,
            query = "提纲",
            files = listOf(file),
        )
        val row = cardOf(outcome)!!.rows.first()

        assertFalse(row.downloadable)
        assertEquals("fileId 仍要带上：界面靠它区分'资料文件'与'正文条目'", file.id, row.fileId)
        // 文案必须说清"还没传上去"，而不是让用户点一个没反应的按钮
        assertTrue(cardOf(outcome)!!.hint, cardOf(outcome)!!.hint.contains("暂不可下载"))
    }

    // ------------------------------------------------------------ 退化路径

    @Test
    fun `拿不到 LibraryFile 时行不能丢`() {
        // 索引刚换过 / 调用方没传 files：下载状态未知，但 [L1] 必须仍有对应行
        val document = document(
            id = UnifiedContent.fileDocumentId("f3"),
            title = "线性代数真题.pdf",
            body = "2022 秋期末真题。",
        )

        val card = cardOf(
            AssistantFormatters.library(
                hits = listOf(hit(document)),
                totalDocuments = 1,
                query = "真题",
                files = emptyList(),
            ),
        )

        assertNotNull("反查不到文件也要出卡片", card)
        assertEquals(1, card!!.rows.size)
        val row = card.rows.first()
        assertEquals("线性代数真题.pdf", row.title)
        assertFalse("下载状态未知时按不可下载处理", row.downloadable)
        assertEquals("", row.fileId)
        // 摘要退回到命中的片段，卡片不至于只剩一个文件名
        assertEquals("2022 秋期末真题。", row.summary)
    }

    @Test
    fun `只有知识库命中时不出卡片`() {
        val document = document(
            id = ContentSource.KNOWLEDGE.prefix + "办事流程/VPN账户申请",
            title = "VPN 账户申请",
            body = "在企业微信里提交申请。",
        )

        val outcome = AssistantFormatters.library(
            hits = listOf(hit(document)),
            totalDocuments = 1,
            query = "VPN",
        )

        assertNull("知识库命中已有来源条，卡片不该重复渲染", outcome.card)
        // 来源条照旧要有——卡片为 null 不等于这条命中丢了
        assertEquals(1, outcome.sources.size)
        assertEquals("K1", outcome.sources.first().marker)
    }

    @Test
    fun `正文条目不可下载但摘要取命中片段`() {
        val doc = LibraryDocFixture(
            id = "d1",
            title = "数据结构 · 第 1 章 绪论（快问快答 6 题）",
            body = "什么是抽象数据类型？答：…",
        )
        val document = document(
            id = UnifiedContent.docDocumentId(doc.id),
            title = doc.title,
            body = doc.body,
        )

        val card = cardOf(
            AssistantFormatters.library(
                hits = listOf(hit(document, text = "什么是抽象数据类型？答：…")),
                totalDocuments = 1,
                query = "抽象数据类型",
                files = emptyList(),
            ),
        )

        assertNotNull("资料站正文条目属于资料库，要进卡片", card)
        val row = card!!.rows.first()
        assertEquals("L1", row.marker)
        assertFalse(row.downloadable)
        assertEquals("", row.fileId)
        assertEquals("什么是抽象数据类型？答：…", row.summary)
        // 正文条目不是"漏传的文件"，提示不能说"还没传上去"
        assertTrue(card.hint, card.hint.contains("资料站"))
        assertFalse(card.hint, card.hint.contains("还没传上去"))
    }

    @Test
    fun `混合命中时卡片只列资料站且编号与来源条一致`() {
        val kb = document(
            id = ContentSource.KNOWLEDGE.prefix + "规章制度/图书馆开放时间",
            title = "图书馆开放时间",
            body = "平时 07:00-22:00。",
        )
        val file = LibraryFile(id = "f4", name = "图书馆分布图.pdf", summary = "各层功能区。")
        val lib = document(
            id = UnifiedContent.fileDocumentId(file.id),
            title = file.name,
            body = file.summary,
        )

        val outcome = AssistantFormatters.library(
            hits = listOf(hit(kb), hit(lib)),
            totalDocuments = 2,
            query = "图书馆",
            files = listOf(file),
        )

        val card = cardOf(outcome)
        assertNotNull(card)
        assertEquals("卡片只列资料站那一条", 1, card!!.rows.size)

        // 编号必须与来源条同源：卡片上行叫 L1，来源条里也得有一条 L1 指的是它
        val row = card.rows.first()
        val source = outcome.sources.first { it.marker == row.marker }
        assertEquals(row.title, source.title)
        assertEquals(listOf("K1", "L1"), outcome.sources.map { it.marker })
    }

    /** 只用到 id/title/body 三个字段，避免测试里重复构造一整个 [LibraryDoc]。 */
    private data class LibraryDocFixture(val id: String, val title: String, val body: String)
}
