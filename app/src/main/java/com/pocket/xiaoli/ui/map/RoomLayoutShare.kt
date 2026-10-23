package com.pocket.xiaoli.ui.map

import android.content.Context
import android.content.Intent
import com.pocket.xiaoli.data.map.RoomCell
import com.pocket.xiaoli.util.RoomLayoutExporter

/** 把当前楼层教室分布导出为内置投稿文本，并走系统分享面板。 */
fun shareRoomLayout(
    context: Context,
    cells: List<RoomCell>,
    title: String,
) {
    if (cells.isEmpty()) return
    val subject = "教室分布图 · $title"
    val text = RoomLayoutExporter.toBuiltinText(cells)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TITLE, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "分享教室分布图"))
}