package com.pocket.xiaoli.data.knowledge

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `knowledge.json` 的 DTO 契约。
 *
 * ## 为什么专门测「Gson 造 null」这条路径
 *
 * 这两份 DTO 是**从网络与文件读进来的**（`assets/knowledge.json` 或镜像下载的缓存），
 * 读的时候走 `Gson().fromJson`。而 Gson **不走 Kotlin 的默认值**那条路取决于一件事：
 * 类有没有无参构造。
 *
 * 机制（本项目 `DormInfo` 在 v1.0.93 崩过一次，见 `CLAUDE.md`）：
 *
 * | 类里有没有「无默认值的字段」 | Kotlin 生成无参构造? | Gson 走哪条路 | 缺字段的结果 |
 * |---|---|---|---|
 * | 全部字段都有默认值 | ✅ | 无参构造 + 反射赋值 | **默认值生效，安全** |
 * | 有一个字段没有默认值 | ❌ | `UnsafeAllocator` 反射直接赋值 | **所有默认值一起失效 → null** |
 *
 * 也就是说：**「每个字段都给默认值」就是这道防线本身**。
 * 只要有一个字段漏了默认值，整个类就丢掉无参构造，于是**所有**字段的默认值一起失效。
 *
 * ⚠️ 而且**单测抓不到"不混淆就没事"那部分** —— 所以这里必须用
 * **真的 Gson 反序列化一个缺字段的 JSON** 来复现，而不是 `KnowledgeDocJson()` 构造。
 * 范例见 `QzxyKnownDeviceTest.每个字段都有默认值`。
 */
class KnowledgeDocJsonTest {

    @Test
    fun `KnowledgeDocJson 每个字段都有默认值`() {
        // 「无参构造存在」精确等价于「全字段都有默认值」——
        // 谁将来给某个字段加默认值时漏掉一个，这条测试就红。
        val noArg = KnowledgeDocJson::class.java.declaredConstructors
            .firstOrNull { it.parameterCount == 0 }
        assertNotNull(
            "KnowledgeDocJson 必须保留无参构造 —— 给某个字段加默认值时漏掉一个，" +
                "就会丢掉无参构造，Gson 改用反射赋值，缺字段变 null → 用户手机上崩。",
            noArg,
        )
    }

    @Test
    fun `KnowledgeIndexJson 每个字段都有默认值`() {
        val noArg = KnowledgeIndexJson::class.java.declaredConstructors
            .firstOrNull { it.parameterCount == 0 }
        assertNotNull(
            "KnowledgeIndexJson 必须保留无参构造 —— 理由同 KnowledgeDocJson。",
            noArg,
        )
    }

    @Test
    fun `缺字段的 JSON 读出来是默认值而不是 null`() {
        // 一份"老版本/别人的"产物，只有最少的字段
        val json = """{"docs":[{"id":"a","title":"标题","body":"正文"}]}"""

        val index = Gson().fromJson(json, KnowledgeIndexJson::class.java)
        val doc = index.docs[0]

        assertEquals("", doc.category)
        assertEquals("", doc.owner)
        assertEquals("", doc.sourceLabel)
        assertEquals("", doc.campus)
        assertEquals("hard", doc.expiryPolicy)
        assertEquals("", doc.effectiveFrom)
        assertEquals("", doc.effectiveTo)
        assertEquals(0L, doc.updatedAt)
        assertTrue("缺 tags 应该是空列表而不是 null", doc.tags.isEmpty())
    }

    @Test
    fun `完全空的 JSON 也能安全读成默认值`() {
        val index = Gson().fromJson("{}", KnowledgeIndexJson::class.java)

        assertEquals(0, index.protocolVersion)
        assertEquals("", index.versionId)
        assertEquals("", index.contentHash)
        assertEquals("", index.generatedAt)
        assertTrue("缺 docs 应该是空列表而不是 null", index.docs.isEmpty())
    }

