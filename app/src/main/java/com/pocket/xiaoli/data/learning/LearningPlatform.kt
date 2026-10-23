package com.pocket.xiaoli.data.learning

enum class LearningPlatform(
    val key: String,
    val label: String,
    val loginUrl: String,
    val cookieUrl: String
) {
    CHAOXING(
        key = "chaoxing",
        label = "学习通",
        loginUrl = "https://passport2.chaoxing.com/login?fid=&newversion=true&refer=https%3A%2F%2Fi.chaoxing.com",
        cookieUrl = "https://i.chaoxing.com"
    ),
    ZHIHUISHU(
        key = "zhihuishu",
        label = "智慧树",
        loginUrl = "https://passport.zhihuishu.com/login?service=https%3A%2F%2Fonlineweb.zhihuishu.com%2F",
        cookieUrl = "https://www.zhihuishu.com"
    );

    /**
     * 需要收集 Cookie 的域名。
     * 学习通的课程/作业页在 mooc1 / mooc2-ans 子域，只从 i.chaoxing.com 取会漏掉
     * host-only 的 Cookie，导致后续请求被判为未登录。
     */
    val cookieUrls: List<String>
        get() = when (this) {
            CHAOXING -> listOf(
                "https://i.chaoxing.com",
                "https://mooc1.chaoxing.com",
                "https://mooc2-ans.chaoxing.com",
                "https://passport2.chaoxing.com",
                "https://mooc1-api.chaoxing.com"
            )
            ZHIHUISHU -> listOf(
                "https://www.zhihuishu.com",
                "https://passport.zhihuishu.com"
            )
        }
}

data class LearningCourse(
    val platform: LearningPlatform,
    val name: String,
    val teacher: String? = null,
    val term: String? = null,
    val link: String? = null
)

data class LearningExam(
    val platform: LearningPlatform,
    val courseName: String,
    val title: String,
    val dueDate: String? = null
)

/** 从学习平台拉取到的一条作业 */
data class LearningHomework(
    val platform: LearningPlatform,
    val courseName: String,
    val title: String,
    val dueDate: String = "",
    val completed: Boolean = false
)

class LearningApiNotConfiguredException(
    val platform: LearningPlatform
) : Exception("${platform.label}接口尚未配置，请先使用网页登录或手动导入")