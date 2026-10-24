package com.pocket.xiaoli.data.agent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 内置系统提示词的**回答预算**。
 *
 * 这一串文字没有编译期约束，改坏了只看代码也发现不了 —— 表现是"回答又变啰嗦了"，
 * 用户很难描述成 bug。所以把三条最容易被后续改动稀释的要求钉在这里：
 *
 * 1. 有明确的默认篇幅（1~3 句），而不是只有一句「简洁」；
 * 2. 明确禁止复述问题与过程话术（模型最容易靠这两件事把回答撑长）；
 * 3. 简洁**不能挤掉**数据诚实与格式规则 —— 这是本 App 与前缀式聊天机器人的区别。
 *
 * 参考 Open WebUI 的「More Concise」：让"更短"成为一个明确的回答目标，
 * 而不是靠用户每次自己补一句「说重点」。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AssistantLlmConfigTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun clearPrefs() {
        context.getSharedPreferences(PLAIN, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(CRED, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun config() = AssistantLlmConfig(
        context.getSharedPreferences(PLAIN, Context.MODE_PRIVATE),
        context.getSharedPreferences(CRED, Context.MODE_PRIVATE),
    )

    @Test
    fun `默认提示词有明确的篇幅预算`() {
        val prompt = config().defaultSystemPrompt()

        assertTrue("缺少默认篇幅：$prompt", prompt.contains("默认 1~3 句话"))
        assertTrue("缺少不定式收尾约束", prompt.contains("只有用户明确说"))
    }

    @Test
    fun `默认提示词禁止复述与过程话术`() {
        val prompt = config().defaultSystemPrompt()

        assertTrue("没有禁止复述问题", prompt.contains("不复述用户的问题"))
        assertTrue("没有禁止过程话术", prompt.contains("我正在查询"))
    }

    @Test
    fun `精简没有挤掉数据诚实与格式规则`() {
        val prompt = config().defaultSystemPrompt()

        assertTrue("工具优先规则丢失", prompt.contains("必须调用工具获取"))
        assertTrue("表格禁令丢失", prompt.contains("不要输出 Markdown 表格"))
        assertTrue("引用编号规则丢失", prompt.contains("[K1]"))
        assertTrue("不确定时必须说明", prompt.contains("不确定就说不确定"))
    }

    @Test
    fun `自定义提示词优先，空白时回落到默认`() {
        val config = config()
        val fallback = config.defaultSystemPrompt()

        config.systemPrompt = "  只回答是或不是。  "
        assertEquals("只回答是或不是。", config.effectiveSystemPrompt())

        config.systemPrompt = "   "
        assertEquals(fallback, config.effectiveSystemPrompt())
    }

    private companion object {
        const val PLAIN = "assistant_llm_config_test"
        const val CRED = "assistant_llm_cred_test"
    }
}
