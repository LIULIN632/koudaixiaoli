package com.pocket.xiaoli.core.bus

/**
 * 公交到站状态的唯一真源：把上游「一行文案 + 一个站数 + 两个标志位」归一成一个可判定的类型。
 *
 * ## 为什么要归一
 *
 * 掌上公交的 CMD=105 对每条线路给的是**给人看的中文文案**（`neartext`），不是结构化时间。
 * 2026-11-04 本校站实测的九条线路，取值只有这几种形态：
 *
 * ```
 * 已到站 [到达 烟台理工学院]      nearnum = 0
 * 2站  [开往 蓝色智谷]            nearnum = 2
 * 8站  [开往 金地格林世界北站]     nearnum = 8
 * 等待发车                        nearnum = 100   ← 哨兵值，不是"还有 100 站"
 * ```
 *
 * 界面若直接显示这串原文，就得在 UI 里做字符串判断；而「几站算临近」「停运怎么标」
 * 这类判据一旦散进 Compose，手机与桌面两端就会各写一套（考试倒计时的老账，
 * 见 [com.pocket.xiaoli.core.exam.ExamCountdown]）。所以判据收在这里，
 * **界面只按 [BusArrivalTone] 取色**。
 *
 * ## 诚实性边界（本项目红线）
 *
 * 拿不到实时就明说拿不到，**绝不用首末班时间倒推一个假倒计时**
 * （`memory/01-constraints.md` §2.4「实时数值需接入各系统，暂不伪造」）。
 * 因此 [BusArrival.Unknown] / [BusArrival.NoRealtime] / [BusArrival.Suspended]
 * 都只配一句说明文案，不配分钟数。
 */
sealed interface BusArrival {

    /** 车已经站在这一站了（`已到站`）。 */
    data object Arrived : BusArrival

    /**
     * 还有 [stops] 站到这里。
     *
     * ⚠️ 上游在站数后面还带一段括号文案（`2站 [开往 蓝色智谷]`），**这里刻意不保留**：
     * 实测它的含义随线路与方向变（本校站 68 路那条是 `开往 蓝色智谷`，而 68 路的终点是
     * `烟台人才市场`）—— 既不是终点也不像下一站，本机没有第二份数据能核实。
     * 把它显示出来只会与卡片标题的「开往 烟台人才市场」当场打架，
     * 用户看到两个互相矛盾的目的地。宁可不显示，也不猜一个意思给用户看。
     */
    data class StopsAway(val stops: Int) : BusArrival

    /** 首班还没发（`等待发车`，上游同时把 nearnum 置为 [STOP_COUNT_SENTINEL]）。 */
    data object WaitingDeparture : BusArrival

    /** 这条线路在上游**没开实时**（`hasReal = 0`）。 */
    data object NoRealtime : BusArrival

    /** 上游标了停运（`runState = 1`）。 */
    data object Suspended : BusArrival

    /** 上游给了一个我们不认识的写法 —— 原样显示，不猜它的意思。 */
    data class Other(val raw: String) : BusArrival

    /** 有实时能力但这一班没给文案。 */
    data object Unknown : BusArrival
}

/** 到站状态的展示档位：界面只认档位取色，不自己比站数。 */
enum class BusArrivalTone {
    /** 车已到位或就近，用警示色抢注意力。 */
    IMMEDIATE,

    /** 还能等到的正常班次。 */
    NORMAL,

    /** 太远 / 等发车 / 没实时 —— 压成次要信息，别跟「马上到」抢同一个颜色。 */
    MUTED,
}

/**
 * 到站判定与文案规则。全部纯函数、不依赖平台，两端与单测共用。
 */
object BusArrivalRules {

    /**
     * 「马上到」的站数阈值。
     *
     * 与参考实现 5151561/Trackr 的默认到站提醒阈值（2 站）对齐；
     * 实测本校站最近的非到站班次就是 2 站（68 路），所以这个值不会让首屏全红。
     */
    const val IMMINENT_STOPS = 2

    /**
     * 上游用这个值表示「没有可报的站数」（等发车时给的就是它）。
     *
     * ⚠️ 它**不是**「还有 100 站」，任何按站数排序/展示的路径都必须先排除它。
     */
    const val STOP_COUNT_SENTINEL = 100

