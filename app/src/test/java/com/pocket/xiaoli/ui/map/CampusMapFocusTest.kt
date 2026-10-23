package com.pocket.xiaoli.ui.map

import com.pocket.xiaoli.data.map.RoomCell
import com.pocket.xiaoli.data.map.model.Campus3DBuilding
import com.pocket.xiaoli.data.map.model.Campus3DScene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CampusMapFocusTest {

    private val scene = Campus3DScene(
        campusId = "ytlg_main",
        buildings = listOf(
            Campus3DBuilding(id = "way/1", name = "1号教学楼"),
            Campus3DBuilding(id = "way/2", name = "综合楼"),
        ),
    )

    private val cells = listOf(
        RoomCell(
            id = "room-406",
            campusId = "ytlg_main",
            building = "1号教学楼",
            floor = "4 层",
            room = "406",
        ),
        RoomCell(
            id = "room-413",
            campusId = "ytlg_main",
            building = "1号教学楼",
            floor = "4 层",
            room = "413",
            name = "北区教室",
        ),
        RoomCell(
            id = "other-campus",
            campusId = "other",
            building = "综合楼",
            floor = "1 层",
            room = "101",
        ),
    )

    @Test
    fun `房间号定位到楼栋`() {
        assertEquals("1号教学楼", resolveFocusBuilding("406", "ytlg_main", cells, scene))
    }

    @Test
    fun `楼名加房间号也能定位`() {
        assertEquals("1号教学楼", resolveFocusBuilding("1号教学楼 406", "ytlg_main", cells, scene))
    }

    @Test
    fun `房间名也能定位`() {
        assertEquals("1号教学楼", resolveFocusBuilding("北区教室", "ytlg_main", cells, scene))
    }

    @Test
    fun `直接写楼名也能定位`() {
        assertEquals("综合楼", resolveFocusBuilding("综合楼", "ytlg_main", cells, scene))
    }

    @Test
    fun `其他校区的房间不参与匹配`() {
        assertNull(resolveFocusBuilding("101", "ytlg_main", cells, scene))
    }

    @Test
    fun `空白文本保持全景`() {
        assertNull(resolveFocusBuilding("   ", "ytlg_main", cells, scene))
    }
}
