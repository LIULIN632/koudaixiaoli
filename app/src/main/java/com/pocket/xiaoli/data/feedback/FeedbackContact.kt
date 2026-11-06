package com.pocket.xiaoli.data.feedback

/**
 * 开发者邮箱 —— 全 App **只有这一处**写地址。
 *
 * 用到它的地方（改地址时不用再找第二遍）：
 * - 反馈页「邮件」通道（`FeedbackScreen` 的 mailto 预填主题与正文）；
 * - 反馈页「开发者邮箱」那一行（点一下复制，给没配邮件 App 的人留一条路）；
 * - 「反馈学校信息」弹窗的适配申请（`ProfileDialogs`）。
 *
 * ## 为什么单独拎出来
 *
 * 2026-10 之前这个地址在 `FeedbackScreen` 里是一份 `private const`、在
 * `ProfileDialogs` 里又手写了一串 `"mailto:pocketxiaoyan@outlook.com")`，
 * 换邮箱时漏一处就是「反馈发到没人看的信箱」。学号脱敏曾有五份实现，教训一样。
 *
 * ## 换地址前先确认
 *
 * 163 免费邮箱**长期不登录会被冻结/停机**（通常 180 天），冻结后收到的信会退回。
 * 换之前先在网页版登录一次，再改这里 —— 用户看不到"信发丢了"，
 * 只会觉得"提了意见没人理"。
 */
const val FEEDBACK_MAIL = "pocketxiaoli@163.com"
