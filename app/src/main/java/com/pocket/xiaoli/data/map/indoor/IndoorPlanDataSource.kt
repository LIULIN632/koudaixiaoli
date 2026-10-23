package com.pocket.xiaoli.data.map.indoor

import android.content.Context

/**
 * 室内平面图资产入口。
 *
 * 资源约定：`assets/indoor/{plan}.json`。预览页与测试都从这里读，
 * 不再维护第二份 Kotlin 硬编码拓扑。
 */
class IndoorPlanDataSource(
    private val assetReader: (String) -> String?,
) {
    fun load(path: String = TEACHING1_FLOOR4_DRAFT): IndoorPlanLoadResult {
        val json = assetReader(path)
            ?: return IndoorPlanLoadResult(
                plan = null,
                issues = listOf("未找到室内平面图资源：$path"),
            )
        return IndoorPlanJson.parse(json)
    }

    companion object {
        const val TEACHING1_FLOOR4_DRAFT = "indoor/1jian-4f-topology-draft.json"

        fun fromAssets(context: Context): IndoorPlanDataSource =
            IndoorPlanDataSource { path ->
                runCatching {
                    context.assets.open(path).bufferedReader().use { it.readText() }
                }.getOrNull()
            }
    }
}
