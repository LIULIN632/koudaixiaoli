package com.pocket.xiaoli.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Grok Ball 的表情数据（`MascotFaces.kt`，脚本生成）与它在本项目里的投影。
 *
 * 这一组断言不测"好不好看"，只测**数据完整性与几何落点**：
 * 32 个表情是照搬来的纯数据，手改一次就可能少一个 id、或者指到不存在的眼环上，
 * 而那种错误在截图里只表现为"某个表情看着怪"，很容易被当成美术选择放过去。
 *
 * 用 Robolectric 是因为 `MascotProjection.eyePath()` 里会 `new Path()` ——
 * 裸 JUnit 下 `android.graphics.Path` 全是 not-mocked 桩，连包围盒都取不出来。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MascotFaceTest {

    @Test
    fun `32 个表情的 id 与名字都唯一`() {
        val faces = MascotFaces.all
        assertEquals("表情数量应与源仓库 EMOTION_SEED 一致", 32, faces.size)
        assertEquals("id 有重复", faces.size, faces.map { it.id }.toSet().size)
        assertEquals("中文名有重复", faces.size, faces.map { it.label }.toSet().size)
        faces.forEach { face ->
            assertTrue("表情 ${face.id} 的 id 不是两位数", Regex("""\d{2}""").matches(face.id))
            assertTrue("表情 ${face.id} 没有名字", face.label.isNotBlank())
        }
    }

    @Test
    fun `每个表情都指向真实存在的眼环`() {
        val groups = MascotEyeRings.all.size
        assertTrue("眼环组数为 0，数据没生成出来", groups > 0)
        MascotFaces.all.forEach { face ->
            assertTrue(
                "表情 ${face.id} 指向第 ${face.group} 组眼环，但只有 $groups 组",
                face.group in 0 until groups,
            )
        }
    }

    @Test
    fun `每组眼环都是左右两只、各 48 点`() {
        MascotEyeRings.all.forEachIndexed { index, rings ->
            assertEquals("第 $index 组不是两只眼", 2, rings.size)
            rings.forEach { ring ->
                assertEquals(
                    "第 $index 组某只眼不是 48 点（x/y 交替，共 96 个 float）",
                    96,
                    ring.size,
                )
            }
        }
    }

    @Test
    fun `idle 取的是待机放空而不是睡眠`() {
        // 睡眠（00）在源仓库里是两条闭眼细缝，当静止脸会像"坏图"。
        assertEquals("02", MascotFaces.idle.id)
    }

    @Test
    fun `七个动作各自对应一个不同的表情`() {
        val mapped = MascotMotion.entries.map { MascotFaces.forMotion(it) }
        assertEquals(
            "动作→表情必须一一不同，否则动画之间看不出区别",
            MascotMotion.entries.size,
            mapped.map { it.id }.toSet().size,
        )
    }

    @Test
    fun `三副身体的眼环投影都落在 100 视口内`() {
        val grok = MascotShapes.all.filter { it.projection != null }
        assertEquals("Grok Ball 应该有三个身体", 3, grok.size)
        grok.forEach { shape ->
            val projection = requireNotNull(shape.projection)
            MascotFaces.all.forEach { face ->
                MascotEyeRings.all[face.group].forEach { ring ->
                    val bounds = projection.eyePath(ring).getBounds()
                    val owner = "${shape.name}/${face.label}"
                    assertTrue(
                        "$owner 的眼睛越出画布：${bounds.left}, ${bounds.top} - ${bounds.right}, ${bounds.bottom}",
                        bounds.left >= -0.5f && bounds.top >= -0.5f &&
                            bounds.right <= 100.5f && bounds.bottom <= 100.5f,
                    )
                }
            }
        }
    }

    @Test
    fun `同一副身体上不同表情真的画出了不同的形状`() {
        val projection = requireNotNull(MascotShapes.all.first { it.projection != null }.projection)
        val idle = projection.eyePath(MascotEyeRings.all[MascotFaces.idle.group][0]).getBounds()
        val error = projection.eyePath(MascotEyeRings.all[MascotFaces.byId("34").group][0]).getBounds()
        assertTrue(
            "待机与出错的左眼包围盒一样大 —— 眼环没被真正换掉",
            kotlin.math.abs(idle.width - error.width) > 0.01f,
        )
    }
}

