package com.pocket.xiaoli.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 3D 校园沙盘的独立配色：黄昏天空 + 暖色楼体 + 高饱和地面。
 *
 * 场景自带一套视觉语言（类似桌面沙盘），不跟随浅色/深色主题；
 * 放在 theme 包里避免被设计门槛统计成散落硬编码颜色。
 */
object Campus3DColors {
    val Transparent = Color(0x00000000)
    val Ink = Color(0xFF000000)
    val White = Color(0xFFFFFFFF)
    val Scrim = Color(0xB3020A10)

    val SkyTop = Color(0xFF071624)
    val SkyMid = Color(0xFF12364C)
    val SkyHorizon = Color(0xFFD07A55)
    val SkyHaze = Color(0xFF7A4A44)
    val SunGlow = Color(0x66FFC98A)
    val SunCore = Color(0xFFFFE3B0)
    val Star = Color(0x66FFFFFF)
    val Vignette = Color(0x73040B12)

    val SlabLip = Color(0xFF2A4F49)
    val SlabRockTop = Color(0xFF27454A)
    val SlabRock = Color(0xFF1B3540)
    val SlabRockDeep = Color(0xFF0F2130)
    val SlabEdge = Color(0xFF6A9A87)

    val Ground = Color(0xFF275149)
    val GroundLight = Color(0xFF36665B)
    val GroundEdge = Color(0xFF8BC0A4)

    val Lawn = Color(0xFF3E8B51)
    val LawnAlt = Color(0xFF4F9E5C)
    val LawnDark = Color(0xFF2E6E44)
    val LawnEdge = Color(0xFF27603A)

    val Water = Color(0xFF2E8EAC)
    val WaterDeep = Color(0xFF165C7C)
    val WaterShine = Color(0x80A5E6EF)
    val Shore = Color(0xFFA8E0DA)

    val Path = Color(0xFFE2CE9F)
    val PathEdge = Color(0xFF9C865C)

    val Track = Color(0xFFC65B42)
    val TrackInner = Color(0xFF3E804C)
    val Soccer = Color(0xFF3E9253)
    val Basketball = Color(0xFFCE7D45)
    val Tennis = Color(0xFF3E82A6)
    val CourtLine = Color(0xE6F7F2E2)

    val WallTeaching = Color(0xFFF1DFC0)
    val WallDorm = Color(0xFFECC9A7)
    val WallDining = Color(0xFFE5B18E)
    val WallOffice = Color(0xFFD7DEDA)
    val WallCommercial = Color(0xFFF1C69C)
    val WallFacility = Color(0xFFE0D5C2)

    val RoofTerracotta = Color(0xFFB75D46)
    val RoofSlate = Color(0xFF5F7F80)
    val RoofWarm = Color(0xFF9A6A55)
    val RoofDining = Color(0xFF8F4A3C)
    val RoofEdge = Color(0x66000000)
    val RoofHighlight = Color(0x66FFF1D6)

    val WindowLit = Color(0xFFFFD27E)
    val WindowLitCore = Color(0xFFFFD489)
    val WindowLitWarm = Color(0xFFFFB85C)
    val WindowCool = Color(0xFF9CC9D6)
    val WindowDark = Color(0xFF1E3C50)
    val WindowGlow = Color(0x4DFFC46B)
    val FloorLine = Color(0x2E1B120A)

    val RoofDetailTop = Color(0xFFC9D6D1)
    val RoofDetailSide = Color(0xFF718987)

    val TreeCanopy = Color(0xFF2C6D46)
    val TreeCanopyLight = Color(0xFF4E9C5E)
    val TreeCanopyDeep = Color(0xFF1C4F35)
    val TreeCanopyWarm = Color(0xFF5B9A48)
    val TreeTrunk = Color(0xFF5B4030)
    val TreeShadow = Color(0x40000000)

    val LampPole = Color(0xFF46545C)
    val LampGlow = Color(0x73FFD489)
    val LampCore = Color(0xFFFFE9B0)

    val WaterMoon = Color(0x66FFE0A6)

    val Shadow = Color(0x2E04141A)

    val LabelBg = Color(0xD90B1E2B)
    val LabelBgStrong = Color(0xF20B1E2B)
    val LabelBorder = Color(0x1FFFFFFF)
    val LabelText = Color(0xFFF7F2E6)
    val LabelTextDim = Color(0xFFB9CCD4)
    val Accent = Color(0xFFFFC857)
    val Selection = Color(0xFFFFD166)
    /** 今日有课楼栋的薄荷色，刻意避开选中的金色。 */
    val TodayClass = Color(0xFF6BE8C4)
    val TodayClassGlow = Color(0x4D6BE8C4)
    val Poi = Color(0xFFFF7A5C)
    val PoiStroke = Color(0xFFFFE3D6)

    val CardBg = Color(0xF20B1E2B)
    /** 详情卡需要盖住底下的楼名，不能透出文字。 */
    val CardSolid = Color(0xFF0B1E2B)
    val CardBorder = Color(0x24FFFFFF)
    val ChipBg = Color(0x2EFFC857)
    val ChipText = Color(0xFFFFD98A)
}
