package com.pocket.xiaoli.ui.treasurebox

// 修课情况页的**树**：一行一个培养方案节点（主修 → 平台 → 模块 → 课程类型），
// 学分、进度条、门次药丸、箭头都在同一行里。单独成文件是因为 `AcademicQueryScreen.kt` 已 1300+ 行。

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.progressSemantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.network.AcademicCourseItem
import com.pocket.xiaoli.network.AcademicTypeInfo
import com.pocket.xiaoli.ui.theme.*
import com.pocket.xiaoli.ui.components.infocard.color

/**
 * 培养方案树：**一行一个节点**，学分 / 进度条 / 门次 / 该类型下的课程全在同一棵树里。
 *
 * ## 为什么合并（2026-10 用户问"课程类型和课程明细为啥是分开的"）
 *
 * 合并前是**两代功能并排**：先有「课程类型学分卡」（回答"我还差多少"），后有「课程明细」
 * （回答"哪几门没修"）。两块画的是**同一棵树**，各有各的展开状态与默认值 ——
 * 收了这个那个还开着，看着像两个不同的东西，还得滚两遍。现在：
 *
 * - **一套展开状态**（`isExpanded`），一个箭头，点哪儿都是"展开/收起这一节点"；
 * - 收起**连子树一起收**：调用方传进来的 `types` 已经是 `visibleAcademicTypes` 裁剪过的；
 * - 类型行同时携带学分与进度（原来只在卡片里）与课程数（原来只在明细表头里）；
 * - 筛选与搜索只需要作用一次。
 *
 * @param parents **有子节点**的 id 集合，必须来自**裁剪前**的完整列表（见 [academicParentIds]）：
 *   现场从 `types` 里找孩子，收起之后的平台会被当成叶子 —— 箭头没了、还多一句"暂无明细"。
 *
 * 抽成 `LazyListScope` 扩展是为了**单测能渲**：整页 `ResultContent` 依赖 ViewModel，
 * 在 Robolectric 里起不来，而"点一行能不能展开/收起"只能靠语义树断言。
 */
internal fun LazyListScope.academicTreeSections(
    types: List<AcademicTypeInfo>,
    courses: List<AcademicCourseItem>,
    parents: Set<String>,
    isExpanded: (String) -> Boolean,
    onToggle: (String) -> Unit,
    hideEmptyHint: Boolean = false,
    /** 平台 id → 构成条段色；空表 = 所有平台退回主色（测试与降级路径都走这条）。 */
    platformAccents: Map<String, ServiceAccent> = emptyMap(),
) {
    // ⚠️ **每个类型都要出行**，哪怕它一门课都没取到 —— 以前"没课就不出行"，
    // 于是明细接口一失败，用户看到的就是"这个类型不见了"，与"类型没解析出来"分不清。
    // ⚠️ 分组用 **id** 不能用名字：培养方案里真的存在同名节点
    //（通识必修与通识选修下各有一个「身心健康类课程」），按名字分会让两组课程串在一起。
    types.forEach { type ->
        val typeCourses = courses.filter { it.typeId == type.id }
        val expanded = isExpanded(type.id)
        // ⚠️ 「学校暂无该类型的课程明细」**只给叶子节点**：
        // 平台/模块**本来就不直接挂课**（课挂在叶子类型上），给它们喊这句是噪音 ——
        // 合并成一棵树之后这一条尤其刺眼（每个父节点下面都挂一句），渲图才看出来的。
        val hasChildren = parents.contains(type.id)
        item(key = "type_${type.id}") {
            AcademicTypeRow(
                type = type,
                courseCount = typeCourses.size,
                expandable = typeCourses.isNotEmpty() || hasChildren,
                expanded = expanded,
                onToggle = { onToggle(type.id) },
                platformAccent = platformAccents[type.id],
                // 模块层借父平台的段色：它自己不进构成条，颜色只能从上一级继承
                //（`parentId` 空串 = 顶层，查不到 → null，正好）
                parentAccent = platformAccents[type.parentId],
            )
        }
        if (typeCourses.isEmpty() && !hideEmptyHint && !hasChildren) {
            item(key = "empty_${type.id}") {
                // ⚠️ 措辞刻意是"学校暂无"而不是"未取到"：实测学校自己的页面上，
                // 「创新创业情况尚未数据信息」这类节点**根本没有明细链接**（`jdkcsx` 为空串的
                // 那几个平台/模块节点也一样）。写成"未取到"会让用户以为这是 App 的故障 ——
                // 用户 2026-10 报的「有的课程明细查不到」有一部分就是这么被误导出来的。
                Text(
                    "（学校暂无该类型的课程明细）",
                    fontSize = 12.sp,
                    color = LocalPocketXiaoliColors.current.textTertiary,
                    modifier = Modifier.padding(
                        start = (LocalSkin.current.spaceSm.value + type.depth * 10).dp,
                        bottom = LocalSkin.current.spaceXs
                    )
                )
            }
        } else if (expanded) {
            // 2026-10-28：一门课一张卡 → **一个类型一张表**。
            // 41 门课原先要滚四屏多，而且每行的学分/成绩/绩点都在不同列位置上，
            // 数字没法横向比；表格把行高压到原来的四成，列固定右对齐。
            item(key = "courses_${type.id}") {
                // 表与它所属的类型行**左对齐**（同缩进）：不然表像是"跳出去"了，
                // 一眼看不出这几门课属于上面那个类型 —— 渲图看出来的。
                Box(modifier = Modifier.padding(start = (type.depth * 10).coerceAtMost(20).dp)) {
                    AcademicCourseTable(typeCourses)
                }
            }
        }
    }
}

