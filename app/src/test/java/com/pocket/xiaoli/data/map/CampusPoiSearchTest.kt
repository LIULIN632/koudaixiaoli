package com.pocket.xiaoli.data.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusPoiSearchTest {

    private val pois = listOf(
        CampusPoi(
            id = "1", campusId = "c1", name = "图书馆", category = PoiCategory.LIBRARY,
            building = "中心楼", aliases = listOf("老图"),
        ),
        CampusPoi(
            id = "2", campusId = "c1", name = "第一食堂", category = PoiCategory.DINING,
            building = "生活区",
        ),
        CampusPoi(
            id = "3", campusId = "c1", name = "A教学楼", category = PoiCategory.TEACHING,
            description = "工程楼",
        ),
        CampusPoi(id = "4", campusId = "c2", name = "图书馆", category = PoiCategory.LIBRARY),
    )

    @Test
    fun `filter keeps only the requested campus`() {
        assertEquals(3, CampusPoiSearch.filter(pois, "c1", "").size)
        assertEquals(1, CampusPoiSearch.filter(pois, "c2", "").size)
    }

    @Test
    fun `filter matches name`() {
        assertEquals(listOf("2"), CampusPoiSearch.filter(pois, "c1", "食堂").map { it.id })
    }

    @Test
    fun `filter matches alias`() {
        assertEquals(listOf("1"), CampusPoiSearch.filter(pois, "c1", "老图").map { it.id })
    }

    @Test
    fun `filter matches description`() {
        assertEquals(listOf("3"), CampusPoiSearch.filter(pois, "c1", "工程").map { it.id })
    }

    @Test
    fun `filter matches category`() {
        assertEquals(
            listOf("1"),
            CampusPoiSearch.filter(pois, "c1", "", PoiCategory.LIBRARY).map { it.id },
        )
    }

    @Test
    fun `filter sorts by category then name`() {
        assertEquals(listOf("3", "1", "2"), CampusPoiSearch.filter(pois, "c1", "").map { it.id })
    }

    @Test
    fun `filter returns empty for unknown campus`() {
        assertTrue(CampusPoiSearch.filter(pois, "c9", "图书馆").isEmpty())
    }
    @Test
    fun `favoritesOnly keeps only favorite ids`() {
        assertEquals(
            listOf("3", "1"),
            CampusPoiSearch.filter(
                pois = pois,
                campusId = "c1",
                query = "",
                favoriteIds = setOf("1", "3"),
                favoritesOnly = true,
            ).map { it.id },
        )
    }

    @Test
    fun `favorites are sorted before non-favorites`() {
        assertEquals(
            listOf("2", "3", "1"),
            CampusPoiSearch.filter(
                pois = pois,
                campusId = "c1",
                query = "",
                favoriteIds = setOf("2"),
            ).map { it.id },
        )
    }
}
