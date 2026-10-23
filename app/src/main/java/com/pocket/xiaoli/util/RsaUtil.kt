package com.pocket.xiaoli.util

import java.math.BigInteger
import java.security.KeyFactory
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object RsaUtil {
    fun encrypt(data: String, modulusStr: String, exponentStr: String): String {
        val mod: BigInteger
        val exp: BigInteger

        mod = if (modulusStr.matches(Regex("[0-9a-fA-F]+"))) {
            BigInteger(modulusStr, 16)
        } else {
            BigInteger(1, Base64.getDecoder().decode(modulusStr))
        }

        exp = if (exponentStr.matches(Regex("[0-9a-fA-F]+"))) {
            BigInteger(exponentStr, 16)
        } else {
            BigInteger(1, Base64.getDecoder().decode(exponentStr))
        }

        val keySpec = RSAPublicKeySpec(mod, exp)
        val keyFactory = KeyFactory.getInstance("RSA")
        val publicKey = keyFactory.generatePublic(keySpec)
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(encrypted)
    }
}
