package com.pocket.xiaoli.ui.treasurebox

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.pocket.xiaoli.data.ANCHORED_SCHOOL_IDS
import com.pocket.xiaoli.data.CampusConfig
import com.pocket.xiaoli.data.CampusInfo
import com.pocket.xiaoli.core.weather.weatherAnchorForCity
import com.pocket.xiaoli.data.CampusServiceKeys
import com.pocket.xiaoli.data.School
import com.pocket.xiaoli.data.model.DormInfo
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

    /**
     * 这一格要靠谁的数据才能工作 —— 决定它对哪所学校可见，见 [ServiceScope]。
     *
     * 默认 [ServiceScope.ANY_SCHOOL] 让**既有行为完全不变**：所有没显式标注的格子
     * 仍然对任何学校显示（本机功能 / 全国性系统正是这一类），
     * 也让测试里直接构造的条目不必关心这个维度。
     */
    val scope: ServiceScope = ServiceScope.ANY_SCHOOL,

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

/**
 * 一格服务**要靠谁的数据**才能工作 —— 服务页据此决定它对哪所学校可见。
 *
 * ## 为什么需要它（与 [CampusServiceCapability] 的分工）
 *
 * 能力档位回答的是"这一格接通到什么程度"，判据是**校区配置**；而 `AppContainer.selectedCampus`
 * 对没收录的学校会静默回退到烟台理工本校区。于是档位永远是"能用"，用户点下去才被告知
 * "这所学校还没收录" —— 界面先画了一张假的格子，再用 Toast 收回它。
 *
 * 本枚举回答的是另一件事：**这一格要不要本校的数据**。
 * 判据换到「学校」这一层，未收录的格子直接不出现（2026-11 用户要求），
 * 而不是留着等用户白点一次。
 *
 * ## 加新格子时怎么选
 *
 * 看它点开之后**读谁的数**：读 `CampusConfig` / 校区服务的选 [CAMPUS_CONFIG]，
 * 读按城市取的数据选 [CITY_DATA]，读某所学校写死的数据源选 [ANCHORED_SCHOOL]，
 * 本机数据或全国性系统（课表导入、学习通、番茄钟）保持默认的 [ANY_SCHOOL]。
 * 判不准就去看 [com.pocket.xiaoli.ui.treasurebox.openCampusService] 有没有被它调用。
 */
internal enum class ServiceScope {
    /** 不需要任何学校数据：本机功能或全国性系统，任何学校都显示。 */
    ANY_SCHOOL,

    /** 需要本校在 `CampusConfig` 里配了校园服务 / 校区（电费、校园卡、报修、地图…）。 */
    CAMPUS_CONFIG,

    /** 需要这座城市配了天气取数坐标（见 core 的 `WEATHER_CITY_ANCHORS`）。 */
    CITY_DATA,

    /** 需要数据源已为这所学校配好：公交站点锚点、图书馆 OPAC 目前只有烟台理工。 */
    ANCHORED_SCHOOL,
}

/**
 * 判可见性所需的三条事实 —— **由界面从 [com.pocket.xiaoli.di.AppContainer] 组装**，
 * 判定本身是纯函数（[serviceEntryVisible]），这样它能被单测直接钉住。
 */
internal data class ServiceScopeFacts(
    val campusConfigReady: Boolean,
    val cityWeatherReady: Boolean,
    val anchoredSchoolReady: Boolean,
) {
    companion object {
        /** 全部可用 —— 烟台理工的学生看到完整服务页，也是快照基线的口径。 */
        val ALL = ServiceScopeFacts(
            campusConfigReady = true,
            cityWeatherReady = true,
            anchoredSchoolReady = true,
        )
    }
}

/**
 * 从**当前学校**算出那三条事实 —— 判据只写这一份。
 *
 * ## 为什么单独成一个函数
 *
 * 它原来是服务页里 `remember { ... }` 的三行内联代码：内联的版本没法被测，
 * 而这三行恰好是整件事的判据（判错了就是"格子凭空消失"或"点了才吃 Toast"）。
 * 挪出来之后 `ServiceScopeTest` 能直接拿学校样本钉住它，
 * 以后别的界面要按学校取舍服务也调同一个函数，不再各抄一份。
 *
 * @param campus 当前学校名下的校区（`AppContainer.campusForCurrentSchool()`）；
 *   **必须由调用方传 null 而不是默认校区** —— 传进来一个借来的校区，
 *   `campusConfigReady` 就永远是 true，整套判据当场失效。
 */
internal fun serviceScopeFacts(
    school: School?,
    campus: CampusInfo?,
): ServiceScopeFacts = ServiceScopeFacts(
    campusConfigReady = campus != null,
    cityWeatherReady = weatherAnchorForCity(school?.city) != null,
    anchoredSchoolReady = school?.id in ANCHORED_SCHOOL_IDS,
)

/**
 * 这一格现在能不能给当前学校看。
 *
 * 与 [serviceEntryCapability] 是**两件事**：可见性决定"有没有这一格"，
 * 能力档位决定"这一格标什么角标"。两者共用同一个校区/宿舍真源，但判据不同 ——
 * 混成一个函数会出现"为了藏格子把档位改成 RESERVED"，那等于让角标替隐藏说谎。
 */
internal fun serviceEntryVisible(entry: ServiceEntry, facts: ServiceScopeFacts): Boolean =
    when (entry.scope) {
        ServiceScope.ANY_SCHOOL -> true
        ServiceScope.CAMPUS_CONFIG -> facts.campusConfigReady
        ServiceScope.CITY_DATA -> facts.cityWeatherReady
        ServiceScope.ANCHORED_SCHOOL -> facts.anchoredSchoolReady
    }

/**
 * 服务页那四节的**完整渲染前处理**：按学校过滤 → 去掉死格子 → 按能力档位排座次。
 *
 * ## 为什么从 composable 里搬出来
 *
 * 它原来内联在 `TreasureBoxScreen` 的 `remember { ... }` 里，于是"未收录学校少了哪几格"
 * 这件事**只能靠人肉点一遍验证**。搬成纯函数后 `ServiceScopeTest` 能直接拿真实条目形状
 * 把整条管道钉住 —— 而这条管道同时管着服务页正文、常用服务与隐藏编辑弹窗
 *（三者都读过滤后的 `sections`），一处判错就是三处一起错。
 *
 * 顺序不能换：先按学校过滤，剩下的才谈档位。反过来会把已经不该出现的格子
 * 排进座次里，"未接入"沉底那种效果会盖掉"这格不属于这所学校"。
 */
internal fun visibleSections(
    sections: List<ServiceSection>,
    facts: ServiceScopeFacts,
    campus: CampusInfo,
    dormInfo: DormInfo?,
): List<ServiceSection> = sections.map { section ->
    section.copy(
        entries = section.entries
            .filter { serviceEntryVisible(it, facts) }
            // 「校园跑」这类**纯直达格子**：没配链接就不出现。
            // 留着一格，点下去只会弹"暂未配置"——那是死格子，不是待接入。
            // （区别于健身房那种 INFO 格子：它有站内说明页，点开有内容。）
            .filterNot {
                it.serviceRegistryKey == CampusServiceKeys.SCHOOL_RUN &&
                    serviceEntryCapability(it, campus, dormInfo) == CampusServiceCapability.INFO
            }
            // `sortedBy` 是稳定排序，所以同档位内部保持作者手写的顺序。
            .sortedBy { serviceEntryCapability(it, campus, dormInfo).sortRank }
    )
}
