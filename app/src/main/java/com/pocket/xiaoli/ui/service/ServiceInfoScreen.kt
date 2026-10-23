package com.pocket.xiaoli.ui.service

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import okhttp3.OkHttpClient
import okhttp3.Request

/** 信息页里的一段说明 */
data class ServiceInfoSection(val title: String, val body: String)

/** 信息页里的一个外链（label 为按钮文案，note 为一行说明） */
data class ServiceInfoLink(val label: String, val note: String, val url: String)

/**
 * 信息页里的推广位。
 *
 * 内容来自服务端（[com.pocket.xiaoli.data.promo.PromoRepository]），不在 APK 里 ——
 * 这样合作方上架/下架只改一个 JSON，不用发版。
 *
 * [actionUrl] / [actionRoute] 二者最多有一个非空；都为空表示**这条推广不可点**
 * （纯文字）。服务端下发非法跳转目标时会被降级成这种形态，见 `PromoValidator`。
 */
data class ServiceInfoPromo(
    val title: String,
    val body: String,
    val imageUrl: String? = null,
    val actionUrl: String? = null,
    val actionRoute: String? = null,
)

/**
 * 信息页里的一个「位置」动作（健身房 / 驾校这类校外场所用）。
 *
 * 为什么不复用 [ServiceInfoLink]：后者走 `onOpenLink`，最终是 `http(s)` 链接或内置 WebView；
 * 而地图是 `geo:` 意图 / 高德搜索页，语义完全不同。混在一起会让"链接"同时表示两件事，
 * 将来给链接加白名单校验时也会互相干扰。
 */
data class ServiceInfoPlace(
    /** 交给系统地图 / 高德搜索的关键词，如「烟台理工学院 健身房」。 */
    val query: String,
    val label: String = "在地图上查找",
)