    /**
     * 把一条线路的四个上游字段归一成 [BusArrival]。
     *
     * 判定顺序是有意的，别调换：
     * 1. **没开实时**排在最前 —— 此时 `neartext` 可能是上一班留下的陈旧文案，
     *    拿它渲染倒计时就是在撒谎；
     * 2. 停运其次 —— 停运时文案也不可信；
     * 3. 才轮到文案本身。
     *
     * 括号里的车辆动态一律丢弃，理由写在 [BusArrival.StopsAway] 上。
     */
    fun parse(neartext: String, nearnum: Int, hasRealtime: Boolean, runState: Int): BusArrival {
        if (!hasRealtime) return BusArrival.NoRealtime
        if (runState != 0) return BusArrival.Suspended
        val head = neartext.substringBefore(' ').trim().ifEmpty { neartext.trim() }
        return when {
            neartext.isBlank() -> BusArrival.Unknown
            head.startsWith("已到站") -> BusArrival.Arrived
            head.startsWith("等待发车") -> BusArrival.WaitingDeparture
            else -> {
                val stops = head.removeSuffix("站").trim().toIntOrNull()
                when {
                    stops != null && stops > 0 -> BusArrival.StopsAway(stops)
                    // 文案读不出站数时，只在站数不是哨兵值的兜底采信它
                    nearnum in 1 until STOP_COUNT_SENTINEL -> BusArrival.StopsAway(nearnum)
                    head.isNotEmpty() -> BusArrival.Other(neartext.trim())
                    else -> BusArrival.Unknown
                }
            }
        }
    }

    fun toneOf(arrival: BusArrival): BusArrivalTone = when (arrival) {
        BusArrival.Arrived -> BusArrivalTone.IMMEDIATE
        is BusArrival.StopsAway -> if (arrival.stops <= IMMINENT_STOPS) BusArrivalTone.IMMEDIATE else BusArrivalTone.NORMAL
        else -> BusArrivalTone.MUTED
    }

    /**
     * 是否该按「临近」强调。
     *
     * 单独留一个函数，是因为「档位」还要区分普通/压暗两档，
     * 而首页/服务页将来只想知道「是不是快到了」这一件事。
     */
    fun isImminent(arrival: BusArrival): Boolean = toneOf(arrival) == BusArrivalTone.IMMEDIATE

    /**
     * 卡片右侧的主文案。
     *
     * ⚠️ 只有真的拿到实时数据时才出现站数；其余状态给的是**说明**，不是估算。
     */
    fun chipText(arrival: BusArrival): String = when (arrival) {
        BusArrival.Arrived -> "已到站"
        is BusArrival.StopsAway -> "${arrival.stops} 站"
        BusArrival.WaitingDeparture -> "等待发车"
        BusArrival.NoRealtime -> "未开通实时"
        BusArrival.Suspended -> "停运"
        BusArrival.Unknown -> "暂无实时"
        is BusArrival.Other -> arrival.raw
    }

    /**
     * 排序权重（小在前）：已到站 → 按站数 → 等发车/读不懂 → 没实时/停运。
     *
     * 停运与未开通实时排最后：它们不回答「下一班几点」，混在能等的班次中间
     * 会让用户按错时间出发。
     */
    fun sortRank(arrival: BusArrival): Int = when (arrival) {
        BusArrival.Arrived -> 0
        is BusArrival.StopsAway -> arrival.stops.coerceIn(1, STOP_COUNT_SENTINEL - 1)
        BusArrival.WaitingDeparture -> STOP_COUNT_SENTINEL
        is BusArrival.Other -> STOP_COUNT_SENTINEL
        BusArrival.Unknown -> STOP_COUNT_SENTINEL
        BusArrival.NoRealtime -> STOP_COUNT_SENTINEL + 2
        BusArrival.Suspended -> STOP_COUNT_SENTINEL + 3
    }

    /** 「开往 火车站」——终点缺失时退回「始发 X」，两头都缺就返回空串（界面那行不画）。 */
    fun directionText(from: String, to: String): String {
        val target = to.trim()
        if (target.isNotEmpty()) return "开往 $target"
        val origin = from.trim()
        return if (origin.isNotEmpty()) "始发 $origin" else ""
    }

    /**
     * 距离文案。[meters] 为负数（上游没给）时返回空串，界面据此不画这一段。
     *
     * 公里段用整数运算取一位小数，避免 commonMain 里依赖格式化器。
     */
    fun distanceText(meters: Int): String = when {
        meters < 0 -> ""
        meters < 1000 -> "$meters 米"
        else -> {
            val decimeters = (meters + 50) / 100
            "${decimeters / 10}.${decimeters % 10} 公里"
        }
    }

    /**
     * 线路名的自然序比较：数字在前且按**数值**比，这样 2 路排在 17 路前面。
     *
     * 纯字符串比较会把「105路」排在「17路」前头，用户按线路号找车时就跟站牌对不上。
     * 返回 Int 而不是排序键，是为了让调用方能直接塞进 `thenComparator` ——
     * `Pair<Int, String>` 不是 Comparable，拿它当 `compareBy` 的第二把尺子编译不过。
     */
    fun compareLineNames(a: String, b: String): Int {
        val digitsA = a.takeWhile { it.isDigit() }
        val digitsB = b.takeWhile { it.isDigit() }
        val numA = digitsA.toIntOrNull()
        val numB = digitsB.toIntOrNull()
        val byNumber = when {
            numA != null && numB != null -> numA.compareTo(numB)
            numA != null -> -1   // 有编号的排在前面，站牌也是这个顺序
            numB != null -> 1
            else -> 0
        }
        return if (byNumber != 0) byNumber else a.compareTo(b)
    }
}
