package com.pocket.xiaoli.data.electricity

import kotlin.math.floor
import kotlin.math.max

/**
 * 宿舍电费的**用量统计**：从本机每日余额采样里推出日均花费与"还能用几天"。
 *
 * ## 为什么不能直接画"用电量"
 *
 * 后勤缴费系统那个页面只给三样东西：用户编号、用户地址、**实时余额**
 * （见 [DormElectricityRepository.parse]，字段名与真实页面一致）。
 * 没有抄表记录、没有日用电量、没有充值流水，也没有可查的历史接口。
 * 所以任何"你这个月用了多少度"的图都只能是编的 —— 本项目不许拿模拟数据进产物
 * （`memory/01-constraints.md` §2.4）。
 *
 * 能诚实做的只有一件：**自己攒采样**。每次查到余额就按天记一条（只在本机，不上传），
 * 于是"两个采样之间掉的钱"就是这段时间用掉的电费。这句话的每个限定词都要成立：
 *
 * - 中间**充过值**的那一段必须扔掉（余额上涨不可能来自用电，算进去会变成"这周用了 -30 元"）；
 * - 采样稀疏时日均只是**平均**，不是每日曲线，界面就这么写；
 * - 一条线都画不出来的时候（只有一两条记录），要说"还看不出来"而不是画个点充数。
 *
 * ## 为什么这一整套是纯函数
 *
 * 上面三条规则每一条都能构造数据测到，而且必须被测到 —— 它们错了不会崩，
 * 只会**显示出一个看起来很可信的错数字**，而用户会照着它决定要不要充值。
 * 所以模型与算法都放这里（无 Android 依赖），存储与画图只是它的两个消费者。
 */

/** 一条采样：某一天（本地时区的 epochDay）记下的余额，单位元。 */
data class BalanceSample(
    val epochDay: Long,
    val balance: Double,
)

/** 统计结果的口径：默认看最近 30 天的采样。 */
const val USAGE_WINDOW_DAYS = 30

/** 小于这个差值不当成"有钱进出"，避免上游小数位抖动被误判成充值。 */
private const val MONEY_EPSILON = 0.005

/**
 * 一次统计的全部输出。字段都可空 ——
 * **null 的含义是"算不出来"，不是 0**，界时必须把这两种情况分开显示。
 *
 * @param points 画图的点（按天升序，同一天只留最后一条，且已裁到窗口内）
 * @param observedDays 窗口内实际有几天记录
 * @param spanDays 窗口内第一条到最后一条跨了几天（`points.size >= 2` 时至少 1）
 * @param rechargeDays 疑似充值的日期（余额上涨），这些**相邻两段都不参与日均**
 * @param perDayYuan 日均花费：所有"没充值"的相邻段加起来 花掉的钱 / 天数
 * @param daysUntilEmpty 按日均，当前余额还能用几天（算不出为 null）
 * @param daysUntilThreshold 按日均，几天后会跌破低余额提醒线；余额已经低于线时为 0
 */
data class UsageStats(
    val points: List<BalanceSample> = emptyList(),
    val observedDays: Int = 0,
    val spanDays: Int = 0,
    val rechargeDays: List<Long> = emptyList(),
    val perDayYuan: Double? = null,
    val daysUntilEmpty: Int? = null,
    val daysUntilThreshold: Int? = null,
    /**
     * 最后一条采样的余额**已经在提醒线以下**。
     *
     * 为什么单独一个布尔而不是"天数 = 0"：0 有两种完全不同的意思 ——
     * "已经越线了"和"还没越线，但不到一天就要越线"。
     * 截图验收时抓到过一次把两者混起来：余额 14 元、线在 10 元，
     * 卡片却写着「已经低于提醒线」，而同一屏的大字是 14.00 元 —— 自己跟自己矛盾。
     */
    val belowLowBalance: Boolean = false,
) {
    /** 至少两个点、且跨了一天以上，才谈得上"走势"。 */
    val plottable: Boolean get() = points.size >= 2 && spanDays >= 1

    /** 图上纵轴的范围（含提醒线时为提醒线），不足两点时为 null。 */
    fun valueRange(extra: List<Double> = emptyList()): Pair<Double, Double>? {
        if (!plottable) return null
        val values = points.map { it.balance } + extra
        val low = values.min()
        val high = values.max()
        // 一条水平线（余额整月不动）也要有高度可画，否则 Canvas 里除以 0。
        return if (high - low < MONEY_EPSILON) {
            Pair(low - 1.0, high + 1.0)
        } else {
            Pair(low, high)
        }
    }
}