/**
 * 通用「服务介绍页」：标题 + 说明段落 + 常用链接 + 推广位。
 *
 * 用于专升本 / 返乡大巴 / 考研这类「以介绍和跳转为主」的服务，
 * 避免每种服务各写一套页面；链接统一走 [onOpenLink] 由调用方决定是在 App 内打开还是丢浏览器。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceInfoScreen(
    title: String,
    subtitle: String,
    accent: Color,
    onBack: () -> Unit,
    onOpenLink: (String, String) -> Unit,
    sections: List<ServiceInfoSection> = emptyList(),
    links: List<ServiceInfoLink> = emptyList(),
    places: List<ServiceInfoPlace> = emptyList(),
    promos: List<ServiceInfoPromo> = emptyList(),
    /** 推广位里的 App 内跳转（如"去看课表"）。为 null 时该动作不出现。 */
    onOpenRoute: ((String) -> Unit)? = null,
    /** 点击「位置」时的处理。为 null 时 places 不渲染。 */
    onOpenPlace: ((String) -> Unit)? = null,
    footer: String = "以上流程与时间以官方最新公告为准。",
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current

    Scaffold(
        containerColor = colors.surfaceBase,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surfaceCard)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(skin.spaceLg),
            verticalArrangement = Arrangement.spacedBy(skin.spaceMd)
        ) {
            // 说明块（**只留说明，不重复页面名**）
            //
            // ⚠️ 这里原来还有一行 `Text(title)`，与 `TopAppBar` 的标题是**同一个字符串** ——
            // 用户原话：「点进去成绩查询，下面还是有一行字写的成绩查询，其他的服务也是这样的」。
            // 这个组件被专升本 / 考研 / 返乡大巴 / 健身房 / 驾校 五个入口共用，
            // 所以那一处重复在服务页里是**成片**出现的。
            // 页面名归 `TopAppBar`（返回箭头也挂在它上面，不能省）；这里只留那句话说明。
            if (subtitle.isNotBlank()) {
                // 说明块从「accent 12% 的灰底胶囊」改成白卡 + 图标：
                // 这几个服务的 accent 本身就偏灰（驾校 #6E8BA8），12% 压在白底上整条
                // 读起来像一块禁用条。白卡 + accent 图标 + 13sp 说明才像"值得读一句"。
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(skin.radiusCard),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
                    border = BorderStroke(1.dp, colors.borderDivider),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = skin.spaceLg, vertical = skin.spaceMd),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.padding(top = 1.dp).size(17.dp),
                        )
                        Spacer(Modifier.width(skin.spaceSm))
                        Text(
                            subtitle,
                            fontSize = 13.sp,
                            color = colors.textSecondary,
                            lineHeight = 20.sp,
                        )
                    }
                }
            }

            sections.forEach { section ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(skin.radiusCard),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
                    border = BorderStroke(1.dp, colors.borderDivider),
                ) {
                    Column(Modifier.padding(skin.spaceLg)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 每条小标题前一根 4×14 的服务色短棒：五个入口各自一个色相，
                            // 这根棒是整页唯一能看出"这是哪个服务"的装饰，也把标题从正文里拎出来。
                            Box(
                                Modifier
                                    .width(4.dp)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(accent)
                            )
                            Spacer(Modifier.width(skin.spaceSm))
                            Text(section.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        }
                        Spacer(Modifier.height(skin.spaceSm))
                        // 12sp 在 Pixel 5 上正文一段就十一二行，读起来太密；13sp / 21sp 行高
                        // 与绑定页的说明文字同一档。
                        Text(section.body, fontSize = 13.sp, color = colors.textSecondary, lineHeight = 21.sp)
                    }
                }
            }

            if (links.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(skin.radiusCard),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
                    border = BorderStroke(1.dp, colors.borderDivider),
                ) {
                    Column {
                        links.forEachIndexed { index, link ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenLink(link.url, link.label) }
                                    .padding(horizontal = skin.spaceLg, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(link.label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary)
                                    if (link.note.isNotBlank()) {
                                        Spacer(Modifier.height(skin.spaceMicro))
                                        // 副标题是"这是什么网站"，值得读 —— 用 textSecondary
                                        // 而不是 textTertiary（后者压在卡底只有约 2.15:1）。
                                        Text(link.note, fontSize = 12.sp, color = colors.textSecondary)
                                    }
                                }
                                Icon(
                                    Icons.Filled.OpenInNew,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            if (index != links.lastIndex) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = skin.spaceLg)
                                        .height(1.dp)
                                        .background(colors.borderDivider)
                                )
                            }
                        }
                    }
                }
            }

            // 「位置」：健身房 / 驾校这类校外场所的主要动作就是"带我去"。
            // 单独一列，不混进 links（地图是 geo: 意图，不是 http 链接）
            if (places.isNotEmpty() && onOpenPlace != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(skin.radiusCard),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
                    border = BorderStroke(1.dp, colors.borderDivider),
                ) {
                    Column {
                        places.forEachIndexed { index, place ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenPlace(place.query) }
                                    .padding(horizontal = skin.spaceLg, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Place,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(skin.spaceMd))
                                Column(Modifier.weight(1f)) {
                                    Text(place.label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary)
                                    Spacer(Modifier.height(skin.spaceMicro))
                                    Text(place.query, fontSize = 12.sp, color = colors.textSecondary)
                                }
                            }
                            if (index != places.lastIndex) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = skin.spaceLg)
                                        .height(1.dp)
                                        .background(colors.borderDivider)
                                )
                            }
                        }
                    }
                }
            }

            promos.forEach { promo ->
                PromoCard(promo = promo, accent = accent, onOpenLink = onOpenLink, onOpenRoute = onOpenRoute)
            }

            Text(footer, fontSize = 12.sp, color = colors.textTertiary, modifier = Modifier.padding(top = skin.spaceMicro))
            Spacer(Modifier.height(skin.spaceSm))
        }
    }
}

/**
 * 一张推广卡。
 *
 * ## 设计上刻意保留的三件事
 *
 * 1. **「推广」标签不能去掉** —— 广告法要求推广内容可识别。这是合规，不是装饰。
 * 2. **不可点的推广也要能显示** —— 服务端下发的跳转目标不合法时会被降级成纯文字
 *    （见 `PromoValidator`），文案本身仍有价值，只是不给它一个危险的跳转。
 * 3. **配图失败不占位** —— 拉不到图就只显示文字，不留一个灰框。
 *
 * ## 为什么自己写图片加载而不用 Coil
 *
 * 项目当前**没有任何图片加载库**（`CLAUDE.md` 的依赖清单里没有），而为一张推广配图
 * 引入 Coil + 它的磁盘缓存/解码线程池，代价明显大于收益。这里的做法与
 * `ZhengfangLoginUi` 加载验证码图片完全一致：OkHttp 取字节 → `BitmapFactory` 解码。
 * 差异只有一处：加了**进程内内存缓存**，因为推广卡会随页面滚动反复进入组合，
 * 每次重组都重新下载是不可接受的。
 */
