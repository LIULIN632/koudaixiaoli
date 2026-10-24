package com.pocket.xiaoli.data.knowledge

import com.google.gson.Gson
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 端到端：从**构建器产物的原文**一路走到鲤鱼的回答文本。
 *
 * 覆盖链路：`parseKnowledgeIndex` → `KnowledgeJsonMapper` → `ExpiryPolicy`
 * → `KeywordRetriever`（含时间加权）→ `AssistantFormatters.knowledge`。
 *
 * 唯一没覆盖的是 `KnowledgeStore` 读 `Context.assets` / `Context.filesDir` 那两步 ——
 * 那需要真机或 Robolectric（本项目单测没有 Robolectric），属于装机验证的范围。
 *
 * 夹具 JSON 刻意手写成**与 `tools/kb/build_knowledge.py` 输出同形**，
 * 字段名由 `KnowledgeDocJsonTest` 与 Python 侧的 `OutputContractTest` 两边钉住。
 */
class KnowledgePipelineEndToEndTest {

    private val fixture = """
        {
          "protocol_version": 1,
          "version_id": "TIME_20261003181000_000",
          "content_hash": "sha256:fixture",
          "generated_at": "2026-10-03T18:10:00+08:00",
          "docs": [
            {
              "id": "场馆信息/图书馆开放时间",
              "title": "图书馆开放时间",
              "category": "场馆信息",
              "campus": "本校区",
              "expiry_policy": "hard",
              "owner": "图书馆",
              "source_label": "图书馆官网 2025-09 通知",
              "effective_from": "2025-09-01",
              "effective_to": "9999-12-31",
              "updated_at": 1758297600000,
              "tags": ["图书馆", "开放时间"],
              "body": "平时开放时间：周一至周五 07:00-22:00\n\n考试周延长：期末考试周延长至 23:30"
            },
            {
              "id": "通知/2025-国庆放假安排",
              "title": "2025 国庆放假安排（已过期）",
              "category": "通知",
              "campus": "本校区",
              "expiry_policy": "soft",
              "owner": "校办公室",
              "source_label": "校办通知",
              "effective_from": "2025-09-28",
              "effective_to": "2025-10-08",
              "updated_at": 1758297600000,
              "tags": ["通知", "放假"],
              "body": "放假时间：10 月 1 日至 10 月 8 日"
            },
            {
              "id": "规章制度/已废止的旧规定",
              "title": "已废止的旧规定",
              "category": "规章制度",
              "campus": "本校区",
              "expiry_policy": "hard",
              "owner": "教务处",
              "source_label": "教务处",
              "effective_from": "2020-01-01",
              "effective_to": "2021-01-01",
              "updated_at": 1577836800000,
              "tags": ["旧规定"],
              "body": "补考报名说明：这条规定已经废止，硬过滤必须让它彻底消失"
            }
          ]
        }
    """.trimIndent()

    /** 一个"今天"，落在两份通知的过期日之后。 */
    private val today = LocalDate.of(2026, 10, 3)

    private fun documents() =
        KnowledgeJsonMapper.toDocuments(parseKnowledgeIndex(fixture)!!)

    private fun answer(query: String, today: LocalDate = this.today): String {
        val usable = ExpiryPolicy.apply(documents(), today)
        return AssistantFormatters.knowledge(
            hits = KeywordRetriever.search(query, usable, topK = 5),
            totalDocuments = usable.size,
            query = query,
        ).text
    }

    @Test
    fun `夹具 JSON 能完整解析成三篇文档`() {
        val documents = documents()
        assertTrue(3 == documents.size)
        assertTrue(documents.all { it.title.isNotBlank() && it.body.isNotBlank() })
    }

    @Test
    fun `问图书馆时间能答出带来源与更新时间的命中`() {
        val output = answer("图书馆开放时间")

        assertTrue(output.startsWith("[K1] 图书馆开放时间"))
        assertTrue(output.contains("更新于 2025-09-20"))
        assertTrue(output.contains("责任人：图书馆"))
        assertTrue(output.contains("07:00-22:00"))
    }

    @Test
    fun `过期的硬过滤内容彻底检索不到`() {
        val output = answer("补考报名说明")

        assertFalse("hard 过期内容不该出现在回答里", output.contains("已经废止"))
        assertTrue(output.contains("没有查到"))
    }

    @Test
    fun `过期的软保留内容仍能检索到但带过期提醒`() {
        val output = answer("国庆放假安排")

        assertTrue("soft 过期内容应当仍然可检索", output.contains("10 月 1 日至 10 月 8 日"))
        assertTrue("必须带过期提醒", output.contains("已过期"))
        assertTrue(output.contains("2025-10-08"))
    }

    @Test
    fun `同一天里 soft 内容在过期当天仍算有效`() {
        // 边界：today == effective_to → 还没过期，不该出现提醒
        val output = answer("国庆放假安排", today = LocalDate.of(2025, 10, 8))

        assertTrue(output.contains("10 月 1 日至 10 月 8 日"))
        assertFalse("最后一天不该被标成已过期", output.contains("⚠️"))
    }

    @Test
    fun `空知识库给出暂无内容文案`() {
        val output = AssistantFormatters.knowledge(emptyList(), 0, "图书馆几点开门").text
        assertTrue(output.contains("暂无内容"))
    }

    @Test
    fun `检索结果同时产出来源条`() {
        val usable = ExpiryPolicy.apply(documents(), today)
        val outcome = AssistantFormatters.knowledge(
            hits = KeywordRetriever.search("图书馆开放时间", usable, topK = 5),
            totalDocuments = usable.size,
            query = "图书馆开放时间",
        )

        assertTrue(outcome.sources.isNotEmpty())
        assertTrue(outcome.sources.first().marker == "K1")
        assertTrue(outcome.sources.first().title == "图书馆开放时间")
        assertTrue(outcome.sources.first().meta.contains("图书馆官网"))
        assertTrue(outcome.sources.first().meta.contains("2025-09-20"))
    }

    @Test
    fun `产物里的时间戳能解析成可读日期`() {
        // 构建器输出的是 epoch 毫秒，App 侧要能还原成日期（来源卡片显示"更新于 X"）
        val index = Gson().fromJson(fixture, KnowledgeIndexJson::class.java)
        assertTrue(index.docs[0].updatedAt > 0L)
        assertTrue(answer("图书馆开放时间").contains("2025-09-20"))
    }
}