/**
 * 把采样算成统计。
 *
 * @param samples 未排序、可能含重复日期、可能含别的宿舍（调用方负责按认人键分好）
 * @param todayDay 今天的 epochDay —— **参数进来**而不是读系统时钟：
 *   快照基线要能钉住"今天是几号"，否则图每天都会红（`memory/04-ui-verification.md` 那条）
 * @param thresholdDays 只统计最近这么多天
 * @param lowBalanceThreshold 低余额提醒线（真源只有一个：`ProactiveRules.LOW_BALANCE_ELECTRICITY`）
 */
fun usageStatsOf(
    samples: List<BalanceSample>,
    todayDay: Long,
    thresholdDays: Int = USAGE_WINDOW_DAYS,
    lowBalanceThreshold: Double = 0.0,
): UsageStats {
    if (samples.isEmpty()) return UsageStats()

    // 同一天多次查询只留最后一条（一天里的余额只会往下走，除了充值）。
    val latestPerDay = samples
        .sortedBy { it.epochDay }
        .associateBy { it.epochDay }
        .values
        .sortedBy { it.epochDay }

    val windowStart = todayDay - thresholdDays + 1L
    val points = latestPerDay.filter { it.epochDay >= windowStart }
    if (points.isEmpty()) return UsageStats(observedDays = 0)

    val spanDays = if (points.size >= 2) (points.last().epochDay - points.first().epochDay).toInt() + 1 else 0

    val rechargeDays = mutableListOf<Long>()
    var spent = 0.0
    var spentDays = 0L
    for (index in 1 until points.size) {
        val from = points[index - 1]
        val to = points[index]
        val delta = to.balance - from.balance
        val gapDays = max(1L, to.epochDay - from.epochDay)
        when {
            // 余额上涨 = 中间充过值。这段的差值与用电无关，两端都不能进日均。
            delta > MONEY_EPSILON -> rechargeDays += to.epochDay
            // 掉钱了：这是真实消耗。跨了几天就摊到几天。
            delta < -MONEY_EPSILON -> {
                spent += -delta
                spentDays += gapDays
            }
            // 一模一样：整段没用也没充，**照样要进分母** ——
            // 否则放假两周后返校，日均会因为这两周被跳过而虚高，"还能用 3 天"会把人吓去充值。
            else -> spentDays += gapDays
        }
    }

    val perDay = if (spentDays > 0L) spent / spentDays else null
    val current = points.last().balance

    return UsageStats(
        points = points,
        observedDays = points.size,
        spanDays = spanDays,
        rechargeDays = rechargeDays,
        perDayYuan = perDay?.takeIf { it >= 0.0 },
        daysUntilEmpty = projectedDays(current, perDay),
        daysUntilThreshold = if (current + MONEY_EPSILON < lowBalanceThreshold) {
            0
        } else {
            projectedDays(current - lowBalanceThreshold, perDay)
        },
        belowLowBalance = current + MONEY_EPSILON < lowBalanceThreshold,
    )
}

/**
 * "几天后"那句话的说法。**不足一天要说"不到 1 天"而不是"约 0 天"** ——
 * 向下取整是为了不吹满话（说 4 天而第 4 天中途断电），
 * 但取整到 0 之后必须换个说法，否则读起来像"现在就没电了"。
 */
fun daysPhrase(days: Int?, alreadyBelow: Boolean = false): String = when {
    alreadyBelow -> "已经低于提醒线"
    days == null -> "算不出（没有稳定用量）"
    days == 0 -> "不到 1 天"
    else -> "约 $days 天"
}

/**
 * 余额（或差额）按日均还能撑几天。**向下取整**是刻意的：
 * 说"还能用 5.7 天"会让人以为第 6 天还有电，而真实情况是第 6 天中途就没电了。
 * 日均为 0（看不出用量）时返回 null —— 那比报一个"无限天"诚实。
 */
private fun projectedDays(amount: Double, perDay: Double?): Int? {
    if (perDay == null || perDay <= MONEY_EPSILON) return null
    if (amount <= 0.0) return 0
    return floor(amount / perDay).toInt()
}

