package com.pocket.xiaoli.ui.map

import com.pocket.xiaoli.data.entity.CourseEntity
import com.pocket.xiaoli.data.holiday.HolidayDay
import com.pocket.xiaoli.data.map.Campus3DTestAssets
import com.pocket.xiaoli.data.map.RoomCell
import com.pocket.xiaoli.data.map.model.Campus3DScene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CampusMapTodayCoursesTest {

    private val scene: Campus3DScene = Campus3DTestAssets.loadScene()
    private val today: LocalDate = LocalDate.of(2026, 10, 22) // 周四
    private val roomCells = listOf(
        RoomCell(
            id = "cell-1",
            campusId = scene.campusId,
            building = "1号教学楼",
            floor = "4 层",
            room = "A406",
            name = "测试教室",
        ),
        RoomCell(
            id = "cell-2",
            campusId = scene.campusId,
            building = "综合楼",
            floor = "2 层",
            room = "B201",
            name = "测试办公室",
        ),
    )

    @Test
    fun `今日课程按地点落到场景楼栋并计数`() {
        val result = resolve(
            courses = listOf(
                course(room = "A406", day = 4),
                course(room = "1号教学楼 A406", day = 4),
                course(room = "综合楼 B201", day = 4),
            ),
        )

        assertEquals(mapOf(buildingId("1号教学楼") to 2, buildingId("综合楼") to 1), result)
    }

    @Test
    fun `同一房间号出现在多栋楼时不猜`() {
        val result = resolve(
            courses = listOf(course(room = "A406", day = 4)),
            roomCells = roomCells + RoomCell(
                id = "cell-3",
                campusId = scene.campusId,
                building = "商业楼",
                floor = "1 层",
                room = "A406",
            ),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `地点自带楼名时按楼名定位`() {
        val result = resolve(
            courses = listOf(course(room = "1号教学楼 A406", day = 4)),
            roomCells = roomCells + RoomCell(
                id = "cell-3",
                campusId = scene.campusId,
                building = "商业楼",
                floor = "1 层",
                room = "A406",
            ),
        )

        assertEquals(mapOf(buildingId("1号教学楼") to 1), result)
    }

    @Test
    fun `非今天的课不计入`() {
        val result = resolve(
            courses = listOf(
                course(room = "A406", day = 3),
                course(room = "综合楼 B201", day = 4),
            ),
        )

        assertEquals(mapOf(buildingId("综合楼") to 1), result)
    }

    @Test
    fun `本周不上或空白地点都不猜楼栋`() {
        val result = resolve(
            courses = listOf(
                course(room = "A406", day = 4, weeks = "2"),
                course(room = "", day = 4),
                course(room = "校外实训基地", day = 4),
            ),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `自定义日程不算课程`() {
        val result = resolve(
            courses = listOf(
                course(room = "A406", day = 4, kind = CourseEntity.KIND_AGENDA),
            ),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `法定放假日不标楼栋`() {
        val result = resolve(
            courses = listOf(course(room = "A406", day = 4)),
            holidays = listOf(HolidayDay(name = "测试假期", date = today.toString(), isOffDay = true)),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `调休补课日按补的那一天取课`() {
        val saturday = LocalDate.of(2026, 10, 24) // 周六，补周三的课
        val result = resolve(
            courses = listOf(
                course(room = "A406", day = 3),
                course(room = "综合楼 B201", day = 6),
            ),
            today = saturday,
            specialWorkDayMap = mapOf(saturday.toString() to "2026-10-21"),
        )

        assertEquals(mapOf(buildingId("1号教学楼") to 1), result)
    }

    private fun resolve(
        courses: List<CourseEntity>,
        today: LocalDate = this.today,
        holidays: List<HolidayDay> = emptyList(),
        specialWorkDayMap: Map<String, String> = emptyMap(),
        roomCells: List<RoomCell> = this.roomCells,
    ): Map<String, Int> = resolveTodayCourseBuildings(
        courses = courses,
        week = 8,
        today = today,
        holidays = holidays,
        specialWorkDayMap = specialWorkDayMap,
        campusId = scene.campusId,
        roomCells = roomCells,
        scene = scene,
    )

    private fun course(
        room: String,
        day: Int,
        weeks: String = "",
        kind: String = CourseEntity.KIND_COURSE,
    ): CourseEntity = CourseEntity(
        name = "测试课",
        room = room,
        dayOfWeek = day,
        startSlot = 1,
        endSlot = 2,
        weeks = weeks,
        kind = kind,
    )

    private fun buildingId(name: String): String =
        scene.buildings.first { it.name == name }.id
}
