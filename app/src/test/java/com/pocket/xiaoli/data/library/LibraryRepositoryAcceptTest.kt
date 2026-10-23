package com.pocket.xiaoli.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 索引「能不能替换本地缓存」的两道闸。
 *
 * 这两条守的是更新链上踩过的老坑：镜像返回空/旧数据时不许冲掉本地好缓存，
 * 否则故障会一直续到用户清数据。
 */
class LibraryRepositoryAcceptTest {

    private fun file(id: String) = LibraryFile(
        id = id,
        name = "$id.pdf",
        course = "数据结构",
        kind = "课件",
        sizeBytes = 1024L,
        sha256 = null,
        updatedAt = "2026-10-19",
        urls = listOf("https://example.com/$id.pdf"),
    )

    private fun state(generatedAt: String, vararg ids: String) = LibraryRepository.State(
        files = ids.map(::file),
        generatedAt = generatedAt,
    )

    private fun index(generatedAt: String, vararg ids: String) =
        LibraryIndex(generatedAt, ids.map(::file))

    @Test
    fun `空索引不清掉已有列表`() {
        val reason = LibraryRepository.acceptReason(index("2026-10-19"), state("2026-10-18", "a"))
        assertEquals("资料库暂时是空的，先显示上次的索引", reason)
    }

    @Test
    fun `本地也空时接受空索引`() {
        assertNull(LibraryRepository.acceptReason(index("2026-10-19"), state("2026-10-18")))
    }

    @Test
    fun `时间更旧的索引不覆盖本地`() {
        val reason = LibraryRepository.acceptReason(
            index("2026-10-17", "a", "b"),
            state("2026-10-19", "a"),
        )
        assertTrue(reason.orEmpty(), reason.orEmpty().startsWith("镜像还在返回旧索引"))
    }

    @Test
    fun `时间相同或更新时接受`() {
        assertNull(LibraryRepository.acceptReason(index("2026-10-19", "a", "b"), state("2026-10-19", "a")))
        assertNull(LibraryRepository.acceptReason(index("2026-10-20", "a", "b"), state("2026-10-19", "a")))
    }

    @Test
    fun `任一方没写时间就不拿时间做比较`() {
        // 老缓存可能是手写的、没 generatedAt：此时只按「空不空」判断。
        assertNull(LibraryRepository.acceptReason(index("2026-10-19", "a", "b"), state("", "a")))
        assertNull(LibraryRepository.acceptReason(index("", "a", "b"), state("2026-10-19", "a")))
    }
}
