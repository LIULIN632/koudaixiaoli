package com.pocket.xiaoli.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 索引 v2：`docs[]` 与 `files[].summary/textRef`。
 *
 * ## 为什么这几条值得钉
 *
 * 1. **旧索引必须照常解析** —— 线上 `library/index.json` 现在是 v1（只有 `files`），
 *    新版客户端启动时读到的就是它。`docs` 缺席不是错误。
 * 2. **只有 `docs` 的索引不算空索引** —— `LibraryRepository.acceptReason` 拿
 *    `isEmpty` 挡"镜像还没同步"。判据只看 files 的话，一份只推了文字条目的索引
 *    会被当成"空的"永远刷不进来。
 * 3. **空条目要丢** —— 没有标题或没有任何可检索文本的 doc 进了检索只会占 topK 名额。
 * 4. **`textRef` 是索引内的引用，不是 URL** —— 手滑写成 http 链接时不该被下载器当文件。
 */
class LibraryDocsIndexTest {

    private val raw = """
        {
          "schemaVersion": 2,
          "generatedAt": "2026-10-30",
          "files": [
            {
              "id": "ds-1",
              "name": "数据结构 第1章 课件.pdf",
              "subject": "计算机",
              "course": "数据结构",
              "kind": "课件",
              "sizeBytes": 1048576,
              "urls": ["https://example.com/a.pdf"],
              "summary": "第一章绪论与算法复杂度，含 12 道课堂练习。",
              "textRef": "ds-1-text",
              "provider": "22 级计算机 王同学"
            },
            {
              "id": "ds-2",
              "name": "数据结构 期末真题.pdf",
              "kind": "历年卷"
            }
          ],
          "docs": [
            {
              "id": "ds-1-text",
              "title": "数据结构 第1章 课件（文字版）",
              "summary": "第一章全文转录",
              "body": "1.1 什么是数据结构\n\n1.2 复杂度分析",
              "subject": "计算机",
              "course": "数据结构",
              "kind": "课件",
              "tags": ["转录"]
            },
            {
              "id": "ds-2-text",
              "title": "数据结构 期末真题（考点整理）",
              "summary": "近三年高频考点：树、图、排序。"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `v2 索引能读出 files 的摘要与正文指向`() {
        val index = LibraryIndex.fromJson(raw)
        assertNotNull(index)
        val file = index!!.files.first { it.id == "ds-1" }
        assertEquals("第一章绪论与算法复杂度，含 12 道课堂练习。", file.summary)
        assertEquals("ds-1-text", file.textRef)
        // 没写摘要的文件照常进索引 —— UI 要能下载它，只是 AI 看不到。
        assertEquals("", index.files.first { it.id == "ds-2" }.summary)
    }

    @Test
    fun `署名 provider 解析出来，没写就是空串`() {
        val index = LibraryIndex.fromJson(raw)
        val file = index!!.files.first { it.id == "ds-1" }
        assertEquals("22 级计算机 王同学", file.provider)
        // 老索引（v1/v2 早期）没有 provider 字段 → 空串，界面据此不渲染署名行
        assertEquals("", index.files.first { it.id == "ds-2" }.provider)
        // 正文条目的署名单独存（`lib:doc:` 命中时没有文件可反查）
        assertEquals("", index.docs.first { it.id == "ds-1-text" }.provider)
    }

    @Test
    fun `docs 解析出两条，正文优先于摘要`() {        val docs = LibraryIndex.fromJson(raw)!!.docs
        assertEquals(2, docs.size)
        val full = docs.first { it.id == "ds-1-text" }
        assertTrue(full.content.contains("1.2 复杂度分析"))
        assertEquals(listOf("转录"), full.tags)
        // 只有摘要的条目：content 退回摘要，不是空串。
        val brief = docs.first { it.id == "ds-2-text" }
        assertEquals("近三年高频考点：树、图、排序。", brief.content)
    }

    @Test
    fun `只有 docs 的索引不算空索引`() {
        val docsOnly = LibraryIndex.fromJson(
            """{"generatedAt":"2026-10-30","docs":[{"id":"a","title":"标题","summary":"一句话"}]}"""
        )
        assertNotNull(docsOnly)
        assertTrue(docsOnly!!.files.isEmpty())
        assertFalse("docs 非空就不能被判成空索引，否则 acceptReason 永远拒绝它", docsOnly.isEmpty)
    }

    @Test
    fun `v1 索引没有 docs 字段也照常解析`() {
        val v1 = LibraryIndex.fromJson("""{"generatedAt":"2026-10-19","files":[{"id":"a","name":"课件.pdf"}]}""")
        assertNotNull(v1)
        assertEquals(1, v1!!.files.size)
        assertTrue(v1.docs.isEmpty())
        assertEquals("", v1.files.first().summary)
    }

    @Test
    fun `没标题或没有任何文本的 doc 被丢掉`() {
        val index = LibraryIndex.fromJson(
            """
            {"docs":[
              {"id":"no-title","summary":"有摘要没标题"},
              {"id":"no-text","title":"有标题没文本"},
              {"id":"ok","title":"正常","summary":"一句话"}
            ]}
            """.trimIndent()
        )!!
        assertEquals(listOf("ok"), index.docs.map { it.id })
    }

    @Test
    fun `坏 JSON 仍然返回 null 而不是抛`() {
        assertNull(LibraryIndex.fromJson("<html>502 Bad Gateway</html>"))
    }
}
