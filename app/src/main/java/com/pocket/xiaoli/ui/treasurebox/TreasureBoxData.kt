package com.pocket.xiaoli.ui.treasurebox

import androidx.compose.runtime.Immutable
import java.util.UUID

@Immutable
data class LearningResource(
    val id: String = UUID.randomUUID().toString(),
    val subject: String,
    val courseName: String,
    val blogger: String,
    val description: String,
    val videoUrl: String,
    val dayOfWeek: Int = 1,
    val startSlot: Int = 1,
    val endSlot: Int = 2,
    val isCustom: Boolean = false
)

@Immutable
data class FoodOption(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: String = "",
    val windowName: String = "",
    val price: String = "",
    val isCustom: Boolean = false
)

/** Subjects for learning resources */
val SUBJECTS = listOf("数学", "编程", "英语", "考研", "设计", "通识", "自定义")

/**
 * 默认学习资源（课程名 / 推荐博主 / 简介）。
 *
 * ## 为什么 `videoUrl` 一律留空
 *
 * 这里原本填的是 `https://b23.tv/gaoshu_songhao` 这类**编造的短链**：
 * b23.tv 的真实短码是 `BV1xx411c7mD` 那种形态，`gaoshu_songhao` 这种
 * 中文拼音拼接的占位符点开必然 404 或跳到 B 站首页。
 *
 * 12 条**全都是**占位符（已用脚本逐条核对）。给用户一个必然打不开的「跳转」按钮，
 * 比不给更糟 —— 用户会以为是自己手机或网络的问题。
 *
 * 所以：**内容留下，假链接清掉**。课程与博主是真实存在的（宋浩高数、
 * 3Blue1Brown 线代、崔庆才爬虫等），保留「导入课表」这个真正可用的动作；
 * 页面在 `videoUrl` 为空时**不显示「跳转」与「分享」**（见 `LearningResourcePage`）。
 *
 * 要恢复跳转能力，就往 `videoUrl` 填**真实的** b23.tv / BV 链接，按钮会自动出现。
 */
val DEFAULT_LEARNING_RESOURCES = listOf(
    LearningResource("lr1", "数学", "高等数学（上）", "宋浩老师", "B站最火高数课，讲课幽默风趣，零基础也能听懂", "", 1, 1, 2),
    LearningResource("lr2", "数学", "线性代数", "3Blue1Brown", "用动画直观理解线代本质，全球公认最佳入门", "", 2, 1, 2),
    LearningResource("lr3", "数学", "概率论与数理统计", "浙大苏德矿", "矿爷经典课程，讲解透彻，考研必备", "", 3, 1, 2),
    LearningResource("lr4", "编程", "Java从入门到精通", "狂神说Java", "全网最全Java教程，配套笔记+源码，适合零基础", "", 1, 3, 4),
    LearningResource("lr5", "编程", "Python爬虫实战", "崔庆才", "《Python3网络爬虫开发实战》作者亲授", "", 2, 3, 4),
    LearningResource("lr6", "编程", "数据结构与算法", "左程云", "大厂面试算法通关，LeetCode刷题路线", "", 3, 3, 4),
    LearningResource("lr7", "英语", "四六级急救班", "刘晓艳", "考前突击必备，作文模板+听力技巧", "", 4, 1, 2),
    LearningResource("lr8", "英语", "英语语法全程", "英语的平行世界", "用中文思维讲透英语语法，简单易懂", "", 5, 1, 2),
    LearningResource("lr9", "考研", "考研政治", "徐涛", "强化班+冲刺班，每年数百万考生跟学", "", 1, 5, 6),
    LearningResource("lr10", "考研", "考研数学", "张宇", "《张宇36讲》配套视频，高数线代概率全覆盖", "", 2, 5, 6),
    LearningResource("lr11", "设计", "PS教程2024", "李涛", "Photoshop零基础到精通，案例驱动教学", "", 4, 3, 4),
    LearningResource("lr12", "通识", "中国哲学简史", "王德峰", "复旦大学哲学王子，讲透儒释道精髓", "", 5, 7, 8)
)

/**
 * 默认菜品。
 *
 * ⚠️ 这只是一组**通用食堂菜名**，用来让「今天吃什么」抽签功能一装上就有东西可抽，
 * 不代表烟台理工食堂的真实菜单。真实菜单请用页面里的「导入」添加。
 *
 * 分类刻意用**菜品种类**（面食 / 小吃 / 快餐）而不是**食堂名**：
 * 之前 `FoodPickerPage` 硬编码了「一食堂 / 二食堂 / 三食堂」三个筛选按钮，
 * 但这批数据里一个都没有这两个分类，点了就是空白页（已修，见该文件）。
 */
val DEFAULT_FOOD_OPTIONS = listOf(
    FoodOption("f1", "黄焖鸡米饭", "食堂"),
    FoodOption("f2", "麻辣香锅", "食堂"),
    FoodOption("f3", "兰州拉面", "面食"),
    FoodOption("f4", "沙县小吃", "小吃"),
    FoodOption("f5", "麻辣烫", "食堂"),
    FoodOption("f6", "汉堡薯条套餐", "快餐"),
    FoodOption("f7", "烤鱼饭", "食堂"),
    FoodOption("f8", "鸡排饭", "食堂"),
    FoodOption("f9", "过桥米线", "面食"),
    FoodOption("f11", "水饺（韭菜鸡蛋）", "面食"),
    FoodOption("f12", "披萨", "快餐"),
    FoodOption("f13", "寿司拼盘", "外卖"),
    FoodOption("f14", "酸辣粉", "小吃"),
    FoodOption("f15", "炸鸡啤酒", "外卖"),
    FoodOption("f16", "煲仔饭", "外卖"),
    FoodOption("f17", "牛肉面", "面食"),
    FoodOption("f18", "煎饼果子", "小吃"),
    FoodOption("f19", "麻辣拌", "食堂"),
    FoodOption("f20", "糖醋里脊盖饭", "食堂"),
    FoodOption("f21", "螺蛳粉", "小吃"),
    FoodOption("f22", "烤肉拌饭", "外卖"),
    FoodOption("f23", "热干面", "面食"),
    FoodOption("f24", "馄饨/抄手", "面食"),
    FoodOption("f25", "咖喱鸡饭", "食堂")
)
