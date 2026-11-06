package com.pocket.xiaoli.desktop.state

import com.pocket.xiaoli.core.notes.NoteItem

/**
 * AppState 的笔记动作（状态字段仍留在 AppState 里，这里只是把写入口拆出去）。
 *
 * 编辑器本身的开关状态留在界面里（`remember`）：笔记没有第二个消费者 —— 不像考试要被
 * 提醒轮询读，所以没必要占 AppState 的行数（它已经贴着 800 行门槛）。
 */

/** 存一条笔记：id 已存在就整条覆盖，否则追加。 */
fun AppState.saveNote(note: NoteItem) {
    mutate { current ->
        val exists = current.notes.any { it.id == note.id }
        current.copy(
            notes = if (exists) current.notes.map { if (it.id == note.id) note else it } else current.notes + note,
        )
    }
}

fun AppState.deleteNote(id: String) {
    mutate { current -> current.copy(notes = current.notes.filter { it.id != id }) }
}
