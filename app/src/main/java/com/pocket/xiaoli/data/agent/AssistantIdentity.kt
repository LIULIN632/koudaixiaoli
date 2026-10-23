package com.pocket.xiaoli.data.agent

/**
 * 助手「鲤鱼」的**用户可见身份常量**。
 *
 * ## 为什么它在这里，而不是 `ui/theme/AiAvatar.kt`
 *
 * 它原先和头图的 emoji 一起住在 `ui/theme/AiAvatar.kt`。但两者性质不同：
 *
 * - 头图画什么，是**渲染选择** → 留在 UI 层是对的（那个 emoji 常量后来又被
 *   `ui/theme/Mascot.kt` 的自绘形象取代了，同样在 UI 层）；
 * - `AI_DEFAULT_NAME` 是**产品配置**（助手叫什么，且用户可在设置里改）→ 属于数据/产品侧。
 *
 * 放错地方的代价是实的：`di/AppContainer` 要读它作为偏好回退值，
 * 于是 **DI 容器不得不 `import com.pocket.xiaoli.ui.theme.*`** —— 依赖方向是倒的。
 * 搬到 `data/agent` 后方向恢复为合法的 `ui → data`。
 *
 * ## 历史
 *
 * 这里曾经是一套**宠物系统**（猫/狗/兔/熊猫 4 个角色 + 各自 drawable + 可改名）。
 * 那是从别的软件带过来的概念，与「口袋小鲤」的 AI 定位不符，已整组移除
 *（见 `AppContainer` 的 AI 助手一节）。现在只有"鲤鱼"这一个形象。
 */

/**
 * AI 助手的默认名字。用户可在设置里改（偏好键 `ai_name`）。
 *
 * ⚠️ 改这个值**只会影响新用户与未改过名的用户**：老用户已存在 `ai_name`
 * （或更早的 `pet_name`）里的值优先生效。
 */
const val AI_DEFAULT_NAME = "鲤鱼"
