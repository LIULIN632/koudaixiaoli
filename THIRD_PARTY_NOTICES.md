# 第三方代码与素材声明（THIRD_PARTY_NOTICES）

本文件列出**真正复制/移植过代码或素材**的上游项目。只"参考接口协议、没有复制代码"的项目
不在此列（它们记在 `README.md` 的致谢里）。

> 为什么单独开一个文件：MIT / Apache-2.0 都要求"在副本或实质性部分中保留版权声明"。
> App 内的「设置 → 关于 → 致谢」是给人看的简版，这个文件是**可审计的完整版**。
> 改代码时如果又移植了一段别人的实现，请同时更新这里。

---

## 代码移植

### 岱宗盒子（xjtu-toolbox-android）

- 上游：https://github.com/yeliqin666/xjtu-toolbox-android
- 许可证：**GPL-3.0**
- 我们用了：版本号比较（`VersionCompare`）、更新与通知链路的部分思路与少量代码、`PidaiNavButton` 的底栏机制参考
- 义务：本项目整体以 GPL-3.0 发布（`LICENSE`），保留出处。

### 拾光课程表（shiguang_warehouse）

- 上游：https://github.com/ShiGuangSchedule/shiguang_warehouse
- 许可证：**MIT**（`Copyright (c) 2025 拾光课表`）
- 我们用了：超星教务适配脚本（`app/src/main/assets/adapters/scripts/chaoxing_jiaowu_chaoxing.js`）的课程数据处理部分
- 义务：保留上述版权声明与本许可声明。

### DeepSeek-Bot

- 上游：https://github.com/Fei2411/DeepSeek-Bot
- 许可证：**Apache-2.0**
- 我们用了：吉祥物的 path 与分层数据
- 义务：保留版权与许可声明；如修改过文件，需注明修改。

### grok-ball

- 上游：https://github.com/TyCoding/grok-ball
- 许可证：**MIT**
- 我们用了：球体轮廓、25 组眼环与 32 个表情数据（Kotlin 重写）

### LaoA-GrokBot

- 上游：https://github.com/zhulin025/LaoA-GrokBot
- 许可证：**MIT**（作者：老A玩AI）
- 我们用了：表情 / 状态阶梯的参考与部分数据（未复制几何）

### npface

- 上游：https://github.com/scccc6ut/npface
- 许可证：**MIT**（头盔形象归 npcrew）
- 我们用了：头盔 / 遮阳板 / 方眼几何

---

## 开源库（经 Maven 依赖引入，未修改源码）

| 库 | 许可证 |
|---|---|
| Jetpack Compose / Material 3 / Room / WorkManager / CameraX | Apache-2.0 |
| OkHttp · Gson · Jsoup · kotlinx.coroutines | Apache-2.0 |
| ZXing（`com.google.zxing:core`） | Apache-2.0 |
| MIUIX（`top.yukonga.miuix.kmp`） | Apache-2.0 |
| Backdrop（`io.github.kyant0`） | Apache-2.0 |

---

## 数据

| 数据 | 来源 | 许可证 |
|---|---|---|
| 法定节假日 / 调休 | [NateScarlet/holiday-cn](https://github.com/NateScarlet/holiday-cn) | MIT |
| 校园地图底图与三维模型 | 自行测量与建模（`tools/campus-map-data/`） | 随本项目 GPL-3.0 |

---

## 仅作协议参考（未复制代码）

`dairoot/school-api`、`openschoolcn/zfn_api`、`chettoy/FxxkStar`（AGPL-3.0）、
`gh-orange/chaoxing`（GPL-3.0）、`luoyily/zhihuishu-tool`（GPL-3.0）：
只参考了接口路径、字段名与登录流程，**没有复制其代码**。若将来复制，必须按其许可证处理
（AGPL / GPL 会要求本项目以相同许可证发布）。

---

*最后更新：2026-10-23*
