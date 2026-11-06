package com.pocket.xiaoli.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.pocket.xiaoli.data.learning.LearningPlatform
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 只是为了把「绑定信息 → 学习通」那条路径真的组合一次。
 *
 * 真机上用户报过点进去闪退，而这条路径此前没有任何组合级测试：
 * 编译过、单测绿都不代表 `Scaffold + AndroidView(WebView)` 能建起来。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LearningWebLoginComposeTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun 学习通登录页能组合起来() {
        rule.setContent {
            PocketXiaoliTheme {
                Box(Modifier.fillMaxSize()) {
                    LearningWebLoginScreen(
                        platform = LearningPlatform.CHAOXING,
                        onSaveCookie = {},
                        onClose = {},
                    )
                }
            }
        }
        rule.waitForIdle()
    }
}
