package com.pocket.xiaoli.ui.treasurebox

// 修课情况页的**折叠算法**（纯函数，不碰 Compose）：谁能展开、谁被显式开过、收起之后该渲染哪些行。
// 页面与快照测试同读 `academicTreeState` —— 上一版这里只有一个孤零零的 `visibleAcademicTypes`，
// 页面从没调用它，于是「收起」只少了半棵树（见 memory/03-pitfalls.md §4.16）。

import com.pocket.xiaoli.network.AcademicCourseItem
import com.pocket.xiaoli.network.AcademicTypeInfo

internal fun String.splitToIds(): Set<String> =
    if (isBlank()) emptySet() else split(',').filter { it.isNotBlank() }.toSet()

/** id 集合 → `rememberSaveable` 存得下的逗号串 */
internal fun joinIds(ids: Set<String>): String = ids.joinToString(",")

/**
 * **有子节点**的 id 集合。
 *
 * ⚠️ 必须从**裁剪前**的完整列表算：收起一个平台之后它的子节点不在渲染列表里了，
 * 现场用 `types.any { parentId == id }` 会把这个平台看成叶子 —— 箭头消失、
 * 还会给它喊一句"学校暂无该类型的课程明细"。
 */
internal fun academicParentIds(types: List<AcademicTypeInfo>): Set<String> =
    types.mapNotNull { it.parentId.takeIf(String::isNotBlank) }.toSet()

/** 能展开 = 有子节点，或名下直接挂着课。两样都没有的行不配一个箭头。 */
internal fun academicExpandableIds(
    types: List<AcademicTypeInfo>,
    courses: List<AcademicCourseItem>,
): Set<String> {
    val parents = academicParentIds(types)
    val withCourses = courses.map { it.typeId }.filter { it.isNotBlank() }.toSet()
    return types.map { it.id }.filter { it in parents || it in withCourses }.toSet()
}

/**
 * 展开态的**唯一算法** —— 页面、表头按钮、子树裁剪全读它这一个结果。
 *
 * 以前是两套集合（父节点查"谁被收起了"、叶子查"谁被展开了"），默认值还不一样，
 * 于是同一个节点在两条判断里能给出两个答案：筛选时点叶子行写进了集合却没人读，
 * **看着就是"点了没反应"**。现在只有三个口径，按优先级：
 *
 * 1. 用户显式收起过（[shutIds]）→ 收起；
 * 2. 用户显式展开过（[openIds]）→ 展开；
 * 3. 没动过 → 默认：筛选/搜索时全展开（把命中的东西藏着没意义），
 *    否则**展开到平台层**（`depth <= 1` = 主修 + 各平台，用户 2026-10-31 的口径）。
 *    模块与叶子类型默认收起：24 行全展开要滚三屏，而"哪个平台还差学分"只看平台层就有答案。
 */
internal fun academicExpandedIds(
    types: List<AcademicTypeInfo>,
    courses: List<AcademicCourseItem>,
    openIds: Set<String>,
    shutIds: Set<String>,
    filtering: Boolean,
): Set<String> {
    val expandable = academicExpandableIds(types, courses)
    val byDefault = if (filtering) {
        expandable
    } else {
        types.filter { it.depth <= ACADEMIC_OPEN_TO_DEPTH }
            .map { it.id }
            .filter { it in expandable }
            .toSet()
    }
    // 收起最后减：万一两个集合里同时出现一个 id（正常路径不会，`onToggle` 写一个删另一个），
    // 用户最后一次动作是"收起来"，那就该是收起 —— 展开态还留着等于没响应。
    return (byDefault + openIds.filter { it in expandable }) - shutIds
}

/** 默认展开到第几层：0 = 主修、1 = 平台。再往下（模块 / 叶子类型）默认收起。 */
private const val ACADEMIC_OPEN_TO_DEPTH = 1

/** 渲染一棵树要的全部结构信息，一次算好。页面与快照测试共用，避免两边各写一套裁剪逻辑。 */
internal class AcademicTreeState(
    /** 裁剪后的行：**收起的节点，整棵子树都不在这里**。 */
    val types: List<AcademicTypeInfo>,
    /** 当前展开的 id。 */
    val expandedIds: Set<String>,
    /** 能展开的 id（表头的「展开全部 / 收回到平台」用它）。 */
    val expandableIds: Set<String>,
    /** 有子节点的 id（来自**裁剪前**的列表，见 [academicParentIds]）。 */
    val parents: Set<String>,
)

/** 展开态 + 子树裁剪的唯一入口：传进全量 `types`/`courses` 与用户的显式覆盖，拿回该渲染什么。 */
internal fun academicTreeState(
    types: List<AcademicTypeInfo>,
    courses: List<AcademicCourseItem>,
    openIds: Set<String> = emptySet(),
    shutIds: Set<String> = emptySet(),
    filtering: Boolean = false,
): AcademicTreeState {
    val parents = academicParentIds(types)
    val expandable = academicExpandableIds(types, courses)
    val expanded = academicExpandedIds(types, courses, openIds, shutIds, filtering)
    return AcademicTreeState(
        types = visibleAcademicTypes(types, expandable - expanded),
        expandedIds = expanded,
        expandableIds = expandable,
        parents = parents,
    )
}

/**
 * 可见的类型：**祖先被收起，整棵子树都不渲染**。
 *
 * `types` 是文档顺序（父在子前，见 `ZhengfangAcademicApi.parentIdOf`），
 * 所以这里只需按 `parentId` 链往上找有没有被收起的祖先。
 */
internal fun visibleAcademicTypes(
    types: List<AcademicTypeInfo>,
    collapsedIds: Set<String>,
): List<AcademicTypeInfo> {
    if (collapsedIds.isEmpty()) return types
    val byId = types.associateBy { it.id }
    return types.filter { type ->
        var parent = byId[type.parentId]
        var guard = 0
        while (parent != null && guard < 8) {
            if (parent.id in collapsedIds) return@filter false
            parent = byId[parent.parentId]
            guard++
        }
        true
    }
}
