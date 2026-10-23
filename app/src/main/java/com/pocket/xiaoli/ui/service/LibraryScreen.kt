package com.pocket.xiaoli.ui.service

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.libraryopac.LibraryOpacBook
import com.pocket.xiaoli.data.libraryopac.LibraryOpacRepository
import com.pocket.xiaoli.ui.components.ErrorCard
import com.pocket.xiaoli.ui.components.uniqueKeys
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import kotlinx.coroutines.launch
import com.pocket.xiaoli.ui.theme.LocalSkin

private data class LibraryAction(
    val title: String,
    val subtitle: String,
    val url: String? = null,
    val nativeSeat: Boolean = false,
)

/**
 * 图书馆原生入口页。
 *
 * - 馆藏检索：先尝试在 App 内解析 OPAC 结果，解析失败自动提供网页检索；
 * - 图书馆座位：走 App 内原生 [com.pocket.xiaoli.ui.seat.LibrarySeatScreen]；
 * - 其余官网栏目按需进入内置 WebView。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onBack: () -> Unit,
    onOpenWeb: (url: String, title: String) -> Unit,
    onOpenSeat: () -> Unit = {},
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<LibraryOpacBook>>(emptyList()) }
    var searchError by remember { mutableStateOf<String?>(null) }

    val actions = listOf(
        LibraryAction("我的借阅", "借阅信息 / 续借（校内网 / VPN）", "http://192.168.2.34/GGCX/readerinfo.htm"),
        LibraryAction("电子资源", "已购数据库 / VPN 使用说明", "https://lib.yitsd.edu.cn/info/1010/1153.htm"),
        LibraryAction("图书馆座位", "原生选座 · 暂离 · 退座", nativeSeat = true),
        LibraryAction("馆藏分布", "各阅览室 / 书库分布", "https://lib.yitsd.edu.cn/info/1004/1125.htm"),
        LibraryAction("借阅规则", "借期 / 续借 / 超期规则", "https://lib.yitsd.edu.cn/info/1004/1132.htm"),
    )

    fun searchCatalog() {
        val keyword = query.trim()
        if (keyword.isBlank() || searching) return
        searching = true
        searched = false
        results = emptyList()
        searchError = null
        scope.launch {
            LibraryOpacRepository.search(keyword)
                .onSuccess {
                    results = it
                    searched = true
                    searching = false
                }
                .onFailure {
                    searchError = it.message ?: "馆藏检索失败"
                    searched = true
                    searching = false
                }
        }
    }

    fun openWebSearch() {
        val keyword = query.trim()
        if (keyword.isNotBlank()) {
            onOpenWeb(LibraryOpacRepository.searchUrl(keyword), "馆藏检索")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("图书馆", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surfaceCard),
            )
        },
        containerColor = colors.surfaceAlt,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(skin.spaceMd),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(skin.radiusCard))
                        .background(colors.accentMain.copy(alpha = 0.12f))
                        .padding(skin.spaceWide),
                ) {
                    Text(
                        text = "烟台理工学院图书馆",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.height(skin.spaceSmall))
                    Text(
                        text = "原生馆藏检索 · 借阅 · 电子资源 · 原生座位预约",
                        fontSize = 13.sp,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(skin.spaceXs))
                    Text(
                        text = "借阅等系统需要校园网 / VPN 登录",
                        fontSize = 11.sp,
                        color = colors.textTertiary,
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("馆藏检索") },
                    placeholder = { Text("输入书名 / 作者 / ISBN") },
                    trailingIcon = {
                        IconButton(onClick = { searchCatalog() }, enabled = query.isNotBlank() && !searching) {
                            Icon(Icons.Default.Search, "搜索")
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { searchCatalog() }),
                )
            }

            if (searching) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(skin.radiusCard))
                            .background(colors.surfaceCard)
                            .padding(skin.spaceLg),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = colors.accentMain,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("正在检索馆藏…", fontSize = 13.sp, color = colors.textSecondary)
                    }
                }
            }

            if (!searching && results.isNotEmpty()) {
                item {
                    Text(
                        text = "馆藏结果（${results.size}）",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        modifier = Modifier.padding(start = skin.spaceXs, top = 4.dp),
                    )
                }
                // key 必须唯一：`detailUrl` 为空时退化成书名，同名书会撞
                // （同一本书的不同版本/馆藏点很常见），而重复 key 会让 LazyColumn 直接崩
                val bookKeys = uniqueKeys(results) { it.detailUrl.ifBlank { it.title } }
                itemsIndexed(results, key = { index, _ -> bookKeys[index] ?: index.toString() }) { _, book ->
                    LibraryBookRow(
                        book = book,
                        onClick = {
                            val target = book.detailUrl.ifBlank { LibraryOpacRepository.searchUrl(query) }
                            onOpenWeb(target, book.title)
                        },
                    )
                }
                item {
                    TextButton(onClick = { openWebSearch() }) {
                        Text("在网页中打开完整检索结果")
                    }
                }
            }

            if (!searching && searched && results.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(skin.radiusCard))
                            .background(colors.surfaceCard)
                            .padding(skin.spaceLg),
                    ) {
                        val err = searchError
                        if (err != null) {
                            // 真失败（网络/解析）→ 统一错误卡片 + 重试；
                            // 「检索到了但没结果」不是错误，别把两件事混成一句灰字
                            ErrorCard(
                                title = "馆藏检索失败",
                                message = err,
                                onRetry = { searchCatalog() },
                            )
                        } else {
                            Text(
                                text = "未解析到馆藏结果",
                                fontSize = 13.sp,
                                color = colors.textSecondary,
                                lineHeight = 20.sp,
                            )
                        }
                        Spacer(Modifier.height(skin.spaceSm))
                        TextButton(onClick = { openWebSearch() }) {
                            Text("用网页检索")
                        }
                    }
                }
            }

            actions.forEach { action ->
                item(key = action.title) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(skin.radiusCard))
                            .background(colors.surfaceCard)
                            .clickable {
                                if (action.nativeSeat) {
                                    onOpenSeat()
                                } else {
                                    action.url?.let { onOpenWeb(it, action.title) }
                                }
                            }
                            .padding(horizontal = skin.spaceLg, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = action.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textPrimary,
                                )
                                if (action.nativeSeat) {
                                    Spacer(Modifier.width(skin.spaceSmall))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(colors.accentMain.copy(alpha = 0.14f))
                                            .padding(horizontal = skin.spaceSmall, vertical = 2.dp),
                                    ) {
                                        Text(
                                            text = "原生",
                                            fontSize = 10.sp,
                                            color = colors.accentMain,
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(skin.spaceTiny))
                            Text(
                                text = action.subtitle,
                                fontSize = 12.sp,
                                color = colors.textTertiary,
                            )
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

            item {
                Text(
                    text = "馆藏结果来自学校 OPAC；如打不开，请连接校园网或使用学校 VPN。",
                    fontSize = 11.sp,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(horizontal = skin.spaceXs, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun LibraryBookRow(
    book: LibraryOpacBook,
    onClick: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(skin.radiusCard))
            .background(colors.surfaceCard)
            .clickable(onClick = onClick)
            .padding(horizontal = skin.spaceLg, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary,
            )
            val details = listOfNotNull(
                book.author.takeIf { it.isNotBlank() }?.let { "作者：$it" },
                book.publisher.takeIf { it.isNotBlank() }?.let { "出版社：$it" },
                book.year.takeIf { it.isNotBlank() },
                book.callNumber.takeIf { it.isNotBlank() }?.let { "索书号：$it" },
            ).joinToString(" · ")
            if (details.isNotBlank()) {
                Spacer(Modifier.height(skin.spaceXs))
                Text(
                    text = details,
                    fontSize = 11.sp,
                    color = colors.textTertiary,
                    lineHeight = 16.sp,
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(20.dp),
        )
    }
}