package com.pocket.xiaoli.network

import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * 校园主机的 TLS 校验策略（`docs/安全审计-TLS.md` 的**方案 B**：严格为主 + 仅校区域名兜底）。
 *
 * ## 背景
 *
 * `ZhengfangClient` / `QiangZhiClient` 原来用的是「信任一切证书 + 不校验主机名」：
 *
 * ```kotlin
 * checkServerTrusted(certs, authType) {}          // 空实现
 * .hostnameVerifier { _, _ -> true }
 * ```
 *
 * 而这条客户端承担的正是**教务账号密码登录**和全部导入请求 ⇒ 中间人可解密、可篡改。
 * 实测六台校园主机（jwxt / jiaowu / ehall / lib / www.yitsd.edu.cn + ydny.ytu.edu.cn）
 * **全部通过系统默认校验**，所以这个放宽从一开始就是多余的。
 *
 * 唯一不敢直接改严的理由是可用性：`*.yitsd.edu.cn` 的证书曾定于 2026-11-18 到期，
 * 学校若未续期，改严会让 App 完全打不开教务。方案 B 因此保留一个**很窄**的兜底。
 *
 * ## 兜底到底放宽了多少（务必看懂这三条，否则会误判它的安全性）
 *
 * 1. 信任链：**默认走系统校验**；只有系统校验抛异常、**且证书链的叶子证书自己声称
 *    是校区域名**时才接受。只看叶子，不看中间证书 —— 否则攻击者只要在链里塞一张
 *    带校园 SAN 的证书，就能让任意域名的自签证书通过。
 * 2. 主机名：**默认走系统校验**；只有严格校验失败、**且请求的主机确实是校区域名**
 *    时才放行。
 * 3. 两条合起来的效果：非校园主机拿到攻击者的自签证书时，第 1 条就会拒绝
 *    （叶子不声称校园域名）。所以放宽面被收窄到「校区域名」这一小撮，
 *    而不是原来的「任意主机」。
 *
 * 代价是老实话：**对校区域名本身，中间人仍然可能成功**（这正是方案 B 换来的可用性）。
 * 想彻底解决只能等学校证书稳定后续期，然后走方案 A（直接把这里退回系统默认）。
 *
 * ## 为什么不靠 trustManager 判主机
 *
 * `X509TrustManager.checkServerTrusted(chain, authType)` **拿不到主机名**（Android 的
 * TLS 栈调的就是这个两参版本），所以「按主机白名单放行」只能在 [hostnameVerifier]
 * 里做，trustManager 这一侧改用「叶子证书是否声称校园域名」来近似。
 *
 * 守门人：`app/src/test/java/com/pocket/xiaoli/network/SchoolTlsPolicyTest.kt`
 * —— 白名单内放行、白名单外拒绝、以及「链里夹带校园证书」这个绕过手法。
 *
 * ⚠️ 改动这里必须补真机验证（登录 + 导入各跑一次），见 `docs/安全审计-TLS.md` §2.2。
 */
object SchoolTlsPolicy {

    /**
     * 允许兜底的校区域名后缀。
     *
     * 只放真实在服务的域名。加域名前先确认它真的需要放宽 —— 六台主机当前都通过
     * 系统校验，正常情况下这里一条都用不上。
     */
    val SCHOOL_DOMAIN_SUFFIXES: List<String> = listOf("yitsd.edu.cn", "ytu.edu.cn")

    /** 主机名是否属于校区。大小写不敏感，尾部点号忽略。 */
    fun isSchoolHost(host: String?): Boolean {
        val normalized = host?.trim()?.lowercase()?.trimEnd('.') ?: return false
        if (normalized.isEmpty()) return false
        return SCHOOL_DOMAIN_SUFFIXES.any { suffix ->
            normalized == suffix || normalized.endsWith(".$suffix")
        }
    }

