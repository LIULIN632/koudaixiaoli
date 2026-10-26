package com.pocket.xiaoli.data.knowledge

/**
 * Built-in FAQ, sourced from the app's own help content.
 * No school regulation or third-party content is bundled here yet.
 */
class LocalFaqKnowledgeSource : KnowledgeSource {
    override val id: String = "local_faq"

    override suspend fun listDocuments(): List<KnowledgeDocument> = documents

    companion object {
        private const val SOURCE_LABEL = "口袋小鲤使用帮助"

        val documents: List<KnowledgeDocument> = listOf(
            KnowledgeDocument(
                id = "faq_import_way",
                title = "如何导入课表？",
                body = "进入课表页，点击空状态里的「导入课表」，或从「我的 -> 导入课表」进入。支持三种方式：网页导入（用教务系统网页登录后抓取）、教务直连导入（自动登录抓取，本校用正方 v8）、文件导入（Excel/HTML/CSV/JSON 备份，PDF 目前只提供转换指引）。导入会写入当前课表方案。",
                sourceLabel = SOURCE_LABEL,
                tags = listOf("导入", "课表"),
            ),
            KnowledgeDocument(
                id = "faq_schools",
                title = "支持哪些学校和教务系统？",
                // ⚠️ 这段文案必须与产品范围一致：本 App 是**烟台理工专用**，
                // 导入页会锁定当前校区的学校（campusOnly），用户看不到全国学校列表。
                // 曾经这里写的是"已适配正方、强智、青果、超星、URP"——那是导入引擎的能力，
                // 不是本 App 的产品范围（青果/超星/URP 连客户端都没有，强智入口被闸门关闭）。
                // 用户问鲤鱼"支持哪些学校"时读的就是这一段，写错等于对用户撒谎。
                body = "本应用是为烟台理工学院做的专用校园助手，只对接本校教务：烟台理工学院本校区使用正方教务 v8（jwxt.yitsd.edu.cn/jwglxt）。烟大南校区用于后勤能源缴费等服务，不含教务导入。导入页会自动锁定当前校区的学校，不需要也不需要你选择其他学校。",
                sourceLabel = SOURCE_LABEL,
                tags = listOf("学校", "教务"),
            ),
            KnowledgeDocument(
                id = "faq_privacy",
                title = "我的数据存在哪里？",
                body = "课表、作业、待办、成绩存档等数据只保存在手机本地的 Room 数据库；账号密码和 Cookie 使用加密存储保存在本机，不会上传服务器。" +
                    "应用不会自动上传任何数据。崩溃日志也只写在本机，要交给开发者需你自己在「我的 → 设置 → 关于 → 导出日志」导出后发送；同页的「让鲤鱼看看」会把已脱敏的报告发给你自己配置的 AI 服务。",
                sourceLabel = SOURCE_LABEL,
                tags = listOf("隐私", "数据"),
            ),
            KnowledgeDocument(
                id = "faq_share",
                title = "如何分享课表？",
                body = "我的 -> 导入课表 -> 分享口令，可以把当前课表导出为口令（JSON 的 Base64）。好友导入口令时会先清空本机已有课程，再写入口令中的课程；请先确认自己是否要覆盖。也可以通过 JSON 备份文件分享。",
                sourceLabel = SOURCE_LABEL,
                tags = listOf("分享", "口令"),
            ),
            KnowledgeDocument(
                id = "faq_theme",
                title = "如何切换深色模式和主题？",
                body = "我的 -> 外观，可以切换海盐蓝、口袋小鲤、马卡龙蓝、马卡龙粉、抹茶绿、樱花粉、紫藤紫、炒饭黄等主题，以及彩虹/纯净模式和浅色/深色/跟随系统。",
                sourceLabel = SOURCE_LABEL,
                tags = listOf("外观", "深色模式"),
            ),
            KnowledgeDocument(
                id = "faq_week",
                title = "课表周次对不上怎么办？",
                body = "导入成功后如果没有识别到开学日期，课表页会弹出「学期周次校准」，选择当前是第几周即可反推开学日期。也可以在我的 -> 课表设置里手动设置学期开始日期和总周数。",
                sourceLabel = SOURCE_LABEL,
                tags = listOf("周次", "学期"),
            ),
            KnowledgeDocument(
                id = "faq_dedup",
                title = "重复导入会怎样？",
                body = "文件导入会按「课程名 + 星期 + 开始节次」跳过已存在的课程，并提示无需重复导入；教务导入会把课程写入当前激活的课表方案。",
                sourceLabel = SOURCE_LABEL,
                tags = listOf("导入", "去重"),
            ),
            KnowledgeDocument(
                id = "faq_map",
                title = "校园地图怎么用？",
                body = "服务 → 地图导航直接进入 3D 校园沙盘，点楼栋可以看详情和各层平面图，双指缩放、拖动旋转。在课表编辑课程时点教室旁的「地图」，会带着教室名进入 3D 地图并聚焦到对应楼栋。",
                sourceLabel = SOURCE_LABEL,
                tags = listOf("地图", "教室"),
            ),
            KnowledgeDocument(
                id = "faq_grade",
                title = "成绩存档是什么？",
                body = "在服务页的考试成绩/课程与成绩里，用正方教务登录后可以把多个学期的成绩保存到本机，之后离线查看。保存时会按课程、学期、总评自动去重。",
                sourceLabel = SOURCE_LABEL,
                tags = listOf("成绩", "存档"),
            ),
            KnowledgeDocument(
                id = "faq_learning",
                title = "学习通怎么登录、作业怎么同步？",
                body = "在「我的 → 绑定信息 → 学习通」用 App 内网页登录，Cookie 加密保存在本机，不上传。" +
                    "登录后点同一张卡里的「同步作业到待办」，会把各门课程的作业导入待办（同一条不会重复加）。" +
                    "只想看一眼的话走「服务 → 学习通」，那一页只读、不动你的待办。" +
                    "智慧树（知到）没有可复用的登录态同步，用「服务 → 知到」打开官方网页。",
                sourceLabel = SOURCE_LABEL,
                tags = listOf("学习通", "智慧树", "知到", "作业"),
            ),
            KnowledgeDocument(
                id = "faq_last_import",
                title = "在哪里看上次导入结果？",
                body = "课表空状态的「导入课表」页顶部有「最近导入」卡片，显示上次导入的来源、课程数量和成功/失败状态；成功后可以点「去课表」。",
                sourceLabel = SOURCE_LABEL,
                tags = listOf("导入", "记录"),
            ),
            KnowledgeDocument(
                id = "faq_binding",
                title = "怎么绑定账号和校区？",
                body = "我的 -> 点击「绑定账号」或「绑定校区」进入绑定信息页，选择校区并填写学号、密码后保存。账号密码加密保存在本机；绑定后导入课表、网页导入、成绩查询都会直接使用。不同校区的账号分别保存，切换回来后无需重新绑定。",
                sourceLabel = SOURCE_LABEL,
                tags = listOf("绑定", "账号", "校区"),
            ),
            KnowledgeDocument(
                id = "faq_dedicated",
                title = "为什么没有其他学校？",
                body = "口袋小鲤是面向烟台理工学院及本校校区的专用校园助手，只显示当前校区对应的学校与教务系统，不再提供全国学校列表。需要切换学校时，在绑定信息页选择校区即可。",
                sourceLabel = SOURCE_LABEL,
                tags = listOf("学校", "专用", "校区"),
            ),
            KnowledgeDocument(
                id = "faq_switch_campus",
                title = "切换校区后课表会变吗？",
                body = "在绑定信息页切换校区后，教务导入与成绩查询会改用该校区对应的教务系统；已导入的旧校区课表不会自动删除，可在课表方案管理里切换或删除。各校区账号独立保存。",
                sourceLabel = SOURCE_LABEL,
                tags = listOf("校区", "课表"),
            ),
            KnowledgeDocument(
                id = "faq_auto_import",
                title = "绑定账号后导入课表还要输密码吗？",
                body = "不需要。进入「教务直连导入」时会检测当前校区的绑定账号，自动填充并开始登录导入；网页导入也会把已保存的账号自动填入登录页。若密码失效，可在绑定信息页更新。",
                sourceLabel = SOURCE_LABEL,
                tags = listOf("绑定", "导入"),
            ),
        )
    }
}