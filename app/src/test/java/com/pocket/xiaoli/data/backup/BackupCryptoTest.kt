package com.pocket.xiaoli.data.backup

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 完整备份加密的判据。
 *
 * ⚠️ 这些断言里「明文不出现在密文里」「错口令必须抛异常」是**发布门禁**要求的本体，
 * 不是锦上添花：只要其中一条退化，加密就是摆设，而界面上看起来一切正常。
 */
class BackupCryptoTest {

    /** 测试里用低迭代次数跑，避免 2 万次 PBKDF2 把单测拖慢；默认值另有专门的用例覆盖。 */
    private val fastIterations = 10_000
    private val passphrase = "correct-horse".toCharArray()

    private fun encryptFast(plaintext: String) =
        BackupCrypto.encrypt(plaintext, passphrase, iterations = fastIterations)

    @Test
    fun roundTripReturnsOriginalText() {
        val backup = """{"tables":{"courses":[{"id":1,"name":"高等数学"}]}}"""
        val envelope = encryptFast(backup)
        assertEquals(backup, BackupCrypto.decrypt(envelope, passphrase))
    }

    @Test
    fun defaultIterationsRoundTrip() {
        // 走一次默认参数，确保生产路径（不带 iterations 参数）也是通的。
        val backup = """{"tables":{}}"""
        val envelope = BackupCrypto.encrypt(backup, passphrase)
        assertEquals(backup, BackupCrypto.decrypt(envelope, passphrase))
        assertEquals(
            BackupCrypto.ITERATIONS,
            JsonParser.parseString(envelope).asJsonObject.get("iterations").asInt,
        )
    }

    @Test
    fun plaintextIsNotPresentInCiphertext() {
        // 用一个绝不可能碰巧出现的标记串，保证命中的是真实泄露而不是巧合。
        val marker = "ZZQQMARKERXKCD42"
        val envelope = encryptFast("""{"note":"$marker"}""")
        assertFalse("密文里出现了明文标记，说明根本没加密", envelope.contains(marker))
    }

    @Test
    fun wrongPassphraseIsRejected() {
        val envelope = encryptFast("""{"tables":{}}""")
        val failure = runCatching { BackupCrypto.decrypt(envelope, "wrong-passphrase".toCharArray()) }
        assertTrue(failure.isFailure)
        assertTrue(failure.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun tamperedCiphertextIsRejected() {
        val envelope = encryptFast("""{"tables":{"courses":[]}}""")
        val root = JsonParser.parseString(envelope).asJsonObject
        val data = root.get("data").asString
        // 翻一位 base64 字符，模拟文件被人改过 / 传输损坏。
        //
        // ⚠️ 翻的是**正中间**那一位，不是倒数第二位。倒数第二位落在最后一组 base64 上，
        // 而那一组的低位可能整组都是补位（"AB=" 改 "AA=" 解出来是同一个字节）——
        // 这时候 GCM 当然不报错，测试就随机变红。2026-10 实测过两次这种假失败。
        val index = data.length / 2
        val flipped = data.substring(0, index) +
            (if (data[index] == 'A') 'B' else 'A') +
            data.substring(index + 1)
        root.addProperty("data", flipped)
        val failure = runCatching {
            BackupCrypto.decrypt(root.toString(), passphrase)
        }
        assertTrue("被改过的备份必须解密失败（GCM tag 校验）", failure.isFailure)
    }

    @Test
    fun samePlaintextProducesDifferentCiphertext() {
        // 每次都要换 salt/iv，否则相同内容会产出相同密文，直接把「用户的课表长什么样」暴露成可比对特征。
        val a = encryptFast("""{"tables":{}}""")
        val b = encryptFast("""{"tables":{}}""")
        assertNotEquals(a, b)
    }

    @Test
    fun unicodeAndNewlinesSurvive() {
        val backup = "课程：高等数学 📚\n教师：张老师\r\n\t制表符"
        assertEquals(backup, BackupCrypto.decrypt(encryptFast(backup), passphrase))
    }

    @Test
    fun envelopeIsSelfDescribing() {
        val root = JsonParser.parseString(encryptFast("{}")).asJsonObject
        assertEquals(BackupCrypto.FORMAT, root.get("format").asString)
        assertEquals(BackupCrypto.FORMAT_VERSION, root.get("version").asInt)
        assertEquals(BackupCrypto.KDF, root.get("kdf").asString)
        assertEquals(BackupCrypto.CIPHER, root.get("cipher").asString)
        assertEquals(fastIterations, root.get("iterations").asInt)
    }

    @Test
    fun decryptionUsesIterationsFromEnvelope() {
        // 用非默认迭代次数加密，解密必须按信封里的值来，否则这条会失败。
        val envelope = BackupCrypto.encrypt("""{"tables":{}}""", passphrase, iterations = 4_096)
        assertEquals("""{"tables":{}}""", BackupCrypto.decrypt(envelope, passphrase))
    }

    // ── 口令强度 ──

    @Test
    fun shortPassphraseIsRejectedWithReason() {
        assertNull(BackupCrypto.passphraseProblem("12345678".toCharArray()))
        assertTrue(BackupCrypto.passphraseProblem("1234567".toCharArray())!!.contains("8"))
    }

    @Test
    fun whitespaceOnlyPassphraseIsRejected() {
        val problem = BackupCrypto.passphraseProblem("          ".toCharArray())
        assertTrue(problem != null)
    }

    @Test
    fun encryptRefusesWeakPassphrase() {
        val failure = runCatching {
            BackupCrypto.encrypt("{}", "123".toCharArray(), iterations = fastIterations)
        }
        assertTrue(failure.isFailure)
    }

    // ── 备份类型识别（恢复流程靠它给老明文备份留路）──

    @Test
    fun encryptedEnvelopeIsRecognised() {
        assertTrue(BackupCrypto.isEncryptedBackup(encryptFast("{}")))
    }

    @Test
    fun legacyPlaintextBackupIsNotMistakenForEncrypted() {
        val legacy = """{"exportedAt":1,"databaseVersion":13,"tables":{"courses":[]}}"""
        assertFalse(BackupCrypto.isEncryptedBackup(legacy))
    }

    @Test
    fun garbageIsRejectedAsMalformedEnvelope() {
        val failure = runCatching { BackupCrypto.decrypt("not json at all", passphrase) }
        assertTrue(failure.isFailure)
        assertTrue(failure.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun missingFieldGivesUnderstandableError() {
        val envelope = JsonParser.parseString(encryptFast("{}")).asJsonObject
        envelope.remove("salt")
        val failure = runCatching {
            BackupCrypto.decrypt(envelope.toString(), passphrase)
        }
        assertTrue(failure.exceptionOrNull()!!.message!!.contains("salt"))
    }

    @Test
    fun absurdlyLowIterationsAreRefused() {
        val envelope = JsonParser.parseString(encryptFast("{}")).asJsonObject
        envelope.addProperty("iterations", 1)
        val failure = runCatching { BackupCrypto.decrypt(envelope.toString(), passphrase) }
        assertTrue(failure.isFailure)
    }
}
