package com.pocket.xiaoli.ui.components

/**
 * 为列表生成**保证唯一**的 key，按位置索引返回。
 *
 * 返回值刻意是 `Map<Int, String>` 而不是 `List<String>`：
 * 调用点通常长这样 ——
 * ```kotlin
 * val keys = remember(items) { uniqueKeys(items) { it.id } }
 * itemsIndexed(items, key = { index, _ -> keys[index] }) { _, item -> Row(item) }
 * ```
 * 用 Map 查表是 O(1)，而且不必在 `key` 这个**非 @Composable 的**回调里做任何计算
 * （那里不能调 `remember`，也不能做重活）。
 *
 * ## 原理
 *
 * Compose 的 `LazyColumn` / `LazyRow` 把 key 当作列表项的**身份**：同一个 key
 * 出现两次会**直接崩**（`IllegalArgumentException: Key "..." was already used`），
 * 而这只在数据真的重复时才复现 —— 开发时拿一份干净的测试数据完全看不出来。
 *
 * [rawKeyOf] 给出业务身份（决定跨重组是否稳定、列表重排时能否复用条目）；
 * 撞车时给后来者补位置后缀，从而保证唯一。
 * 后缀的稳定性来自"同一份列表 + 同一个 Key 函数"：成绩、搜索结果这类列表是
 * **整体替换**而不是随机插入删除，顺序在两次重组之间是稳定的。
 *
 * 为什么不能图省事直接 `"$raw#$index"`：那样每次列表一头一尾有增删，
 * 所有下标都会平移，等于每次重组全部重建条目（丢失滚动位置与条目内部状态）。
 * 先按原始 key 去重，只有撞车的那几项才带后缀。
 */
fun <T> uniqueKeys(items: List<T>, rawKeyOf: (T) -> String): Map<Int, String> {
    val seen = HashSet<String>(items.size)
    val out = HashMap<Int, String>(items.size)
    items.forEachIndexed { index, item ->
        val raw = rawKeyOf(item)
        val key = if (raw.isEmpty()) "#$index" else raw
        out[index] = if (seen.add(key)) key else "$key#$index"
    }
    return out
}
