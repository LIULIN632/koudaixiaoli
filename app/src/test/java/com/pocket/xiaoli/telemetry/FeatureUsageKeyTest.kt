package com.pocket.xiaoli.telemetry

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「功能使用」的维度闸门。
 *
 * 这里测的不是"统计好不好用"，而是**这一列能不能被拿来传别的东西**。
 * 只要这些用例绿着，客服端就没办法把搜索词、课程名、学号写进 `feature` ——
 * 那条路一旦通了，`docs/adr/0001-匿名运行统计.md` 里"不采集内容"的承诺就是假的。
 */
class FeatureUsageKeyTest {

    @Test
    fun `accepts real service titles`() {
        listOf("成绩查询", "今天吃什么", "空教室查询", "麦当劳蛋白榜", "四级六级", "留声树洞")
            .forEach { assertTrue("应当接受：$it", Telemetry.isTrackableFeature(it)) }
    }

    @Test
    fun `rejects free text that looks like user content`() {
        listOf(
            "搜索: 高数",          // 有冒号
            "高等数学(上)",        // 有括号
            "2024级计算机/3班",    // 有斜杠
            "成绩查询\n第二行",     // 有换行
            "a@b.com",            // 邮箱
            "1234567890123456789012345", // 超过 24 字
            "",                   // 空
        ).forEach { assertFalse("应当拒绝：$it", Telemetry.isTrackableFeature(it)) }
    }

    @Test
    fun `rejects strings that would break the json envelope`() {
        // 引号和反斜杠是最直接的注入形状：它们能把 k 变成一个额外的 JSON 字段。
        listOf("成绩\"查询", "成绩\\查询", "成绩查询\"").forEach {
            assertFalse("应当拒绝：$it", Telemetry.isTrackableFeature(it))
        }
    }
}
