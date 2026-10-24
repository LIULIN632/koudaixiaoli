package com.pocket.xiaoli.ui.treasurebox

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.pocket.xiaoli.data.CampusConfig
import com.pocket.xiaoli.data.CampusServiceKeys
import com.pocket.xiaoli.data.service.CampusServiceCapability
import com.pocket.xiaoli.ui.theme.ServiceAccent

// ═══════════════════════════════════════════════════════════════════
// 服务入口的数据形状（2026-10 从 `TreasureBoxScreen.kt` 搬出来）
//
// ## 为什么搬
//
// 这三件东西原先埋在 `TreasureBoxScreen.kt` 里（那个文件一度 1676 行）：
// 它们描述的是"**一个服务入口长什么样**"，与"这个页面怎么画"是两件事。
// Phase B 要给它做**两种渲染器**（金刚格 / 列表），届时两个渲染器都要读这个形状 ——
// 形状因此必须能脱离那一个文件被引用。
//
// ## 为什么**不**放进 `data/service/`
//
// 它带 `ImageVector` / `ServiceAccent` / `() -> Unit` —— 是**UI 层类型**。
// 放进 `data/` 会让数据层反向依赖 Compose，破坏既有分层。
// 所以落点是**同包新文件**：`internal` 可见性不变，**调用点一行都不用改**。
// ═══════════════════════════════════════════════════════════════════
/**
 * 服务角标的**语义色调** —— 决定角标是否按状态上色。
 *
 * ## 为什么必须是类型而不是字符串
 *
 * 改造前 `badge` 只是一个 String（"待绑定" / "待登录" / "不可用" …）。
 * 要给它上色，只能拿字符串去反查色调 —— 而字符串是给人看的文案：
 * 改一个字（"待接 API" → "待接入"）就会静默失去颜色，且编译器不会报错。
 *
 * ## 与 `colorfulTags` 的关系
 *
 * 色调只表示"**想**用什么颜色"；能不能用还取决于皮肤上限。
 * 最终判定走 `IconPresentation.colorfulTags`（= 皮肤允许 且 用户选了彩虹），
 * 与图标共用同一个已解析标志 —— 所以不会出现"图标单色、角标却五彩"的错配。
 *
 * ⚠️ 标 `internal` 而非 `private` 是为了让 `SkinScreenshotTest` 能构造带角标的
 * 服务条目 —— 角标与标题的**宽度竞争**曾把「宿舍电费」挤成「宿舍…」，
 * 那类布局回归只有用真实组件 + 真实宽度约束才抓得到。**不要改回 private。**
 */
internal enum class BadgeTone { NEUTRAL, INFO, WARNING, DANGER }

/**
 * 服务入口条目。
 *
 * ⚠️ 同 [BadgeTone]：标 `internal` 是为了让快照测试能用**真实组件**渲染真实数据形状
 *（而不是在测试里另造一个仿制品 —— 那会重新制造"预览与实际漂移"）。
 * **不要改回 private。**
 */
internal data class ServiceEntry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tint: Color,
    val background: Color,

    /**
     * 面性（`Icons.Filled.*`）变体 —— 鲜活皮肤的 `IconStyle.FILLED` 用它，
     * 极简皮肤的 `IconStyle.OUTLINED` 仍用 [icon]。
     *
     * ## 为什么必须放在 [background] **之后**
     *
     * 上面 5 个字段是**位置传参**的（40 多个入口都写
     * `ServiceEntry(名, 副标题, 图标, 色, 色, accent = …)`）。
     * 插在它们中间会让位置实参整体错位到新字段上，变成一堆类型不匹配 ——
     * 放这里则既不破坏既有位置传参，也允许 `iconFilled = …` 具名传入。
     *
     * ## 默认值 = [icon]
     *
     * 未登记 Filled 变体的条目**行为完全不变**（仍画线性图标），所以可以渐进补齐。
     * Material 图标并非每个都有 Outlined / Filled 两版，强行全量映射会编译不过。
     */
    val iconFilled: ImageVector = icon,

    /** 语义分类；非空时优先于 tint / background，颜色从当前主题派生（见 ServiceAccent）。 */
    val accent: ServiceAccent? = null,
    /** 服务注册表 key；为空表示纯本地入口。用于接 Adapter Summary。 */
    val serviceKey: String? = null,
    /** 由 ServiceSummary 动态覆盖的角标；null = 不画角标。 */
    val badge: String? = null,

    /**
     * 角标的语义色调，由 [withSummary] 依据 [ServiceSummaryState] 填。
     *
     * 这是 `colorfulTags` 的**着色依据**：没有它就只知道"要画什么字"，
     * 不知道"该用什么颜色"，只能拿字符串反查 —— 脆且会静默失效。
     */
    val badgeTone: BadgeTone = BadgeTone.NEUTRAL,

    /**
     * 这个入口**当前能做到什么**（原生 / 网页 / 仅信息 / 未接入）。
     *
     * ## 为什么放在 UI 形状里，而不是每次现算
     *
     * 它由 [CampusServiceResolver.capabilityOf] 按校区 + 宿舍算出来，与渲染无关；
     * 放进形状里是为了让**两种渲染器**（富卡片 / 工具箱宫格）读到同一个值 ——
     * 否则两个渲染器要各自调一次 resolver，就又多一处可能漂移的真源。
     *
     * ## 默认值为什么是 NATIVE
     *
     * 默认值必须让**既有行为完全不变**：`ServiceGridScreenshotTest` 等基线用位置/具名
     * 参数直接构造条目且不传本字段，若默认成"仅信息"就会给每一格凭空加角标、全量基线变红。
     * ⚠️ 生产条目的档位**不从这个字段读**：`withSummary` → `serviceEntryCapability`
     * 按 `serviceRegistryKey` + 当前校区/宿舍现算。这里只是给测试构造留的默认值。
     */
    val capability: CampusServiceCapability = CampusServiceCapability.NATIVE,

    val aliases: List<String> = emptyList(),
    val onClick: () -> Unit
) {
    /** 稳定标识，用于「常用服务」的用户自定义持久化 */
    val key: String get() = title
}

