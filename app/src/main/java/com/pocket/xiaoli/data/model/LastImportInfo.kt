package com.pocket.xiaoli.data.model

/**
 * Persisted summary of the most recent import attempt.
 *
 * ⚠️ **每个字段都必须有默认值** —— 只要有一个裸字段，Kotlin 就不生成无参构造，
 * Gson 会改用 `UnsafeAllocator` 直接反射赋值，于是**所有**默认值一起失效
 *（`DormInfo` v1.0.93 的闪退就是这个）。这道防线的可测形式见
 * `PrefsJsonDefaultsTest`；完整判据见 `CLAUDE.md` 的
 * "给存在 SharedPreferences 里的 JSON 类加字段"一节。
 */
data class LastImportInfo(
    val source: String = "",
    val courseCount: Int = 0,
    val success: Boolean = false,
    val detail: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * 读取边界归零。
     *
     * `source` 声明成 `String` 也可能**真的是 null**：Gson 的反射赋值绕过构造器，
     * 所以这个类曾经（三个裸字段时期）读出来的 `source` 是 null，
     * 而调用点写的是 `lastImport.source` —— 会直接把 `"null"` 当成来源名显示在
     * 「绑定信息」页上。`.orEmpty()` **不是多余的**（与 `DormInfo.sanitized()` 同一个理由）。
     *
     * ⚠️ `timestamp` 这里**不改成"现在"**：缺失就从 0 开始，由展示侧写成"时间未知"。
     * 把 0 补成当前时间等于**编一个假时间**出来，而这个 App 的规矩是不编造状态
     *（同类取舍见绑定状态卡的 `BindState.UNKNOWN`）。
     */
    fun sanitized(): LastImportInfo = copy(source = source.orEmpty())
}
