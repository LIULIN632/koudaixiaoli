package com.pocket.xiaoli.data.library

import com.pocket.xiaoli.ui.library.mimeOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 资料站的数据层与两条"最容易被忽略的正确性"。
 *
 * ## 为什么这几条值得钉
 *
 * 1. **索引解析失败必须返回 null 而不是抛** —— 拉到的 JSON 要是被墙挂首页 HTML，
 *    抛异常会让整页停在一个加载态；返回 null 才能退回本地缓存。
 * 2. **搜索要能按课程名、类型、别名命中** —— 学生记不住文件名，只记得"数据结构的期末卷"。
 * 3. **`urls` 为空 = 待上传**，不是"下载失败" —— 两句话在 UI 上完全不同。
 * 4. **MIME 写死一张小表**（`LibraryScreen.mimeOf`）：`MimeTypeMap` 在部分 ROM 上
 *    对 `.docx`/`.pptx` 返回 null，而这几个正是资料站的主力格式。
 */
class LibraryIndexTest {

    private val raw = """
        {
          "generatedAt": "2026-10-19",
          "files": [
            {
              "id": "ds-1",
              "name": "数据结构 第1章 课件.pdf",
              "subject": "计算机",
              "course": "数据结构",
              "kind": "课件",
              "sizeBytes": 1048576,
              "urls": ["https://example.com/a.pdf"],
              "tags": ["严蔚敏", "绪论"]
            },
            {
              "id": "ds-2",
              "name": "数据结构 期末真题.pdf",
              "subject": "计算机",
              "course": "数据结构",
              "kind": "历年卷",
              "sizeBytes": 2097152
            },
            {
              "id": "math-1",
              "name": "高数习题答案.docx",
              "subject": "数学",
              "course": "高等数学",
              "kind": "习题答案",
              "sizeBytes": 512000,
              "urls": ["https://example.com/b.docx"]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `解析索引并保留下载状态`() {
        val index = LibraryIndex.fromJson(raw)
        assertNotNull("合法 JSON 必须解析成功", index)
        val files = index!!.files
        assertEquals("三份资料都要进来", 3, files.size)
        assertTrue("有 urls 的才算可下载", files.first { it.id == "ds-1" }.downloadable)
        assertTrue("没有 urls 的是待上传，不是下载失败", !files.first { it.id == "ds-2" }.downloadable)
    }

    @Test
    fun `坏 JSON 返回 null 而不是抛异常`() {
        assertEquals("空串", null, LibraryIndex.fromJson(""))
        assertEquals("HTML 首页", null, LibraryIndex.fromJson("<html>404</html>"))
        assertEquals("截断的 JSON", null, LibraryIndex.fromJson("""{"files": [{"id": """))
    }

    @Test
    fun `搜索能按文件名、课程、类型与别名命中`() {
        val index = LibraryIndex.fromJson(raw)!!
        assertEquals("按课程名", 2, index.search("数据结构").size)
        assertEquals("按类型", 1, index.search("历年卷").size)
        assertEquals("按别名（tags）", 1, index.search("严蔚敏").size)
        assertEquals("大小写与空白不影响", 2, index.search("  数据结构  ").size)
        assertEquals("搜不到就是空", 0, index.search("量子力学").size)
    }

    @Test
    fun `搜索排序把文件名命中排在别名命中前面`() {
        val index = LibraryIndex.fromJson(raw)!!
        val hits = index.search("绪论")
        assertTrue("至少命中一条", hits.isNotEmpty())
        // 文件名里带「绪论」的排在只有 tags 命中的前面 —— 这里两条其实是同一份，
        // 所以只断言「命中的就是那份课件」。
        assertEquals("ds-1", hits.first().id)
    }

    @Test
    fun `体积格式化按 MB 与 KB 分档`() {
        assertEquals("1.0 MB", LibraryFile.formatSize(1024L * 1024L))
        assertEquals("4.6 MB", LibraryFile.formatSize(4_823_449L))
        assertEquals("792 KB", LibraryFile.formatSize(812_000L))
    }

    @Test
    fun `扩展名到 MIME 的映射覆盖资料站主力格式`() {
        assertEquals("application/pdf", mimeOf("pdf"))
        assertEquals("application/pdf", mimeOf("PDF"))
        assertEquals(
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            mimeOf("pptx"),
        )
        assertEquals(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            mimeOf("docx"),
        )
        assertEquals("application/zip", mimeOf("zip"))
        assertEquals("未知扩展名兜底", "application/octet-stream", mimeOf("xyz"))
    }
    @Test
    fun `学科是第一层分组且按索引顺序去重`() {
        val index = LibraryIndex.fromJson(raw)!!
        assertEquals(listOf("计算机", "数学"), index.subjects())
        // 没写 subject 的文件归到「未分学科」，不会出现空标题的分组。
        val loose = LibraryIndex.fromJson(
            """{"files":[{"name":"a.pdf"}]}""",
        )!!
        assertEquals(listOf(LibraryFile.UNCATEGORIZED_SUBJECT), loose.subjects())
    }

    @Test
    fun `搜学科名也能命中该学科下的文件`() {
        val index = LibraryIndex.fromJson(raw)!!
        val hits = index.search("计算机")
        assertTrue("按学科搜应该有结果", hits.isNotEmpty())
        assertTrue("结果必须都属于该学科", hits.all { it.subjectGroup == "计算机" })
    }
}