    /**
     * 证书的 Subject 里取 CN（纯字符串处理，可单测）。
     *
     * ⚠️ 不能用 `javax.naming.ldap.LdapName` —— **Android 没有 `javax.naming`**。
     * 这里只处理 DN 里最常见的 `CN=xxx` 形态；拿不到就返回 null，后面靠 SAN 兜。
     */
    fun commonNameOf(distinguishedName: String?): String? {
        val dn = distinguishedName ?: return null
        return dn.split(',')
            .asSequence()
            .map { it.trim() }
            .firstOrNull { it.substringBefore('=').trim().equals("CN", ignoreCase = true) }
            ?.substringAfter('=', "")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    /** 名字列表里有没有一个属于校区（纯函数，可单测）。 */
    fun namesClaimSchoolDomain(names: List<String>): Boolean =
        names.any { isSchoolHost(it) }

    /**
     * 纯逻辑版：只有**叶子**声称校园域名才算数。
     *
     * 把「只看第一张」这条规则单独提出来，是为了能在不伪造证书的前提下单测
     * （`X509Certificate` 有二十来个抽象方法，为测试实现一个假证书不值得）。
     *
     * @param chainNames 证书链自报的名字，`chainNames[0]` 是叶子。
     */
    fun leafClaimsSchoolDomain(chainNames: List<List<String>>): Boolean =
        chainNames.firstOrNull()?.let { namesClaimSchoolDomain(it) } ?: false

    /**
     * 证书链的**叶子**证书是否自己声称是校区域名。
     *
     * ⚠️ 只看 `chain[0]`。若遍历整条链，攻击者可以构造
     * `[ evil.com 自签证书, 任意带校园 SAN 的证书 ]` 让本函数返回 true，
     * 从而让 evil.com 的信任校验被放行（而 evil.com 的主机名校验是通过的）。
     */
    fun certificateClaimsSchoolDomain(chain: Array<X509Certificate>?): Boolean {
        val chain = chain ?: return false
        return leafClaimsSchoolDomain(chain.map { namesOf(it) })
    }

    /** 取一张证书自报的名字：SAN 里的 dNSName + Subject 的 CN。 */
    fun namesOf(cert: X509Certificate): List<String> {
        val dnsNames = runCatching {
            cert.subjectAlternativeNames.orEmpty()
                .filter { it.size >= 2 && (it[0] as? Int) == SAN_TYPE_DNS }
                .mapNotNull { it[1] as? String }
        }.getOrDefault(emptyList())
        return dnsNames + listOfNotNull(commonNameOf(cert.subjectX500Principal?.name))
    }

    /** SAN 里 dNSName 的类型编号（RFC 5280）。 */
    private const val SAN_TYPE_DNS = 2

    /**
     * 系统默认信任管理器。
     *
     * `TrustManagerFactory` 用默认 `KeyStore` 初始化，等价于平台出厂信任库。
     */
    private val systemTrustManager: X509TrustManager by lazy {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        factory.trustManagers
            .filterIsInstance<X509TrustManager>()
            .firstOrNull()
            ?: error("系统没有提供 X509TrustManager")
    }

    /**
     * 「严格为主 + 校园域名兜底」的信任管理器。
     *
     * 客户端证书校验直接委托系统（我们不做双向认证）。
     */
    fun trustManager(): X509TrustManager = object : X509TrustManager {

        override fun getAcceptedIssuers(): Array<X509Certificate> =
            systemTrustManager.acceptedIssuers

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
            systemTrustManager.checkClientTrusted(chain, authType)
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            try {
                systemTrustManager.checkServerTrusted(chain, authType)
            } catch (e: CertificateException) {
                // 系统拒绝：只有叶子证书自己声称是校区域名时才兜底，否则原样抛出。
                if (!certificateClaimsSchoolDomain(chain)) throw e
            }
        }
    }

    /**
     * 「严格为主 + 校园域名兜底」的主机名校验器。
     *
     * 默认用平台自带校验（`HttpsURLConnection` 的默认实现，做 RFC 2818 匹配）；
     * 只有严格校验失败、且请求主机确实是校区域名时才放行。
     */
    fun hostnameVerifier(): HostnameVerifier {
        val strict = HttpsURLConnection.getDefaultHostnameVerifier()
        return HostnameVerifier { host, session ->
            if (strict.verify(host, session)) {
                true
            } else {
                // 严格校验失败：拒绝任意主机，只对校区域名开口子。
                isSchoolHost(host)
            }
        }
    }

    /** 新建一个使用本策略的 SSLContext。 */
    fun sslContext(trustManager: X509TrustManager): SSLContext =
        SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        }
}
