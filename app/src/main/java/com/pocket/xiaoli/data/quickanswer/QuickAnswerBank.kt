package com.pocket.xiaoli.data.quickanswer

import org.json.JSONArray
import org.json.JSONObject

/**
 * 快问快答道题：一道题 = 老师的提问 + 几条能照着说的答案要点。
 *
 * ## 为什么是「要点列表」而不是一段范文
 *
 * 课堂提问要的是「站起来能接上话」，不是背作文。所以答案按点拆开，
 * 每条一句，扫一眼就能开口；[hint] 放老师的口头习惯（"这题必考，注意区分"）。
 */
data class QuickAnswerQuestion(
    val id: String,
    val question: String,
    val answerPoints: List<String> = emptyList(),
    val hint: String = "",
    val tags: List<String> = emptyList(),
)

data class QuickAnswerChapter(
    val index: Int,
    val title: String,
    val questions: List<QuickAnswerQuestion> = emptyList(),
) {
    val displayTitle: String get() = if (title.startsWith("第")) title else "第${index}章 $title"
}

/** 在 `bank.json` 里 id 必须全局唯一 —— 会/不会 的标记就是按它存的，重名会串台。 */
data class QuickAnswerCourse(
    val id: String,
    val name: String,
    val subject: String = "",
    val teacher: String = "",
    val chapters: List<QuickAnswerChapter> = emptyList(),
) {
    val questionCount: Int get() = chapters.sumOf { it.questions.size }

    /** 学科（第一层分组）：留空归到「未分学科」。 */
    val subjectGroup: String get() = subject.ifBlank { QuickAnswerBank.UNCATEGORIZED_SUBJECT }
}

/** 搜索结果：把「在哪门课、哪一章」一起带出来，免得用户看到一个孤零零的题干。 */
data class QuickAnswerHit(
    val course: QuickAnswerCourse,
    val chapter: QuickAnswerChapter,
    val question: QuickAnswerQuestion,
)

data class QuickAnswerBank(
    val updatedAt: String = "",
    val courses: List<QuickAnswerCourse> = emptyList(),
) {
    val isEmpty: Boolean get() = courses.isEmpty()

    fun course(id: String?): QuickAnswerCourse? = courses.firstOrNull { it.id == id } ?: courses.firstOrNull()

    /** 出现过的学科，按 bank.json 里的顺序去重 —— 作者写的顺序就是展示顺序。 */
    fun subjects(): List<String> = courses.map { it.subjectGroup }.distinct()

    /** 某学科下的课程；学科为 null 时返回全部。 */
    fun coursesOf(subject: String?): List<QuickAnswerCourse> =
        subject?.let { s -> courses.filter { it.subjectGroup == s } } ?: courses

    /** 在给定课程里按题干/要点/标签打分搜索；[courseId] 为空则全库搜。 */
    fun search(query: String, courseId: String? = null, limit: Int = 60): List<QuickAnswerHit> {
        val q = normalize(query)
        val scope = courseId?.let { id -> courses.filter { it.id == id } } ?: courses
        if (q.isEmpty()) return emptyList()
        return scope.flatMap { course ->
            course.chapters.flatMap { chapter ->
                chapter.questions.mapNotNull { question ->
                    scoreOf(question, q)?.let { score -> Triple(QuickAnswerHit(course, chapter, question), score, chapter.index) }
                }
            }
        }
            .sortedWith(
                compareByDescending<Triple<QuickAnswerHit, Int, Int>> { it.second }
                    .thenBy { it.first.course.name }
                    .thenBy { it.third }
            )
            .map { it.first }
            .take(limit)
    }

    private fun scoreOf(question: QuickAnswerQuestion, q: String): Int? {
        val title = normalize(question.question)
        val tagHit = question.tags.any { normalize(it).contains(q) }
        val answerHit = question.answerPoints.any { normalize(it).contains(q) }
        return when {
            title.startsWith(q) -> 40
            title.contains(q) -> 30
            tagHit -> 20
            answerHit -> 10
            else -> null
        }
    }

    companion object {
        val EMPTY = QuickAnswerBank()

        const val UNCATEGORIZED_SUBJECT = "未分学科"

        internal fun normalize(raw: String): String =
            raw.lowercase().replace(" ", "").replace("\u3000", "").trim()

        /** 坏数据整份丢弃：宁可显示「题库还没上架」，也不要显示半份空题干。 */
        fun fromJson(raw: String): QuickAnswerBank? = runCatching {
            val root = JSONObject(raw)
            val array: JSONArray = root.optJSONArray("courses") ?: JSONArray()
            val courses = buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    parseCourse(obj)?.let(::add)
                }
            }
            QuickAnswerBank(updatedAt = root.optString("updatedAt", ""), courses = courses)
        }.getOrNull()

        private fun parseCourse(obj: JSONObject): QuickAnswerCourse? {
            val name = obj.optString("name", "").trim()
            if (name.isEmpty()) return null
            val id = obj.optString("id", "").trim().ifEmpty { name }
            val chaptersArray = obj.optJSONArray("chapters") ?: JSONArray()
            val chapters = buildList {
                for (i in 0 until chaptersArray.length()) {
                    val chapterObj = chaptersArray.optJSONObject(i) ?: continue
                    parseChapter(chapterObj, index = i + 1, courseId = id)?.let(::add)
                }
            }
            return QuickAnswerCourse(
                id = id,
                name = name,
                subject = obj.optString("subject", "").trim(),
                teacher = obj.optString("teacher", "").trim(),
                chapters = chapters,
            )
        }

        private fun parseChapter(obj: JSONObject, index: Int, courseId: String): QuickAnswerChapter? {
            val questionsArray = obj.optJSONArray("questions") ?: JSONArray()
            val questions = buildList {
                for (i in 0 until questionsArray.length()) {
                    val questionObj = questionsArray.optJSONObject(i) ?: continue
                    val question = questionObj.optString("question", "").trim()
                    if (question.isEmpty()) continue
                    add(
                        QuickAnswerQuestion(
                            // 缺 id 时用「课程-章-序」兜底，保证标记能落地。
                            id = questionObj.optString("id", "").trim()
                                .ifEmpty { "$courseId-${obj.optInt("index", index)}-${i + 1}" },
                            question = question,
                            answerPoints = questionObj.optJSONArray("answerPoints").toStringList(),
                            hint = questionObj.optString("hint", "").trim(),
                            tags = questionObj.optJSONArray("tags").toStringList(),
                        )
                    )
                }
            }
            if (questions.isEmpty()) return null
            return QuickAnswerChapter(index = obj.optInt("index", index), title = obj.optString("title", "").trim(), questions = questions)
        }

        private fun JSONArray?.toStringList(): List<String> {
            if (this == null) return emptyList()
            return buildList {
                for (i in 0 until length()) {
                    optString(i, "").trim().takeIf { it.isNotEmpty() }?.let(::add)
                }
            }
        }
    }
}