/** 树的小标题行：一句说明 + 「展开全部课程 / 收回到平台」 */
/**
 * 培养方案的层级怎么区分（4 级：主修 → 平台 → 模块 → 课程类型）。
 *
 * 只靠缩进是不够的 —— 2026-10 渲了真实 23 个节点看过：四级**字号字重完全一样**，
 * 屏幕上就是"一串往右挪了 12dp 的行"，分不出谁属于谁。所以这里给每一级三样东西：
 *
 * | 层级 | 字号 / 字重 | 颜色 | 容器（底 + 描边） |
 * |---|---|---|---|
 * | 0 主修（总计） | 16sp ExtraBold | 主色 | 无 —— 直接坐在页面底色上，就是这一页的标题 |
 * | 1 平台 | 14.5sp Bold | textPrimary | **段色带**：左深右浅渐变底 + 同方向渐变描边（1.5dp） |
 * | 2 模块 | 13.5sp SemiBold | textPrimary | 极淡同色底（0.07）+ 一圈比平台淡一档的描边（1dp），借**父平台**的段色 |
 *
 * 能展开的行**整块**可点，按压走 MIUIX 的下沉反馈（[rememberSinkIndication]）——
 * 这么大一块上用水波纹会铺满整行，反而脏。
 * | 3 课程类型 | 13sp Normal | textPrimary | 无 |
 *
 * 颜色在这里只回答"**是哪一块**"，不回答"**是哪一级**"——后者由字号、字重和有没有卡片承担。
 * 这么分工的原因：这套配色要跟着 **8 套主题 × 明暗** 走，为四级各造一种"层级色"必然有一半
 * 主题下对比度不达标（`ThemeContrastTest` 一直在守这条）；而"平台段色"是**已有令牌**
 * （[ServiceAccent] 那一套），跟着主题色相转，不需要新造颜色。
 */
private data class HierarchyStyle(
    val fontSize: TextUnit,
    val weight: FontWeight,
    val color: Color?,
    val indentPerDepth: Dp,
)

