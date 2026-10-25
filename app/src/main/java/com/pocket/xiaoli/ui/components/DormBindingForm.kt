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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.pocket.xiaoli.data.CampusServiceKeys
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
    initialDormCode: String = "",
    preferredCampusId: String = DormBuildingCatalog.CAMPUS_MAIN,
    canUnbind: Boolean,
    onConfirm: (building: String, room: String, serviceCampusId: String?, dormCode: String) -> Unit,
    onUnbind: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val campusOptions = remember { CampusConfig.campuses.filterNot { it.underConstruction } }

    // ── 宿舍校区 → 该校区的宿舍楼 ──
    //
    // 2026-10：楼栋轮原来把 1-6/13（烟理）与 7-12（烟大）混在一个列表里，
    // 还另有一个「服务校区」轮可以手改 —— 用户看到的是"1 到 13 号楼"一长串，
    // 根本分不清哪栋属于哪个学校。现在先选校区，楼栋只列该校区的；
    // 服务校区**不再手改**：它由楼栋决定（见 `DormServiceRouter`），
    // 服务商则直接来自该校区配置（烟大 U净/趣智，烟理 浣熊/易校园）。
    var dormCampusId by remember(initialBuilding, preferredCampusId) {
        mutableStateOf(DormBuildingCatalog.find(initialBuilding)?.campusId ?: preferredCampusId)
    }
    val dormCampusIndex = campusOptions.indexOfFirst { it.id == dormCampusId }.coerceAtLeast(0)
    val dormCampus = campusOptions.getOrNull(dormCampusIndex) ?: CampusConfig.defaultCampus
    val campusBuildings = remember(dormCampusId) {
        DormBuildingCatalog.buildings.filter { it.campusId == dormCampusId }
    }
    val buildingLabels = remember(campusBuildings) { campusBuildings.map { it.name } }

    // ── 已有值反解成三个轮的位置 ──
    //
    // ⚠️ 反解不出来（老数据里那种缴费页抄来的 `A114`）时**保留原字符串不动**：
    // 猜成"1 楼 14 间"会静默把用户的房间改掉，而电费是照房号认人的。
    val parsed = remember(initialRoom) { DormRoomNumber.parse(initialRoom) }
    val legacyRoom = initialRoom.trim().takeIf { it.isNotBlank() && parsed == null }

    var buildingIndex by remember(initialBuilding, dormCampusId) {
        val exact = campusBuildings.indexOfFirst { it.name == initialBuilding }
        val matchedAlias = DormBuildingCatalog.find(initialBuilding)
            ?.let { found -> campusBuildings.indexOfFirst { it.id == found.id } }
        mutableIntStateOf((exact.takeIf { it >= 0 } ?: matchedAlias?.takeIf { it >= 0 } ?: 0).coerceAtLeast(0))
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

    var dormCode by remember(initialDormCode) { mutableStateOf(initialDormCode) }

    val selectedBuilding = campusBuildings.getOrNull(buildingIndex)?.name.orEmpty()
    // 南校（7-12 号楼）的房号带侧翼：n602 / s1602；本校区就是楼层 + 房号（412）
    val southCampus = dormCampusId == DormBuildingCatalog.CAMPUS_SOUTH
    val selectedFloor = DormRoomNumber.floors.getOrNull(floorIndex) ?: DormRoomNumber.MIN_FLOOR
    val selectedRoom = DormRoomNumber.rooms.getOrNull(roomIndex) ?: DormRoomNumber.MIN_ROOM
    val composedRoom = DormRoomNumber.compose(
        side = if (southCampus) DormRoomNumber.SIDES.getOrNull(sideIndex)?.first else null,
        floor = selectedFloor,
        room = selectedRoom,
    )
    val effectiveRoom = if (legacyRoom != null && !roomTouched) legacyRoom else composedRoom

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(skin.spaceMd),
    ) {
        // ── 第一步：宿舍校区 ──
        DormFormCard {
            Text(
                "宿舍校区",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(skin.spaceXs))
            Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                campusOptions.forEach { option ->
                    FilterChip(
                        selected = option.id == dormCampusId,
                        onClick = {
                            dormCampusId = option.id
                            roomTouched = true
                        },
                        label = { Text(option.schoolName ?: option.name, fontSize = 13.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.accentMain,
                            selectedLabelColor = androidx.compose.ui.graphics.Color.White,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(skin.spaceMicro))
            Text(
                "先选校区，下面的宿舍楼只会列出这个校区的。",
                fontSize = 11.sp,
                color = colors.textTertiary,
            )
        }

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

        // ── 服务商（只读，跟着宿舍校区走）──
        //
        // 以前这里是一个「服务校区」轮，允许用户把洗衣/洗澡/电费改到另一个学校。
        // 实际上一栋楼属于哪个学校是确定的，手动改只会制造"楼在烟理、洗澡走烟大"这种
        // 自相矛盾的状态。现在服务校区 = 宿舍校区，这里只把结果摆出来让用户核对。
        DormFormCard {
            Text(
                "宿舍服务商",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(skin.spaceXs))
            DORM_SERVICE_LINES.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(skin.spaceMd),
                ) {
                    pair.forEach { (label, key) ->
                        Column(modifier = Modifier.weight(1f)) {
                            Text(label, fontSize = 11.sp, color = colors.textTertiary)
                            Text(
                                dormCampus.services[key] ?: "未配置",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.textPrimary,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(skin.spaceMicro))
            Text(
                "洗衣、洗澡、热水、电费按宿舍校区自动匹配；选错校区就改上面的「宿舍校区」。",
                fontSize = 11.sp,
                lineHeight = 16.sp,
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
                val tail = dormCampus.shortName
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
                    dormCampus.id,
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
 * 宿舍绑定页要展示的四项服务：键与 `CampusConfig.services` 一一对应。
 *
 * 只用于**展示**（让用户看到"烟大宿舍 = U净 / 趣智校园"）；真正的路由仍走
 * `DormServiceRouter`，不要在别处硬编码服务商名字。
 */
private val DORM_SERVICE_LINES: List<Pair<String, String>> = listOf(
    "洗衣" to CampusConfig.SERVICE_LAUNDRY,
    "洗澡" to CampusServiceKeys.SHOWER,
    "热水" to CampusServiceKeys.HOT_WATER,
    "电费" to CampusServiceKeys.ELECTRICITY,
)

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
