package com.pocket.xiaoli.data.map

import androidx.compose.runtime.Immutable

/** A user-maintained campus place (M3 first version: POI + external map jump). */
@Immutable
data class CampusPoi(
    val id: String,
    val campusId: String,
    val name: String,
    val category: PoiCategory = PoiCategory.OTHER,
    val building: String = "",
    val floor: String = "",
    val aliases: List<String> = emptyList(),
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

enum class PoiCategory(val key: String, val label: String, val emoji: String) {
    TEACHING("teaching", "教学楼", "🏫"),
    LIBRARY("library", "图书馆", "📚"),
    DINING("dining", "食堂", "🍚"),
    DORM("dorm", "宿舍", "🛏"),
    OFFICE("office", "行政", "🏛"),
    SPORT("sport", "运动", "🏃"),
    GATE("gate", "校门", "🚪"),
    LIFE("life", "生活", "🛒"),
    OTHER("other", "其他", "📍");

    companion object {
        fun fromKey(key: String): PoiCategory = entries.firstOrNull { it.key == key } ?: OTHER
    }
}

/** Pure search/filter helper so the behavior can be unit-tested later. */
object CampusPoiSearch {
    fun filter(
        pois: List<CampusPoi>,
        campusId: String,
        query: String,
        category: PoiCategory? = null,
        favoriteIds: Set<String> = emptySet(),
        favoritesOnly: Boolean = false,
    ): List<CampusPoi> {
        val q = query.trim().lowercase()
        return pois
            .filter { it.campusId == campusId }
            .filter { category == null || it.category == category }
            .filter { !favoritesOnly || it.id in favoriteIds }
            .filter { poi ->
                q.isEmpty() ||
                    poi.name.lowercase().contains(q) ||
                    poi.building.lowercase().contains(q) ||
                    poi.description.lowercase().contains(q) ||
                    poi.aliases.any { it.lowercase().contains(q) }
            }
            .sortedWith(
                compareBy(
                    { if (it.id in favoriteIds) 0 else 1 },
                    { it.category.ordinal },
                    { it.name },
                )
            )
    }
}
