package com.pocket.xiaoli.data.weather

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 天气仓储的节流 / 缓存 / 失败回落单测。
 *
 * 这四条行为**任意一条错了都不会崩**，只是页面表现悄悄变差：
 * TTL 失效 → 每次进页面都转圈；缓存不写 → 断网白屏；
 * 失败不回落 → 明明有 40 分钟前的数据却给错误卡；force 无效 → 点刷新没反应。
 * 所以每条都单独守一遍。
 *
 * 用 MockWebServer 而不是 mock 客户端：真实走一遍 OkHttp 的 URL 拼接、
 * 状态码与 `body.string()` —— 这几步恰恰是最容易和真实服务对不上的地方。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeatherRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var context: Context
    private var now = 1_700_000_000_000L

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        context = ApplicationProvider.getApplicationContext()
        WeatherStore(context).clear()
    }

    @After
    fun tearDown() {
        WeatherStore(context).clear()
        server.shutdown()
    }

    private fun repository() = WeatherRepository(
        store = WeatherStore(context),
        endpoint = server.url("/v1/forecast").toString(),
        clock = { now },
        client = OkHttpClient(),
    )

    @Test
    fun `首次加载联网成功并写入缓存`() = runBlocking {
        server.enqueue(MockResponse().setBody(SAMPLE))

        val result = repository().load()

        assertTrue(result is WeatherRepository.Result.Success)
        val success = result as WeatherRepository.Result.Success
        assertEquals(false, success.fromCache)
        assertEquals(12.3, success.snapshot.current.temperature, 0.001)

        val request = server.takeRequest()
        assertNotNull(request.requestUrl)
        val url = request.requestUrl!!
        assertEquals("37.47649", url.queryParameter("latitude"))
        assertEquals("121.44081", url.queryParameter("longitude"))
        assertEquals("Asia/Shanghai", url.queryParameter("timezone"))
        assertNotNull(WeatherStore(context).cachedJson())
    }

    @Test
    fun `TTL 内再次加载不联网`() = runBlocking {
        server.enqueue(MockResponse().setBody(SAMPLE))
        val repo = repository()
        repo.load()

        now += WeatherRepository.TTL_MS - 1
        val second = repo.load()

        assertTrue(second is WeatherRepository.Result.Success)
        assertTrue((second as WeatherRepository.Result.Success).fromCache)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `TTL 过期后重新联网`() = runBlocking {
        server.enqueue(MockResponse().setBody(SAMPLE))
        val repo = repository()
        repo.load()

        now += WeatherRepository.TTL_MS + 1
        server.enqueue(MockResponse().setBody(SAMPLE))
        val second = repo.load()

        assertTrue(second is WeatherRepository.Result.Success)
        assertEquals(false, (second as WeatherRepository.Result.Success).fromCache)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `用户强制刷新会跳过 TTL`() = runBlocking {
        server.enqueue(MockResponse().setBody(SAMPLE))
        val repo = repository()
        repo.load()

        now += 1_000
        server.enqueue(MockResponse().setBody(SAMPLE))
        repo.load(force = true)

        assertEquals(2, server.requestCount)
    }

    @Test
    fun `网络失败时带上缓存而不是白屏`() = runBlocking {
        server.enqueue(MockResponse().setBody(SAMPLE))
        val repo = repository()
        repo.load()

        now += WeatherRepository.TTL_MS + 1
        server.enqueue(MockResponse().setResponseCode(500))
        val result = repo.load()

        assertTrue(result is WeatherRepository.Result.Failure)
        val failure = result as WeatherRepository.Result.Failure
        assertEquals("天气服务暂时不可用（HTTP 500）", failure.message)
        assertNotNull("有缓存时必须把它带出去，页面要照常渲染", failure.cached)
        assertEquals(12.3, failure.cached!!.current.temperature, 0.001)
    }

    @Test
    fun `没有缓存时失败就是失败`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))

        val result = repository().load()

        assertTrue(result is WeatherRepository.Result.Failure)
        assertNull((result as WeatherRepository.Result.Failure).cached)
        assertTrue(result.message.contains("HTTP 503"))
    }

    @Test
    fun `损坏的缓存会被清掉并重新联网`() = runBlocking {
        WeatherStore(context).save("{ 这不是 JSON", now)
        server.enqueue(MockResponse().setBody(SAMPLE))

        val result = repository().load()

        assertTrue(result is WeatherRepository.Result.Success)
        assertEquals(1, server.requestCount)
        assertEquals(SAMPLE, WeatherStore(context).cachedJson())
    }

    @Test
    fun `解析失败时不覆盖好缓存`() = runBlocking {
        server.enqueue(MockResponse().setBody(SAMPLE))
        val repo = repository()
        repo.load()
        val goodCache = WeatherStore(context).cachedJson()

        now += WeatherRepository.TTL_MS + 1
        server.enqueue(MockResponse().setBody("{}"))
        val result = repo.load()

        assertTrue(result is WeatherRepository.Result.Failure)
        assertNotNull((result as WeatherRepository.Result.Failure).cached)
        assertEquals(goodCache, WeatherStore(context).cachedJson())
    }

    // ── 共享快照（首页胶囊 / 天气工具 / 主动提醒都读它）──

    @Test
    fun `联网成功会把快照发布给观察者`() = runBlocking {
        server.enqueue(MockResponse().setBody(SAMPLE))
        val repo = repository()
        assertNull("进程刚起时没有内存快照", repo.snapshot.value)

        val result = repo.load() as WeatherRepository.Result.Success

        assertEquals(result.snapshot, repo.snapshot.value)
    }

    @Test
    fun `冷启动命中磁盘缓存也发布快照`() = runBlocking {
        // 仓储是单例，但进程重启后 snapshot 是空的；首页要靠 TTL 命中把它填上，
        // 否则用户冷启动后的头 30 分钟里时钟旁什么天气都没有
        WeatherStore(context).save(SAMPLE, now)
        val repo = repository()

        val result = repo.load() as WeatherRepository.Result.Success

        assertTrue(result.fromCache)
        assertEquals(result.snapshot, repo.snapshot.value)
    }

    @Test
    fun `失败但带缓存时也发布缓存快照`() = runBlocking {
        server.enqueue(MockResponse().setBody(SAMPLE))
        val repo = repository()
        repo.load()
        val good = repo.snapshot.value

        now += WeatherRepository.TTL_MS + 1
        server.enqueue(MockResponse().setResponseCode(500))
        val result = repo.load()

        assertTrue(result is WeatherRepository.Result.Failure)
        assertEquals("有缓存时页面照常渲染，共享快照也得是这一份", good, repo.snapshot.value)
    }

    @Test
    fun `纯失败不清空已有快照`() = runBlocking {
        server.enqueue(MockResponse().setBody(SAMPLE))
        val repo = repository()
        repo.load()
        val good = repo.snapshot.value

        // 让磁盘缓存也不可用，这次失败手里什么都没有
        WeatherStore(context).clear()
        now += WeatherRepository.TTL_MS + 1
        server.enqueue(MockResponse().setResponseCode(503))
        val result = repo.load()

        assertTrue(result is WeatherRepository.Result.Failure)
        assertNull((result as WeatherRepository.Result.Failure).cached)
        assertEquals(
            "上一秒还显示对的天气，不能因为一次请求失败就从首页凭空消失",
            good,
            repo.snapshot.value,
        )
    }

    companion object {
        /** Open-Meteo forecast 响应裁剪（字段与真实响应一致，数值已固定）。 */
        private val SAMPLE = """
            {
              "current": {
                "time": "2026-10-22T22:45",
                "temperature_2m": 12.3,
                "relative_humidity_2m": 67,
                "apparent_temperature": 9.9,
                "is_day": 0,
                "precipitation": 0.0,
                "weather_code": 1,
                "cloud_cover": 24,
                "wind_speed_10m": 9.4,
                "wind_direction_10m": 167
              },
              "hourly": {
                "time": ["2026-10-22T23:00", "2026-10-23T00:00"],
                "temperature_2m": [11.8, 11.5],
                "weather_code": [1, 2],
                "precipitation_probability": [10, 20],
                "is_day": [0, 0]
              },
              "daily": {
                "time": ["2026-10-22", "2026-10-23"],
                "weather_code": [3, 1],
                "temperature_2m_max": [16.9, 17.7],
                "temperature_2m_min": [7.8, 10.7],
                "sunrise": ["2026-10-22T06:08", "2026-10-23T06:09"],
                "sunset": ["2026-10-22T17:08", "2026-10-23T17:07"],
                "precipitation_probability_max": [0, 20],
                "uv_index_max": [4.45, 3.35]
              }
            }
        """.trimIndent()
    }
}