/**
 * 服务注册表 key —— **唯一真源**。
 *
 * 2026-10 从 `TreasureBoxScreen.kt` 搬到这里，因为它现在有**两个**消费者：
 * 1. 取 Adapter 摘要（`summaries[key]`）；
 * 2. 判定能力档位（[CampusServiceResolver.capabilityOf]）。
 *
 * 两处若各写一份 `when (title)` 回退表，加了新服务就会出现"摘要取了 A 的、
 * 能力判成 B 的"这种静默错位。所以搬成一份，放在形状文件里。
 *
 * ## 为什么要按 title 回退
 *
 * 四个宿舍生活服务在 `CampusConfig` 里用的是 `SERVICE_*` / `CampusServiceKeys.*`
 * 两套 key 命名，构造 `ServiceEntry` 时不便逐个传 `serviceKey`，
 * 所以按**显示名**兜底。⚠️ 显示名改了这里必须同步改 —— 否则电费/洗澡的能力角标会消失。
 */
internal val ServiceEntry.serviceRegistryKey: String?
    get() = serviceKey ?: when (title) {
        "洗衣机" -> CampusConfig.SERVICE_LAUNDRY
        "宿舍电费" -> CampusServiceKeys.ELECTRICITY
        "洗澡" -> CampusServiceKeys.SHOWER
        "热水" -> CampusServiceKeys.HOT_WATER
        else -> null
    }

/**
 * 把能力档位写成角标。
 *
 * ## 只有"用户需要知道才能不白点"的档位才画
 *
 * [CampusServiceCapability.NATIVE] / [WEB] **刻意不画**：它们的副标题本来就写着
 * "原生入口 · 正选/补退选" / "网课 · 作业 · 考试"，再加一个"能打开"的角标只是噪音 ——
 * 绝大多数卡都挂角标，角标就退化成装饰，反而把真正需要注意的两档淹掉。
 *
 * 色调刻意用 [BadgeTone.NEUTRAL]：这两档都不是"出错"，是**事实**。
 * 用 WARNING 会让"健身房有介绍可看"看起来像故障（与 `withSummary` 里
 * "查到余额了不该染成橙色"是同一条纪律）。
 */
internal fun ServiceEntry.withCapability(
    capability: CampusServiceCapability,
): ServiceEntry {
    val label = when (capability) {
        CampusServiceCapability.NATIVE, CampusServiceCapability.WEB -> null
        CampusServiceCapability.INFO -> "仅介绍"
        CampusServiceCapability.RESERVED -> "未接入"
    } ?: return this
    return copy(badge = label, badgeTone = BadgeTone.NEUTRAL)
}

/**
 * 能力档位的**排序档**：能用得上的排前面。
 *
 * "未接入"排最后是这次修改的重点 —— 原先它们混在 16 项的生活区中部，
 * 学生课间点到的概率和真能用的服务一样高。
 */
internal val CampusServiceCapability.sortRank: Int
    get() = when (this) {
        CampusServiceCapability.NATIVE -> 0
        CampusServiceCapability.WEB -> 1
        CampusServiceCapability.INFO -> 2
        CampusServiceCapability.RESERVED -> 3
    }

internal data class ServiceSection(
    val title: String,
    val entries: List<ServiceEntry>
)
