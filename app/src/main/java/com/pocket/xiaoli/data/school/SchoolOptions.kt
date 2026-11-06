package com.pocket.xiaoli.data.school

import com.pocket.xiaoli.core.campus.CampusInfo
import com.pocket.xiaoli.data.HOME_CITY
import com.pocket.xiaoli.data.JwSystemType
import com.pocket.xiaoli.data.School
import com.pocket.xiaoli.data.isCustomSchool
import com.pocket.xiaoli.data.isJwSystemAvailable
import com.pocket.xiaoli.data.isPickerSchool
import com.pocket.xiaoli.data.pickerRank

/**
 * 一个「可以接入」的学校 —— 首次引导与「绑定信息 → 换学校」共用的展示单元。
 *
 * 两个入口必须来自同一份判据：否则引导里能选、设置里选不到（或反过来），
 * 用户会以为 App 在两个地方说了两套话。
 *
 * @param hasCampusConfig 这所学校的校园服务（电费 / 洗衣 / 校园卡）与 3D 地图是否已收录。
 *   **false 不是错误**，但界面必须据此说实话 —— 让山东理工的学生看到烟台理工的电费入口，
 *   比不显示更糟。
 * @param protocolVerified **这套协议**（正方 / 强智）有没有用真账号跑通过。
 *   正方在烟台理工本校实测过；强智的客户端按公开协议实现了，但从未跑通真账号。
 *   ⚠️ 它说的是**协议**，不是"这所学校测过" —— 后者是 [isLoginProven]，两者故意分开：
 *   逐校实测目前只有 1 所，要是把它渲染成每所学校的标签，11/12 都挂着同一个标记，
 *   标记就退化成装饰（与服务页角标那条「40/42 都挂等于没挂」同一条纪律）。
 *   所以这个标签留在协议这一层，逐校那件事只在**验证失败时**说一句（见 `academicFailureHint`）。
 */
data class SchoolOption(
    val id: String,
    val name: String,
    val city: String,
    val systemLabel: String,
    val hasCampusConfig: Boolean,
    val protocolVerified: Boolean,
) {
    /** `淄博 · 正方教务`；协议没跑通过的一律带「待验证」，不让人背账号的锅。 */
    val subtitle: String
        get() = listOf(city, systemLabel, if (protocolVerified) null else "待验证")
            .filter { !it.isNullOrBlank() }
            .joinToString(" · ")
}

/**
 * 教务登录链路**在这所学校本身**上用真账号跑通过的名单。
 *
 * ## 为什么只有一个 id 而不是"正方都算"
 *
 * 正方是一套被几百所学校采购的系统，界面风格、版本（`isV8`）、路径、验证码策略都可能不同。
 * 在烟台理工跑通不等于在山东理工跑通 —— 把两者混为一谈，失败时用户只会怀疑自己的密码。
 *
 * ## 加一个 id 需要的证据（不是"应该没问题"）
 *
 * ① 用该校真实学号密码登录成功过一次；② 拉到课表且周次/节次对得上；
 * ③ 顺手记进 `docs/学校接入矩阵.md` 的说明里。
 * 这条名单同时被服务接入矩阵那列核对，所以加了就必须有实测撑着。
 */
val LOGIN_PROVEN_SCHOOL_IDS = setOf("YIT")

/** 这所学校的登录链路实测过没有 —— 决定失败提示要不要多说那一句。 */
fun School.isLoginProven(): Boolean = id in LOGIN_PROVEN_SCHOOL_IDS

/**
 * 可接入学校列表：选校白名单 ∩ 教务系统已支持，按「有校区的在前 → 烟台在前 → 校名」排。
 *
 * ## 为什么是交集而不是直接给白名单
 *
 * 白名单里有 4 所烟台院校是超星 / 青果（`isPickerSchool()` 放行），
 * 但这两家的原生适配器**没写**（`isJwSystemAvailable` 只放正方与强智）。
 * 把它们列出来 = 承诺一个点进去就走不通的入口。名单数据仍然保留，
 * 只是不进这两个入口 —— 与 `memory/01-constraints.md` §3「刻意保留，不是漏改」一致。
 *
 * @param schools 内置名单 + 用户自建学校（自建的一律视为可接入）
 * @param campuses 用来判定校园服务是否收录
 */
fun schoolOptions(schools: List<School>, campuses: List<CampusInfo>): List<SchoolOption> {
    val campusSchoolIds = campuses
        .filter { !it.underConstruction }
        .mapNotNull { it.schoolId }
        .toSet()
    return schools
        .asSequence()
        .filter { it.isCustomSchool() || (it.isPickerSchool() && isJwSystemAvailable(it.jwType)) }
        .filter { it.baseUrl.isNotBlank() }
        .map { school ->
            school to SchoolOption(
                id = school.id,
                name = school.name,
                city = school.city,
                systemLabel = school.jwType.label,
                hasCampusConfig = school.id in campusSchoolIds,
                // 这一条是**协议级**的：正方在烟台理工跑通过就算这套协议已验证；
                // "这所学校本身测过没有"看 LOGIN_PROVEN_SCHOOL_IDS，两件事别混。
                protocolVerified = school.jwType == JwSystemType.ZHENGFANG,
            )
        }
        .sortedWith(
            // 置顶顺序复用选校列表那一份判据（PINNED_SCHOOL_IDS），不在这里另定一套：
            // 两处排序不一致时，用户在引导里看到的"第一所"和设置里看到的"第一所"会不是同一所
            compareBy<Pair<School, SchoolOption>> { it.first.pickerRank() }
                .thenByDescending { it.second.hasCampusConfig }
                .thenByDescending { it.second.city == HOME_CITY }
                .thenBy { it.second.name }
        )
        .map { it.second }
        .toList()
}