@Composable
private fun hierarchyStyle(depth: Int): HierarchyStyle {
    val colors = LocalPocketXiaoliColors.current
    // 层级色条不在这儿：它跟着卡片走（见 `AcademicTypeRow` 里那圈描边与底），
    // 只有成卡的两级有框，所以这个表里不需要"多宽 / 什么色"两列。
    return when (depth) {
        0 -> HierarchyStyle(16.sp, FontWeight.ExtraBold, accentColor(), 0.dp)
        1 -> HierarchyStyle(14.5.sp, FontWeight.Bold, colors.textPrimary, 10.dp)
        2 -> HierarchyStyle(13.5.sp, FontWeight.SemiBold, colors.textPrimary, 10.dp)
        else -> HierarchyStyle(13.sp, FontWeight.Normal, colors.textPrimary, 10.dp)
    }
}

/**
 * 数字一律走**等宽字形**（`tnum`）。
 *
 * 树里每一行的「已获 / 要求」都各自右对齐成一条竖轴，而比例字形下「11.1」比「4.0」宽、
 * 「0.0」又比「8.0」窄 —— 轴会随内容左右抖。minSdk 29，`fontFeatureSettings` 全量生效。
 */
private val tabularNumber = TextStyle(fontFeatureSettings = "tnum")

/**
 * 树里的**一行**：名称 + 已获/要求 + 进度条 + 还差/门次 + 展开箭头。
 *
 * 这一行以前是两块东西（卡片里的一行只带学分、明细区的一行只带课程数与箭头），
 * 2026-10 合并成一行 —— 点它同时意味着"展开这一个节点"。
 *
 * ⚠️ `toggleable` 不补最小触摸尺寸（Material 组件才补），本项目底线 44dp，
 * 所以显式写死 `heightIn(min = 44.dp)`；否则手指点不准，而截图看不出来。
 */
