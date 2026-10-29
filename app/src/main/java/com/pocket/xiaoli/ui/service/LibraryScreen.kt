package com.pocket.xiaoli.ui.service

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.EventSeat
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.libraryopac.LibraryOpacBook
import com.pocket.xiaoli.data.libraryopac.LibraryOpacRepository
import com.pocket.xiaoli.ui.components.AppCard
import com.pocket.xiaoli.ui.components.ErrorCard
import com.pocket.xiaoli.ui.components.ServiceHeroCard
import com.pocket.xiaoli.ui.components.ServiceHeroChip
import com.pocket.xiaoli.ui.components.ServicePageTopBar
import com.pocket.xiaoli.ui.components.ServiceSectionLabel
import com.pocket.xiaoli.ui.components.campusui.CampusSearchBar
import com.pocket.xiaoli.ui.profile.SettingsIcon
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.ServiceAccent
import com.pocket.xiaoli.ui.theme.serviceAccentColor
import com.pocket.xiaoli.ui.components.uniqueKeys
import kotlinx.coroutines.launch

/**
 * 一个功能入口。
 *
 * `internal` 而不是 private：整页要能被测试凭空渲出来（见 `LibraryPageScreenshotTest`），
 * 而"点哪条开哪个网页"这件事只能由调用方决定，所以入口表由内容组件持有、由外层回调消费。
 */
internal data class LibraryAction(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    /** true = App 内原生页（不出网页）；false = 内置 WebView 打开 [url]。 */
    val native: Boolean,
    val url: String = "",
)

/**
 * 这一页的可见状态（纯数据）。
 *
 * 抽出来的理由和「修课情况」「反馈」那几页一样：整页原先把状态散在五个 `var` 里，
 * 于是"检索中""有结果""没命中""失败"四个态**一个都渲不出来** —— 只能等真联网。
 */
internal data class LibraryPageUi(
    val query: String = "",
    val searching: Boolean = false,
    /** 已经发起过一次检索（区分"还没搜过"与"搜了但没结果"，这两件事必须说得不一样） */
    val searched: Boolean = false,
    val results: List<LibraryOpacBook> = emptyList(),
    val error: String? = null,
)

/**
 * 图书馆原生入口页。
 *
 * - 馆藏检索：先尝试在 App 内解析 OPAC 结果，解析失败自动提供网页检索；
 * - 图书馆座位：走 App 内原生 [com.pocket.xiaoli.ui.seat.LibrarySeatScreen]；
 * - 其余官网栏目按需进入内置 WebView。
 *
 * ## 2026-10-29 归口到服务页视觉语言
 *
 * 这一页是服务页视觉统一那几轮漏下的角落：别人早就换成 `ServicePageTopBar` + 主视觉卡 +
 * 图标行，它还是"灰底 + 白卡 + 无图标无按压的裸列表"。顺手清掉的还有**同一句话说三遍**：
 * 校园网 / VPN 这件事原先同时出现在主视觉卡第三行、「我的借阅」的副标题和页脚小字里，
 * 现在只在主视觉卡说一次。
 */
@Composable
fun LibraryScreen(
    onBack: () -> Unit,
    onOpenWeb: (url: String, title: String) -> Unit,
    onOpenSeat: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var ui by remember { mutableStateOf(LibraryPageUi()) }

    fun searchCatalog(keyword: String) {
        if (keyword.isBlank() || ui.searching) return
        ui = ui.copy(query = keyword, searching = true, searched = false, results = emptyList(), error = null)
        scope.launch {
            LibraryOpacRepository.search(keyword)
                .onSuccess { ui = ui.copy(searching = false, searched = true, results = it) }
                .onFailure { ui = ui.copy(searching = false, searched = true, error = it.message ?: "馆藏检索失败") }
        }
    }

    LibraryPageContent(
        ui = ui,
        onQueryChange = { ui = ui.copy(query = it) },
        onSearch = { searchCatalog(ui.query.trim()) },
        onAction = { action ->
            if (action.native) onOpenSeat() else onOpenWeb(action.url, action.title)
        },
        onOpenBook = { book ->
            onOpenWeb(book.detailUrl.ifBlank { LibraryOpacRepository.searchUrl(ui.query) }, book.title)
        },
        onOpenWebSearch = {
            val keyword = ui.query.trim()
            if (keyword.isNotBlank()) onOpenWeb(LibraryOpacRepository.searchUrl(keyword), "馆藏检索")
        },
        onBack = onBack,
    )
}

