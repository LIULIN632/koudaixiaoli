package com.pocket.xiaoli.util

/**
 * 绑定表单的**本地**校验（唯一真源）—— 只判断"格式对不对"，**不联网**。
 *
 * ## 为什么放在 `util/` 而不是 `ui/profile/`
 *
 * 它的消费者横跨两个包：`ui/profile`（教务 / 今日校园 / 校园卡表单）与
 * `ui/components`（宿舍表单）。塞进 `ui/profile` 会让 `ui/components` 反向依赖 `ui/profile`，
 * 而 `ui/profile` 本来就依赖 `ui/components`（`PocketField` 等）—— 那是一个包级环。
 * `util/` 是这两个包共同的底层，`maskStudentId` 当初也是这么放的。
 *
 * ## 为什么单独一个文件、而且全是纯函数
 *
 * 1. **可单测**：这些规则是"账号填错了却不告诉我"这类投诉的直接对策，
 *    必须能用用例把每条规则钉死（见 `BindingValidatorsTest`），而不是埋在 Composable 里；
 * 2. **一处口径**：同一个学号会出现在教务与今日校园两张表单里，[studentId] 只能有一份实现
 *   —— 与 `util/maskStudentId()` 是同一个理由（同一个值被两种规则解释，用户会怀疑自己看错了）。
 *
 * ## 约定
 *
 * 每个函数返回 **null = 通过**，否则返回一句**给用户看的中文**（直接渲染在字段下方，
 * 所以不要写"invalid student id"这种开发者文案，也不用句号）。
 *
 * ## ⚠️ 本地校验**不能**证明账号是对的
 *
 * "学号是 13 位数字"和"这 13 位数字是你的学号"是两件事。后者只有问教务系统才知道 ——
 * 那部分归 `AcademicVerifyViewModel`（跑一次真登录），两者是**互补**的：
 * 本地校验拦掉明显填错的（省一次网络往返），真验证给出确定性结论。
 */
internal object BindingValidators {

    /** 学号：6–20 位纯数字。烟台理工是 13 位，但留出余量，不把规则写死成 13。
     *
     *  ⚠️ 只认 **ASCII 数字**：`Char.isDigit()` 连全角「２０２３」也认，
     *  而中文输入法下全角数字是**真会发生**的输入 —— 拿它当学号去登录只会失败，
     *  不如在本地就拦下来。 */
    fun studentId(value: String): String? {
        val v = value.trim()
        if (v.isEmpty()) return "请填写学号"
        if (v.length !in 6..20 || v.any { it !in '0'..'9' }) return "学号应是一串 6–20 位数字"
        return null
    }

    /** 教务 / 今日校园密码：非空且至少 6 位（学校初始密码 `Ytlg@` + 6 位，正好 11 位）。 */
    fun password(value: String): String? {
        if (value.isEmpty()) return "请填写密码"
        if (value.length < 6) return "密码至少 6 位（初始密码是 Ytlg@ + 学号后 6 位）"
        return null
    }

    /** 学校名称：非空。默认会带出当前校区校名，一般不会触发。 */
    fun schoolName(value: String): String? =
        if (value.trim().isEmpty()) "请填写学校名称" else null

    /** 手机号（校园卡 / 易校园）：11 位、以 1 开头。同样只认 ASCII 数字。 */
    fun phone(value: String): String? {
        val v = value.trim()
        if (v.isEmpty()) return "请填写手机号"
        if (v.length != 11 || !v.startsWith("1") || v.any { it !in '0'..'9' }) {
            return "手机号应是 11 位数字"
        }
        return null
    }

    /**
     * 宿舍编号的**提示**（对接电费，可留空）—— 注意它不是"错误"，**不阻塞保存**。
     *
     * ## 为什么它和上面几个校验器不一样（2026-10 踩过的坑）
     *
     * 宿舍编号**不是本机编出来的值**，而是后勤缴费系统（`ydny.ytu.edu.cn`）给的一串，
     * 用户是从缴费页上**抄**过来的。`DormElectricityRepository.normalizeCode` 里写得明白：
     * **空格、`-`、`_`、`#` 都不敏感** —— 因为是手抄，写法五花八门。
     *
     * 我第一版把它当普通字段严格校验（只允许字母数字、还必须 ≥4 位），后果是
     * **合法编号会被挡在保存外面**：`BS10-S0602`、`bs10s0602 `、`BS10 S0602` 全都存不进去 ——
     * 而编号是电费**精确认人**的唯一依据（`DormElectricityRepository.parse` 的第 ① 条路径，
     * 比"楼栋 + 房间号猜地址"可靠得多）。挡掉它 = 让用户永远查不到电费。
     *
     * 所以：
     * - 字符集与 `normalizeCode` **对齐**（字母、数字、空格、`-`、`_`、`#`）；
     * - 返回值只用来在界面下方给一句**灰字提示**，调用方**不得**据此拒绝保存；
     * - 真正判断编号对不对的办法只有一个：**去查一次电费余额**（查得到就对上了）。
     */
    fun dormCodeHint(value: String): String? {
        val v = value.trim()
        if (v.isEmpty()) return null
        if (v.length !in 3..32) return "这串编号长度不太对（常见形如 BS10S0602），存了可能匹配不到电费账户"
        val allowed = v.all {
            it in '0'..'9' || it in 'a'..'z' || it in 'A'..'Z' ||
                it == ' ' || it == '-' || it == '_' || it == '#'
        }
        if (!allowed) return "这串里有不太像编号的字符（常见形如 BS10S0602），存了可能匹配不到电费账户"
        return null
    }
}
