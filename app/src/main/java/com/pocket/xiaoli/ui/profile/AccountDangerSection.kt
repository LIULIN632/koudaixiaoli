package com.pocket.xiaoli.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.LocalProfileRowPadding

/**
 * 销毁性操作（危险区）的三种零件：待确认的动作、一行操作、整组卡片。
 *
 * ## 为什么单独一个文件
 *
 * 1. **`AccountBindingScreen.kt` 已经超过 1400 行**（优化清单 P2-1 的"巨型文件"清单里），
 *    而这一簇是**可以整块拿走**的：它只依赖主题令牌与 `BindingSectionCard`，
 *    不碰页面那二十来个 `remember` 状态。搬走之后主文件只剩"结构 + 表单"。
 * 2. **它们跨越了"主页面 / 二级页"两处**：`DangerRow` 既是主页面上那个「账号管理」**入口**，
 *    也是组内那四条**执行**行 —— 放在一起才看得出"入口有框、执行无框"这条区别是刻意的。
 *
 * ⚠️ 这一簇的规则（**不要**在别处另写一份）：
 *
 * - **不可恢复的动作必须二次确认**（`PendingDanger` + 主页面那个共用确认框）
 * - **不要用 danger 低透明度当底色**，用 35% 描边（`CLAUDE.md` 记过：深色下那点红偏移约 2 个色阶）
 * - **副标题用 `textSecondary`**（与设置页 `DangerSettingsItem` 同款）
 */

/**
 * 一条**待二次确认**的销毁性操作。
 *
 * ## 为什么要有这个类型（2026-10）
 *
 * 改造前这一页只有 `showUnbindConfirm: Boolean` —— 也就是**只有「解绑教务」会问一句**，
 * 而同一张卡里的另外三条（退出今日校园登录态 / 删除今日校园账号 / 删除校园卡账号）
 * **点了就执行、不可恢复**。本页自己的注释还写着"只搬家，没加二次确认
 * （改造前没有的现在也没有）"—— 那是**把历史遗留当成了设计**。
 *
 * 这一页的标题就叫「账号管理」，副标题写着"销毁性操作，删了不可恢复"：
 * 既然页面已经这样承诺了，四条就必须一致。所以把布尔换成"待确认的那一条"，
 * 文案与动作都随行携带，将来加第五条也只是多一个 [PendingDanger]。
 */
internal data class PendingDanger(
    /** 对话框标题，直接用那一行的 title。 */
    val title: String,
    /** 那一行下的说明（"只清理本机的登录 Cookie，账号还在"）。 */
    val rowSubtitle: String,
    /** 确认框里说清"会发生什么" + 一句确认问句。 */
    val message: String,
    val confirmLabel: String,
    val onConfirm: () -> Unit,
)

/**
 * 「账号管理」这一组**该出现哪几条**：条件与文案都收在这一个函数里。
 *
 * 2026-10-26 抽出来的原因很具体：加"退出学习通登录态"这条之前，
 * 行文案在**生产代码与快照测试里各写了一份**（测试注释还写着"行文案与真实条件分支一致"，
 * 靠人工维持一致）。搬到这儿之后测试直接调这个函数，改文案只需改一处。
 */
internal data class AccountDangerStates(
    /** 校区名，进「解绑教务」那条的文案。 */
    val campusName: String,
    val jwBound: Boolean,
    /** null = 还没查过；只有确认已登录（true）才给"退出登录态"那条。 */
    val portalLoggedIn: Boolean?,
    val casBound: Boolean,
    val cardBound: Boolean,
    val chaoxingBound: Boolean,
) {
    /** 「账号管理」入口行在不在 —— 一条都没有时整组都不该露出来。 */
    val hasManageableAccount: Boolean
        get() = jwBound || portalLoggedIn == true || casBound || cardBound || chaoxingBound
}

