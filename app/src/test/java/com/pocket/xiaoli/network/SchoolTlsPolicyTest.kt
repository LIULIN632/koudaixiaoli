package com.pocket.xiaoli.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 校园 TLS 兜底策略的守门人（方案 B，见 `docs/安全审计-TLS.md`）。
 *
 * 这里守两件事，都不会让别的测试变红、也不会让截图变化：
 *
 * 1. **白名单判定**：放行的只有 `yitsd.edu.cn` / `ytu.edu.cn` 及其子域，
 *    长得像的（`notyitsd.edu.cn`、`yitsd.edu.cn.attacker.com`）必须拒绝。
 * 2. **只能看叶子证书**：攻击者若能在链里塞一张带校园 SAN 的证书就放行，
 *    那这个兜底等于没做 —— 见 [SchoolTlsPolicy.leafClaimsSchoolDomain] 的用例。
 *
 * 第 3 个用例是源码扫描：它守的是「别再把 `hostnameVerifier` 写成无条件 true」，
 * 因为那正是这条安全债的原始形态，而且**它编译得过、跑得通、看起来一切正常**。
 */
class SchoolTlsPolicyTest {

    // ── 1. 白名单判定 ──────────────────────────────────────────────

    @Test
    fun `校区域名及其子域放行`() {
        listOf(
            "yitsd.edu.cn",
            "jwxt.yitsd.edu.cn",
            "ehall.yitsd.edu.cn",
            "ytu.edu.cn",
            "ydny.ytu.edu.cn",
        ).forEach { assertTrue("$it 应当放行", SchoolTlsPolicy.isSchoolHost(it)) }
    }

    @Test
    fun `大小写与结尾点号不影响判定`() {
        assertTrue(SchoolTlsPolicy.isSchoolHost("JWXT.YITSD.EDU.CN"))
        assertTrue(SchoolTlsPolicy.isSchoolHost("jwxt.yitsd.edu.cn."))
    }

    @Test
    fun `长得像的域名必须拒绝`() {
        listOf(
            "notyitsd.edu.cn",          // 后缀不是 .yitsd.edu.cn
            "yitsd.edu.cn.attacker.com", // 把校区域名当子域用
            "attacker.com",
            "edu.cn",
            "yitsd.edu.cn.evil",
            "xynyitsd.edu.cn",
        ).forEach { assertFalse("$it 必须拒绝", SchoolTlsPolicy.isSchoolHost(it)) }
    }

    @Test
    fun `空值不崩且拒绝`() {
        assertFalse(SchoolTlsPolicy.isSchoolHost(null))
        assertFalse(SchoolTlsPolicy.isSchoolHost(""))
        assertFalse(SchoolTlsPolicy.isSchoolHost("   "))
    }

    // ── 2. 只看叶子证书（防「链里夹带校园证书」绕过） ────────────────

    @Test
    fun `叶子声称校园域名时放行`() {
        assertTrue(
            SchoolTlsPolicy.leafClaimsSchoolDomain(
                listOf(listOf("jwxt.yitsd.edu.cn"), listOf("Some CA"))
            )
        )
    }

    @Test
    fun `叶子不是校园域名时，链里夹带校园证书也不放行`() {
        // ⚠️ 这条就是绕过手法：攻击者给 evil.com 发自签证书，链里跟一张带校园 SAN 的证书。
        // 若实现改成遍历整条链，这里会变 true，兜底就形同虚设。
        assertFalse(
            SchoolTlsPolicy.leafClaimsSchoolDomain(
                listOf(listOf("evil.com"), listOf("jwxt.yitsd.edu.cn"))
            )
        )
    }

    @Test
    fun `空链不放行`() {
        assertFalse(SchoolTlsPolicy.leafClaimsSchoolDomain(emptyList()))
        assertFalse(SchoolTlsPolicy.leafClaimsSchoolDomain(listOf(emptyList())))
    }

    @Test
    fun `叶子名字里任意一个命中即可`() {
        assertTrue(
            SchoolTlsPolicy.namesClaimSchoolDomain(listOf("a.com", "ehall.yitsd.edu.cn"))
        )
        assertFalse(SchoolTlsPolicy.namesClaimSchoolDomain(listOf("a.com", "b.com")))
    }

    // ── 3. CN 解析（Android 没有 javax.naming，只能手写） ────────────

    @Test
    fun `从 DN 里取 CN`() {
        assertEquals(
            "*.yitsd.edu.cn",
            SchoolTlsPolicy.commonNameOf("CN=*.yitsd.edu.cn,OU=IT,O=烟台理工学院,C=CN"),
        )
    }

    @Test
    fun `没有 CN 或空 DN 时返回 null`() {
        assertNull(SchoolTlsPolicy.commonNameOf("OU=IT,O=某校,C=CN"))
        assertNull(SchoolTlsPolicy.commonNameOf(null))
        assertNull(SchoolTlsPolicy.commonNameOf("CN="))
    }

    // ── 4. 源码扫描：别再写回「信任一切」 ────────────────────────────

    @Test
    fun `没有任何客户端再无条件接受主机名或信任一切证书`() {
        val root = File("app/src/main/java")
        if (!root.isDirectory) return  // 跑在别的工作目录下时不做判断

        // 原始形态的两行写法：hostnameVerifier { _, _ -> true } / 空实现的 checkServerTrusted。
        val forbiddenHostnameVerifier = Regex("""hostnameVerifier\s*\{\s*_\s*,\s*_\s*->\s*true\s*}""")
        val emptyCheckServerTrusted = Regex("""checkServerTrusted\s*\([^)]*\)\s*\{\s*}""")

        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .mapNotNull { file ->
                val text = file.readText()
                when {
                    forbiddenHostnameVerifier.containsMatchIn(text) ->
                        "${file.relativeTo(root).path}：hostnameVerifier 无条件返回 true"
                    emptyCheckServerTrusted.containsMatchIn(text) ->
                        "${file.relativeTo(root).path}：checkServerTrusted 是空实现（信任一切）"
                    else -> null
                }
            }
            .sorted()
            .toList()

        assertEquals(
            "这些文件把 TLS 校验关掉了。它承担的是教务账号密码登录与全部导入请求，\n" +
                "中间人可解密、可篡改。修法见 docs/安全审计-TLS.md 方案 B，\n" +
                "并用 SchoolTlsPolicy.trustManager() / hostnameVerifier()：",
            emptyList<String>(),
            offenders,
        )
    }
}