@Composable
private fun PromoCard(
    promo: ServiceInfoPromo,
    accent: Color,
    onOpenLink: (String, String) -> Unit,
    onOpenRoute: ((String) -> Unit)?,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current

    // 有跳转目标才可点；两者都空 = 纯文字推广
    val clickable: (() -> Unit)? = when {
        promo.actionUrl != null -> ({ onOpenLink(promo.actionUrl, promo.title) })
        promo.actionRoute != null && onOpenRoute != null -> ({ onOpenRoute(promo.actionRoute) })
        else -> null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (clickable != null) Modifier.clickable { clickable() } else Modifier),
        shape = RoundedCornerShape(skin.radiusCard),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
        border = BorderStroke(1.dp, colors.borderDivider),
    ) {
        Column(Modifier.padding(skin.spaceLg)) {
            promo.imageUrl?.let { url ->
                RemotePromoImage(url = url)
                Spacer(Modifier.height(skin.spaceSm))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = accent.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
                    Text(
                        // ⚠️ 不可删：推广内容必须可识别（合规要求）
                        "推广",
                        fontSize = 10.sp,
                        color = accent,
                        modifier = Modifier.padding(horizontal = skin.spaceSmall, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.width(skin.spaceSm))
                Text(promo.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                if (clickable != null) {
                    Spacer(Modifier.width(skin.spaceXs))
                    Icon(
                        imageVector = Icons.Filled.OpenInNew,
                        contentDescription = null,
                        tint = colors.textTertiary,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
            if (promo.body.isNotBlank()) {
                Spacer(Modifier.height(skin.spaceSm))
                Text(promo.body, fontSize = 13.sp, color = colors.textSecondary, lineHeight = 20.sp)
            }
        }
    }
}

/**
 * 推广配图。**加载失败就什么都不画**（不留灰框、不留占位）。
 *
 * 用 `painterResource` 之类的本地图机制无法满足"图在服务端"，所以走
 * OkHttp + BitmapFactory；内存缓存按 URL 存，避免滚动时反复下载。
 */
@Composable
private fun RemotePromoImage(url: String) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    var bitmap by remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var failed by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        bitmap = PromoImageCache.get(url)
        if (bitmap == null) {
            val loaded = PromoImageCache.load(url)
            if (loaded != null) bitmap = loaded else failed = true
        }
    }

    val bmp = bitmap
    if (bmp != null) {
        androidx.compose.foundation.Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(skin.radiusChip))
                .background(colors.surfaceAlt),
        )
    } else if (!failed) {
        // 加载中：留出与成品图同高的占位，避免图片到达时页面跳动
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(skin.radiusChip))
                .background(colors.surfaceAlt),
        )
    }
    // failed == true：不画任何东西（宁可只有文字，也不要一个失败的空框）
}

/**
 * 推广配图的**进程内**缓存。
 *
 * 只做内存、不做磁盘：一张推广图几百 KB，页面退出后重新下载的代价可以接受；
 * 而做磁盘缓存要考虑失效、清理、配额，为一张图不值得。
 */
private object PromoImageCache {

    private val memory = java.util.concurrent.ConcurrentHashMap<String, android.graphics.Bitmap>()

    /** 上限：同时展示的推广位很少，8 张足够覆盖"来回滑动"的场景。 */
    private const val MAX_ENTRIES = 8
    private const val MAX_BYTES = 3 * 1024 * 1024

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    fun get(url: String): android.graphics.Bitmap? = memory[url]

    /** 下载并解码。任何失败返回 null（调用方据此不显示图片）。 */
    fun load(url: String): android.graphics.Bitmap? {
        // 域名白名单已在上游 PromoValidator 校验过；这里再挡一次，防止将来有人绕过
        if (!url.startsWith("https://")) return null
        val bytes = runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return null
                // 上限 3 MB：推广图不该超过这个量级，超了直接放弃（防止被塞大文件拖垮内存）
                resp.body?.let { body ->
                    if (body.contentLength() > MAX_BYTES) return null
                    body.bytes()
                }
            }
        }.getOrNull() ?: return null

        val bmp = runCatching {
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull() ?: return null

        if (memory.size >= MAX_ENTRIES) memory.clear()
        memory[url] = bmp
        return bmp
    }
}
