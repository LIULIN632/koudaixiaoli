package com.pocket.xiaoli.desktop.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * 自绘线性图标集（24×24，1.8 描边，圆角端点）。
 *
 * ## 为什么不引入 material-icons-extended
 *
 * 桌面端要的是**一套线性的、同一粗细的图标**，与移动端的手绘风格一致；
 * 而 Material 图标是面性的、粗细不可调。这里用 SVG 路径 + 描边画，
 * 全局由 `Icon(tint = …)` 着色，不依赖任何图标库版本。
 */
object PocketIcons {

    private fun line(name: String, vararg paths: String, width: Float = 1.8f): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        paths.forEach { d ->
            builder.addPath(
                pathData = addPathNodes(d),
                stroke = SolidColor(Color(0xFF1F2937)),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }

    // ═══ 导航 ═══
    val Home: ImageVector = line(
        "home",
        "M4 10.5 L12 4 L20 10.5 L20 19 A1.5 1.5 0 0 1 18.5 20.5 L5.5 20.5 A1.5 1.5 0 0 1 4 19 Z",
        "M9.5 20.5 L9.5 14.5 L14.5 14.5 L14.5 20.5",
    )
    val Timetable: ImageVector = line(
        "timetable",
        "M4.5 7.5 L19.5 7.5 L19.5 20 A1 1 0 0 1 18.5 21 L5.5 21 A1 1 0 0 1 4.5 20 Z",
        "M4.5 7.5 L4.5 6 A1 1 0 0 1 5.5 5 L18.5 5 A1 1 0 0 1 19.5 6 L19.5 7.5",
        "M8 3 L8 7 M16 3 L16 7",
        "M8 11.5 L16 11.5 M8 15.5 L12.5 15.5",
    )
    val Chat: ImageVector = line(
        "chat",
        "M5 5.5 L19 5.5 A1.5 1.5 0 0 1 20.5 7 L20.5 15 A1.5 1.5 0 0 1 19 16.5 L11 16.5 L7 20 L7 16.5 L5 16.5 A1.5 1.5 0 0 1 3.5 15 L3.5 7 A1.5 1.5 0 0 1 5 5.5 Z",
        "M8 9.5 L16 9.5 M8 12.8 L13 12.8",
    )
    val Services: ImageVector = line(
        "services",
        "M4 4 L10 4 L10 10 L4 10 Z",
        "M14 4 L20 4 L20 10 L14 10 Z",
        "M4 14 L10 14 L10 20 L4 20 Z",
        "M14 14 L20 14 L20 20 L14 20 Z",
    )
    val Profile: ImageVector = line(
        "profile",
        "M12 11.2 A3.9 3.9 0 1 0 12 3.4 A3.9 3.9 0 1 0 12 11.2 Z",
        "M4.8 20.6 C5.8 16.6 8.7 15 12 15 C15.3 15 18.2 16.6 19.2 20.6",
    )

    // ═══ 方向与操作 ═══
    val ChevronRight: ImageVector = line("chevron_right", "M9 4.8 L16.2 12 L9 19.2")
    val ChevronLeft: ImageVector = line("chevron_left", "M15 4.8 L7.8 12 L15 19.2")
    val ChevronDown: ImageVector = line("chevron_down", "M4.8 9 L12 16.2 L19.2 9")
    val Plus: ImageVector = line("plus", "M12 5 L12 19", "M5 12 L19 12")
    val Close: ImageVector = line("close", "M6 6 L18 18", "M18 6 L6 18")
    val Check: ImageVector = line("check", "M5 12.6 L10 17.6 L19 7")
    val Search: ImageVector = line(
        "search",
        "M11 4 A7 7 0 1 0 11 18 A7 7 0 1 0 11 4 Z",
        "M16.2 16.2 L21 21",
    )
    val Send: ImageVector = line(
        "send",
        "M4 11.4 L20.5 4 L13.6 20.6 L11.4 13.6 Z",
        "M11.4 13.6 L20.5 4",
    )
    val Settings: ImageVector = line(
        "settings",
        "M12 8.6 A3.4 3.4 0 1 0 12 15.4 A3.4 3.4 0 1 0 12 8.6 Z",
        "M12 2.8 L12 5 M12 19 L12 21.2 M2.8 12 L5 12 M19 12 L21.2 12",
        "M5.5 5.5 L7 7 M17 17 L18.5 18.5 M18.5 5.5 L17 7 M7 17 L5.5 18.5",
    )
    val Edit: ImageVector = line(
        "edit",
        "M4 20 L4.4 16.2 L15.2 5.4 L18.6 8.8 L7.8 19.6 Z",
        "M13.4 7.2 L16.8 10.6",
    )
    val Trash: ImageVector = line(
        "trash",
        "M4 6.8 L20 6.8",
        "M9 6.8 L9 4.4 L15 4.4 L15 6.8",
        "M6.2 6.8 L7.2 20 L16.8 20 L17.8 6.8",
        "M10.2 10.6 L10.6 16.4 M13.8 10.6 L13.4 16.4",
    )
    val More: ImageVector = line("more", "M5 12 L5.2 12", "M12 12 L12.2 12", "M19 12 L19.2 12", width = 2.6f)

    // ═══ 信息与状态 ═══
    val Clock: ImageVector = line(
        "clock",
        "M12 3 A9 9 0 1 0 12 21 A9 9 0 1 0 12 3 Z",
        "M12 7.2 L12 12.4 L15.8 14.4",
    )
    val Pin: ImageVector = line(
        "pin",
        "M12 21 C12 21 5 14.6 5 9.6 A7 7 0 1 1 19 9.6 C19 14.6 12 21 12 21 Z",
        "M12 6.9 A2.7 2.7 0 1 0 12 12.3 A2.7 2.7 0 1 0 12 6.9 Z",
    )
    val Book: ImageVector = line(
        "book",
        "M4 5.2 L4 19.2 L10.6 19.2 L12 20.4 L13.4 19.2 L20 19.2 L20 5.2 L13.4 5.2 L12 6.4 L10.6 5.2 Z",
        "M12 6.4 L12 20.4",
    )
    val Lock: ImageVector = line(
        "lock",
        "M6 10.5 L18 10.5 L18 20 L6 20 Z",
        "M8.4 10.5 L8.4 7.6 A3.6 3.6 0 0 1 15.6 7.6 L15.6 10.5",
    )
    val LinkExternal: ImageVector = line(
        "link_external",
        "M13.6 4 L20 4 L20 10.4",
        "M20 4 L11.2 12.8",
        "M18 14 L18 19 A1 1 0 0 1 17 20 L5 20 A1 1 0 0 1 4 19 L4 7 A1 1 0 0 1 5 6 L10 6",
    )
    val Download: ImageVector = line(
        "download",
        "M12 4 L12 14.8",
        "M7.2 10.4 L12 15.2 L16.8 10.4",
        "M4 19.4 L20 19.4",
    )
    val Upload: ImageVector = line(
        "upload",
        "M12 20 L12 9.2",
        "M7.2 13.6 L12 8.8 L16.8 13.6",
        "M4 4 L20 4",
    )
    val Info: ImageVector = line(
        "info",
        "M12 3 A9 9 0 1 0 12 21 A9 9 0 1 0 12 3 Z",
        "M12 11 L12 16.6",
        "M12 7.6 L12 7.7",
    )
    val Copy: ImageVector = line(
        "copy",
        "M9 8.5 L9 5 A1 1 0 0 1 10 4 L19 4 A1 1 0 0 1 20 5 L20 14 A1 1 0 0 1 19 15 L15.5 15",
        "M5 8.5 L14 8.5 A1 1 0 0 1 15 9.5 L15 18.5 A1 1 0 0 1 14 19.5 L5 19.5 A1 1 0 0 1 4 18.5 L4 9.5 A1 1 0 0 1 5 8.5 Z",
    )
    val Refresh: ImageVector = line(
        "refresh",
        "M20 12 A8 8 0 1 1 17.2 6.2",
        "M20 4 L20 9 L15 9",
    )
    val Bell: ImageVector = line(
        "bell",
        "M12 4 A6 6 0 0 0 6 10 L6 15 L4.6 17 L19.4 17 L18 15 L18 10 A6 6 0 0 0 12 4 Z",
        "M10 20 L14 20",
    )
    val Moon: ImageVector = line(
        "moon",
        "M20 13.2 A8 8 0 1 1 10.8 4 A6.6 6.6 0 0 0 20 13.2 Z",
    )
    val Sun: ImageVector = line(
        "sun",
        "M12 7.6 A4.4 4.4 0 1 0 12 16.4 A4.4 4.4 0 1 0 12 7.6 Z",
        "M12 2.6 L12 4.4 M12 19.6 L12 21.4 M2.6 12 L4.4 12 M19.6 12 L21.4 12",
        "M5.4 5.4 L6.7 6.7 M17.3 17.3 L18.6 18.6 M18.6 5.4 L17.3 6.7 M6.7 17.3 L5.4 18.6",
    )
    val Palette: ImageVector = line(
        "palette",
        "M12 3 A9 9 0 1 0 21 12 C21 13.4 19.6 14.4 18.1 14.4 L16.2 14.4 C15.2 14.4 14.5 15.3 14.9 16.2 C15.5 17.6 14.6 19.2 13 19.2 A9 9 0 0 1 12 3 Z",
        "M8 8.4 L8.1 8.4 M12 6.6 L12.1 6.6 M15.8 8.6 L15.9 8.6 M7.4 13 L7.5 13",
    )
    val Timer: ImageVector = line(
        "timer",
        "M9 2.6 L15 2.6 M12 2.6 L12 4.4",
        "M12 4.4 A8 8 0 1 0 12 20.4 A8 8 0 1 0 12 4.4 Z",
        "M12 9 L12 13 L15 15",
    )
    val TodoList: ImageVector = line(
        "todo_list",
        "M4 6.4 L5.4 7.8 L7.6 5.4",
        "M10.6 6.6 L20 6.6",
        "M4 12.4 L5.4 13.8 L7.6 11.4",
        "M10.6 12.6 L20 12.6",
        "M4 18.4 L5.4 19.8 L7.6 17.4",
        "M10.6 18.6 L20 18.6",
    )
    val Sparkles: ImageVector = line(
        "sparkles",
        "M10.6 3.4 L12.2 8.2 L17 9.8 L12.2 11.4 L10.6 16.2 L9 11.4 L4.2 9.8 L9 8.2 Z",
        "M17.6 14.6 L18.4 16.8 L20.6 17.6 L18.4 18.4 L17.6 20.6 L16.8 18.4 L14.6 17.6 L16.8 16.8 Z",
    )
    /** 鲤鱼吉祥物标记（圆身 + 尾 + 眼）。 */
    val Fish: ImageVector = line(
        "fish",
        "M4.4 12 C4.4 8 8.4 4.8 13 4.8 C17.2 4.8 20.2 8 20.2 12 C20.2 16 17.2 19.2 13 19.2 C8.4 19.2 4.4 16 4.4 12 Z",
        "M4.4 12 L1.6 9.2 M4.4 12 L1.6 14.8",
        "M15.4 9.4 L15.5 9.4",
        "M6.6 10.4 C8 11 8.6 13 7.6 14.4",
        width = 1.7f,
    )
    val GraduationCap: ImageVector = line(
        "graduation",
        "M2.8 9.2 L12 5 L21.2 9.2 L12 13.4 Z",
        "M6.4 11 L6.4 16.2 C6.4 17.6 8.9 18.8 12 18.8 C15.1 18.8 17.6 17.6 17.6 16.2 L17.6 11",
        "M21.2 9.2 L21.2 14",
    )
    val CalendarDays: ImageVector = line(
        "calendar",
        "M4.5 7 L19.5 7 L19.5 20 A1 1 0 0 1 18.5 21 L5.5 21 A1 1 0 0 1 4.5 20 Z",
        "M8 3.4 L8 7 M16 3.4 L16 7",
        "M4.5 11 L19.5 11",
        "M8.4 14.6 L8.6 14.6 M12 14.6 L12.2 14.6 M15.6 14.6 L15.8 14.6 M8.4 17.8 L8.6 17.8 M12 17.8 L12.2 17.8",
        width = 1.7f,
    )
    val Folder: ImageVector = line(
        "folder",
        "M3.6 7.4 A1.4 1.4 0 0 1 5 6 L9.2 6 L11 8.4 L19 8.4 A1.4 1.4 0 0 1 20.4 9.8 L20.4 19.6 A1.4 1.4 0 0 1 19 21 L5 21 A1.4 1.4 0 0 1 3.6 19.6 Z",
    )
    val Heart: ImageVector = line(
        "heart",
        "M12 20.4 C12 20.4 3.6 15.2 3.6 9.4 A4.4 4.4 0 0 1 12 8 A4.4 4.4 0 0 1 20.4 9.4 C20.4 15.2 12 20.4 12 20.4 Z",
    )
    val Grid: ImageVector = line(
        "grid",
        "M4 9 L20 9 M4 15 L20 15",
        "M9 4 L9 20 M15 4 L15 20",
    )
}
