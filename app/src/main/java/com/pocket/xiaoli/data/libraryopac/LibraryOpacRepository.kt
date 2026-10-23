package com.pocket.xiaoli.data.libraryopac

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

data class LibraryOpacBook(
    val title: String,
    val author: String = "",
    val publisher: String = "",
    val year: String = "",
    val callNumber: String = "",
    val detailUrl: String = "",
    val rawText: String = "",
)

object LibraryOpacRepository {

    private const val SEARCH_URL = "https://lib.yitsd.edu.cn/opac/search"

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    fun searchUrl(query: String): String =
        SEARCH_URL + "?q=" + URLEncoder.encode(query.trim(), "UTF-8")

    suspend fun search(query: String): Result<List<LibraryOpacBook>> = withContext(Dispatchers.IO) {
        val keyword = query.trim()
        if (keyword.isBlank()) return@withContext Result.success(emptyList())
        try {
            val request = Request.Builder()
                .url(searchUrl(keyword))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("图书馆检索返回 HTTP " + response.code)
                    )
                }
                val html = response.body?.string().orEmpty()
                if (html.isBlank()) {
                    return@withContext Result.failure(IllegalStateException("图书馆检索返回空页面"))
                }
                val books = parseSearchResults(html, searchUrl(keyword))
                if (books.isEmpty()) {
                    Result.failure(IllegalStateException("未解析到馆藏结果，可切换到网页检索"))
                } else {
                    Result.success(books)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    internal fun parseSearchResults(html: String, baseUrl: String): List<LibraryOpacBook> {
        if (html.isBlank()) return emptyList()
        val document = Jsoup.parse(html, baseUrl)

        val strict = document.select("a[href]")
            .mapNotNull { anchor ->
                val href = anchor.absUrl("href").ifBlank { anchor.attr("href") }
                if (!looksLikeBookDetail(href)) null else parseAnchor(anchor)
            }
            .distinctBy { it.detailUrl.ifBlank { it.title } }
            .take(40)
        if (strict.isNotEmpty()) return strict

        val broadSelectors = listOf(
            ".booklist li a[href]",
            ".result-list li a[href]",
            ".book-item a[href]",
            ".result-item a[href]",
            "#searchresult a[href]",
            ".search-result a[href]",
            "table tr a[href]",
        ).joinToString(",")
        return document.select(broadSelectors)
            .mapNotNull { anchor -> parseAnchor(anchor) }
            .filter { it.title.length in 2..120 && !it.title.contains("检索") && !it.title.contains("登录") }
            .distinctBy { it.detailUrl.ifBlank { it.title } }
            .take(40)
    }

    private fun looksLikeBookDetail(href: String): Boolean {
        if (href.isBlank()) return false
        val lower = href.lowercase()
        if (lower.contains("login") || lower.contains("search?")) return false
        return lower.contains("/opac/book") ||
            lower.contains("/opac/detail") ||
            lower.contains("/opac/bib") ||
            lower.contains("/book/") ||
            lower.contains("bookid=") ||
            lower.contains("book_id=")
    }

    private fun parseAnchor(anchor: Element): LibraryOpacBook? {
        val title = anchor.text().trim().ifBlank { anchor.attr("title").trim() }
        if (title.length !in 2..120) return null

        val container = anchor.closest("li, tr, .book-item, .result-item, .list-item, .booklist-item")
            ?: anchor.parent()
            ?: anchor
        val rawText = container.text()
            .replace('\u00A0', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()

        return LibraryOpacBook(
            title = title,
            author = extractField(rawText, "作者", "著者", "责任者"),
            publisher = extractField(rawText, "出版社", "出版者", "出版发行"),
            year = extractYear(rawText),
            callNumber = extractField(rawText, "索书号", "馆藏索书号", "索取号"),
            detailUrl = anchor.absUrl("href").ifBlank { anchor.attr("href") },
            rawText = rawText,
        )
    }

    private fun extractField(text: String, vararg labels: String): String {
        for (label in labels) {
            val match = Regex(
                Regex.escape(label) + "\\s*[:：]?\\s*([^\\s|]{1,40})"
            ).find(text) ?: continue
            val value = match.groupValues.getOrNull(1)
                .orEmpty()
                .trim()
                .trimEnd('，', ',', '；', ';')
            if (value.isNotBlank()) return value
        }
        return ""
    }

    private fun extractYear(text: String): String =
        Regex("(?:出版年|出版日期|出版时间)\\s*[:：]?\\s*(\\d{4})").find(text)?.groupValues?.getOrNull(1)
            ?: Regex("(19|20)\\d{2}").find(text)?.value
            ?: ""
}