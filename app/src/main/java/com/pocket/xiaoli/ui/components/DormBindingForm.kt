package com.pocket.xiaoli.ui.components

import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import android.widget.NumberPicker
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.pocket.xiaoli.data.CampusConfig
import com.pocket.xiaoli.data.model.DormBuildingCatalog
import com.pocket.xiaoli.data.model.DormRoomNumber
import com.pocket.xiaoli.util.BindingValidators
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin

// ═══ 宿舍绑定表单（整页）═══
//
// 「宿舍编号」是后勤缴费系统用的编号（缴费页上的「用户编号」）。它与房间号**不是一回事**：
// 房间号是人写的（602 / n602），编号是系统给的（BS10S0602）。电费余额要在缴费系统的账户
// 列表里认领本人那一间，列表里只有地址和编号两样可用 —— 地址写法五花八门，编号才是唯一标识。
// 所以这里允许单独填；**留空也能用**（退回"楼栋 + 房间号"匹配）。
//
// ⚠️ 2026-10 之前这里是一个 `AlertDialog`，后来改成整页；同一批里**转盘本身也重做了一次**（见下）。
@Composable
fun DormBindingContent(
    initialBuilding: String,
    initialRoom: String,
    initialServiceCampusId: String? = null,
    initialDormCode: String = "",
    preferredCampusId: String = DormBuildingCatalog.CAMPUS_MAIN,
    canUnbind: Boolean,
    onConfirm: (building: String, room: String, serviceCampusId: String?, dormCode: String) -> Unit,
    onUnbind: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val buildings = remember { DormBuildingCatalog.buildings }
    // 轮子里只放**短**文案（`10号宿舍楼`）：带上校区标记（`10号宿舍楼 · 南校`）在四列布局里
    // 会被截成"10号宿舍楼 · 南"—— 校区信息由下面的「服务校区」轮与实时摘要表达，不靠截断的字。
    val buildingLabels = remember(buildings) { buildings.map { it.name } }
    val campusOptions = remember { CampusConfig.campuses.filterNot { it.underConstruction } }
    val campusLabels = remember(campusOptions) { campusOptions.map { it.shortName } }

    // ── 已有值反解成三个轮的位置 ──
    //
    // ⚠️ 反解不出来（老数据里那种缴费页抄来的 `A114`）时**保留原字符串不动**：
    // 猜成"1 楼 14 间"会静默把用户的房间改掉，而电费是照房号认人的。
    val parsed = remember(initialRoom) { DormRoomNumber.parse(initialRoom) }
    val legacyRoom = initialRoom.trim().takeIf { it.isNotBlank() && parsed == null }

    var buildingIndex by remember(initialBuilding, preferredCampusId) {
        val exact = buildings.indexOfFirst { it.name == initialBuilding }
        val matchedAlias = DormBuildingCatalog.find(initialBuilding)
            ?.let { found -> buildings.indexOfFirst { it.id == found.id } }
        val preferred = buildings.indexOfFirst { it.campusId == preferredCampusId }
        mutableIntStateOf(
            (exact.takeIf { it >= 0 } ?: matchedAlias?.takeIf { it >= 0 } ?: preferred).coerceAtLeast(0)
        )
    }
    var sideIndex by remember(initialRoom) {
        mutableIntStateOf(
            DormRoomNumber.SIDES.indexOfFirst { it.first == parsed?.side }.coerceAtLeast(0)
        )
    }
    var floorIndex by remember(initialRoom) {
        mutableIntStateOf(DormRoomNumber.floors.indexOf(parsed?.floor).coerceAtLeast(0))
    }
    var roomIndex by remember(initialRoom) {
        mutableIntStateOf(DormRoomNumber.rooms.indexOf(parsed?.room).coerceAtLeast(0))
    }
    // "用户有没有动过房间号" —— 决定历史房号是不是原样写回
    var roomTouched by remember(initialRoom) { mutableStateOf(false) }

    var campusIndex by remember(initialBuilding, initialServiceCampusId, preferredCampusId, campusOptions) {
        // ⚠️ `initialBuilding` **必须在 remember 的键里**：服务校区默认值是由楼栋推出来的
        //（1-6/13 → 本校区，7-12 → 南校），漏了它就会"换了楼栋、服务校区还停在上一栋"——
        // 而服务校区决定洗澡 / 洗衣 / 电费走哪套服务商，错一个就是整条生活服务指向错误的学校。
        val defaultCampusId = DormBuildingCatalog.find(initialBuilding)?.campusId ?: preferredCampusId
        val preferred = campusOptions.indexOfFirst { it.id == defaultCampusId }
        val exact = initialServiceCampusId
            ?.let { id -> campusOptions.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
        mutableIntStateOf((exact ?: preferred).coerceAtLeast(0))
    }

    var dormCode by remember(initialDormCode) { mutableStateOf(initialDormCode) }

    val selectedBuilding = buildings.getOrNull(buildingIndex)?.name.orEmpty()
    val selectedCampus = buildings.getOrNull(buildingIndex)?.campusId
    // 南校（7-12 号楼）的房号带侧翼：n602 / s1602；本校区就是楼层 + 房号（412）
    val southCampus = selectedCampus == DormBuildingCatalog.CAMPUS_SOUTH
    val selectedFloor = DormRoomNumber.floors.getOrNull(floorIndex) ?: DormRoomNumber.MIN_FLOOR
    val selectedRoom = DormRoomNumber.rooms.getOrNull(roomIndex) ?: DormRoomNumber.MIN_ROOM
    val composedRoom = DormRoomNumber.compose(
        side = if (southCampus) DormRoomNumber.SIDES.getOrNull(sideIndex)?.first else null,
        floor = selectedFloor,
        room = selectedRoom,
    )
    val effectiveRoom = if (legacyRoom != null && !roomTouched) legacyRoom else composedRoom
    val selectedServiceCampusId = campusOptions.getOrNull(campusIndex)?.id

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(skin.spaceMd),
    ) {
        // ── 房间号：楼层 + 房号（+ 南校的侧翼）──
        //
        // ⚠️ 2026-10 重做的理由：改造前是**一个 720 项的房间号转盘**（1-18 层 × 01-40 间拼成
        // `101`…`1840`）。后果有两层：选「412」要在一长串里滚过去；而且它**根本不认南校的
        // 房号规则**（南校是 `n602` 这种带侧翼的写法）—— 南校同学在转盘里找不到自己的房。
        // 现在拆成"侧翼 / 楼层 / 房号"三级短轮（本校区只有后两级），每一级都短到能一眼扫完。
        DormFormCard {
            Row(
                modifier = Modifier.fillMaxWidth().height(180.dp),
                horizontalArrangement = Arrangement.spacedBy(skin.spaceSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 楼栋轮
                WheelColumn(
                    label = "宿舍楼",
                    modifier = Modifier.weight(if (southCampus) 1.5f else 1.6f),
                ) {
                    WheelPicker(
                        values = buildingLabels,
                        selectedIndex = buildingIndex,
                        onSelected = { index ->
                            buildingIndex = index
                            roomTouched = true
                            // 换楼栋时把服务校区拉回该楼的默认值，用户仍可再手动改
                            val defaultCampusId = buildings.getOrNull(index)?.campusId
                            val defaultIndex = campusOptions.indexOfFirst { it.id == defaultCampusId }
                            if (defaultIndex >= 0) campusIndex = defaultIndex
                        },
                        height = 150.dp,
                    )
                }
                // 侧翼轮：只在南校楼出现（本校区房号没有方位）
                if (southCampus) {
                    WheelColumn(label = "朝向", modifier = Modifier.weight(1f)) {
                        WheelPicker(
                            values = DormRoomNumber.SIDES.map { it.second },
                            selectedIndex = sideIndex,
                            onSelected = { sideIndex = it; roomTouched = true },
                            height = 150.dp,
                        )
                    }
                }
                WheelColumn(label = "楼层", modifier = Modifier.weight(1f)) {
                    WheelPicker(
                        values = DormRoomNumber.floors.map { DormRoomNumber.floorLabel(it) },
                        selectedIndex = floorIndex,
                        onSelected = { floorIndex = it; roomTouched = true },
                        height = 150.dp,
                    )
                }
                WheelColumn(label = "房间", modifier = Modifier.weight(1.2f)) {
                    WheelPicker(
                        values = DormRoomNumber.rooms.map { DormRoomNumber.roomLabel(it) },
                        selectedIndex = roomIndex,
                        onSelected = { roomIndex = it; roomTouched = true },
                        height = 150.dp,
                    )
                }
            }
            // 历史房号只在"用户没动过转盘"时保留 —— 明说出来，别让它悄悄生效
            if (legacyRoom != null && !roomTouched) {
                Text(
                    "当前保存的是历史房号「$legacyRoom」（认不出它的楼层/房间，所以没动它）；" +
                        "拨一下上面的转盘就会改成按楼层 + 房间拼出来的号。",
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    lineHeight = 15.sp,
                )
            }
        }

        // ── 服务校区 ──
        DormFormCard {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                WheelPicker(
                    values = campusLabels,
                    selectedIndex = campusIndex,
                    onSelected = { campusIndex = it },
                    width = 200.dp,
                    height = 116.dp,
                )
                Spacer(Modifier.height(skin.spaceXs))
                Text("服务校区", fontSize = 12.sp, color = colors.textSecondary)
            }
            Text(
                "默认：1-6 / 13 号楼走本校区，7-12 号楼走烟大南校；" +
                    "自动匹配不准时可以在这里手动修正。",
                fontSize = 11.sp,
                color = colors.textTertiary,
            )
        }

        // ── 宿舍编号（可选）──
        //
        // ⚠️ 这一段是**电费**认人用的，与上面的房间号**不是一回事**（见 DormInfo 的 KDoc）：
        //   · 编号 = 后勤缴费系统给的唯一标识（缴费页上的「用户编号」，形如 BS10S0602）→ **精确认人**；
        //   · 房间号 = 人写的、也是门牌上的号（412 / n602）→ 只在没填编号时用来**猜地址**兜底。
        // 所以这里**不拿房间号那套严格口径去管编号**：用户是从缴费页抄过来的，
        // 空格 / - / _ / # 都可能带着（normalizeCode 对这些都不敏感），
        // 校验只给一句灰字提示，**绝不因此拦住保存** —— 挡住了用户就永远查不到电费。
        DormFormCard {
            Text(
                "宿舍编号（对接电费，可留空）",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary,
            )
            PocketField(
                label = "用户编号",
                value = dormCode,
                onValueChange = { dormCode = it },
                placeholder = "缴费页上的「用户编号」，如 BS10S0602",
                // 编号是字母 + 数字（`BS10S0602`），**不要中文候选** —— 这一串要跟缴费系统逐字对上。
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            )
            Text(
                "填了它，电费余额按编号**精确认人**；留空则退回按楼栋 + 房间号匹配地址。" +
                    "抄的时候空格和横线不用管，系统会自动忽略。",
                fontSize = 11.sp,
                color = colors.textTertiary,
            )
            // 只是提示，不是错误（不改描边、不禁用按钮）
            BindingValidators.dormCodeHint(dormCode)?.let { hint ->
                Text("⚠ $hint", fontSize = 11.sp, color = colors.textSecondary, lineHeight = 15.sp)
            }
            Text(
                "怎么确认编号填对了：去「服务 → 宿舍电费」查一次余额 —— 余额出来了就是对上了。",
                fontSize = 11.sp,
                color = colors.textTertiary,
            )
        }

        // ── 实时摘要：一路选下来最终会存成什么，写清楚再让用户按保存 ──
        Text(
            text = if (selectedBuilding.isBlank()) {
                "请选择宿舍楼"
            } else {
                val tail = campusOptions.getOrNull(campusIndex)?.shortName.orEmpty()
                val code = dormCode.trim()
                "$selectedBuilding  $effectiveRoom · $tail" + if (code.isBlank()) "" else " · $code"
            },
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = colors.accentMain,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        // ⚠️ 按钮**不再静默禁用**：以前是"两个字段有一个空就灰掉，也不说为什么"。
        // 现在可以点。⚠️ 而且**不拿宿舍编号的格式去拦保存** —— 编号是外部系统给的一串，
        // 格式校验最多只能给提示（见上面那段），拦下来等于让人绑不了电费。
        Button(
            onClick = {
                onConfirm(
                    selectedBuilding,
                    effectiveRoom,
                    selectedServiceCampusId,
                    dormCode.trim(),
                )
            },
            enabled = selectedBuilding.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accentMain,
                disabledContainerColor = colors.surfaceHighlight,
                disabledContentColor = colors.textTertiary,
            ),
        ) {
            Text("保存宿舍", fontWeight = FontWeight.Bold)
        }
        if (canUnbind) {
            TextButton(
                onClick = onUnbind,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("解绑宿舍", color = colors.danger, fontSize = 13.sp)
            }
        }
    }
}

