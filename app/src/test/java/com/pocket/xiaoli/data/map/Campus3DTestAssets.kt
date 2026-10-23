package com.pocket.xiaoli.data.map

import com.pocket.xiaoli.data.map.model.Campus3DScene
import java.io.File

/** 测试里按线上同样的路径合并 campus_3d.json + campus_3d_traced.json。 */
internal object Campus3DTestAssets {

    private val assetRoot: File by lazy {
        listOf(File("src/main"), File("app/src/main"), File("../app/src/main"))
            .firstOrNull { File(it, "assets/campus_map/ytlg_main/campus_3d.json").isFile }
            ?: error("campus_3d.json not found")
    }

    fun loadResult(): Campus3DMapLoadResult {
        val source = Campus3DMapDataSource { path ->
            File(assetRoot, "assets/$path").takeIf { it.isFile }?.readText()
        }
        return source.load("ytlg_main")
    }

    fun loadScene(): Campus3DScene = loadResult().scene
}
