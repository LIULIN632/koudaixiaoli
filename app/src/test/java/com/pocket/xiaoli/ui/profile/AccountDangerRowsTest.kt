package com.pocket.xiaoli.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「账号管理」这一组该出现哪几条 —— 纯函数用例，不需要 Robolectric。
 *
 * 为什么值得单独钉住（2026-10-26）：学习平台的登录态原先藏在中间面板的一个红字按钮上，
 * 面板删掉之后"退出学习通登录态"归到这里。这组行的语义是**不可恢复**，
 * 所以新增的这条必须和已有四条一样过二次确认，而且**没登录就不该出现那条行**。
 *
 * 智慧树同一天撤出绑定页：它存的 Cookie 全仓零消费者，所以既不给登录入口也不给退出项 ——
 * 下面那条"没登录就不出现"的用例正是这个决定的可执行版本。
 */
class AccountDangerRowsTest {

    private fun states(
        jw: Boolean = false,
        portal: Boolean? = false,
        cas: Boolean = false,
        card: Boolean = false,
        chaoxing: Boolean = false,
    ) = AccountDangerStates(
        campusName = "烟台理工学院",
        jwBound = jw,
        portalLoggedIn = portal,
        casBound = cas,
        cardBound = card,
        chaoxingBound = chaoxing,
    )

    private fun rowsOf(state: AccountDangerStates, onClearChaoxing: () -> Unit = {}) =
        accountDangerRows(
            states = state,
            onUnbindJw = {},
            onClearCasLogin = {},
            onDeleteCas = {},
            onDeleteCard = {},
            onClearChaoxingLogin = onClearChaoxing,
        )

    @Test
    fun `全绑定时五条按顺序给齐`() {
        val rows = rowsOf(states(jw = true, portal = true, cas = true, card = true, chaoxing = true))
        assertEquals(
            listOf(
                "解绑教务账号",
                "退出今日校园登录态",
                "删除今日校园账号",
                "删除校园卡账号",
                "退出学习通登录态",
            ),
            rows.map { it.title },
        )
    }

    @Test
    fun `没登录的学习通不出现那一条`() {
        val titles = rowsOf(states(jw = true)).map { it.title }
        assertFalse("学习通没登录却给出退出项，等于让人去清一个不存在的东西", titles.contains("退出学习通登录态"))
        assertTrue(titles.contains("解绑教务账号"))
    }

    @Test
    fun `只绑了学习通时账号管理入口仍然要露出来`() {
        // 「账号管理」入口行按 hasManageableAccount 决定在不在。
        // 学习通登录态也要算一项 —— 否则绑了学习通却找不到退出入口，等于清不掉。
        assertTrue(states(chaoxing = true).hasManageableAccount)
        assertFalse(states().hasManageableAccount)
    }

    @Test
    fun `门户登录态未知时不给退出登录态那条`() {
        // portalLoggedIn 是三态：null = 还没查过。查过之前给"退出"就是在清一个可能不存在的东西。
        val titles = rowsOf(states(jw = true, portal = null)).map { it.title }
        assertFalse(titles.contains("退出今日校园登录态"))
    }

    @Test
    fun `退出学习通登录态点确认只走回调`() {
        var hits = 0
        rowsOf(states(chaoxing = true)) { hits++ }.first { it.title == "退出学习通登录态" }.onConfirm()
        assertEquals(1, hits)
    }

    @Test
    fun `每一条都带确认按钮文案与问句`() {
        // 这一组的规矩：不可恢复 = 必须先问一句。加行时忘了写 message 就会被这条钉住。
        val rows = rowsOf(states(jw = true, portal = true, cas = true, card = true, chaoxing = true))
        rows.forEach { row ->
            assertTrue("${row.title} 缺确认问句", row.message.contains("确定吗？"))
            assertTrue("${row.title} 缺确认按钮文案", row.confirmLabel.isNotBlank())
        }
    }
}
