package com.pocket.xiaoli.data.map

/**
 * 内置校园 POI 种子数据（M3 第一版）。
 *
 * 数据来源：公开信息整理（学校官网 / 地图标注 / 招标公告 / 图书馆 FAQ）。
 * 仅供站内检索与跳转第三方地图使用，不构成测绘数据。
 *
 * 注意：
 * 1. 楼栋名称以现场为准；用户可在「服务 → 校园导航」内自由增删改。
 * 2. 播种只在首次启动或 [VERSION] 升级时执行一次，用户删除后不会自动复活。
 * 3. 修改/新增内置数据后请把 [VERSION] 加一，老用户下次启动即可拿到增量。
 */
object BuiltinPois {

    /** 内置数据版本；内容有变更时 +1 */
    const val VERSION = 1

    /** 全部内置 POI（当前仅覆盖烟台理工本校区） */
    val all: List<CampusPoi> = ytlgMain()

    private const val CAMPUS_YTLG_MAIN = "ytlg_main"

    private fun ytlgMain(): List<CampusPoi> = listOf(
        // ── 教学楼 ──
        ytlgPoi(
            key = "teaching_1",
            name = "1号教学楼",
            category = PoiCategory.TEACHING,
            building = "1号教学楼",
            aliases = listOf("一教", "1教", "1号楼", "第一教学楼"),
            description = "公共课与专业课教室"
        ),
        ytlgPoi(
            key = "teaching_2",
            name = "2号教学楼",
            category = PoiCategory.TEACHING,
            building = "2号教学楼",
            aliases = listOf("二教", "2教", "2号楼", "第二教学楼"),
            description = "公共课与专业课教室"
        ),
        ytlgPoi(
            key = "teaching_3",
            name = "三教",
            category = PoiCategory.TEACHING,
            building = "三教",
            aliases = listOf("3教", "三号教学楼", "第三教学楼", "S楼"),
            description = "部分课程与考试教室，如 S552"
        ),
        ytlgPoi(
            key = "teaching_2_ab",
            name = "二教AB区",
            category = PoiCategory.TEACHING,
            building = "2号教学楼",
            aliases = listOf("A区", "B区", "二教连廊", "朗读空间", "AB区"),
            description = "含中间连廊，设有朗读空间"
        ),

        // ── 图书馆 ──
        ytlgPoi(
            key = "library",
            name = "图书馆",
            category = PoiCategory.LIBRARY,
            building = "图书馆楼",
            aliases = listOf("图书馆楼", "借阅室", "自习室"),
            description = "1-5 层为借阅室，全馆 Wi-Fi 覆盖，选座走微信公众号"
        ),

        // ── 宿舍 ──
        ytlgPoi(
            key = "dorm_1",
            name = "1号宿舍楼",
            category = PoiCategory.DORM,
            building = "1号宿舍楼",
            aliases = listOf("1号楼", "一号宿舍", "1号公寓")
        ),
        ytlgPoi(
            key = "dorm_2",
            name = "2号宿舍楼",
            category = PoiCategory.DORM,
            building = "2号宿舍楼",
            aliases = listOf("2号楼", "二号宿舍", "2号公寓")
        ),
        ytlgPoi(
            key = "dorm_3",
            name = "3号宿舍楼",
            category = PoiCategory.DORM,
            building = "3号宿舍楼",
            aliases = listOf("3号楼", "三号宿舍", "3号公寓")
        ),
        ytlgPoi(
            key = "dorm_4",
            name = "4号宿舍楼",
            category = PoiCategory.DORM,
            building = "4号宿舍楼",
            aliases = listOf("4号楼", "四号宿舍", "4号公寓")
        ),
        ytlgPoi(
            key = "dorm_5",
            name = "5号宿舍楼",
            category = PoiCategory.DORM,
            building = "5号宿舍楼",
            aliases = listOf("5号楼", "五号宿舍", "5号公寓")
        ),
        ytlgPoi(
            key = "dorm_6",
            name = "6号宿舍楼",
            category = PoiCategory.DORM,
            building = "6号宿舍楼",
            aliases = listOf("6号楼", "六号宿舍", "6号公寓")
        ),
        ytlgPoi(
            key = "dorm_13",
            name = "13号楼",
            category = PoiCategory.DORM,
            building = "13号楼",
            aliases = listOf("13号宿舍楼", "十三号楼", "13号公寓")
        ),

        // ── 餐饮 ──
        ytlgPoi(
            key = "dining_2",
            name = "学院二餐",
            category = PoiCategory.DINING,
            building = "学院二餐",
            aliases = listOf("二餐", "第二餐厅", "2餐")
        ),
        ytlgPoi(
            key = "dining_3",
            name = "第三餐厅",
            category = PoiCategory.DINING,
            building = "第三餐厅",
            aliases = listOf("三餐厅", "三餐", "3餐")
        ),

        // ── 运动 ──
        ytlgPoi(
            key = "sport_stadium",
            name = "体育场",
            category = PoiCategory.SPORT,
            building = "体育场",
            aliases = listOf("操场", "田径场", "运动场", "体育馆")
        ),

        // ── 生活 ──
        ytlgPoi(
            key = "life_cotti",
            name = "库迪咖啡",
            category = PoiCategory.LIFE,
            building = "第三餐厅",
            floor = "1楼",
            aliases = listOf("库迪", "Cotti", "Cotti Coffee", "咖啡")
        ),

        // ── 行政 ──
        ytlgPoi(
            key = "office_complex",
            name = "综合楼",
            category = PoiCategory.OFFICE,
            building = "综合楼",
            aliases = listOf("行政楼", "办公楼"),
            description = "会议室等，如 S609"
        ),

        // ── 其他 ──
        ytlgPoi(
            key = "art_center",
            name = "公共艺术教育中心",
            category = PoiCategory.OTHER,
            building = "公共艺术教育中心",
            aliases = listOf("艺术中心", "公共艺术中心")
        )
    )

    private fun ytlgPoi(
        key: String,
        name: String,
        category: PoiCategory,
        building: String = "",
        floor: String = "",
        aliases: List<String> = emptyList(),
        description: String = ""
    ) = CampusPoi(
        id = "builtin_" + CAMPUS_YTLG_MAIN + "_" + key,
        campusId = CAMPUS_YTLG_MAIN,
        name = name,
        category = category,
        building = building,
        floor = floor,
        aliases = aliases,
        description = description,
        // 固定时间戳，保证内置数据可复现（不随启动时间变化）
        createdAt = 0L
    )
}