/**
 * 转盘 + 下方小标题的竖排容器。
 *
 * 顺带承担"选中行高亮条"的位置：高亮画在 [WheelPicker] **之下**（`NumberPicker` 的背景被
 * 置成透明，所以能透出来），而不是盖在上面 —— 盖上去会吃掉滑动的手势。
 */
@Composable
private fun WheelColumn(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = LocalPocketXiaoliColors.current
    val skin = LocalSkin.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            // 选中行高亮：改造前转盘只有两行细分割线，用户看不出"哪一行是当前值"
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(RoundedCornerShape(skin.radiusChip))
                    .background(colors.surfaceHighlight),
            )
            content()
        }
        Spacer(Modifier.height(skin.spaceSmall))
        Text(label, fontSize = 12.sp, color = colors.textSecondary)
    }
}

/** 宿舍表单里的一张分组卡：与设置页 `SettingsCard` 同一套观感（同圆角、同底色、同内距）。 */
@Composable
private fun DormFormCard(content: @Composable ColumnScope.() -> Unit) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusLarge),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(skin.cardPadding),
            verticalArrangement = Arrangement.spacedBy(skin.spaceSm),
            content = content,
        )
    }
}

@Composable
private fun WheelPicker(
    values: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    width: Dp = 132.dp,
    height: Dp = 150.dp,
) {
    AndroidView(
        factory = { context ->
            NumberPicker(context).apply {
                minValue = 0
                maxValue = (values.size - 1).coerceAtLeast(0)
                displayedValues = values.toTypedArray()
                wrapSelectorWheel = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                // 背景与自带分割线都去掉：背景透明才能让 Compose 画的"选中行高亮"透出来，
                // 自带那两条分割线则与高亮条重复（一起出现会像"三行都被选中"）。
                setBackgroundColor(AndroidColor.TRANSPARENT)
                dividerDrawable = null
                value = selectedIndex.coerceIn(0, maxValue)
                setOnValueChangedListener { _, _, newValue -> onSelected(newValue) }
            }
        },
        update = { picker ->
            val latestValues = values.toTypedArray()
            if (picker.displayedValues?.toList() != latestValues.toList()) {
                picker.displayedValues = latestValues
                picker.maxValue = (latestValues.size - 1).coerceAtLeast(0)
            }
            val clamped = selectedIndex.coerceIn(0, (latestValues.size - 1).coerceAtLeast(0))
            if (picker.value != clamped) picker.value = clamped
        },
        modifier = Modifier
            .width(width)
            .height(height)
    )
}