@Composable
internal fun AcademicTypeRow(
    type: AcademicTypeInfo,
    courseCount: Int,
    expandable: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    /** 平台层（depth 1）在上方「学分构成」那张条里的段色；其余层级传 null。 */
    platformAccent: ServiceAccent? = null,
    /** 父平台的段色：模块层（depth 2）借它画左侧色条，表示"我挂在上面那个平台底下"。 */
    parentAccent: ServiceAccent? = null,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val style = hierarchyStyle(type.depth)
    // 平台层的段色带跟着它的段色走，与上方构成条对得上
    val platformTint = platformAccent?.let { serviceAccentColor(it) }
    // 模块层自己不进构成条，所以借**父平台**的段色：同一平台的模块是同一个颜色
    val groupTint = parentAccent?.let { serviceAccentColor(it) }
    // 这一行的"身份色"：画在段色带、描边与药丸上 —— 只回答"是哪一块"。
    // 查不到段色（降级路径、单测夹具）退回弱化灰，不硬凑一个颜色。
    val identity = platformTint ?: groupTint ?: colors.textTertiary
    val earned = type.earnedCredit.trim().toFloatOrNull()
    val required = type.requiredCredit.trim().toFloatOrNull()
    val ratio = if (required != null && required > 0f && earned != null) {
        (earned / required).coerceIn(0f, 1f)
    } else null

    // 外壳三档视觉、两个开关就够：「有底」只给平台，「有框」给平台与模块，叶子级裸行。
    val banded = type.depth == 1
    val carded = type.depth == 1 || type.depth == 2
    // 整块（标题行 + 进度条 + 药丸行）就是按钮，所以点击挂在最外面那层而不是标题行上；
    // 能展开的行按下要有回应 —— 本项目统一走 MIUIX 的下沉手感（见 [rememberSinkIndication]），
    // 水波纹在这么大一块上会铺满整行，反而脏。展不开的行（没子节点也没课程）退回纯展示，不弹箭头。
    val sink = rememberSinkIndication()
    val pressSource = remember { MutableInteractionSource() }
    val clickable = if (expandable) {
        Modifier.toggleable(
            value = expanded,
            role = Role.Button,
            interactionSource = pressSource,
            indication = sink,
            onValueChange = { onToggle() },
        )
    } else {
        Modifier
    }
    val body: @Composable ColumnScope.() -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // 层级色条不在这一行里：成卡两级的身份由**整圈描边 + 底**承担（见下面的外壳），
            // 左边那根满高色脊 2026-11-04 按用户要求撤掉了。
            Text(
                type.name,
                fontSize = style.fontSize,
                fontWeight = style.weight,
                color = style.color ?: colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            // 「已获」与「/ 要求」拆成**两列各自右对齐**：改造前是一串流式文本，
            // 每行数字的起点都不一样，扫读时找不到竖线；右对齐后整页数字钉在两条轴上。
            // 数字一律等宽（见 [tabularNumber]）—— 比例字形下「11.1」比「4.0」宽，右轴会抖。
            Text(
                text = type.earnedCredit.ifBlank { "—" },
                style = tabularNumber,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                textAlign = TextAlign.End,
                modifier = Modifier.width(46.dp),
            )
            Text(
                text = "/",
                style = tabularNumber,
                fontSize = 12.sp,
                color = colors.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(10.dp),
            )
            // 要求学分是 `0.0` 时学校说的是"这块不设要求"（真实数据：其他选修课程
            // 已获 4.0 / 要求 0.0）。照抄成「4.0 / 0.0」会被读成 0% —— 与事实正好相反。
            Text(
                text = when {
                    type.requiredCredit.isBlank() -> "—"
                    (type.requiredCredit.trim().toFloatOrNull() ?: 1f) <= 0f -> "不限"
                    else -> type.requiredCredit
                },
                style = tabularNumber,
                fontSize = 12.sp,
                color = colors.textTertiary,
                textAlign = TextAlign.End,
                modifier = Modifier.width(40.dp),
            )
            if (expandable) {
                Spacer(Modifier.width(skin.spaceSm))
                // 箭头收进一枚圆底：它是个控件，不该跟文字抢重量。
                // 圆底用 `surfaceHighlight`（比页面底色重一档，又压不过数字）；
                // 成卡的两级再掺一档身份色 —— 展开键长在谁的框里，就认谁的颜色
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(
                            if (carded) lerp(colors.surfaceHighlight, identity, 0.16f)
                            else colors.surfaceHighlight
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展开",
                        tint = if (carded) lerp(colors.textSecondary, identity, 0.45f) else colors.textTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        // 进度条**每一级都画**（只要拿得到「要求学分」当分母）。
        //
        // ⚠️ v0.1.18 曾限制成"只画到模块级"，理由是"13 个课程类型各来一条横杠会把层级压平"。
        // 但那次同时加的字号阶梯 + 左侧层级色条已经承担了层级，进度条不必再让位；
        // 而"这块修满没有"恰恰是这一页最该一眼看到的东西（用户直接问过"为啥有的类型没有进度条"）。
        // 叶子级 2026-10-29 从 3dp 加粗到 5dp（用户："各类课程进度条可以加粗"）——
        // 3dp 在 5dp 的模块条旁边细到几乎看不见，"这一类修了多少"反而最不好读。
        //
        // ⚠️ 分母缺失（要求学分为空 / 0）时**仍然不画**：没有分母就谈不上进度，
        // 补一条 0% 或 100% 都是**编数据**（真实数据里「其他选修课程」正是这种：
        // `已获 4.0 / 要求 0.0`）。
        //
        // ⚠️ 但**分子为 0 一定要画**（0% 的条）：那是"这门模块一门都没过"，
        // 是这一页最该被看见的状态。曾经因为"学分 0 被当缺失"整条不画 ——
        // 用户报过「为零分的课程类型没有进度条」（见 normalizeCredit 的 KDoc）。
        // 0% 时画面上只剩轨道，所以轨道色要用**看得见**的那一档
        //（`surfaceHighlight` 在页面底色上几乎不可见，等于没画）。
        if (ratio != null) {
            Spacer(Modifier.height(skin.spaceSmall))
            // 填充色三档（2026-10-29 按用户要求重排）：
            //   · **修满 → 绿**：这块过没过，颜色直接给结论，压过下面两档；
            //   · **平台层 → 金属金**：金色原先给"主修"总计那条，但总计本来就是全页字号最大、
            //     字重最重的一行，不需要再靠颜色抢；平台是这一页真正要横向对比的几块，
            //     给它们同一档金属色，"看到金色就是平台级"反而成了层级信号。
            //   · **其余 → 主色**。
            val metallic = type.depth == 1
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressTrack(
                    modifier = Modifier.weight(1f),
                    progress = ratio,
                    height = when {
                        // 总计最厚（8dp）：金属质感靠"高光 + 阴影 + 渐变"三层，条太薄撑不住
                        //（渲图对比过 —— 5dp 上那 1dp 高光几乎看不见，金属感出不来）
                        type.depth == 0 -> 8.dp
                        metallic -> 7.dp
                        else -> 5.dp
                    },
                    brush = when {
                        ratio >= 1f -> Brush.horizontalGradient(listOf(colors.success, colors.success))
                        metallic -> Brush.horizontalGradient(listOf(MetallicGoldStart, MetallicGoldEnd))
                        else -> Brush.horizontalGradient(listOf(accentColor(), accentColor()))
                    },
                    track = when {
                        // 0% 时画面上只剩轨道：纯 `borderDivider` 在深色主题下是一条几乎看不见
                        // 的暗线（等于没画），掺一档身份色既看得见、又认得出它属于哪一支
                        ratio <= 0f -> lerp(colors.borderDivider, identity, 0.25f)
                        // 平台条的轨道也染一档身份淡调：整条（实心 + 缺口）都属这一块，
                        // 缺口段不至于变成"一根条上挖了个白洞"
                        platformTint != null -> lerp(colors.surfaceHighlight, platformTint, 0.22f)
                        else -> colors.surfaceHighlight
                    },
                    // 金属质感只给平台层：它要"像一条实心金属"，其余层级仍是平面填充
                    //（一页里十几条立体阴影会变成噪音）。
                    highlight = metallic,
                )
                // 差额写在条的**右端**（改造前它是一枚独占一行的药丸）：一个节点少一行高，
                // 真实 24 节点的树省下来的是将近一屏。判档规则在 `creditGap`，
                // 与「学分构成」那张卡共用 —— 超修 / 还没开始 这两件事两处都得说得出。
                creditGap(type.earnedCredit, type.requiredCredit)?.let { gap ->
                    Spacer(Modifier.width(skin.spaceSm))
                    Text(
                        gap.label,
                        style = tabularNumber,
                        fontSize = 11.sp,
                        color = creditGapColor(gap.kind),
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(62.dp),
                    )
                }
            }
        }
        // 门次的缺口按**数值**判 0，不按字符串：解析层会保留 `0`（那是学校给的结论），
        // 字符串比较 `!= "0"` 会漏掉它 ⇒ 门次够了还喊"还差 0 门"。
        // ⚠️ 学分的缺口**不再做药丸**：这一行标题已经摆着「49.5 / 53.5」、条的右端又写着
        // 「差 4.0」，同一件事说三遍就是用户反复报的那种重复。药丸只留门次这一档 ——
        // 它是另一个数（要求门次 vs 到达门次），标题行里没有。
        val missedGates = type.missedCount.toIntOrNull() ?: 0
        val pills = buildList {
            if (missedGates > 0) add("还差 $missedGates 门")
        }
        // 门次 / 课程门数这类**上下文**仍然是弱化小字：它们回答"为什么够/不够"，
        // 而不是"我该做什么"，与药丸分工明确。
        // ⚠️ `includeShortfall = false`：「还差 N 门」已经在药丸里了，
        // 这里再说一遍就是同一句话出现两次（渲图抓到的）。
        val countLine = type.countSummary(includeShortfall = false)
        // 药丸与上下文**共用一行**（2026-10-28）：改造前它们各占一行，
        // 一个类型节点从标题到脚注要四行，23 个节点滚不到底。
        // 现在左边是"该做什么"、右边是"为什么"，一行读完。
        if (pills.isNotEmpty() || countLine.isNotBlank()) {
            Spacer(Modifier.height(skin.spaceXs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                pills.forEachIndexed { index, text ->
                    if (index > 0) Spacer(Modifier.width(skin.spaceSm))
                    ShortfallPill(text, tint = if (carded) identity else null)
                }
                if (countLine.isNotBlank()) {
                    // 有药丸时把上下文推到行尾；没有药丸时它自己就是这一行的主角，留在左边
                    if (pills.isNotEmpty()) Spacer(Modifier.weight(1f))
                    Text(
                        countLine,
                        fontSize = 11.sp,
                        color = colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
    // 缩进与上间距留在最外面：色带不该把页面的左边距一起吃掉
    Column(
        modifier = Modifier
            .padding(start = (type.depth * style.indentPerDepth.value).dp)
            // 每组之间留一口气：平台带是"一组的开始"，它上面比其他行多留 6dp，
            // 带的分组感靠间距成立，不靠框
            .padding(top = if (banded) 14.dp else if (carded) 8.dp else 6.dp)
    ) {
        // 一个 Column 三种外壳：**平台 = 容器**（段色带 + 左深右浅的渐变描边，1.5dp），
        // **模块 = 容器里的条目**（极淡同色底 + 一圈淡描边，1dp），叶子级什么都不加。
        // 2026-11-04 用户先要模块也描边、再要"边框和内容好看一点"、最后说**左侧那根亮色线不要**：
        // 脊撤掉之后，两档的差别由「铺不铺底 + 描边浓淡 + 描边粗细」承担（1.5dp vs 1dp 顶掉
        // 原来 5dp vs 2dp 那根脊的作用），层级照样看得出来。
        // ⚠️ 更早那一版给平台与模块各套一张**同权重**的卡，一屏十几个框、层级被框抹平；
        // 所以模块的框始终比平台淡一档（掺卡底 0.66 vs 渐变 0.28→0.66）、圆角更小（10 vs 14）、
        // 底只垫 0.07（平台 0.24→0.07）。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    when {
                        banded -> Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        // 底比 2026-10-29 那版重一档（0.18 → 0.24）：
                                        // 再淡就淡到与"没底"只差一点，读不出这是一块
                                        lerp(colors.surfaceCard, identity, 0.24f),
                                        lerp(colors.surfaceCard, identity, 0.07f),
                                    )
                                )
                            )
                            // 描边也走左深右浅，与底同一个方向：框像是色带自己长出来的边，
                            // 而不是后贴上去的一圈线（纯段色在浅色主题下会描成贴纸）。
                            // 1.5dp：脊撤掉后，"平台更重"这件事由框的粗细说
                            .border(
                                1.5.dp,
                                Brush.horizontalGradient(
                                    listOf(
                                        lerp(identity, colors.surfaceCard, 0.28f),
                                        lerp(identity, colors.surfaceCard, 0.66f),
                                    )
                                ),
                                RoundedCornerShape(14.dp),
                            )
                        carded -> Modifier
                            .clip(RoundedCornerShape(10.dp))
                            // 给淡描边垫一层极淡的同色底：框悬在白底上像没画完的草稿，
                            // 垫了底才读得出"这是一条"，又不至于抢平台的段色带
                            .background(lerp(colors.surfaceCard, identity, 0.07f))
                            // 模块的框只说"这条有边界"，不说"这是一块"
                            .border(1.dp, lerp(identity, colors.surfaceCard, 0.66f), RoundedCornerShape(10.dp))
                        else -> Modifier
                    }
                )
                .then(clickable)
                .heightIn(min = 44.dp)
                // 成卡的两级内容不贴框；叶子级没有框，也就不吃这份内边距
                .then(if (carded) Modifier.padding(horizontal = skin.spaceMd) else Modifier)
                .then(
                    when {
                        banded -> Modifier.padding(vertical = skin.spaceSmall)
                        // 模块有了框，内容就不能贴边：比平台少一档，框矮一点才压不过平台
                        carded -> Modifier.padding(vertical = skin.spaceXs)
                        else -> Modifier
                    }
                ),
            content = body,
        )
    }
}

/**
 * 进度条（自绘，替代 `LinearProgressIndicator`）。
 *
 * ## 为什么要自绘
 *
 * `LinearProgressIndicator` 只吃**单色**，而总进度要按用户要求做成
 * **`#FFD700 → #F9A825` 的线性渐变 + 白色高光边缘 + 轻微阴影**的金属质感
 *（"金属金"没法用一个 `Color` 表达）。渐变、高光、阴影三样它都不支持。
 *
 * ## ⚠️ 不能把语义丢了
 *
 * `LinearProgressIndicator` 顺带提供了 `ProgressBarRangeInfo` 语义（读屏会念"进度 56%"），
 * 自绘时必须自己补 [progressSemantics] —— 否则**读屏用户完全不知道这条是什么**，
 * 而这件事在任何截图与像素比对里都看不出来。守门人：
 * `AcademicQueryPrivacyTest.已获得学分为 0 的模块也要有进度条`
 *（它断言 `ProgressBarRangeInfo(0f, 0f..1f)`）。
 *
 * [highlight] 为真时画三样金属细节，全部**不透明**（用户明确要求"去掉任何降低透明度的属性"）：
 * ① 顶部 1dp 的实心近白高光带；② 底部 1dp 的暗端压边；③ 轻微 `shadow`。
 */
@Composable
private fun ProgressTrack(
    progress: Float,
    height: Dp,
    brush: Brush,
    track: Color,
    highlight: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // 端头收全圆：条加粗到 5~8dp 以后，固定 3dp 圆角看着像"两头没削平的方木条"
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .then(if (highlight) Modifier.shadow(1.5.dp, shape) else Modifier)
            .clip(shape)
            .background(track)
            .progressSemantics(progress)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(brush)
        ) {
            if (highlight) {
                // 高光/压边用**实心色**而不是半透明白：半透明会被底下的渐变透上来，
                // 金属感反而糊掉（用户也明确要求不要透明度）。
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MetallicGoldHighlight)
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MetallicGoldEnd)
                    )
                }
            }
        }
    }
}

