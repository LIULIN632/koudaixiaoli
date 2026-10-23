package com.pocket.xiaoli.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 网络日志：环形缓冲的边界，以及 URL 脱敏只打敏感参数。 */
class NetworkLogStoreTest {

    @Before
    fun setUp() = NetworkLogStore.resetForTest()

    @Test
    fun newestEntryComesFirst() {
        NetworkLogStore.record("GET", "https://a/1", 200, 10)
        NetworkLogStore.record("POST", "https://a/2", 201, 20)
        val entries = NetworkLogStore.entries.value
        assertEquals(2, entries.size)
        assertEquals("https://a/2", entries[0].url)
        assertEquals(201, entries[0].status)
    }

    @Test
    fun ringBufferDropsOldest() {
        repeat(NetworkLogStore.MAX_ENTRIES + 5) { i ->
            NetworkLogStore.record("GET", "https://a/$i", 200, 1)
        }
        val entries = NetworkLogStore.entries.value
        assertEquals(NetworkLogStore.MAX_ENTRIES, entries.size)
        assertTrue("应保留最新的", entries[0].url.endsWith("/${NetworkLogStore.MAX_ENTRIES + 4}"))
    }

    @Test
    fun failureKeepsNullStatusAndError() {
        NetworkLogStore.record("GET", "https://a", null, 5, error = "IOException: timeout")
        val e = NetworkLogStore.entries.value[0]
        assertEquals(null, e.status)
        assertTrue(e.error!!.contains("timeout"))
    }

    @Test
    fun clearEmptiesLog() {
        NetworkLogStore.record("GET", "https://a", 200, 1)
        NetworkLogStore.clear()
        assertTrue(NetworkLogStore.entries.value.isEmpty())
    }

    @Test
    fun redactLeavesUrlWithoutQueryUntouched() {
        val url = "https://jwxt.yitsd.edu.cn/jwglxt/xtgl/login_slogin.html"
        assertEquals(url, NetworkLogStore.redactUrl(url))
    }

    @Test
    fun redactMasksOnlySensitiveValues() {
        val redacted = NetworkLogStore.redactUrl(
            "https://cas.yitsd.edu.cn/cas/login?service=https%3A%2F%2Fa&ticket=ST-123&username=2024123456"
        )
        assertTrue("ticket 应被打码", redacted.contains("ticket=***"))
        assertTrue("service 应保留（排障要看）", redacted.contains("service=https%3A%2F%2Fa"))
        assertTrue("username 应保留", redacted.contains("username=2024123456"))
    }

    @Test
    fun redactIsCaseInsensitiveOnParamName() {
        assertTrue(NetworkLogStore.redactUrl("https://a?TICKET=x").contains("TICKET=***"))
    }

    @Test
    fun redactHandlesValuelessAndMultipleParams() {
        val redacted = NetworkLogStore.redactUrl("https://a?flag&token=abc&x=1")
        assertTrue(redacted.contains("flag"))
        assertTrue(redacted.contains("token=***"))
        assertTrue(redacted.contains("x=1"))
    }
    @Test
    fun toCurlRedactsSensitiveParams() {
        NetworkLogStore.record("GET", "https://a?token=abc&x=1", 200, 1)
        assertEquals(
            "curl 'https://a?token=***&x=1'",
            NetworkLogStore.toCurl(NetworkLogStore.entries.value[0]),
        )
    }

    @Test
    fun toCurlKeepsMethodForNonGet() {
        NetworkLogStore.record("post", "https://a/import", 200, 1)
        assertEquals(
            "curl -X POST 'https://a/import'",
            NetworkLogStore.toCurl(NetworkLogStore.entries.value[0]),
        )
    }

    @Test
    fun toCurlEscapesSingleQuotes() {
        NetworkLogStore.record("GET", "https://a/it's", 200, 1)
        assertEquals(
            "curl 'https://a/it'\\''s'",
            NetworkLogStore.toCurl(NetworkLogStore.entries.value[0]),
        )
    }
}



