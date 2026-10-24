package com.pocket.xiaoli.data.agent

import android.content.Context
import android.content.SharedPreferences

/**
 * 助手 LLM 配置（OpenAI 兼容协议）。
 *
 * 存储分两处，按敏感度决定：
 * - **apiKey** → [credPrefs]（`EncryptedSharedPreferences`，AES256-GCM），绝不明文落盘
 * - 其余非敏感项 → 普通 prefs（baseUrl / model / systemPrompt / 开关）
 *
 * 为什么不用 Room：这是单例配置而非列表数据，prefs 的读写原子性足够，
 * 也避免为几个标量升一次数据库版本。
 */
class AssistantLlmConfig(
    private val prefs: SharedPreferences,
    private val credPrefs: SharedPreferences,
) {

    /** OpenAI 兼容端点根地址，如 `https://api.deepseek.com/v1`（不含 `/chat/completions`） */
    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL).orEmpty()
        set(value) = prefs.edit().putString(KEY_BASE_URL, value.trim().trimEnd('/')).apply()

    var model: String
        get() = prefs.getString(KEY_MODEL, DEFAULT_MODEL).orEmpty()
        set(value) = prefs.edit().putString(KEY_MODEL, value.trim()).apply()

    /** API Key。走加密存储；读取失败时返回空串（视作未配置）。 */
    var apiKey: String
        get() = runCatching { credPrefs.getString(KEY_API_KEY, null).orEmpty() }.getOrDefault("")
        set(value) {
            runCatching {
                credPrefs.edit().putString(KEY_API_KEY, value.trim()).apply()
            }
        }

    /** 用户可自定义的系统提示词；留空则用 [defaultSystemPrompt]。 */
    var systemPrompt: String
        get() = prefs.getString(KEY_SYSTEM_PROMPT, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_SYSTEM_PROMPT, value).apply()

    /** 是否允许联网搜索等需要额外网络请求的工具；默认关，用户显式开。 */
    var allowWebTools: Boolean
        get() = prefs.getBoolean(KEY_ALLOW_WEB, false)
        set(value) = prefs.edit().putBoolean(KEY_ALLOW_WEB, value).apply()

    /**
     * 是否已配置到可用状态。
     * 三者缺一不可：没有 baseUrl 不知往哪发，没有 apiKey 会被 401，没有 model 不知道用哪个。
     */
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()

    /** 请求实际使用的系统提示词：用户自定义优先，否则内置。 */
    fun effectiveSystemPrompt(): String =
        systemPrompt.trim().ifBlank { defaultSystemPrompt() }

    /** 内置系统提示词：只描述身份、能力边界与回答要求，具体数据由工具提供。 */
    fun defaultSystemPrompt(): String = buildString {
        append("你是「鲤鱼」，烟台理工学院校园助手「口袋小鲤」里的 AI 助手。\n")
        append("你服务的是烟台理工（本校区/新校区）与烟大南校区的学生。\n\n")
        append("能力边界：\n")
        append("- 课表、待办、成绩、教室位置、校园服务入口、天气等信息，**必须调用工具获取**，不要凭记忆或猜测编造。\n")
        append("- 天气工具的坐标是**烟台市**，不是校区实测；回答时可以说「烟台」，不要说成教学楼里的实测数据。\n")
        append("- 工具返回的是权威数据；如果工具说没有数据，就如实告诉用户，并告诉他去哪里补录（例如「服务 → 教室分布图」）。\n")
        append("- 你无法操作学校系统（不能选课、不能缴费、不能提交申请），只能查询与指路。\n\n")
        append("回答要求：\n")
        append("- 中文，简洁，先给结论再给必要细节。\n")
        append("- 涉及具体课程/成绩/地点时，保留工具返回的关键信息（节次、时间、教室、分数），不要含糊概括。\n")
        append("- 不要输出 Markdown 表格；用短行或短列表。\n")
        append("- 引用校园知识库的内容时，保留工具返回的 `[K1]` 这类编号（例如「……[K1]」）。\n")
        append("- 只能引用工具**真实返回过**的编号；不要自己编号、不要编造来源、链接或文件。\n")
        append("- 不确定就说不确定，不要编造校园信息。")
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.deepseek.com/v1"
        const val DEFAULT_MODEL = "deepseek-chat"

        private const val KEY_BASE_URL = "assistant_llm_base_url"
        private const val KEY_MODEL = "assistant_llm_model"
        private const val KEY_API_KEY = "assistant_llm_api_key"
        private const val KEY_SYSTEM_PROMPT = "assistant_llm_system_prompt"
        private const val KEY_ALLOW_WEB = "assistant_llm_allow_web"

        fun from(context: Context, prefs: SharedPreferences, credPrefs: SharedPreferences): AssistantLlmConfig =
            AssistantLlmConfig(prefs, credPrefs)
    }
}
