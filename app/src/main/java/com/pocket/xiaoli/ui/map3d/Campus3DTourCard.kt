package com.pocket.xiaoli.ui.map3d

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.ui.theme.invisibleTouchTarget
import com.pocket.xiaoli.ui.theme.Campus3DColors as Palette

/**
 * 导览播放卡：站序、来源说明与「上一站 / 播放 / 下一站 / 退出」。
 *
 * 卡片常驻在底部，不遮挡画面中央的取景；来源行只渲染数据事实，
 * 不给站点编造介绍文案。
 */
@Composable
internal fun Campus3DTourCard(
    stop: Campus3DTourStop,
    index: Int,
    total: Int,
    playing: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTogglePlay: () -> Unit,
    onExit: () -> Unit,
) {
    val last = index >= total - 1
    Surface(
        modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
        color = Palette.CardSolid,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Palette.CardBorder),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Palette.Accent, Palette.Poi, Palette.Accent)
                        )
                    )
            )
            Column(
                modifier = Modifier.padding(
                    start = 16.dp,
                    end = 12.dp,
                    top = 12.dp,
                    bottom = 12.dp,
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = Palette.ChipBg, shape = CircleShape) {
                        Text(
                            text = "第 ${index + 1} / $total 站",
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                            color = Palette.ChipText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(total) { dot ->
                            Box(
                                modifier = Modifier
                                    .height(3.dp)
                                    .width(if (dot == index) 16.dp else 6.dp)
                                    .background(
                                        color = if (dot == index) {
                                            Palette.Accent
                                        } else {
                                            Palette.LabelTextDim.copy(alpha = 0.32f)
                                        },
                                        shape = CircleShape,
                                    )
                            )
                        }
                    }
                    Surface(
                        onClick = onExit,
                        modifier = Modifier.invisibleTouchTarget(),
                        shape = CircleShape,
                        color = Palette.Transparent,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "退出导览",
                            tint = Palette.LabelTextDim,
                            modifier = Modifier.padding(6.dp).size(18.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stop.title,
                    color = Palette.LabelText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = stop.subtitle,
                    color = Palette.LabelTextDim,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = onPrevious,
                        enabled = index > 0,
                        modifier = Modifier.invisibleTouchTarget(),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = if (index > 0) {
                                Palette.LabelTextDim
                            } else {
                                Palette.LabelTextDim.copy(alpha = 0.32f)
                            },
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "上一站",
                            color = if (index > 0) {
                                Palette.LabelTextDim
                            } else {
                                Palette.LabelTextDim.copy(alpha = 0.32f)
                            },
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Surface(
                        onClick = onTogglePlay,
                        modifier = Modifier.invisibleTouchTarget(),
                        shape = CircleShape,
                        color = Palette.ChipBg,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = if (playing) {
                                    Icons.Filled.Pause
                                } else {
                                    Icons.Filled.PlayArrow
                                },
                                contentDescription = null,
                                tint = Palette.ChipText,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (playing) "暂停" else "自动播放",
                                color = Palette.ChipText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        onClick = onNext,
                        modifier = Modifier.invisibleTouchTarget(),
                        shape = RoundedCornerShape(12.dp),
                        color = Palette.Accent,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (last) "回到第 1 站" else "下一站",
                                color = Palette.CardSolid,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "›",
                                color = Palette.CardSolid.copy(alpha = 0.8f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}
