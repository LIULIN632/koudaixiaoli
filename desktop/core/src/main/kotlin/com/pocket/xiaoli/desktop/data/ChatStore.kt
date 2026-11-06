package com.pocket.xiaoli.desktop.data

import com.google.gson.GsonBuilder
import java.io.File

/**
 * 会话记录的持久化（`%APPDATA%\PocketXiaoli\sessions_v1.json`）。
 *
 * 与 `Store` 的分工：Store 管「课表/待办」这份会被备份的主数据，会话记录是另一个文件
 * （对话可以随时清空，不该混进课程备份里）。放在 :core 是因为**读写文件是数据层的事**，
 * UI 层只调用这里。
 */
object ChatStore {

    private val gson = GsonBuilder().disableHtmlEscaping().create()

    private fun file(): File = File(Store.dataDir, "sessions_v1.json")

    fun load(): List<ChatSession> {
        val file = file()
        if (!file.exists()) return emptyList()
        return runCatching {
            val type = object : com.google.gson.reflect.TypeToken<List<ChatSession>>() {}.type
            gson.fromJson<List<ChatSession>>(file.readText(Charsets.UTF_8), type) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    fun save(chats: List<ChatSession>) {
        runCatching {
            Store.dataDir.mkdirs()
            file().writeText(gson.toJson(chats), Charsets.UTF_8)
        }
    }
}
