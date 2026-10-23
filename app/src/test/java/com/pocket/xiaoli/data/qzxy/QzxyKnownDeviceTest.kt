package com.pocket.xiaoli.data.qzxy

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 设备台账。
 *
 * ## 为什么专门测「Gson 造 null」这条路径
 *
 * 台账是以 JSON 存在 SharedPreferences 里的，读的时候走 `gson.fromJson`。
 * **Gson 不走 Kotlin 的默认值**：JSON 里没有的字段会被直接反射赋成 `null`，
 * 而声明是非空的 `val snCode: String` 在反射路径上不兜底 → 取用时 NPE。
 *
 * 本项目 `DormInfo.dormCode` 就是这样在 v1.0.93 崩过（见 CLAUDE.md）。
 * ⚠️ 而且**单测抓不到"不混淆就没事"那部分** —— 所以这里必须用
 * **真的 Gson 反序列化一个缺字段的 JSON** 来复现，而不是 `QzxyKnownDevice()` 构造。
 */
class QzxyKnownDeviceTest {

    private fun device(
        mac: String = "C47F0EDA85D8",
        floor: String? = null,
        room: String? = null,
        building: String? = null,
        name: String = "",
    ) = QzxyKnownDevice(
        macAddress = mac, snCode = "QZXY001", deviceName = name,
        building = building, floor = floor, room = room,
    )

    @Test
    fun `位置标签优先楼层加房间`() {
        assertEquals("3层 301", device(floor = "3层", room = "301").locationLabel)
        assertEquals("3层", device(floor = "3层").locationLabel)
        assertEquals("301", device(room = "301").locationLabel)
    }

    @Test
    fun `位置标签解析不出时回退到设备名而不是空串`() {
        // 界面上出现一行空白比显示原始名字更糟
        assertTrue(device(name = "热水器-某楼-301").locationLabel.isNotBlank())
    }

    @Test
    fun `位置标签连设备名都没有时回退到 snCode`() {
        assertTrue(device(name = "").locationLabel.isNotBlank())
    }

    @Test
    fun `楼层按数字排序 10层不会排到 2层前面`() {
        // 这是必须用数字比大小的原因：按字符串比 "10层" < "2层"
        val two = device(floor = "2层").sortKey
        val ten = device(floor = "10层").sortKey
        assertTrue("10 层的排序键应当大于 2 层", ten.second > two.second)
    }

    @Test
    fun `没有楼层的排在最后`() {
        assertTrue(device(floor = "9层").sortKey.second < device(floor = null).sortKey.second)
    }

    // ─────────────────────────────────────────────
    //  Gson 路径：缺字段的老存档
    // ─────────────────────────────────────────────

    @Test
    fun `缺字段的旧 JSON 读出来不留 null`() {
        // 老版本只存了 mac + snCode，没有 floor/room/building/deviceName
        val json = """[{"macAddress":"C47F0EDA85D8","snCode":"QZXY001"}]"""

        // 前提：JSON 里确实没有这个字段
        val asMaps: List<Map<String, Any?>> = Gson().fromJson(
            json,
            object : TypeToken<List<Map<String, Any?>>>() {}.type,
        )
        assertNull("前提：JSON 里没有 deviceName", asMaps[0]["deviceName"])

        val type = object : TypeToken<List<QzxyKnownDevice>>() {}.type
        val raw: List<QzxyKnownDevice> = Gson().fromJson(json, type)

        // ⚠️ 实测结论（2026-09-30）：这里拿到的是**默认值 ""**，不是 null。
        //
        // 原因是 [QzxyKnownDevice] 的**每个字段都有默认值**，Kotlin 因此生成了无参构造，
        // Gson 走无参构造 + 反射赋值，于是默认值生效 —— 不会出现本项目 `DormInfo` 那种
        // “反射绕过默认值塞 null” 的崩溃。
        //
        // 换句话说：**「字段全给默认值」本身就是这道防线**。
        // 谁要是给这个类加一个「非空且无默认值」的字段，这条断言就会失败 —— 那正是提醒。
        assertEquals("默认值生效，不是 null", "", raw[0].deviceName)
        assertNull("可空字段本来就允许为 null", raw[0].floor)

        val safe = QzxyDeviceStore.sanitizedList(raw)
        assertEquals(1, safe.size)
        assertEquals("", safe[0].deviceName)
        // 归零之后界面能安全调用（不会因为 null 崩）
        assertTrue(safe[0].locationLabel.isNotBlank())
    }

    @Test
    fun `每个字段都有默认值 —— 这是防 null 崩溃的真正机制`() {
        // ── 为什么这条测试是这个写法 ──
        //
        // 本项目 `DormInfo` 崩过一次（v1.0.93）：老存档里没有 `dormCode`，
        // 读出来是 null，`isNotBlank()` 直接抛 NPE。
        //
        // 查清了机制：`DormInfo` 的声明是
        //     val dormCode: String = "",   // 有默认值
        //     val label: String            // ← 没有默认值
        // **只要有一个字段没有默认值，Kotlin 就不生成无参构造**，
        // Gson 于是退回 `UnsafeAllocator` **反射直接赋值** ——
        // 那条路径上**所有**字段的默认值都不生效，JSON 里缺的就是 null。
        //
        // 反过来：**全部字段都有默认值 ⟺ 存在无参构造 ⟺ Gson 走默认值路径**。
        // 所以「无参构造存在」就是我们要守的那个不变量，它精确等价于「不会出现 null 字段」。
        val noArg = QzxyKnownDevice::class.java.declaredConstructors
            .firstOrNull { it.parameterCount == 0 }
        assertNotNull(
            "QzxyKnownDevice 必须保留无参构造 —— 给某个字段加默认值时漏掉一个，" +
                "就会丢掉无参构造，Gson 改用反射赋值，缺字段变 null → 用户手机上崩。",
            noArg,
        )
    }

    @Test
    fun `全默认构造出来的对象可以安全交给界面`() {
        // 无参构造可用的直接后果：所有字段都是类型安全的默认值，能直接调派生属性
        val blank = QzxyKnownDevice()
        assertEquals("", blank.deviceName)
        assertTrue("哪怕全空也不能崩，且要有可显示文本", blank.locationLabel.isNotBlank())
    }

    @Test
    fun `macAddress 为 null 的项被过滤掉而不是留下来崩`() {
        val json = """[{"snCode":"QZXY001"},{"macAddress":"AABBCCDDEEFF","snCode":"QZXY002"}]"""
        val type = object : TypeToken<List<QzxyKnownDevice>>() {}.type
        val raw: List<QzxyKnownDevice> = Gson().fromJson(json, type)
        val safe = QzxyDeviceStore.sanitizedList(raw)

        // 第一项没有 MAC，无法去重也无法再查它 —— 必须丢
        assertEquals(1, safe.size)
        assertEquals("AABBCCDDEEFF", safe[0].macAddress)
    }

    @Test
    fun `snCode 为空的项也被过滤`() {
        val json = """[{"macAddress":"AABBCCDDEEFF"}]"""
        val type = object : TypeToken<List<QzxyKnownDevice>>() {}.type
        val raw: List<QzxyKnownDevice> = Gson().fromJson(json, type)
        // 没有 snCode 就开不了阀，留着只会在界面上生成一个点了没用的条目
        assertTrue(QzxyDeviceStore.sanitizedList(raw).isEmpty())
    }

    @Test
    fun `sanitizedList 对 null 输入返回空列表`() {
        assertTrue(QzxyDeviceStore.sanitizedList(null).isEmpty())
    }
}