/** 五个入口，顺序 = 学生用得多的在前。 */
internal val LibraryActions = listOf(
    LibraryAction("我的借阅", "借了什么 · 哪天到期", Icons.Outlined.MenuBook, native = false, url = "http://192.168.2.34/GGCX/readerinfo.htm"),
    LibraryAction("图书馆座位", "选座 · 暂离 · 退座", Icons.Outlined.EventSeat, native = true),
    LibraryAction("电子资源", "已购数据库 · 校外怎么访问", Icons.Outlined.Storage, native = false, url = "https://lib.yitsd.edu.cn/info/1010/1153.htm"),
    LibraryAction("馆藏分布", "各阅览室在哪一层", Icons.Outlined.Place, native = false, url = "https://lib.yitsd.edu.cn/info/1004/1125.htm"),
    LibraryAction("借阅规则", "借期 · 续借 · 超期", Icons.Outlined.Rule, native = false, url = "https://lib.yitsd.edu.cn/info/1004/1132.htm"),
)

/**
 * 整页内容（只吃数据、只吐回调）。
 *
 * 四个检索态都在这里，测试能凭空造出"检索中 / 有结果 / 没命中 / 失败"四张图 ——
 * 以前这四个态只能靠真联网才能看到，所以也从没有过基线。
 */
@Composable
internal fun LibraryPageContent(
    ui: LibraryPageUi,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onAction: (LibraryAction) -> Unit,
    onOpenBook: (LibraryOpacBook) -> Unit,
    onOpenWebSearch: () -> Unit,
    onBack: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current

    Scaffold(
        // 页底与顶栏同色（surfaceBase）：服务页其余二级页早就不再用 surfaceAlt 压一层灰底。
        containerColor = colors.surfaceBase,
        topBar = { ServicePageTopBar(title = "图书馆", onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            contentPadding = PaddingValues(start = skin.spaceLg, end = skin.spaceLg, top = skin.spaceSm, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(skin.spaceMd),
        ) {
            item { LibraryHero(compact = ui.searching || ui.searched) }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CampusSearchBar(
                        value = ui.query,
                        onValueChange = onQueryChange,
                        placeholder = "书名 / 作者 / ISBN",
                        onSearch = onSearch,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(skin.spaceSm))
                    // 键盘的「搜索」键也能触发（CampusSearchBar 的 onSearch）；这颗按钮是给
                    // 不想找键盘的人的第二条路，也是"检索中"的落点。空输入时它是灰的 ——
                    // 灰着比"点了没反应"诚实。
                    Button(
                        onClick = onSearch,
                        enabled = ui.query.isNotBlank() && !ui.searching,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.heightIn(min = 44.dp),
                    ) {
                        Text("检索", fontSize = 14.sp)
                    }
                }
            }

            when {
                ui.searching -> item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(skin.radiusCard))
                            .background(colors.surfaceCard)
                            .padding(skin.spaceLg),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.accentMain)
                        Spacer(Modifier.width(10.dp))
                        Text("正在检索馆藏…", fontSize = 13.sp, color = colors.textSecondary)
                    }
                }

                ui.results.isNotEmpty() -> {
                    item { ServiceSectionLabel(title = "馆藏结果 · ${ui.results.size} 本", accent = ServiceAccent.QUERY) }
                    // key 必须唯一：`detailUrl` 为空时退化成书名，同名书会撞
                    // （同一本书的不同版本/馆藏点很常见），而重复 key 会让 LazyColumn 直接崩
                    val bookKeys = uniqueKeys(ui.results) { it.detailUrl.ifBlank { it.title } }
                    itemsIndexed(ui.results, key = { index, _ -> bookKeys[index] ?: index.toString() }) { _, book ->
                        LibraryBookRow(book = book, onClick = { onOpenBook(book) })
                    }
                    item {
                        TextButton(onClick = onOpenWebSearch) {
                            Text("在网页中打开完整检索结果", fontSize = 13.sp)
                        }
                    }
                }

                ui.searched && ui.error != null -> item {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(skin.spaceLg)) {
                            ErrorCard(title = "馆藏检索失败", message = ui.error!!, onRetry = onSearch)
                            Spacer(Modifier.height(skin.spaceSm))
                            TextButton(onClick = onOpenWebSearch) {
                                Text("改用网页检索", fontSize = 13.sp)
                            }
                        }
                    }
                }

                ui.searched -> item {
                    // 「检索到了但没结果」不是错误，别与失败共用一张红卡
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(skin.spaceLg)) {
                            Text(
                                "没查到这本书",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                            )
                            Spacer(Modifier.height(skin.spaceXs))
                            Text(
                                "换个说法再试：书名只打关键字（如「活着」而不是完整书名），或直接试作者。",
                                fontSize = 12.sp,
                                color = colors.textSecondary,
                                lineHeight = 18.sp,
                            )
                            Spacer(Modifier.height(skin.spaceSm))
                            TextButton(onClick = onOpenWebSearch) {
                                Text("用网页检索看看", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            item { ServiceSectionLabel(title = "常用功能", accent = ServiceAccent.QUERY) }
            itemsIndexed(LibraryActions, key = { _, action -> action.title }) { _, action ->
                LibraryActionRow(action = action, onClick = { onAction(action) })
            }
        }
    }
}

