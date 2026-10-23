package com.pocket.xiaoli.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.PocketXiaoliApplication
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 单栋教学楼的楼层分布图。
 *
 * 入口：地图导航 → 点教学楼 POI 的「楼层」
 *
 * 与「教室分布图管理」全屏页共用同一套数据与渲染
 * （[RoomFloorMapPanel] + `RoomDirectorySearch.layoutGrid`），
 * 区别只在于：这里**锁定一栋楼**，且是只读视图。
 *
 * 数据来源有两类，对用户是透明的：
 * 1. **内置数据** —— 由 `BuiltinRoomCells` 播种（使用者按格式整理后随版本发布）
 * 2. **用户自建** —— 在管理页手动录入
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildingMapScreen(
    buildingName: String,
    onBack: () -> Unit,
    onOpenRoom: (String) -> Unit,
    onOpenManage: () -> Unit,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as PocketXiaoliApplication).appContainer
    val campus by container.selectedCampus.collectAsStateWithLifecycle()
    val allCells by container.roomCells.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = buildingName.ifBlank { "楼层分布" },
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            RoomFloorMapPanel(
                allCells = allCells,
                campusId = campus.id,
                modifier = Modifier.fillMaxSize(),
                fixedBuilding = buildingName,
                onRoomClick = { onOpenRoom(it.room) },
                onOpenFullScreen = onOpenManage,
            )
        }
    }
}