    @Test
    fun `空对象作为一篇文档也能安全读`() {
        val index = Gson().fromJson("""{"docs":[{}]}""", KnowledgeIndexJson::class.java)

        assertEquals(1, index.docs.size)
        assertEquals("", index.docs[0].id)
        assertEquals("", index.docs[0].title)
    }

    @Test
    fun `字段名与构建器的 snake_case 输出对得上`() {
        val json = """
            {"protocol_version":1,"version_id":"TIME_1","content_hash":"sha256:x",
             "generated_at":"2026-10-03T18:00:00+08:00",
             "docs":[{"id":"i","title":"t","body":"b","category":"c","campus":"本校区",
                      "expiry_policy":"soft","owner":"o","source_label":"s",
                      "effective_from":"2025-09-01","effective_to":"2025-10-08",
                      "updated_at":1758297600000,"tags":["a"]}]}
        """.trimIndent()

        val index = Gson().fromJson(json, KnowledgeIndexJson::class.java)
        val doc = index.docs[0]

        assertEquals(1, index.protocolVersion)
        assertEquals("TIME_1", index.versionId)
        assertEquals("sha256:x", index.contentHash)
        assertEquals("soft", doc.expiryPolicy)
        assertEquals("s", doc.sourceLabel)
        assertEquals("2025-09-01", doc.effectiveFrom)
        assertEquals("2025-10-08", doc.effectiveTo)
        assertEquals(1_758_297_600_000L, doc.updatedAt)
        assertEquals("本校区", doc.campus)
    }

    @Test
    fun `缺字段的 JSON 经映射后仍可用`() {
        // 端到端：坏 JSON 不能把整个知识库弄崩
        val index = Gson().fromJson(
            """{"docs":[{"id":"a","title":"标题","body":"正文内容"}]}""",
            KnowledgeIndexJson::class.java,
        )
        val documents = KnowledgeJsonMapper.toDocuments(index)

        assertEquals(1, documents.size)
        assertEquals(ExpiryKind.HARD, documents[0].expiryKind)
        assertEquals(null, documents[0].effectiveTo)
    }

    // ── parseKnowledgeIndex：坏数据一律 null，不抛异常 ─────────────

    @Test
    fun `解析空白输入返回 null`() {
        assertNull(parseKnowledgeIndex(null))
        assertNull(parseKnowledgeIndex(""))
        assertNull(parseKnowledgeIndex("   "))
    }

    @Test
    fun `解析坏 JSON 返回 null 而不是抛异常`() {
        assertNull(parseKnowledgeIndex("{这不是 JSON"))
        assertNull(parseKnowledgeIndex("[]"))
    }

    @Test
    fun `docs 为 null 的 JSON 判为损坏`() {
        // Gson 绕过 Kotlin 空安全：JSON 里的 null 会真的覆盖掉非空 List 的默认值。
        // 不拦这一条的话，后面遍历 docs 就是 NPE 崩在用户手机上。
        assertNull(parseKnowledgeIndex("""{"protocol_version":1,"docs":null}"""))
    }

    @Test
    fun `正常 JSON 解析成功`() {
        val index = parseKnowledgeIndex(
            """{"protocol_version":1,"version_id":"TIME_1","docs":[{"id":"a","title":"t","body":"b"}]}""",
        )

        assertNotNull(index)
        assertEquals(1, index!!.protocolVersion)
        assertEquals("TIME_1", index.versionId)
        assertEquals(1, index.docs.size)
    }

    @Test
    fun `空骨架产物可以解析且没有任何文档`() {
        // 出厂内置的 assets/knowledge.json 就是这个形态
        val index = parseKnowledgeIndex(
            """{"protocol_version":1,"version_id":"TIME_19700101000000_000","content_hash":"","generated_at":"","docs":[]}""",
        )

        assertNotNull(index)
        assertTrue(index!!.docs.isEmpty())
        assertTrue(KnowledgeJsonMapper.toDocuments(index).isEmpty())
    }
}
