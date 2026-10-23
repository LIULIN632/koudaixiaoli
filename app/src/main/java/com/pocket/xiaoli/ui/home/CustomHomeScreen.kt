package com.pocket.xiaoli.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.di.AppContainer
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.invisibleTouchTarget
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 自定义首页：模块显隐 + 拖动手柄排序。
 *
 * 拖动时按固定行高计算跨过的行数，实时调用 AppContainer.moveHomeModule；
 * 不引入第三方 reorderable 库，保持依赖干净。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomHomeScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val modules by container.homeModules.collectAsStateWithLifecycle()
    val hidden by container.hiddenHomeModules.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val rowHeightPx = with(density) { 76.dp.toPx() }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("自定义首页", fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { container.resetHomeLayout() }) {
                        Text("恢复默认", fontSize = 12.sp)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(skin.spaceXs),
        ) {
            Spacer(Modifier.height(skin.spaceSm))
            Text(
                "拖动右侧手柄排序；开关控制模块是否在首页显示。",
                fontSize = 12.sp,
                color = colors.textTertiary,
            )
            Spacer(Modifier.height(skin.spaceSm))

            modules.forEach { module ->
                val visible = module.key !in hidden
                val isDragging = draggingKey == module.key
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(76.dp)
                        .background(
                            color = if (isDragging) colors.surfaceHighlight else colors.surfaceCard,
                            shape = RoundedCornerShape(skin.radiusChip),
                        )
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(module.label, fontSize = 14.sp, color = colors.textPrimary)
                        Text(module.description, fontSize = 11.sp, color = colors.textTertiary)
                    }
                    Switch(
                        checked = visible,
                        onCheckedChange = { container.setHomeModuleEnabled(module, it) },
                    )
                    Icon(
                        imageVector = Icons.Outlined.DragHandle,
                        contentDescription = "拖动排序",
                        tint = colors.textTertiary,
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .size(30.dp)
                            // 30dp 的拖拽柄本来就够呛，而它的交互还是**长按**拖动 ——
                            // 热区越小越按不住。抬高热区，可见图标不变。
                            .invisibleTouchTarget()
                            .pointerInput(module.key) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggingKey = module.key
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                        val current = container.homeModules.value
                                        val from = current.indexOfFirst { it.key == draggingKey }
                                        if (from >= 0) {
                                            val steps = (dragOffset / rowHeightPx).toInt()
                                            if (steps != 0) {
                                                val to = (from + steps).coerceIn(0, current.lastIndex)
                                                if (to != from) {
                                                    container.moveHomeModule(from, to)
                                                    dragOffset -= (to - from) * rowHeightPx
                                                }
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        draggingKey = null
                                        dragOffset = 0f
                                    },
                                    onDragCancel = {
                                        draggingKey = null
                                        dragOffset = 0f
                                    },
                                )
                            },
                    )
                }
            }

            Spacer(Modifier.height(skin.spaceMd))
        }
    }
}


