package com.pocket.xiaoli.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.window.Dialog
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * MIUIX 弹层依赖的 `LocalNavigationEventDispatcherOwner` 必须在根部提供，且**只能以
 * `parent = null` 建根**。
 *
 * ## 为什么单独守这一条
 *
 * 0.1.56 上真机点了「更新渠道」就整应用闪退，栈顶是
 * `No NavigationEventDispatcher was provided via LocalNavigationEventDispatcherOwner`；
 * 0.1.57 补了根部 provider 却在**启动**时就崩，因为
 * `rememberNavigationEventDispatcherOwner()` 的 `parent` 默认值取 `LocalNavigationEventDispatcherOwner.current`，
 * 根 composition 里它本来就是空的，库会直接抛
 * `No NavigationEventDispatcherOwner provided in LocalNavigationEventDispatcherOwner. If you intended
 * to create a root dispatcher, explicitly pass null as the parent.`
 *
 * 编译和 1580+ 条单测都发现不了这两次崩溃 —— 它们只在「compose 真的跑起来」时才发生，
 * 所以这里用 Robolectric 把 composition 真跑一遍。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NavigationEventOwnerTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `parent 传 null 时能建出根 owner 且 Dialog 里继承得到`() {
        var ownerInDialog: Any? = null

        composeRule.setContent {
            val owner = rememberNavigationEventDispatcherOwner(parent = null)
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                Dialog(onDismissRequest = {}) {
                    ownerInDialog = LocalNavigationEventDispatcherOwner.current
                    Text("弹层内容")
                }
            }
        }
        composeRule.waitForIdle()

        assertNotNull("Dialog 里读不到 owner —— MIUIX 弹层会当场抛 IllegalStateException", ownerInDialog)
    }

    @Test
    fun `不传 parent 时库里会抛 IllegalStateException`() {
        var thrown: Throwable? = null

        try {
            composeRule.setContent {
                // 故意踩坑：这就是 0.1.57 启动崩溃的写法
                rememberNavigationEventDispatcherOwner()
                Text("不该跑到这里")
            }
            composeRule.waitForIdle()
        } catch (t: Throwable) {
            thrown = t
        }

        assertTrue(
            "不传 parent 应当抛 IllegalStateException（库里找不到上级 owner），实际：${thrown?.javaClass?.simpleName}",
            thrown is IllegalStateException,
        )
    }
}
