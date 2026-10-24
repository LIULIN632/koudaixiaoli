package com.pocket.xiaoli.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 「回到底部」必须真的到底。
 *
 * 2026-10-24 的实机反馈：上滑后点「回到底部」，页面**跳回了顶部**。
 * 根因不是按钮接错，而是滚动目标用了负偏移
 * （`animateScrollToItem(rounds.size, -100_000)`）—— 负偏移的语义是"往回滚"，
 * 给大了就一路顶到列表开头（实测 `scrollToItem(15, -100_000)` 落在 index 0）。
 *
 * 这里按 `AssistantScreen` 的列表结构复刻三种情形，钉住 [scrollToLatest] 的行为：
 * 追问 chips 有无、瞬间贴底、动画贴底、以及内容不足一屏时不出错。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class AssistantScrollToLatestTest {

    @get:Rule
    val rule = createComposeRule()

    private lateinit var state: LazyListState
    private lateinit var scope: CoroutineScope

    /** 与 AssistantScreen 一致的项顺序：rounds → 追问 chips（可选）→ 页脚。 */
    private fun setUpList(roundCount: Int, withFollowUps: Boolean) {
        rule.setContent {
            state = rememberLazyListState()
            scope = rememberCoroutineScope()
            LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
                items(roundCount) {
                    Box(Modifier.height(120.dp).background(Color.LightGray))
                }
                if (withFollowUps) {
                    item { Box(Modifier.height(72.dp).background(Color.Gray)) }
                }
                item { Box(Modifier.height(40.dp).background(Color.DarkGray)) }
            }
        }
        rule.waitForIdle()
    }

    private fun scrollTo(index: Int) {
        rule.runOnIdle { scope.launch { state.scrollToItem(index) } }
        rule.waitForIdle()
    }

    private fun scrollLatest(lastItemIndex: Int, animate: Boolean) {
        rule.runOnIdle { scope.launch { state.scrollToLatest(lastItemIndex, animate) } }
        rule.waitForIdle()
    }

    private fun assertAtBottom(lastItemIndex: Int) {
        assertFalse("触底之后不该还能往下滚", state.canScrollForward)
        assertTrue("从中间滚过来之后应当能往上滚", state.canScrollBackward)
        assertEquals(
            "最后一项必须完整落在视口里",
            lastItemIndex,
            state.layoutInfo.visibleItemsInfo.last().index,
        )
    }

    @Test
    fun `带追问 chips 时回到底部落在页脚`() {
        setUpList(roundCount = 20, withFollowUps = true)
        val lastItemIndex = 20 + 1
        scrollTo(5)
        scrollLatest(lastItemIndex, animate = true)
        assertAtBottom(lastItemIndex)
    }

    @Test
    fun `没有追问 chips 时回到底部落在页脚`() {
        setUpList(roundCount = 20, withFollowUps = false)
        val lastItemIndex = 20
        scrollTo(5)
        scrollLatest(lastItemIndex, animate = false)
        assertAtBottom(lastItemIndex)
    }

    @Test
    fun `内容不足一屏时不会反向跑`() {
        setUpList(roundCount = 1, withFollowUps = false)
        // 只有一轮 + 页脚，160dp 远小于一屏：本来就贴着底部，滚不滚都不该动
        scrollLatest(lastItemIndex = 1, animate = true)
        assertEquals(0, state.firstVisibleItemIndex)
        assertFalse(state.canScrollBackward)
        assertFalse(state.canScrollForward)
    }
}
