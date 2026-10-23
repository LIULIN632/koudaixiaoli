package com.pocket.xiaoli.data.model

import com.pocket.xiaoli.data.map.CampusUserLocation
import com.pocket.xiaoli.focus.FocusConfig
import com.pocket.xiaoli.focus.FocusRuntime
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「存在 SharedPreferences 里的 JSON 类，**每个字段都必须有默认值**」这条不变量。
 *
 * ## 为什么这条断言就够
 *
 * Kotlin 只在**全部参数都有默认值**时才生成无参构造。Gson 反序列化时：
 *
 * | 有没有无参构造 | Gson 走哪条路 | 缺字段的结果 |
 * |---|---|---|
 * | 有 | 无参构造 + 反射赋值 | **默认值生效，安全** |
 * | 没有 | `UnsafeAllocator` 直接反射赋值 | **所有默认值一起失效 → null / 0** |
 *
 * 所以「存在无参构造」**精确等价于**「全字段都有默认值」——
 * 谁将来给这些类加一个裸字段，这条断言立刻变红。`QzxyKnownDeviceTest` 用的是同一条判据。
 *
 * ## ⚠️ 为什么不靠"喂一段 JSON 看读出来对不对"来测
 *
 * `CLAUDE.md` 记过：测试环境不混淆，反射行为与 release 包不一致 ——
 * 那种用例**可能测试环境绿、release 上崩**，是最危险的一类假绿。
 * 这里只守"语言层面的不变量"，它与混淆无关。
 *
 * ## ⚠️ `DormInfo` 不在这张名单里，不是漏了
 *
 * 它是**旧策略**的代表：`label: String` 是裸字段（无参构造确实不存在），
 * 补救办法是在读取边界调 `sanitized()`。**新类一律用默认值 + `sanitized()` 双保险**，
 * 别再走"只有 sanitized"那条路（那正是 v1.0.93 闪退的成因）。
 */
class PrefsJsonDefaultsTest {

    private fun assertHasNoArgConstructor(type: Class<*>) {
        assertTrue(
            "${type.simpleName} 没有无参构造 —— 说明有字段漏了默认值。" +
                "这会让 Gson 改用 UnsafeAllocator，把**所有**默认值一起作废" +
                "（含 Object 字段变 null）。给它补上默认值，别只加 sanitized()。",
            type.declaredConstructors.any { it.parameterCount == 0 },
        )
    }

    @Test
    fun `LastImportInfo 每个字段都有默认值`() {
        assertHasNoArgConstructor(LastImportInfo::class.java)
    }

    @Test
    fun `CampusUserLocation 每个字段都有默认值`() {
        assertHasNoArgConstructor(CampusUserLocation::class.java)
    }

    // ── 番茄钟（`FocusStore` 把这两个类 Gson 进 `pocket_focus.xml`）──

    @Test
    fun `FocusRuntime 每个字段都有默认值`() {
        assertHasNoArgConstructor(FocusRuntime::class.java)
    }

    @Test
    fun `FocusConfig 每个字段都有默认值`() {
        assertHasNoArgConstructor(FocusConfig::class.java)
    }
}