/**
 * 把采样归一化成画图坐标：x ∈ [0,1] 按天铺开，y ∈ [0,1] **自上而下**（1 = 最低值贴底）。
 *
 * ## 为什么这段数学要放在纯逻辑里而不是 Canvas 内
 *
 * 画图最容易出的不是画歪，而是**在没数据的时候画出一条像样的线**：
 * 只有一个点、所有值都一样（余额整月不动）、纵轴跨度为 0 这三种情况，
 * 直接算就会得到 NaN 或一条贴底的直线，而贴底的线读起来正好就是"钱用光了"。
 * 所以这里把三种情况都收敛成 `null`（= 不画），并且能在 JVM 单测里逐条钉住。
 *
 * @param extraLine 要一起进纵轴范围的水平线（低余额提醒线）；它落在范围外时会把范围撑开，
 *   否则"提醒线"会被裁掉，用户看不到自己离那条线还有多远。
 */
fun trendCoordinates(
    stats: UsageStats,
    extraLine: Double? = null,
): List<Pair<Float, Float>>? {
    if (!stats.plottable) return null
    val range = stats.valueRange(listOfNotNull(extraLine)) ?: return null
    val (low, high) = range
    val firstDay = stats.points.first().epochDay
    val lastDay = stats.points.last().epochDay
    val daySpan = (lastDay - firstDay).toDouble()
    if (daySpan <= 0.0) return null
    val valueSpan = high - low
    return stats.points.map { sample ->
        val x = ((sample.epochDay - firstDay) / daySpan).toFloat()
        val y = (1.0 - (sample.balance - low) / valueSpan).toFloat()
        Pair(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
    }
}

/** 提醒线在图上的 y 坐标（与 [trendCoordinates] 同一套归一化），画不出图时为 null。 */
fun trendLineY(line: Double, stats: UsageStats): Float? {
    if (!stats.plottable) return null
    val (low, high) = stats.valueRange(listOf(line)) ?: return null
    return (1.0 - (line - low) / (high - low)).toFloat().coerceIn(0f, 1f)
}

/**
 * 画不出来 / 算不出来的时候该说的那句实话。null = 一切正常，可以画图。
 *
 * 这条函数存在的理由：数据不够时最省事的做法是把空图渲染成一条贴底的线，
 * 用户看不出那是在说"我不知道"。所以"不知道"必须有**一句人话**，而不是一个形状。
 */
fun usageNotice(stats: UsageStats): String? = when {
    stats.points.isEmpty() -> "本机还没有这间宿舍的余额记录，查一次之后才开始攒"
    stats.points.size == 1 -> "已有 1 天记录，再查一天就能看到余额变化"
    !stats.plottable -> "同一天里的多次查询只算一条记录，隔一天再查就能画出走势"
    stats.perDayYuan == null -> "这段时间看不出用量（可能没用电，也可能只在同一天里查过）"
    else -> null
}

/**
 * 卡里的三行数字。**纯函数**返回 label/value，为的是这几句话能被单测逐字钉住 ——
 * 它们是最容易写错成"看起来像读数"的地方（比如日均算不出来时报 0 元/天，
 * 那等于宣布这间宿舍不耗电）。
 */
internal fun usageStatLines(usage: UsageStats, threshold: Double): List<Pair<String, String>> =
    listOf<Pair<String, String>>(
        "日均花费" to (usage.perDayYuan?.let { "约 %.2f 元 / 天".format(it) } ?: "看不出来"),
        "还能用" to daysPhrase(usage.daysUntilEmpty),
        "距提醒线 %.0f 元".format(threshold) to daysPhrase(usage.daysUntilThreshold, usage.belowLowBalance),
    )

/**
 * 脚注：天数、窗口、充值怎么处理 —— 一句话把口径说完。
 *
 * ⚠️ 这里说「N 天的余额记录」而不是「最近 N 天」：`observedDays` 是**有记录的天数**，
 * 中间可能断了好几天（没查就没有），写成"最近 8 天"会让人以为这 8 天是连续的。
 */
internal fun usageFootnote(usage: UsageStats): String {
    val base = "按本机 ${usage.observedDays} 天余额记录推算（最近 $USAGE_WINDOW_DAYS 天窗口），不是后勤系统给的数据"
    val recharges = usage.rechargeDays.size
    return if (recharges > 0) {
        "$base；期间有 $recharges 天余额上涨（充值），这些天不计入用量"
    } else {
        base
    }
}
