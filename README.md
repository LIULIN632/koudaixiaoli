# 口袋小鲤

<p align="center">
  <img src="https://img.shields.io/badge/platform-Android-green?logo=android" alt="Android" />
  <img src="https://img.shields.io/badge/minSdk-29%20(Android%2010)-blue" alt="minSdk 29" />
  <img src="https://img.shields.io/github/v/release/LIULIN632/koudaixiaoli?label=version&color=orange" alt="version" />
  <img src="https://img.shields.io/badge/license-GPL--3.0-blue" alt="GPL-3.0" />
</p>

面向**烟台理工学院**的 Android 校园助手。Kotlin + Jetpack Compose 原生开发，覆盖课表、成绩、服务聚合、校园地图与 AI 助手，**不经过任何自建业务中转服务器** —— 账号凭据与教务数据只在你手机和学校系统之间流动。

非官方项目，不代表学校；开源、无广告、无第三方统计/推送 SDK。

> [!NOTE]
> 本仓库**只放软件源码**（`app/`、构建配置、单元测试与截图基线、更新数据）。
> 设计文档、运维脚本、后端与内部笔记不在仓库内。

---

## 目录

| 目录 | 内容 |
|---|---|
| `app/` | Android 应用（Kotlin + Compose），含 2000+ 条单元测试与 190 张截图基线 |
| `gradle/`、`build.gradle.kts`、`settings.gradle.kts` | 构建配置（Gradle 9、AGP 9） |
| `announce/` | 更新与公告数据（`feed.json`、`promos.json`），客户端远程读取 |
| `docs/用户协议.md` | 用户协议的人读版（App 内为唯一真源） |
| `tools/promos.example.json` | 推广位配置样例（被单测读取） |

## 它能做什么

| 模块 | 能力 |
|---|---|
| 首页 | 下一节课倒计时、待办与考试、天气、全局搜索、消息（更新 + 公告） |
| 课表 | 周课表、调停课、当前周切换、导入/导出（ICS / 图片 / 日历）、桌面小组件 |
| 鲤鱼（AI 助手） | 自行配置模型与 Key；查课表、成绩、空教室、天气等；工具可单独开关；形象与表情可换 |
| 服务 | 成绩与修课查询、选课、空教室、教师查询、图书馆、**公交到站**（本校站各线路下一班还有几站）、热水、快递、校园卡、资料站、快问快答 |
| 校园地图 | 全屏 3D 沙盘（旋转/缩放）、楼栋档案与分层平面图、教室搜索、导览、室内平面图 |
| 我的 | 外观（9 套皮肤 / 深浅色 / 强调色）、更新与公告、意见反馈、用户协议 |
| 更新 | 双仓镜像 + 灰度 + 内测通道 + 断点续传 + SHA-256 校验，下载完先验再装 |

## 界面

<p align="center">
  <img src="docs/images/home.jpg" width="180" alt="首页" />
  <img src="docs/images/campus-3d.jpg" width="180" alt="3D 校园" />
  <img src="docs/images/assistant.jpg" width="180" alt="鲤鱼" />
  <img src="docs/images/services.jpg" width="180" alt="服务" />
</p>

<p align="center">
  <img src="docs/images/weather.jpg" width="180" alt="天气" />
  <img src="docs/images/settings.jpg" width="180" alt="设置" />
</p>

<sub>以上是单元测试里的截图基线（Pixel 5 / 3x），不是效果图。</sub>

## 下载与更新

