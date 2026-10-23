package com.pocket.xiaoli.data.secondclass

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SecondClassRepository] 的契约，重点是**会话失效的收敛**。
 *
 * 用假的 [SecondClassDataSource] 驱动：Repository 的职责是"转发 + 收敛"，
 * 与真实接口的字段名无关，所以这些用例在拿到 `scorelist` 真响应之前就能写、也能证明。
 * （假实现只活在 `src/test`，不是产品里的模拟数据 —— 约束 2 管的是产物。）
 */
class SecondClassRepositoryTest {

    private class FakeDataSource(
        var transcriptResult: SecondClassResult<SecondClassTranscript> =
            SecondClassResult.Ok(SecondClassTranscript()),
        var activitiesResult: SecondClassResult<SecondClassPage<SecondClassActivity>> =
            SecondClassResult.Ok(SecondClassPage()),
        var detailResult: SecondClassResult<SecondClassActivityDetail> =
            SecondClassResult.Ok(SecondClassActivityDetail()),
    ) : SecondClassDataSource {

        /** 记录每次分页请求的 (page, pageSize)，用来验证默认页大小真的传下去了。 */
        val activityCalls = mutableListOf<Pair<Int, Int>>()

        override suspend fun fetchActivities(
            page: Int,
            pageSize: Int,
        ): SecondClassResult<SecondClassPage<SecondClassActivity>> {
            activityCalls += page to pageSize
            return activitiesResult
        }

        override suspend fun fetchActivityDetail(activityId: String): SecondClassResult<SecondClassActivityDetail> =
            detailResult

        override suspend fun fetchTranscript(): SecondClassResult<SecondClassTranscript> = transcriptResult
    }

    /** 记录"清会话"被调用了几次。 */
    private class SessionSpy {
        var count = 0
        fun onInvalidated() {
            count++
        }
    }

    private fun repo(ds: SecondClassDataSource, spy: SessionSpy) =
        SecondClassRepository(dataSource = ds, onSessionInvalidated = spy::onInvalidated)

    // ───────────────────────── 基本转发 ─────────────────────────

    @Test
    fun `成功结果原样透传`() = runBlocking {
        val ds = FakeDataSource()
        val spy = SessionSpy()

        val result = repo(ds, spy).transcript()

        assertTrue(result is SecondClassResult.Ok)
        assertEquals("成功不该触发清会话", 0, spy.count)
    }

    @Test
    fun `分页请求带上默认页大小`() = runBlocking {
        val ds = FakeDataSource()
        val spy = SessionSpy()

        repo(ds, spy).activities(page = 2)

        assertEquals(listOf(2 to SecondClassDataSource.DEFAULT_PAGE_SIZE), ds.activityCalls)
        assertEquals("实测的服务端默认页大小是 20", 20, SecondClassDataSource.DEFAULT_PAGE_SIZE)
    }

    @Test
    fun `显式页大小会覆盖默认值`() = runBlocking {
        val ds = FakeDataSource()

        repo(ds, SessionSpy()).activities(page = 1, pageSize = 5)

        assertEquals(listOf(1 to 5), ds.activityCalls)
    }

    // ───────────────────────── 会话失效的收敛 ─────────────────────────

    /**
     * 守门人：未登录**必须原样交给 UI**，不能被这里换成"失败"。
     *
     * 换了会怎样：用户看到"加载失败"，而没有任何一处告诉他去重新登录 ——
     * 架构文档 §4 点名这是本模块最容易做错的一处体验。
     */
    @Test
    fun `未登录原样返回给 UI 而不是变成失败`() = runBlocking {
        val ds = FakeDataSource(transcriptResult = SecondClassResult.Unauthorized)
        val spy = SessionSpy()

        val result = repo(ds, spy).transcript()

        assertEquals(SecondClassResult.Unauthorized, result)
    }

    /** 会话失效时要清本机会话：否则 `hasCookiesFor` 会一直说"我有 cookie"，UI 反复把用户弹回失败。 */
    @Test
    fun `未登录会清一次会话`() = runBlocking {
        val ds = FakeDataSource(transcriptResult = SecondClassResult.Unauthorized)
        val spy = SessionSpy()
        val repository = repo(ds, spy)

        repository.transcript()

        assertEquals(1, spy.count)
    }

    /** 闩锁：用户连点"重试"不该连清几次盘（`clear()` 会连带写存储）。 */
    @Test
    fun `连续未登录只清一次会话`() = runBlocking {
        val ds = FakeDataSource(transcriptResult = SecondClassResult.Unauthorized)
        val spy = SessionSpy()
        val repository = repo(ds, spy)

        repository.transcript()
        repository.transcript()
        repository.transcript()

        assertEquals(1, spy.count)
    }

    /** 有过一次成功说明会话又好了，闩锁要重新武装 —— 否则"又过期了"那一次不会清。 */
    @Test
    fun `成功之后再次未登录会再清一次`() = runBlocking {
        val ds = FakeDataSource(transcriptResult = SecondClassResult.Unauthorized)
        val spy = SessionSpy()
        val repository = repo(ds, spy)

        repository.transcript()
        ds.transcriptResult = SecondClassResult.Ok(SecondClassTranscript())
        repository.transcript()
        ds.transcriptResult = SecondClassResult.Unauthorized
        repository.transcript()

        assertEquals(2, spy.count)
    }

    /**
     * 限流与其它失败**不动闩锁**：它们不代表会话坏了。
     *
     * 若在失败分支把闩锁复位，一次网络抖动的后面那次 401 就会再清一次盘；
     * 反过来若在失败分支把闩锁置上，则真正的失效可能被跳过。
     */
    @Test
    fun `失败与限流不会重新武装会话闩锁`() = runBlocking {
        val ds = FakeDataSource(transcriptResult = SecondClassResult.Unauthorized)
        val spy = SessionSpy()
        val repository = repo(ds, spy)

        repository.transcript() // 清一次
        ds.transcriptResult = SecondClassResult.Failed(500, "系统繁忙")
        repository.transcript()
        ds.transcriptResult = SecondClassResult.RateLimited(3_000L)
        repository.transcript()
        ds.transcriptResult = SecondClassResult.Unauthorized
        repository.transcript()

        assertEquals("会话没恢复过，就不该再清", 1, spy.count)
    }

    /** 清理是尽力而为：回调自己抛异常，不该把一次"未登录"升级成崩溃。 */
    @Test
    fun `清会话抛异常不会把未登录升级成崩溃`() = runBlocking {
        val ds = FakeDataSource(transcriptResult = SecondClassResult.Unauthorized)
        val repository = SecondClassRepository(ds) { throw IllegalStateException("清盘失败") }

        val result = repository.transcript()

        assertEquals(SecondClassResult.Unauthorized, result)
    }
}
