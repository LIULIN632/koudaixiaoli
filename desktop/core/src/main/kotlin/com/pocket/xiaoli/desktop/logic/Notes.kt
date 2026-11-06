package com.pocket.xiaoli.desktop.logic

import com.pocket.xiaoli.core.notes.NoteItem

/**
 * 桌面的笔记展示规则。
 *
 * 只放桌面端真正在用的规则：Android 的列表排序在 `AppContainer` 里、标题兜底写「无标题笔记」、
 * 时间用相对说法，和这里不一样。把它们塞进共享核心会变成"只有一端用的共享代码"，
 * 那和留一个没人读的字段是同一类问题。
 */

/** 按更新时间倒序（新笔记在最上面）；同一时刻写入时按 id 兜底，保证顺序稳定可测。 */
fun sortedNotes(notes: List<NoteItem>): List<NoteItem> =
    notes.sortedWith(compareByDescending<NoteItem> { it.updatedAt }.thenByDescending { it.id })

/**
 * 列表里的一行摘要：正文取第一个非空行、去掉首尾空白，最多 [maxChars] 字。
 *
 * 空正文和"只有空白"要区分开：前者返回空串（界面据此写「还没写内容」），
 * 后者是用户真敲了空格，都当成空会让用户以为笔记没保存。
 */
fun noteExcerpt(note: NoteItem, maxChars: Int = 40): String {
    val firstLine = note.content.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: return ""
    return if (firstLine.length <= maxChars) firstLine else firstLine.take(maxChars) + "…"
}

/** 标题为空时列表显示什么（不要显示空白行，用户会以为笔记丢了）。 */
fun noteTitle(note: NoteItem): String =
    note.title.ifBlank { if (note.content.isBlank()) "空白笔记" else "未命名笔记" }