internal fun accountDangerRows(
    states: AccountDangerStates,
    onUnbindJw: () -> Unit,
    onClearCasLogin: () -> Unit,
    onDeleteCas: () -> Unit,
    onDeleteCard: () -> Unit,
    onClearChaoxingLogin: () -> Unit,
): List<PendingDanger> = buildList {
    if (states.jwBound) {
        add(
            PendingDanger(
                title = "解绑教务账号",
                rowSubtitle = "删除本机保存的「${states.campusName}」学号与密码",
                message = "将删除 ${states.campusName} 在本机保存的学号和密码，确定吗？",
                confirmLabel = "确认解绑",
                onConfirm = onUnbindJw,
            )
        )
    }
    if (states.portalLoggedIn == true) {
        add(
            PendingDanger(
                title = "退出今日校园登录态",
                rowSubtitle = "只清理本机的登录 Cookie，账号还在",
                message = "将清理本机保存的今日校园登录 Cookie，账号与密码仍留在本机。确定吗？",
                confirmLabel = "确认退出",
                onConfirm = onClearCasLogin,
            )
        )
    }
    if (states.casBound) {
        add(
            PendingDanger(
                title = "删除今日校园账号",
                rowSubtitle = "删除本机保存的学号与密码",
                message = "将删除本机保存的今日校园学号与密码（教务账号不受影响）。确定吗？",
                confirmLabel = "确认删除",
                onConfirm = onDeleteCas,
            )
        )
    }
    if (states.cardBound) {
        add(
            PendingDanger(
                title = "删除校园卡账号",
                rowSubtitle = "删除本机保存的手机号与密码",
                message = "将删除本机保存的易校园手机号与密码，校园余额页随即不可用。确定吗？",
                confirmLabel = "确认删除",
                onConfirm = onDeleteCard,
            )
        )
    }
    // 学习通这条原先挂在中间面板的红字按钮上（点了就清、没有确认）；
    // 面板删掉后归到这里 —— 同为"退出登录态"，就该过同一层二次确认。
    // （智慧树 2026-10-26 撤出绑定页：它存的 Cookie 没有任何消费者，所以既不给登录入口也不给退出项。）
    if (states.chaoxingBound) {
        add(
            PendingDanger(
                title = "退出学习通登录态",
                rowSubtitle = "只清理本机的 Cookie，账号还在",
                message = "将清理本机保存的学习通登录 Cookie，作业同步随即不可用（已导入待办的作业不受影响）。确定吗？",
                confirmLabel = "确认退出",
                onConfirm = onClearChaoxingLogin,
            )
        )
    }
}

/**
 * 「账号管理」整组：区块卡 + 若干销毁性操作行（行间分隔线，**不**逐条描边）。
 *
 * 抽成独立组件不只是为了复用，更是为了**让快照能渲生产代码本体**：
 * `AccountBindingScreen` 的第一个参数是 `AppContainer`（单测里造不出来），
 * 页面上的块必须能单独渲。否则快照只能渲"测试里另写的一份仿制品" ——
 * 那正是 `UI验收手册.md` 坑 2 记下的"预览与实际漂移"。
 */
@Composable
internal fun AccountDangerCard(
    rows: List<PendingDanger>,
    onPick: (PendingDanger) -> Unit,
    // false 时整张卡不渲染（理由见 BindingStatusCard 的同名参数）。
    visible: Boolean = true,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    BindingSectionCard(
        visible = visible,
        icon = Icons.Filled.Warning,
        title = "账号管理",
        subtitle = "销毁性操作，删了不可恢复",
        // 危险组：图标取 danger 色、衬底用中性色（**不能**用 danger 低透明度当底，
        // 深色下那点红只偏移约 2 个色阶 —— 见 CLAUDE.md）。
        danger = true,
    ) {
        // ⚠️ 内层再套一个 Column：外层的内容列按 `spaceMd` 排间距，若让分隔线直接做外层的
        // 子项，每道线两侧各得一份 14dp —— 于是线"浮"在两段文字中间（实测约 53dp 的间隔），
        // 读起来像四块而不是一份列表。套一层之后行与线相邻，分隔线才回到"列表分隔"的本义。
        Column {
            rows.forEachIndexed { index, item ->
                DangerRow(
                    title = item.title,
                    subtitle = item.rowSubtitle,
                    onClick = { onPick(item) },
                    boxed = false,
                )
                if (index != rows.lastIndex) {
                    HorizontalDivider(color = colors.borderDivider)
                }
            }
            // 一句底线说明：这些操作只动本机，不会去改学校的密码。
            // 用户站在"删除"按钮前最需要的正是这句 —— 没有它，删账号看起来像把学号注销了。
            Text(
                "以上操作只清理本机保存的数据，不会修改学校教务 / 智慧校园的密码。",
                modifier = Modifier.padding(top = skin.spaceSm),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = colors.textSecondary,
            )
        }
    }
}

