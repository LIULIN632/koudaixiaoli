package com.pocket.xiaoli.desktop.logic

import com.pocket.xiaoli.desktop.data.QaCourse
import com.pocket.xiaoli.desktop.data.QaQuestion

/**
 * 没配 AI Key 时的本地回答。
 *
 * 鲤鱼页原来的行为是「没 Key 就弹配置框」—— 那是对的（不能假装能联网答），但用户问的
 * 十有八九是校园事实题，知识库里往往就有答案。所以这里给一条**离线也能答**的路径：
 * 命中就直说来源，没命中就明说没收录，绝不编。
 */
data class OfflineAnswer(
    val text: String,
    val sourceTitles: List<String>,
)

/** 由知识库命中拼一条回答；没有命中返回 null（调用方再决定怎么说）。 */
fun answerFromKnowledge(hits: List<Knowledge.Hit>, question: String): OfflineAnswer? {
    val usable = hits.filter { !it.expired && it.doc.body.isNotBlank() }
    if (usable.isEmpty()) return null
    // 同一篇文档可能被切成多段命中，取前两段会把一条答案说两遍 —— 按文档去重
    val top = usable.distinctBy { it.doc.id }.take(2)
    val body = buildString {
        appendLine("先按本机知识库答你（没配 AI Key，联网问答不可用）：")
        appendLine()
        top.forEachIndexed { index, hit ->
            appendLine("${index + 1}. ${hit.doc.title}")
            appendLine(hit.snippet.ifBlank { hit.doc.body.replace("\n", " ").take(200) })
            appendLine()
        }
        append("来源：${top.joinToString("、") { it.doc.title }}。要联网再问一轮，去「鲤鱼 → 右上角设置」填 API Key。")
    }
    return OfflineAnswer(text = body.trimEnd(), sourceTitles = top.map { it.doc.title })
}

/**
 * 快问快答里按关键词匹配题目。
 *
 * 中文没有空格分词，所以取「连续汉字/字母数字段 + 二字组合」当关键词：
 * 「数据结构 第三章 绪论」会拆出 `数据结构`/`绪论`/`数据`/`据结`/`结构` 这类，
 * 足够让"绪论讲了什么"命中到对应题，而不会靠整句相等这种一碰就碎的判据。
 */
fun matchQuickAnswer(question: String, bank: List<QaCourse>, limit: Int = 3): List<QaQuestion> {
    val keys = keywordsOf(question)
    if (keys.isEmpty()) return emptyList()
    return bank.asSequence()
        .flatMap { it.chapters.asSequence() }
        .flatMap { chapter ->
            chapter.questions.asSequence().map { q ->
                val haystack = (q.question + " " + q.tags.joinToString(" ") + " " + chapter.title).lowercase()
                q to keys.count { haystack.contains(it.lowercase()) }
            }
        }
        .filter { it.second > 0 }
        .sortedWith(compareByDescending<Pair<QaQuestion, Int>> { it.second }.thenBy { it.first.question })
        .take(limit)
        .map { it.first }
        .toList()
}

/** 关键词 = 连续段（≥2 字符）+ 段内的二字组合。 */
internal fun keywordsOf(text: String): List<String> {
    val runs = Regex("""[\p{L}\p{N}]{2,}""").findAll(text).map { it.value }.toList()
    val bigrams = runs.flatMap { run -> (0 until run.length - 1).map { run.substring(it, it + 2) } }
    return (runs + bigrams).distinct()
}
