package com.pocket.xiaoli.ui.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.promo.Promo
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.reduceMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 服务页顶部的**图片海报横幅**（自有推广位，不是广告联盟）。
 *
 * ## 合规边界（这几条不是可选项）
 *
 * 项目的约束里明确"禁止广告"，README 也把允许的形态写死了：内容由**自有域名**下发、
 * **必须带「推广」标识**、不读取不上传任何本地数据。所以这里：
 * - 角标「推广」常驻，不接受"为了好看先不显示"的变通 —— **轮播的每一页都要有**；
 * - 只认 [PromoRepository] 已经过 [PromoValidator] 白名单校验的那份内容；
 * - 不做任何曝光/点击统计 —— 一旦埋点，"无埋点、无用户行为追踪"那句承诺就假了。
 *
 * ## 服务端可换
 *
 * 内容来自 `PromoRepository`（已经在拉 `promos.json` 并本地缓存），
 * 换海报**不需要发版**：服务端改 JSON 即可。图片放在 OSS+CDN，不进 APK。
 *
 * ## 失败与空态：整块不显示
 *
 * 没有内容、图片下载失败、JSON 校验没过 —— 一律**整块不出现**。
 * 留一个空框或一张破图，比没有横幅更糟：用户会以为 App 坏了。
 *
 * ## 轮播（2026-10）
 *
 * 改造前它只取 `priority` **最高的那一张**，其余全部不展示 —— 服务端配了多条也看不见。
 * 现在按"有图的条数"分流：
 *
 * | 有图的条数 | 行为 |
 * |---|---|
 * | 0 | 整块不出现（与改造前一致）|
 * | **1** | **完全走改造前的渲染** —— 无圆点、不自动播。**单条服务端的观感逐像素不变** |
 * | ≥2 | 横向分页 + 底部小圆点 + 每 4 秒自动翻一页（循环）|
 *
 * 自动播放**在用户拖动时暂停**：靠 `pagerState.isScrollInProgress` 判断，
 * 不在用户正拿着它的时候把页面抢走。
 *
 * ⚠️ **本文件仍是合规三件事的唯一落点**（推广角标常驻 / 无统计 / 失败即隐藏）。
 * 需要轮播时请改这里，**不要**在调用点写分支 —— 分散出去就是多几处会写错的地方。
 *
 * @param autoPlay 自动轮播开关。**默认开**；快照测试必须传 `false` ——
 * 自动播放属于本项目《UI验收手册》点名的"无限动画"不确定性，会让基准图自己变红。
 */
@Composable
fun PromoBanner(
    promos: List<Promo>,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    onClick: (Promo) -> Unit,
) {
    // 全部**有图**的有效推广位，按 priority 降序（仓储已按 slot + 时间窗筛过）
    val pages = remember(promos) {
        promos.filter { !it.imageUrl.isNullOrBlank() }.sortedByDescending { it.priority }
    }
    if (pages.isEmpty()) return

    if (pages.size == 1) {
        // ⚠️ 这条路径要与改造前**逐像素一致**（modifier 链的顺序都保持原样）：
        // 单条服务端不该因为"支持了轮播"而多出圆点之类的元素。
        PromoPage(
            promo = pages.first(),
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(16f / 6f),
            onClick = onClick,
        )
        return
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })

    if (autoPlay && !reduceMotion()) {
        // ⚠️ 必须显式判 reduceMotion()：这一段是**协程 delay**，不是 Compose 动画，
        // 所以系统"移除动画"对它**没有任何作用** —— 用户关掉动画之后，
        // 服务页那个每 4 秒自走的轮播照样在动，而且他没有任何开关能停。
        // 这是全 App 唯一一处"自己数拍子"的动画，也是 Reduce Motion 最该管住的一处。
        LaunchedEffect(pagerState, pages.size) {
            while (true) {
                delay(PROMO_AUTO_ADVANCE_MS)
                // 用户正在拖动时不抢；等他松手，下一轮再翻。
                if (!pagerState.isScrollInProgress) {
                    pagerState.animateScrollToPage((pagerState.currentPage + 1) % pages.size)
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            // 预载相邻一页：翻页时不该出现"图还没下好"的白页
            beyondViewportPageCount = 1,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 6f),
        ) { index ->
            PromoPage(promo = pages[index], modifier = Modifier.fillMaxSize(), onClick = onClick)
        }

        // 小圆点：底部居中。当前页实心白、其余半透明白（压在图上，需要足够对比）
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            repeat(pages.size) { index ->
                val active = index == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .size(if (active) 7.dp else 5.dp)
                        .clip(CircleShape)
                        .background(if (active) Color.White else Color.White.copy(alpha = 0.45f)),
                )
            }
        }
    }
}

/**
 * 一页海报（单条路径与轮播路径**共用**，保证两条路观感一致）。
 *
 * 位图未到手时 `return` —— 单条时表现为整块不出现；轮播时该页内部为空，
 * 靠 `beyondViewportPageCount = 1` 预载把"白页"概率压到最低。
 */
@Composable
private fun PromoPage(
    promo: Promo,
    modifier: Modifier,
    onClick: (Promo) -> Unit,
) {
    val skin = LocalSkin.current
    val url = promo.imageUrl ?: return
    val bitmap by rememberPromoImage(url)
    // 图还没到手（含下载失败）就不画 —— 宁可晚一拍出现，也不要先占一块空白
    val image = bitmap ?: return

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(skin.radiusLarge))
            .clickable { onClick(promo) },
    ) {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = promo.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        // 「推广」角标常驻。文案用"推广"而不是"广告"：它是自有合作位，
        // 但必须让用户一眼能看出来"这不是功能，是推荐"。
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text("推广", fontSize = 10.sp, color = Color.White)
        }
    }
}

/** 自动轮播的翻页间隔。 */
private const val PROMO_AUTO_ADVANCE_MS = 4_000L

/**
 * 进程内图片缓存：同一张海报在切页面来回时不该重复下载。
 *
 * ⚠️ 标 `internal` 而非 `private` 是为了让**快照测试能预置一张位图** ——
 * `PromoBanner` 在拿不到位图时整块不渲染，而 Robolectric 里没有网络；
 * 不预置的话基准图会是**一张空图**，等于没测。**不要改回 private。**
 *（同 `BadgeTone` / `ServiceEntry` 的做法：为了可测而开可见性。）
 */
internal val promoImageCache = LruCache<String, Bitmap>(8)

private val promoImageClient by lazy { OkHttpClient() }

/**
 * 取一张远程海报。
 *
 * 刻意**只做内存缓存**、不做磁盘缓存：推广位是"可随时被服务端换掉"的内容，
 * 落盘缓存会让用户迟迟看不到新海报，而它本来就不是离线必须的东西。
 * 代价是每次冷启动会重新下载一次（轮播最多几条，可接受）。
 */
@Composable
private fun rememberPromoImage(url: String): State<Bitmap?> {
    val state = remember(url) { mutableStateOf(promoImageCache.get(url)) }
    LaunchedEffect(url) {
        if (state.value != null) return@LaunchedEffect
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                promoImageClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.byteStream()?.use { BitmapFactory.decodeStream(it) }
                }
            }.getOrNull()
        }
        if (bitmap != null) {
            promoImageCache.put(url, bitmap)
            state.value = bitmap
        }
    }
    return state
}
