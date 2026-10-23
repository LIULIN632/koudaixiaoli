package com.pocket.xiaoli.data.backup

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 完整备份的口令加密 / 解密。
 *
 * ## 为什么必须有这一层
 *
 * 导出的完整备份是一份**全量用户数据快照**：课表、成绩存档、待办、笔记、绑定过的
 * 教务账号标识。原来它是一个明文 JSON，用户随手存到网盘 / 微信 / 电脑桌面就等于
 * 把这些数据公开了。发布门禁要求「加密备份」正是这个原因。
 *
 * ## 格式
 *
 * 信封是普通 JSON（所以文件本身可读、可辨认），真正的内容是密文：
 * ```json
 * {
 *   "format": "pocket-xiaoli-backup",
 *   "version": 1,
 *   "kdf": "PBKDF2WithHmacSHA256",
 *   "iterations": 200000,
 *   "cipher": "AES/GCM/NoPadding",
 *   "salt": "<base64>",
 *   "iv": "<base64>",
 *   "data": "<base64 密文+tag>"
 * }
 * ```
 *
 * - 密钥由**用户口令 + 随机 salt** 经 PBKDF2 派生，不落盘、不进 `keystore`，
 *   所以备份文件换设备也能恢复（这正是备份的意义）。
 * - AES-GCM 自带完整性校验：口令错了、文件被改过，解密会失败而不是解出垃圾。
 *   这一点很重要 —— 它同时充当「密码错误」和「文件损坏」的判据。
 *
 * ## 向后兼容
 *
 * 老版本导出的**明文**备份仍然可以恢复（[isEncryptedBackup] 为 false 时按明文走），
 * 否则用户手上已有的备份会全部作废。
 */
object BackupCrypto {

    /** 信封里的格式标识，用来把加密备份和明文旧备份区分开。 */
    const val FORMAT = "pocket-xiaoli-backup"

    const val FORMAT_VERSION = 1
    const val KDF = "PBKDF2WithHmacSHA256"
    const val CIPHER = "AES/GCM/NoPadding"

    /** PBKDF2 迭代次数。2026 年的手机跑 20 万次约几十毫秒，值得。 */
    const val ITERATIONS = 200_000

    const val SALT_BYTES = 16
    const val IV_BYTES = 12
    const val TAG_BITS = 128
    const val KEY_BITS = 256

    /** 口令最短长度。太短的口令挡不住离线爆破，不如直接不让用。 */
    const val MIN_PASSPHRASE_LENGTH = 8

    /** 解密时接受的最低迭代次数；低于这个值拒绝，免得被喂一个「迭代 1 次」的信封拖延破解。 */
    const val MIN_ITERATIONS = 1_000

    /**
     * 口令是否可用；返回 `null` 表示可用，否则返回给用户看的原因。
     *
     * 纯函数，方便单测直接覆盖边界。
     */
    fun passphraseProblem(passphrase: CharArray): String? = when {
        passphrase.size < MIN_PASSPHRASE_LENGTH -> "口令至少 $MIN_PASSPHRASE_LENGTH 位"
        passphrase.all { it.isWhitespace() } -> "口令不能全是空格"
        else -> null
    }

    /** 把明文 JSON 加密成信封 JSON。 */
    fun encrypt(
        plaintext: String,
        passphrase: CharArray,
        random: SecureRandom = SecureRandom(),
        iterations: Int = ITERATIONS,
    ): String {
        passphraseProblem(passphrase)?.let { throw IllegalArgumentException(it) }
        require(iterations >= MIN_ITERATIONS) { "迭代次数过低" }

        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)

        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            deriveKey(passphrase, salt, iterations),
            GCMParameterSpec(TAG_BITS, iv),
        )
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val encoder = Base64.getEncoder()
        return JsonObject().apply {
            addProperty("format", FORMAT)
            addProperty("version", FORMAT_VERSION)
            addProperty("kdf", KDF)
            addProperty("iterations", iterations)
            addProperty("cipher", CIPHER)
            addProperty("salt", encoder.encodeToString(salt))
            addProperty("iv", encoder.encodeToString(iv))
            addProperty("data", encoder.encodeToString(ciphertext))
        }.toString()
    }

    /**
     * 解密信封 JSON。
     *
     * ⚠️ 迭代次数与算法**从信封里读**而不是用常量：以后调高 `ITERATIONS`
     * 或换 KDF 时，用户手上按旧参数生成的备份仍然必须能打开。
     *
     * @throws IllegalArgumentException 信封格式不对、口令不对，或文件被改动过（GCM tag 校验失败）。
     */
    fun decrypt(envelope: String, passphrase: CharArray): String {
        val root = try {
            JsonParser.parseString(envelope).takeIf { it.isJsonObject }?.asJsonObject
        } catch (e: Exception) {
            throw IllegalArgumentException("备份文件不是合法 JSON", e)
        } ?: throw IllegalArgumentException("备份文件不是 JSON 对象")

        if (root.get("format")?.asString != FORMAT) {
            throw IllegalArgumentException("不是「口袋小鲤」加密备份文件")
        }
        if (root.get("kdf")?.asString != KDF) {
            throw IllegalArgumentException("备份用了不支持的密钥派生算法（${root.get("kdf")?.asString}）")
        }
        if (root.get("cipher")?.asString != CIPHER) {
            throw IllegalArgumentException("备份用了不支持的加密算法（${root.get("cipher")?.asString}）")
        }
        val iterations = root.get("iterations")?.takeIf { it.isJsonPrimitive }?.asInt
            ?: ITERATIONS
        if (iterations < MIN_ITERATIONS) {
            throw IllegalArgumentException("备份声明的迭代次数过低（$iterations）")
        }

        val decoder = Base64.getDecoder()
        val salt = root.requireBase64(decoder, "salt")
        val iv = root.requireBase64(decoder, "iv")
        val data = root.requireBase64(decoder, "data")

        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(
            Cipher.DECRYPT_MODE,
            deriveKey(passphrase, salt, iterations),
            GCMParameterSpec(TAG_BITS, iv),
        )

        val plain = try {
            cipher.doFinal(data)
        } catch (e: Exception) {
            // GCM 校验失败只有两种可能：口令错，或文件被改过 / 截断。不区分，避免泄露信息。
            throw IllegalArgumentException("口令不对，或备份文件已损坏", e)
        }
        return String(plain, Charsets.UTF_8)
    }

    /**
     * 这段文本是不是本 App 的加密备份信封。
     *
     * 只做结构判断，不尝试解密 —— 恢复流程要在问用户口令**之前**用它分流，
     * 好让「老的明文备份」仍然能恢复。
     */
    fun isEncryptedBackup(text: String): Boolean {
        val root = runCatching {
            JsonParser.parseString(text).takeIf { it.isJsonObject }?.asJsonObject
        }.getOrNull() ?: return false
        return root.get("format")?.takeIf { it.isJsonPrimitive }?.asString == FORMAT
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance(KDF)
            SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun JsonObject.requireBase64(
        decoder: Base64.Decoder,
        key: String,
    ): ByteArray {
        val raw = get(key)?.takeIf { it.isJsonPrimitive }?.asString
            ?: throw IllegalArgumentException("加密备份缺少 $key 字段")
        return try {
            decoder.decode(raw)
        } catch (e: Exception) {
            throw IllegalArgumentException("加密备份的 $key 字段不是合法 base64", e)
        }
    }
}