/**
 * 主视觉卡：校名 + 一句真约束 + 两个数据来源。
 *
 * 语义色取 [ServiceAccent.QUERY]（与「服务」页那一格的入口色同源）：点的是查询类入口，
 * 进来还是那套颜色。
 *
 * ⚠️ 这里**不列功能清单**。原先第二行写的是「原生馆藏检索 · 借阅 · 电子资源 · 原生座位预约」，
 * 那是把下面五条入口的标题重抄一遍 —— 用户往下滚两行就会看到同样的词，白占一行高度。
 *
 * [compact] = 已经发起过检索时收成两行：整块 hero 约 200dp，加搜索框就是 260dp，
 * 结果列表被顶到折叠线以下 —— 而"搜完第一眼就该看到书"是这一页唯一的正事。
 * 来源胶囊（OPAC / 我去图书馆）在搜过之后不再重要，先让位。
 */
@Composable
private fun LibraryHero(compact: Boolean) {
    val skin = LocalSkin.current
    ServiceHeroCard(accent = ServiceAccent.QUERY) {
        Text(
            "烟台理工学院图书馆",
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
        )
        Spacer(Modifier.height(skin.spaceSmall))
        Text(
            "馆藏、座位、借阅分属不同系统，部分要校园网或 VPN 才打得开。",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.9f),
            lineHeight = 18.sp,
        )
        if (!compact) {
            Spacer(Modifier.height(skin.spaceMd))
            // 来源写在明面上：打不开时用户能立刻判断"是 OPAC 挂了还是座位系统挂了"
            Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                ServiceHeroChip("馆藏来自 OPAC")
                ServiceHeroChip("座位来自 我去图书馆")
            }
        }
    }
}

/** 一个功能入口：图标块 + 名称（原生动作带徽标）+ 一句它到底给什么 + 箭头。 */
@Composable
private fun LibraryActionRow(action: LibraryAction, onClick: () -> Unit) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    // onClick 交给 AppCard：它自带 miuixPress 的下沉反馈。
    // 原先这里是手搓的 `Modifier.clickable`，按下去什么反馈都没有 —— 用户报过"点不动？"。
    AppCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = skin.spaceLg, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsIcon(action.icon, ServiceAccent.QUERY)
            Spacer(Modifier.width(skin.spaceMd))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        action.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary,
                    )
                    if (action.native) {
                        Spacer(Modifier.width(skin.spaceSmall))
                        NativeBadge()
                    }
                }
                Spacer(Modifier.height(skin.spaceTiny))
                Text(action.subtitle, fontSize = 12.sp, color = colors.textTertiary)
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * 「原生」徽标：这条入口不出网页，直接开 App 内的选座页。
 *
 * 值得单独标出来 —— 网页与原生在**能用功能**上不一样（原生能点按钮预约，网页要重新登录），
 * 用户按旧入口名点进来之前应该知道这一点。
 */
@Composable
private fun NativeBadge() {
    val colors = LocalPocketXiaoliColors.current
    val tint = serviceAccentColor(ServiceAccent.QUERY)
    Text(
        "原生",
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        color = tint,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * 一条馆藏结果。
 *
 * ⚠️ **索书号从点号串里提出来单独放右边**：那是这一行唯一要抄下来去书架找书的号码
 *（`TP312/88` 这种），混在「作者 · 出版社 · 年份 · 索书号」里等于让人在长句子里做填空。
 */
@Composable
internal fun LibraryBookRow(book: LibraryOpacBook, onClick: () -> Unit) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    AppCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = skin.spaceLg, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    book.title.ifBlank { "未命名条目" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary,
                    maxLines = 2,
                )
                val details = listOfNotNull(
                    book.author.takeIf { it.isNotBlank() },
                    book.publisher.takeIf { it.isNotBlank() },
                    book.year.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                if (details.isNotBlank()) {
                    Spacer(Modifier.height(skin.spaceXs))
                    Text(details, fontSize = 11.sp, color = colors.textTertiary, lineHeight = 16.sp, maxLines = 2)
                }
            }
            if (book.callNumber.isNotBlank()) {
                Spacer(Modifier.width(skin.spaceSm))
                Text(
                    book.callNumber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.accentMain,
                    modifier = Modifier
                        .clip(RoundedCornerShape(skin.radiusChip))
                        .background(colors.surfaceAlt)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    maxLines = 1,
                )
            }
        }
    }
}
