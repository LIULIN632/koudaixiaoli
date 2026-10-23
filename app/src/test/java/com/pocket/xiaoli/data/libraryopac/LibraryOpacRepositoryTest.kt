package com.pocket.xiaoli.data.libraryopac

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryOpacRepositoryTest {

    @Test
    fun parseStrictBookDetailLinks() {
        val html = """
            <html><body>
              <ul class="booklist">
                <li>
                  <a href="/opac/book/12345">Java 编程思想</a>
                  <div>作者：Bruce Eckel 出版社：机械工业出版社 出版年：2007 索书号：TP312/123</div>
                </li>
                <li>
                  <a href="/opac/book/67890">算法导论</a>
                  <div>作者：Cormen 出版社：机械工业出版社 出版年：2013 索书号：TP301/66</div>
                </li>
              </ul>
            </body></html>
        """.trimIndent()

        val books = LibraryOpacRepository.parseSearchResults(
            html = html,
            baseUrl = "https://lib.yitsd.edu.cn/opac/search?q=java",
        )

        assertEquals(2, books.size)
        assertEquals("Java 编程思想", books[0].title)
        assertEquals("Bruce", books[0].author)
        assertEquals("机械工业出版社", books[0].publisher)
        assertEquals("2007", books[0].year)
        assertEquals("TP312/123", books[0].callNumber)
        assertTrue(books[0].detailUrl.contains("/opac/book/12345"))
    }

    @Test
    fun parseFallsBackToResultListContainers() {
        val html = """
            <div id="searchresult">
              <div class="result-item">
                <a href="/book/999">数据结构与算法</a>
                <span>著者：严蔚敏 出版社：清华大学出版社 出版日期：2018 索取号：TP311.12/1</span>
              </div>
            </div>
        """.trimIndent()

        val books = LibraryOpacRepository.parseSearchResults(
            html = html,
            baseUrl = "https://lib.yitsd.edu.cn/opac/search?q=data",
        )

        assertEquals(1, books.size)
        assertEquals("数据结构与算法", books[0].title)
        assertEquals("严蔚敏", books[0].author)
        assertEquals("清华大学出版社", books[0].publisher)
        assertEquals("2018", books[0].year)
        assertEquals("TP311.12/1", books[0].callNumber)
    }

    @Test
    fun parseIgnoresNavigationAndLoginLinks() {
        val html = """
            <a href="/opac/search">馆藏检索</a>
            <a href="/opac/login">登录</a>
            <div id="searchresult">
              <div class="result-item">
                <a href="/book/999">操作系统概念</a>
                <span>作者：Silberschatz 出版社：机械工业出版社 出版年：2018</span>
              </div>
            </div>
        """.trimIndent()

        val books = LibraryOpacRepository.parseSearchResults(html, "https://lib.yitsd.edu.cn/opac/search?q=os")
        assertEquals(1, books.size)
        assertEquals("操作系统概念", books[0].title)
    }
}