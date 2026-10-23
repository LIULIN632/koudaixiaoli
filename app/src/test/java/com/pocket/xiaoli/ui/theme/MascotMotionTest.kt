package com.pocket.xiaoli.ui.theme

import com.pocket.xiaoli.data.agent.AgentPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 动作目录与"阶段 → 动作"映射的不变量。
 *
 * 这一层同样是"写错了不报错"的地方：
 * - 新增一个 [MascotMotion] 却忘了加进 [MASCOT_MOTION_CATALOG] → 设置页少一个按钮，
 *   用户永远试演不到它，而编译、截图全绿；
 * - 把循环动作标成一次性（或反过来）→ 真机上要么"只动一下"，要么"错误态一直跳"；
 * - `mascotMotionFor` 漏一个 [AgentPhase] → `when` 会编译失败，但**映射到错的动作**不会。
 */
class MascotMotionTest {

    @Test
    fun `每个动作都能在设置页被试演，顺序固定`() {
        assertEquals(
            "动作目录必须一项不漏（漏了就没有按钮，用户看不到这个动效）",
            MascotMotion.entries.toSet(),
            MASCOT_MOTION_CATALOG.toSet(),
        )
        assertEquals(
            "目录里不能有重复项：重复会让两个 chip 同时是选中态",
            MASCOT_MOTION_CATALOG.size,
            MASCOT_MOTION_CATALOG.toSet().size,
        )
        assertEquals(
            "顺序是「静息 → 一轮对话的时间线（思考/查资料/干活/等你/收工）→ 出错」，快照依赖它",
            MascotMotion.IDLE,
            MASCOT_MOTION_CATALOG.first(),
        )
    }

    @Test
    fun `每个动作都有中文名，且不重名`() {
        val labels = MascotMotion.entries.map { it.previewLabel }
        assertTrue("空白标签在 chip 上就是一个空按钮", labels.none { it.isBlank() })
        assertEquals("两个动作同名的 chip 分不出点的是哪个", labels.size, labels.toSet().size)
    }

    @Test
    fun `只有收工与出岔子是一次性动作`() {
        val oneShot = MascotMotion.entries.filter { it.isOneShot }.toSet()
        assertEquals(
            "一次性动作的判据是「演一遍就停」：错误态会一直停在 ERROR（见 AgentPhase 的 KDoc），" +
                "循环动作在那儿会永远跳；反过来把循环动作标成一次性，就会变成「跳一下就不动了」",
            setOf(MascotMotion.PROUD, MascotMotion.HOP),
            oneShot,
        )
    }

    @Test
    fun `阶段映射：忙的两种状态各有动作，静息与出错不对调`() {
        assertEquals(MascotMotion.PONDER, mascotMotionFor(AgentPhase.THINKING))
        assertEquals(MascotMotion.WORKING, mascotMotionFor(AgentPhase.TOOL))
        assertEquals(MascotMotion.HOP, mascotMotionFor(AgentPhase.ERROR))
        // IDLE 必须是"什么都不动"，不能是 PROUD：收工动作由 AiAvatar 在"忙 → 静息"那一下
        // 单独触发一次，直接把 IDLE 映成 PROUD 会让每个静息头像反复演一遍。
        assertEquals(MascotMotion.IDLE, mascotMotionFor(AgentPhase.IDLE))
    }

    @Test
    fun `阶段映射不会返回没有对应信号的试演动作`() {
        // SEARCH / WAITING 目前没有 AgentPhase 能表达（阶段只有四个取值），
        // 它们只出现在设置页的试演行。若哪天被接进映射，这里会红 —— 那时应同时
        // 更新 MascotMotion 的 KDoc 与 memory/02-feature-map.md。
        val mapped = AgentPhase.entries.map { mascotMotionFor(it) }.toSet()
        assertFalse(mapped.contains(MascotMotion.SEARCH))
        assertFalse(mapped.contains(MascotMotion.WAITING))
    }
}
