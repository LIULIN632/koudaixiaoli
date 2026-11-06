package com.pocket.xiaoli.desktop.logic

import com.pocket.xiaoli.desktop.data.Todo
import java.time.LocalDate

/** 待办的分桶。顺序就是界面上的展示顺序。 */
enum class TodoBucket { OVERDUE, TODAY, WEEK, LATER, NO_DUE, DONE }

data class TodoGroup(val bucket: TodoBucket, val label: String, val todos: List<Todo>) {
    val count: Int get() = todos.size
}

/**
 * 按截止日把待办分组。
 *
 * 判据刻意保守：日期解析不出（写成 `1.15`、`待定`）算「没填日期」而不是逾期 ——
 * 把一条正常待办标红成逾期，用户只会怀疑列表。
 */
fun groupTodos(todos: List<Todo>, today: LocalDate = LocalDate.now()): List<TodoGroup> {
    val done = todos.filter { it.done }
    val open = todos.filter { !it.done }
    val buckets = LinkedHashMap<TodoBucket, MutableList<Todo>>()
    TodoBucket.entries.forEach { buckets[it] = mutableListOf() }

    open.forEach { todo ->
        val due = runCatching { LocalDate.parse(todo.due.trim()) }.getOrNull()
        when {
            due == null -> buckets.getValue(TodoBucket.NO_DUE) += todo
            due.isBefore(today) -> buckets.getValue(TodoBucket.OVERDUE) += todo
            due == today -> buckets.getValue(TodoBucket.TODAY) += todo
            !due.isAfter(today.plusDays(6)) -> buckets.getValue(TodoBucket.WEEK) += todo
            else -> buckets.getValue(TodoBucket.LATER) += todo
        }
    }
    buckets.getValue(TodoBucket.DONE) += done

    val labels = mapOf(
        TodoBucket.OVERDUE to "已逾期",
        TodoBucket.TODAY to "今天截止",
        TodoBucket.WEEK to "本周内",
        TodoBucket.LATER to "以后",
        TodoBucket.NO_DUE to "没填截止日",
        TodoBucket.DONE to "已完成",
    )
    // 逾期按"逾期最久的在前"，其余按截止日升序；没日期的按创建时间倒序
    val sorted = buckets.map { (bucket, items) ->
        val ordered = when (bucket) {
            TodoBucket.OVERDUE -> items.sortedBy { parseDue(it) }
            TodoBucket.NO_DUE -> items.sortedByDescending { it.createdAt }
            TodoBucket.DONE -> items.sortedByDescending { it.createdAt }
            else -> items.sortedBy { parseDue(it) }
        }
        TodoGroup(bucket, labels.getValue(bucket), ordered)
    }
    return sorted.filter { it.count > 0 }
}

private fun parseDue(todo: Todo): LocalDate =
    runCatching { LocalDate.parse(todo.due.trim()) }.getOrElse { LocalDate.of(1970, 1, 1) }

/** 供 UI 显示的「还有几天」；解析不出日期返回 null。 */
fun daysUntil(due: String, today: LocalDate = LocalDate.now()): Long? =
    runCatching { LocalDate.parse(due.trim()) }.getOrNull()?.let { java.time.temporal.ChronoUnit.DAYS.between(today, it) }
