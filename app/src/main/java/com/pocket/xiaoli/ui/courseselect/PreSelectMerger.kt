package com.pocket.xiaoli.ui.courseselect

import com.pocket.xiaoli.network.CourseSelectItem

/**
 * 预选列表的合成逻辑（**纯函数**，无 Android / 网络依赖，可全量单测）。
 *
 * ## 为什么需要合成
 * 预选页在教务那边是**左右两张表**：
 * - 左表「可选课程」→ `kcyx_cxYxkcListIndex.html`
 * - 右表「已预选」  → `kcyx_cxYxkcChoosed.html`
 *
 * 两张表都要看：左表有**余量**（`sxrsmc` / `yxrs`，决定这门课还抢不抢得到），
 * 右表有**真实预选 ID 与停开状态**（`xsyxb_id` / `sftkzt`，决定"我到底选上没有"）。
 * 只取一张就会缺信息 —— 而缺的那一半恰恰是用户最关心的那个问题。
 *
 * ## 为什么键不能只用课程代码
 * 同一门课可能有**多个教学班**（同 `kch`、不同 `kcyxb_id`、不同老师）。
 * 用 `kch` 当键会让两个教学班互相覆盖，用户少看到一门课，
 * 而界面上完全看不出来 —— 所以主键是**行级唯一**的 `kcyxb_id`，
 * 缺失时依次退到「课程代码+课名」、课名。
 *
 * 这里的实现刻意不写成私有方法留在 ViewModel 里：ViewModel 依赖 `AppContainer`，
 * 在纯 JVM 单测里构造不出来，逻辑留在里面就等于**测不到**。
 */
internal object PreSelectMerger {

    /** 合并键：主键是行 ID，退路依次是"课程代码+课名"、课名 */
    internal fun mergeKey(item: CourseSelectItem): String = when {
        item.kcyxbId.isNotBlank() -> "id:${item.kcyxbId}"
        item.kch.isNotBlank() -> "kc:${item.kch}|${item.kcmc}"
        else -> "nm:${item.kcmc}"
    }

    /**
     * 以左表（可选课程）为骨架，把右表（已预选）的状态合并进来。
     *
     * - 左表为空 → 直接用右表（有的部署左表不返回，右表仍要能看）
     * - 右表为空 → 左表原样返回（尚未预选是常态，不是异常）
     * - 行 ID 匹配不上时**不轻易按课名兜**：规则与理由见 [fallbackByName]
     */
    internal fun merge(
        listItems: List<CourseSelectItem>,
        choosedItems: List<CourseSelectItem>,
    ): List<CourseSelectItem> {
        if (listItems.isEmpty()) return choosedItems
        if (choosedItems.isEmpty()) return listItems

        val chosenByKey = choosedItems.associateBy { mergeKey(it) }
        val chosenByName = choosedItems.groupBy { it.kcmc }

        return listItems.map { item ->
            val fromChoosed = chosenByKey[mergeKey(item)] ?: fallbackByName(item, chosenByName)
            if (fromChoosed == null) {
                item
            } else {
                item.copy(
                    // 已选端点的 xsyxb_id 才是真实值（列表端点是 "0" / 占位）
                    xsyxbId = fromChoosed.xsyxbId.ifBlank { item.xsyxbId },
                    sftkzt = fromChoosed.sftkzt.ifBlank { item.sftkzt },
                    // 只要确认是同一门课，就按右表判为已选
                    chosen = true,
                )
            }
        }
    }

    /**
     * 按课名兜底的**严格条件**：只要某一侧带了行 ID，就不许按课名兜。
     *
     * ⚠️ 这条规则是被测试逼出来的真实缺陷：最初的实现无条件按课名兜底，
     * 于是同一门课的**两个教学班**（同 `kcmc`、不同 `kcyxb_id`）会被判成互相匹配 ——
     * 只选了 ROW2，界面上 ROW1 也显示「已选」。
     * 后果是用户以为两门都选上了，实际只选了一门，**而他自己去教务页之前不会发现**。
     *
     * 行 ID（`kcyxb_id`）是行级唯一键，它对不上就是两行，此时"没匹配上"是**结论**而不是"信息不足"。
     * 只有当某一侧确实没有行 ID 时，课名才是唯一可用的依据。
     */
    private fun fallbackByName(
        item: CourseSelectItem,
        chosenByName: Map<String, List<CourseSelectItem>>,
    ): CourseSelectItem? {
        val candidates = chosenByName[item.kcmc] ?: return null
        // 左表这一行有行 ID ⇒ 只认行 ID，不按课名兜
        if (item.kcyxbId.isNotBlank()) return null
        // 左表没有行 ID，但右表候选里有多个带行 ID 的 ⇒ 无法判断是哪一行，也不猜
        if (candidates.size != 1) return null
        return candidates.first().takeIf { it.kcyxbId.isBlank() }
    }
}
