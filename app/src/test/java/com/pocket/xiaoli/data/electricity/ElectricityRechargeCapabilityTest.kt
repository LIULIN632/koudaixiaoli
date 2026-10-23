package com.pocket.xiaoli.data.electricity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 缴费页「按钮能不能用」的判定。
 *
 * ## 为什么这值得单测
 *
 * 这个判定直接决定**给用户看哪套界面**：
 * - 判「坏」→ 弹替代路径（微信打开 / 复制链接）
 * - 判「好」→ 让用户照常点「立即缴费」
 *
 * 两个方向判错的代价**不对称**：误判坏只是多一句提示；误判好会让用户
 * 反复点一个永远不响应的按钮，而且完全不知道为什么。所以规则要钉死、要有回归。
 *
 * 样本取自 2026-09-30 对 `ydny.ytu.edu.cn` 的真实抓取（见
 * [ElectricityRechargeCapability] 的 KDoc）。
 */
class ElectricityRechargeCapabilityTest {

    /** 真实抓到的「坏」样本：内层分支渲染成 "1"。 */
    private val broken = """
        function payItemCharge() {
            if("0" > "0"){ return fasle; }
            var amount = ${'$'}("#amount").val();
            var re = /^\+?[1-9][0-9]*$/ ;
            if(re.test(amount)) {
                if(parseFloat(amount) + parseFloat("43.09")>0){
                    if("" == "1"){
                        payItemCharge(amount);
                        return fasle;
                    }
                    window.location = "../../wechat/ytdx/YDPost?payMoney=" + amount;
                }
            }
        }
    """.trimIndent()

    /** 真实抓到的「好」样本：内层分支渲染成空串，会正常跳转。 */
    private val working = broken.replace("""if("" == "1")""", """if("" == "")""")

    @Test
    fun `渲染成1时判定为坏 —— 会无限递归`() {
        assertTrue(ElectricityRechargeCapability.isRechargeBroken(broken))
    }

    @Test
    fun `渲染成空串时判定为好 —— 会正常跳转缴费`() {
        assertFalse(ElectricityRechargeCapability.isRechargeBroken(working))
    }

    @Test
    fun `留空或拿不到页面时一律判定为可用`() {
        // 判不出来的方向必须是"放行"：宁可让用户自己点一下试试，
        // 也不要因为一次解析失败就武断地告诉他"这里缴不了费"。
        assertFalse(ElectricityRechargeCapability.isRechargeBroken(null))
        assertFalse(ElectricityRechargeCapability.isRechargeBroken(""))
        assertFalse(ElectricityRechargeCapability.isRechargeBroken("   "))
        assertFalse(ElectricityRechargeCapability.isRechargeBroken("<html>登录页</html>"))
    }

    @Test
    fun `不误伤页面上其它等于1的判断`() {
        // 缴费页里还有 if(result.code == 0) 之类；"1" 也常出现在别处。
        // 判定必须锚定 `if("" == "1")` 这个完整形状，否则会把正常页面误判成坏的。
        val otherOnes = """
            if("" == "0"){ }
            if(state == "1"){ }
            if(code == 1){ }
            var changeNum = "1";
            if("" == "11"){ }
        """.trimIndent()
        assertFalse(
            "只有 if(\"\" == \"1\") 才算坏，其它含 1 的判断都不能误伤",
            ElectricityRechargeCapability.isRechargeBroken(otherOnes),
        )
    }

    @Test
    fun `容得下服务端模板的空格差异`() {
        // 实测抓到的真实形态就是第二种（`if("" == "1")`，`)` 前**没有**空格）。
        // 其余是人类可读的排版差异，允许但不强求 —— 服务端改模板时多几个空格不该让它失效。
        assertTrue(
            "无空格",
            ElectricityRechargeCapability.isRechargeBroken("""if(""=="1"){"""),
        )
        assertTrue(
            "两侧各一个空格（真实形态）",
            ElectricityRechargeCapability.isRechargeBroken("""if("" == "1"){"""),
        )
        assertTrue(
            "换行分隔",
            ElectricityRechargeCapability.isRechargeBroken("if(\"\"\n    == \"1\")"),
        )
    }

    @Test
    fun `单引号写法不算命中 —— 只认双引号那一种真实渲染`() {
        // 页面上真实出现的是双引号（服务端模板产出）。
        // 单引号是假想变体，不予识别：判定要对着**见过的**形态，不做无依据的扩展，
        // 否则以后误判的风险比漏判更大（误判会白白告诉用户"这里缴不了费"）。
        assertFalse(
            ElectricityRechargeCapability.isRechargeBroken("""if('' == '1')"""),
        )
    }
}
