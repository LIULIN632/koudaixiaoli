package com.pocket.xiaoli.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.map.CampusLocationSource
import com.pocket.xiaoli.data.map.CampusQrCodec
import com.pocket.xiaoli.data.map.CampusUserLocation
import com.pocket.xiaoli.data.map.model.CampusMapData
import com.pocket.xiaoli.data.map.model.CampusNode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin

/** 手动选择当前位置：建筑 → 楼层 → 附近关键点。 */
@Composable
fun CampusLocationPickerDialog(
    campusId: String,
    data: CampusMapData,
    initial: CampusUserLocation?,
    onConfirm: (CampusUserLocation) -> Unit,
    onDismiss: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    var buildingId by remember(initial) {
        mutableStateOf(initial?.buildingId ?: data.buildings.firstOrNull()?.id)
    }
    val floors = remember(data, buildingId) {
        data.floors.filter { it.buildingId == buildingId }
    }
    var floorId by remember { mutableStateOf(initial?.floorId) }
    LaunchedEffect(buildingId, floors) {
        if (floorId == null || floors.none { it.id == floorId }) {
            floorId = floors.firstOrNull()?.id
        }
    }
    val nodes = remember(data, floorId) {
        data.nodes.filter { node ->
            node.floorId == floorId && node.kind in setOf("entrance", "corridor", "stairs", "elevator", "room")
        }
    }
    var nodeId by remember { mutableStateOf(initial?.nodeId) }
    LaunchedEffect(floorId, nodes) {
        if (nodeId == null || nodes.none { it.id == nodeId }) {
            nodeId = nodes.firstOrNull()?.id
        }
    }
    var qrInput by remember { mutableStateOf("") }
    var qrError by remember { mutableStateOf<String?>(null) }
    var showScanner by remember { mutableStateOf(false) }

    val applyDecodedQr: (String) -> Unit = { raw ->
        val decoded = CampusQrCodec.decode(raw)
        val decodedBuildingId = decoded?.buildingId
        val buildingFound = decodedBuildingId != null && data.buildings.any { it.id == decodedBuildingId }
        val decodedNodeId = decoded?.nodeId
        val decodedFloorId = decoded?.floorId
        val floorFound = decodedFloorId == null || data.floors.any {
            it.id == decodedFloorId && it.buildingId == decodedBuildingId
        }
        val nodeFound = decodedNodeId == null || data.nodes.any {
            it.id == decodedNodeId && (decodedFloorId == null || it.floorId == decodedFloorId)
        }
        when {
            decoded == null -> qrError = "无法识别这个二维码"
            decoded.campusId != campusId -> qrError = "二维码校区不匹配"
            !buildingFound -> qrError = "当前地图没有这个楼栋"
            !floorFound -> qrError = "当前地图没有这个楼层"
            !nodeFound -> qrError = "当前地图没有这个关键点"
            else -> {
                buildingId = decoded.buildingId
                floorId = decoded.floorId
                nodeId = decoded.nodeId
                qrError = null
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置当前位置", fontWeight = FontWeight.Bold) },
        text = {
            if (data.buildings.isEmpty() && data.nodes.isEmpty()) {
                Text("校园地图数据尚未配置", fontSize = 13.sp, color = colors.textSecondary)
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    PickerLabel("二维码定位")
                    OutlinedTextField(
                        value = qrInput,
                        onValueChange = {
                            qrInput = it
                            qrError = null
                        },
                        label = { Text("粘贴位置二维码内容") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceXs)) {
                        TextButton(onClick = { applyDecodedQr(qrInput) }) {
                            Text("识别并填入", color = colors.accentMain)
                        }
                        TextButton(onClick = { showScanner = true }) {
                            Text("相机扫码", color = colors.accentMain)
                        }
                    }
                    qrError?.let {
                        Text(it, fontSize = 11.sp, color = colors.danger)
                    }

                    PickerLabel("建筑")
                    data.buildings.forEach { building ->
                        PickerRow(
                            text = building.name,
                            selected = building.id == buildingId,
                            onClick = {
                                buildingId = building.id
                                floorId = data.floors.firstOrNull { it.buildingId == building.id }?.id
                                nodeId = null
                            }
                        )
                    }

                    if (floors.isNotEmpty()) {
                        PickerLabel("楼层")
                        floors.forEach { floor ->
                            PickerRow(
                                text = floor.name.ifBlank { "${floor.level}层" },
                                selected = floor.id == floorId,
                                onClick = {
                                    floorId = floor.id
                                    nodeId = null
                                }
                            )
                        }
                    }

                    if (nodes.isNotEmpty()) {
                        PickerLabel("附近关键点")
                        nodes.forEach { node ->
                            PickerRow(
                                text = nodeLabel(data, node),
                                selected = node.id == nodeId,
                                onClick = { nodeId = node.id }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        CampusUserLocation(
                            campusId = campusId,
                            buildingId = buildingId,
                            floorId = floorId,
                            nodeId = nodeId,
                            source = CampusLocationSource.MANUAL,
                            updatedAt = System.currentTimeMillis(),
                        )
                    )
                },
                enabled = buildingId != null || nodeId != null,
            ) { Text("定位到这里") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )

    if (showScanner) {
        CampusQrScannerDialog(
            onDecoded = { raw ->
                qrInput = raw
                applyDecodedQr(raw)
                showScanner = false
            },
            onDismiss = { showScanner = false },
        )
    }
}

@Composable
private fun PickerLabel(text: String) {
    val colors = LocalPocketXiaoliColors.current
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = colors.textTertiary,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
    )
}

@Composable
private fun PickerRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(skin.radiusChip))
            .background(if (selected) colors.surfaceHighlight else colors.surfaceCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) colors.accentMain else colors.textPrimary,
        )
    }
}

private fun nodeLabel(data: CampusMapData, node: CampusNode): String {
    val roomName = data.rooms.firstOrNull { it.nodeId == node.id }?.name
    return when {
        !roomName.isNullOrBlank() -> roomName
        node.kind == "entrance" -> "建筑入口"
        node.kind == "corridor" -> "走廊"
        node.kind == "stairs" -> "楼梯"
        node.kind == "elevator" -> "电梯"
        else -> node.id
    }
}
