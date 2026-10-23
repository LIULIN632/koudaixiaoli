package com.pocket.xiaoli.data.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 老版本存下来的宿舍 JSON 里没有 [DormInfo.dormCode] 这一栏。
 *
 * 这些用例守的是一个**真的崩过**的边界（v1.0.93 服务页）：Gson 反序列化不走 Kotlin 默认值，
 * 它要么用无参构造（填默认值），要么退回 `UnsafeAllocator` 直接反射赋值 ——
 * 后者会把 JSON 里没有的字段留成 `null`，而 Kotlin 的非空类型在反射路径上不兜底。
 * release 包（R8 之后）踩的是后一条路，debug / 单测踩的是前一条，
 * 所以「单测全绿」并不能证明老用户的存档读得进来。
 */
class DormInfoSerializationTest {

    @Test
    fun legacyJsonWithoutDormCodeKeepsEmptyDefault() {
        val legacy = """{"building":"10号宿舍楼","room":"602","serviceCampusId":"ytu_south"}"""

        val restored = Gson().fromJson(legacy, DormInfo::class.java)

        assertEquals("10号宿舍楼", restored.building)
        assertEquals("602", restored.room)
        assertEquals("ytu_south", restored.serviceCampusId)
        assertEquals("", restored.dormCode)
    }

    @Test
    fun roundTripKeepsDormCodeAndBoundState() {
        val gson = Gson()
        val original = DormInfo(building = "10号宿舍楼", room = "602", dormCode = "BS10S0602")

        val restored = gson.fromJson(gson.toJson(original), DormInfo::class.java)

        assertEquals(original, restored)
        assertEquals("10号宿舍楼 602", restored.label)
    }

    @Test
    fun emptyCodeIsNotBound() {
        assertFalse(DormInfo().isBound)
        assertFalse(DormInfo(building = "", room = "", dormCode = "BS10S0602").isBound)
    }

    @Test
    fun explicitNullOrMissingFieldsNeverThrowOnRead() {
        // 复现崩溃现场：老存档没有 dormCode，release 包里读出来是 null。
        // 修复前 `restored.isBound` / `dormCode.isNotBlank()` 都会 NPE
        // （堆栈里那句 `StringsKt.isBlank` 抛的 getClass on a null object reference）。
        val fromLegacyJson = Gson().fromJson(
            """{"building":"10号宿舍楼","room":"602","serviceCampusId":null}""",
            DormInfo::class.java,
        )
        assertEquals("", fromLegacyJson.dormCode)
        assertEquals("10号宿舍楼 602", fromLegacyJson.label)

        // 显式 null（手改过 / 别的工具写过存档）也要能活
        val allNull = Gson().fromJson(
            """{"building":null,"room":null,"serviceCampusId":null,"dormCode":null}""",
            DormInfo::class.java,
        )
        assertFalse(allNull.isBound)
        assertEquals("", allNull.label)

        val fixed = allNull.sanitized()
        assertEquals("", fixed.building)
        assertEquals("", fixed.room)
        assertEquals("", fixed.dormCode)
        assertFalse(fixed.dormCode.isNotBlank())
    }
}
