package com.pocket.xiaoli.data.bus

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「公交到站」钉住两个随包发布的事实：学校中心锚点与本校站名。
 *
 * ## 为什么这两条值得单独一个测试
 *
 * [BusRepository.CAMPUS_LAT] / [BusRepository.CAMPUS_LNG] 是**抄**自
 * `assets/campus_map/ytlg_main/campus_3d.json` 的 `center`（OpenStreetMap 校园轮廓算出来的点）。
 * 抄的东西就会漂：3D 地图那侧重新描点、导出一遍资产，界面里的坐标还在原地，
 * 「附近站点」就会静悄悄地按一个错误的中心去排 —— 距离和排序都会错，但没有任何一处会报错。
 * 所以这里直接读资产文件比对，漂了就红。
 *
 * 站名同理：上游按**精确站名**查线路，站名改一个字就是查不到（实测「烟台理工学校」
 * 是另一个站，不是这个门）。
 */
class BusCampusAnchorTest {

    private val sceneJson: String = File("src/main/assets/campus_map/ytlg_main/campus_3d.json")
        .also { assertTrue("找不到 3D 校园地图资产，路径要按测试工作目录（app/）算", it.isFile) }
        .readText(Charsets.UTF_8)

    @Test
    fun `锚点与 3D 地图资产的 center 同源`() {
        val center = JSONObject(sceneJson).getJSONObject("center")
        assertEquals(BusRepository.CAMPUS_LAT, center.getDouble("lat"), 1e-4)
        assertEquals(BusRepository.CAMPUS_LNG, center.getDouble("lng"), 1e-4)
    }

    @Test
    fun `锚点落在校园轮廓范围内`() {
        // 资产里的 bounds 是 OSM 轮廓的外接矩形；锚点跑出校园就意味着抄错了位数
        val bounds = JSONObject(sceneJson).getJSONObject("bounds")
        val lat = BusRepository.CAMPUS_LAT
        val lng = BusRepository.CAMPUS_LNG
        assertTrue("纬度越界", lat >= bounds.getDouble("minLat") && lat <= bounds.getDouble("maxLat"))
        assertTrue("经度越界", lng >= bounds.getDouble("minLng") && lng <= bounds.getDouble("maxLng"))
    }

    @Test
    fun `城市名带行政区划后缀且站名非空`() {
        // 上游 CMD=101 给的是「烟台市」这种带后缀形式；裸「烟台」查不到
        assertEquals("烟台市", BusRepository.CITY_NAME)
        assertTrue(BusRepository.DEFAULT_STATION.startsWith("烟台理工"))
        assertTrue(BusRepository.DEFAULT_STATION.endsWith("学院"))
    }

    @Test
    fun `上游端点与请求头同域`() {
        // Referer 与 API 域不一致时上游会挡；这条把两个常量绑在一起
        assertTrue(BusRepository.API_URL.startsWith(BusRepository.ORIGIN))
        assertEquals("https://h5.mygolbs.com/ApiData.do", BusRepository.API_URL)
    }
}
