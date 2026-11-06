package com.pocket.xiaoli.desktop.data

import com.pocket.xiaoli.desktop.ai.LlmConfig
import java.io.File

/**
 * 轻量偏好（只放「不属于课程数据」的开关）。
 *
 * AI 配置本身存在 `AppSettings` 里（它会跟着备份一起走），这里只补充"一次性"标记：
 * 是否已完成首启引导。用**标记文件**而不是加字段，是因为加字段要动数据模型 ——
 * 而数据模型每动一次，旧备份就多一种要兼容的形状。
 */
object AppPrefs {

    private val flagFile: File get() = File(Store.dataDir, "onboarded.flag")

    fun onboarded(): Boolean = flagFile.exists()

    fun setOnboarded(value: Boolean = true) {
        runCatching {
            if (value) {
                Store.dataDir.mkdirs()
                flagFile.writeText(System.currentTimeMillis().toString())
            } else {
                flagFile.delete()
            }
        }
    }

    fun loadLlm(): LlmConfig = Store.data.settings.let {
        LlmConfig(
            baseUrl = it.aiBaseUrl,
            apiKey = it.aiApiKey,
            model = it.aiModel,
            temperature = it.aiTemperature,
        )
    }

    // ── 更新检查 ────────────────────────────────────────────────────────────
    // 同样用标记文件而不是数据模型的字段：加字段会让旧备份多一种要兼容的形状。

    private val updateCheckFile: File get() = File(Store.dataDir, "update-check.flag")

    /** 上次检查更新的时间戳（毫秒）；从没查过返回 0。 */
    fun lastUpdateCheck(): Long = runCatching { updateCheckFile.readText().trim().toLong() }.getOrDefault(0L)

    fun markUpdateChecked(at: Long = System.currentTimeMillis()) {
        runCatching {
            Store.dataDir.mkdirs()
            updateCheckFile.writeText(at.toString())
        }
    }

    /** 自动检查每天最多一次；手动检查不走这个判断。 */
    fun shouldAutoCheckUpdate(now: Long = System.currentTimeMillis()): Boolean =
        now - lastUpdateCheck() > 24L * 3600 * 1000

    fun saveLlm(config: LlmConfig) {
        Store.update { data ->
            data.copy(
                settings = data.settings.copy(
                    aiBaseUrl = config.baseUrl,
                    aiApiKey = config.apiKey,
                    aiModel = config.model,
                    aiTemperature = config.temperature,
                )
            )
        }
    }
}
