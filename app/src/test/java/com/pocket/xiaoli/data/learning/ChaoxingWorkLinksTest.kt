package com.pocket.xiaoli.data.learning

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 学习通作业链接缓存的行为约定。
 *
 * 这里守的是三件事，都是线上会踩到的：
 * 1. 过期（6 小时）后不许再交给调用方 —— 签名参数里带时间戳，放太久必然失效；
 * 2. [ChaoxingWorkLinks.remove] 必须真的删掉，否则"失败一次"之后每次同步都要先白等一次；
 * 3. 登出必须清空 —— 缓存里是带个人签名的链接，换账号后不能用上一个人的。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChaoxingWorkLinksTest {

    private val key = ChaoxingWorkLinks.key("course1", "clazz1", "cpi1")
    private val now = 1_700_000_000_000L

    @Before
    fun setUp() {
        ChaoxingWorkLinks.init(ApplicationProvider.getApplicationContext<Context>())
        ChaoxingWorkLinks.clear()
    }

    @Test
    fun 存进去能取出来() {
        ChaoxingWorkLinks.put(key, "https://mooc1.chaoxing.com/mooc2/work/list?enc=abc", now)
        assertEquals("https://mooc1.chaoxing.com/mooc2/work/list?enc=abc", ChaoxingWorkLinks.get(key, now))
    }

    @Test
    fun 超过六小时就不再用() {
        ChaoxingWorkLinks.put(key, "https://x/work?enc=abc", now)
        assertNull(ChaoxingWorkLinks.get(key, now + 6 * 60 * 60 * 1000 + 1))
    }

    @Test
    fun remove之后取不到() {
        ChaoxingWorkLinks.put(key, "https://x/work?enc=abc", now)
        ChaoxingWorkLinks.remove(key)
        assertNull(ChaoxingWorkLinks.get(key, now))
    }

    @Test
    fun clear清空所有课程() {
        ChaoxingWorkLinks.put(key, "https://x/work?enc=abc", now)
        ChaoxingWorkLinks.put(ChaoxingWorkLinks.key("c2", "k2", "p2"), "https://x/work?enc=def", now)
        ChaoxingWorkLinks.clear()
        assertNull(ChaoxingWorkLinks.get(key, now))
        assertNull(ChaoxingWorkLinks.get(ChaoxingWorkLinks.key("c2", "k2", "p2"), now))
    }

    @Test
    fun 空链接不写入() {
        ChaoxingWorkLinks.put(key, "   ", now)
        assertNull(ChaoxingWorkLinks.get(key, now))
    }

    @Test
    fun 不同班级的键不同() {
        assertEquals(false, ChaoxingWorkLinks.key("c", "1", "p") == ChaoxingWorkLinks.key("c", "2", "p"))
    }
}