| 渠道 | 地址 |
|---|---|
| Gitee（主） | [gitee.com/liulin62633/pocket-carp/releases](https://gitee.com/liulin62633/pocket-carp/releases) |
| GitHub（备） | [github.com/LIULIN632/koudaixiaoli/releases](https://github.com/LIULIN632/koudaixiaoli/releases) |

两个渠道是**同一份字节**（每次发版都核对 SHA-256）。检查到新版本会先弹说明，由你决定是否下载；
下载失败会自动换另一个仓库重试，断开后可以接着下。

## 从源码构建

**环境**：JDK 21、Android SDK（`compileSdk 37`、`build-tools 37.0.0`）。

```bash
# Linux / macOS
./gradlew :app:assembleRelease

# Windows
.\build.ps1
```

- 仓库里**没有签名密钥**。在根目录放一份 `keystore.properties`（`storeFile` / `storePassword` / `keyAlias` / `keyPassword`）才会签名；
  没有它也能构建，只是产物未签名。
- `./gradlew :app:assembleRelease -Pfdroid=true` 生成 **F-Droid 版**：匿名运行统计完全不初始化（不建队列、不发请求），
  其余功能与正式版一致。普通构建不带这个属性，行为不变。
- `build.ps1` 是本项目的正式打包脚本：递增 `gradle.properties` 的版本号 → 构建 → 改名 → 校验签名。

## 测试

```bash
./gradlew :app:testDebugUnitTest
```

- 2381 条单元测试（另有 `core/` 里 235 条跨端共享的纯逻辑用例）；其中 308 张是 Compose 截图基线
  （`app/src/test/screenshots/`），改了观感需要 `-PrecordScreenshots` 重录并**看图**确认。
- 基线覆盖深浅色、窄屏（360dp）、大字号等边界。

## 隐私

- **课表、成绩、待办、笔记全部存本机**；`allowBackup="false"`，系统云备份不会带走它们。
- **学号与密码用 Android Keystore 加密后只存本机**（AES-256-GCM），不上传、不进日志。
- **无第三方广告 / 统计 / 推送 SDK**；崩溃日志只写在本机，是否导出由你决定。
- **天气**按**你所在学校那座城市**向 Open-Meteo 查询（免 API Key、开源，数据许可 CC BY 4.0，页面保留署名）。
  请求里只有城市坐标，没有账号、没有设备标识；页面脚注如实写「按烟台市的坐标请求（非校区实测）」，
  不假装是本校区实测。这座城市还没配取数坐标时，天气那一格直接不出现，不拿别处的温度凑数。
- **公交到站**按站名向掌上公交（`h5.mygolbs.com`）查询，无需账号。**不申请定位权限、不上传位置** ——
  「附近站点」用的是写死的学校中心坐标，请求里代表"我的位置"的两个字段留空（有单测钉住这条）。
  2026-11-06 起该接口对这两种查询只返回一段加密内容，此时页面会如实说「接口这次没给回能用的数据」，
  并把上次刷新到的看板标成上次的，而不是让你去检查自己的网络。
- 唯一自动上报的是**匿名运行统计**（启动、更新、崩溃的次数与版本号，无设备标识、30 天换号），
  可在「我的 → 设置 → 关于 → 隐私」关闭；关掉后本机不再产生数据。
- 完整条款见 [用户协议与免责声明](docs/用户协议.md)，首次启动需同意。

## 许可与致谢

以 **GNU GPL v3.0** 发布，见 [LICENSE](LICENSE)。移植过的上游代码与素材、各自的许可证与义务见
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)；感谢岱宗盒子、拾光课程表、DeepSeek-Bot、grok-ball、
LaoA-GrokBot、npface、MIUIX、Backdrop 等项目，以及 **追客 / Trackr**（[5151561/Trackr](https://github.com/5151561/Trackr)，
实时公交一页只参考它的接口协议与交互形态，未复制任何代码）。

## 参与贡献

- 问题与建议：[GitHub Issues](https://github.com/LIULIN632/koudaixiaoli/issues)
- 提 PR 前先跑 `./gradlew :app:testDebugUnitTest`；截图基线有变要一并提交。
- 主场是烟台理工学院：可接入的学校 = 烟台本地院校（教务系统已支持的）+ 点名接入的山东理工大学、燕京理工学院，
  以及你自己添加的学校。**不做"全国 300 所"那种列表**（名单数据留在仓库里，但入口不放开）。
- 各校接入到什么程度见 [学校接入矩阵](docs/学校接入矩阵.md) —— 那张表由测试按代码现算核对，
  改了接入状态它会红，所以不会变成一段过期说明。
- 一台设备只服务一所学校：换学校会把课表切到以那所学校命名的方案，旧学校的课表原样保留在自己的方案里。
- 校园服务（电费 / 校园卡 / 报修 / 地图等）按学校配置：**没收录这所学校时，那些入口直接不出现**，
  而不是点开了才提示"暂未配置"，更不会借用别的学校的配置——看到别人的账单页比少一个入口糟得多。
