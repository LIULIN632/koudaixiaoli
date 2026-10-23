package com.pocket.xiaoli.ui.components.campusui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow

/**
 * CampusUI 进程内滚动位置记忆。
 *
 * 只解决切 Tab / 进出详情再回来的位置恢复：
 * - 不做磁盘持久化；
 * - 冷启动回顶部；
 * - key 必须唯一标识一份列表。
 */
object CampusScrollMemory {
    private val positions = mutableMapOf<String, Pair<Int, Int>>()

    fun get(key: String): Pair<Int, Int> = positions[key] ?: (0 to 0)

    fun put(key: String, index: Int, offset: Int) {
        positions[key] = index to offset
    }

    fun clear(keyPrefix: String) {
        positions.keys.filter { it.startsWith(keyPrefix) }.forEach { positions.remove(it) }
    }
}

@Composable
fun rememberCampusScrollState(key: String): ScrollState {
    val initial = remember(key) { CampusScrollMemory.get(key).first }
    val state = rememberScrollState(initial = initial)
    LaunchedEffect(state, key) {
        snapshotFlow { state.value }.collect { value ->
            CampusScrollMemory.put(key, value, 0)
        }
    }
    return state
}

@Composable
fun rememberCampusLazyListState(key: String): LazyListState {
    val initial = remember(key) { CampusScrollMemory.get(key) }
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = initial.first,
        initialFirstVisibleItemScrollOffset = initial.second,
    )
    LaunchedEffect(state, key) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .collect { (index, offset) -> CampusScrollMemory.put(key, index, offset) }
    }
    return state
}