/**
 * 危险操作行：danger 描边 + danger 文字。
 *
 * 为什么用**描边**而不是"低透明度 danger 当底色"：深色主题下那点红几乎看不出来
 *（`CLAUDE.md` 记过这个坑；与设置页的 `DangerSettingsItem` 是同一个约定）。
 *
 * 行内**不**再放一个 Warning 图标：所在卡片的分组图标已经是它，紧挨着重复一遍
 * 只是噪音 —— "危险"由描边、字色与副标题表达。
 *
 * ## 副标题为什么是 `textSecondary` 而不是 `textTertiary`（2026-10 改）
 *
 * 副标题承载的是**决定用户敢不敢点**的那句话（"只清理本机的登录 Cookie，账号还在"）。
 * 而 `textTertiary / 卡底` 实测只有 **2.15:1** —— `ThemeContrastTest` 里被标为
 * "最该改的一个"。危险区用全 App 最弱的字，是最不该省的对比度。
 * 已与设置页的 `DangerSettingsItem` **一起**提到 `textSecondary`（两处必须同款，
 * 否则又是"同类东西两种长相"）。
 * ⚠️ `textSecondary` 仍是 3.02:1、未达 AA —— 那是品牌色冲突（`#8B95A8`），
 * 属产品决定，不是这一处能单独修的。
 */
@Composable
internal fun DangerRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    // false 时整行不渲染（同上）。
    visible: Boolean = true,
    // 右侧箭头：只有"点进去"的入口行才画。
    // 直接执行动作的行（解绑 / 删除）**不画** —— 画了会看起来像跳转。
    trailingArrow: Boolean = false,
    /**
     * 是否画那圈 danger 描边。
     *
     * - `true`（默认）：独立一行时用 —— 主页面那个「账号管理」**入口**就是它，
     *   "这里藏着不可恢复的操作"这个信号不能因为多了一层就消失。
     *   形状取**卡级圆角 + surfaceCard 填底**（与状态卡同款），靠 danger 描边区分，
     *   否则主页面上一张白卡、一个空心描边框，读起来像两种没做完的东西。
     * - `false`：在「账号管理」组**内部**用。组内四条各自描边会变成一面红墙，
     *   而且与主页面那个同样是红框的入口长得一样 —— 于是"点进去"与"点了就删"
     *   在形状上分不出来。组内改用分隔线（由调用方插入）。
     */
    boxed: Boolean = true,
) {
    if (!visible) return
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (boxed) {
                    // 白卡填底 + **卡级圆角**：入口要与主页面其它卡同一套形状，
                    // 否则它看起来像一截没画完的描边框（旧版用 radiusChip，比上面的状态卡
                    // 窄两档圆角，像贴上去的临时块）。描边仍留 danger —— "这里有不可恢复
                    // 的操作"这个信号不能因为多了层白底就消失。
                    Modifier
                        .clip(RoundedCornerShape(skin.radiusCard))
                        .background(colors.surfaceCard)
                        .border(1.dp, colors.danger.copy(alpha = 0.35f), RoundedCornerShape(skin.radiusCard))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(
                horizontal = if (boxed) skin.cardPadding else 0.dp,
                // 行高跟随「外观 → 我的 → 列表密度」（与绑定信息清单、设置页行同一档）
                vertical = LocalProfileRowPadding.current,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            // 16sp / 13sp：比「我的」其它行的 15sp 高半档 —— 这一页是"逐字核对账号"的地方
            //（用户 2026-10 要求"账号绑定的字体再大一点"）。4 条行 + 卡头一起上浮，页面内部仍然自洽。
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = colors.danger)
            Spacer(Modifier.height(skin.spaceMicro))
            Text(subtitle, fontSize = 13.sp, color = colors.textSecondary)
        }
        if (trailingArrow) {
            Spacer(Modifier.width(skin.spaceSm))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.danger.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