/**
 * 「还差 X」药丸：淡底 + 同色字。
 *
 * [tint] 给这一行的身份色（成卡的平台/模块级），药丸就长在卡里而不是浮在外面；
 * 不传（叶子级没有身份色可借）退回主色。
 *
 * ⚠️ 底色用低透明度而**不是** `danger`：学分没修满是**正常进度状态**，
 * 不是错误（本项目在 `ErrorCard` 那处专门记过"danger 低透明度在深色下几乎不可见"的坑，
 * 而这里用主色既看得见、又不会把正常的学业进度说成故障）。
 *
 * 注：这里的半透明底**不是**用户要求去掉的那类 —— 他指的是金属金条
 *（"去掉任何降低透明度的属性"紧跟在高光/阴影那句后面）。药丸的淡底是**文字的背景**，
 * 换成不透明色就意味着要为每套主题各配一个淡色底，反而更难维护；
 * 而金色元素本身（条、环、色条）现在都是**全不透明**的。
 */
@Composable
private fun ShortfallPill(text: String, tint: Color? = null) {
    val skin = LocalSkin.current
    val accent = tint ?: accentColor()
    Surface(color = accent.copy(alpha = 0.13f), shape = RoundedCornerShape(50)) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = accent,
            modifier = Modifier.padding(horizontal = skin.spaceSm, vertical = 2.dp),
        )
    }
}
