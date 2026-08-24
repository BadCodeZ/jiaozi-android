package com.jiaozi.sz.domain

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 同步包加解密（SYNCPKG1，与「综合教资备考工作台.html」字节级一致）。
 *
 * 线格式：SYNCPKG1:<b64(salt16)>.<b64(iv12)>.<b64(ct+tag)>
 *  - PBKDF2(SHA-256, 100000 次, salt) → AES-256-GCM 密钥
 *  - AES-GCM, IV=12 字节, 认证标签 16 字节（附在密文后）
 *  - Base64 用 java.util.Base64（标准字母表 + 填充，无换行），与网页端 btoa 一致
 *
 * 明文包（未开启加密）直接以 JSON 传输，无前缀。
 * 纯 Java SE（API 26+ 可用，minSdk=26），可在 JVM 单测。
 */
object SyncCrypto {
    private const val PREFIX = "SYNCPKG1:"
    private const val ITERATIONS = 100_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    /** 加密：返回 SYNCPKG1: 前缀的密文包 */
    fun wrap(pass: String, plainJson: String): String {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(pass, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val ct = cipher.doFinal(plainJson.toByteArray(Charsets.UTF_8))
        return PREFIX + b64(salt) + "." + b64(iv) + "." + b64(ct)
    }

    /** 解密：自动识别明文（以 { 开头）或 SYNCPKG1 密文包；返回明文 JSON 字符串 */
    fun unwrap(pass: String, pkg: String): String {
        val s = pkg.trim()
        val body = if (s.startsWith(PREFIX)) s.substring(PREFIX.length) else s
        if (body.startsWith("{")) return body
        val parts = body.split(".")
        if (parts.size != 3) throw IllegalArgumentException("同步包格式不正确")
        val salt = deb64(parts[0]); val iv = deb64(parts[1]); val data = deb64(parts[2])
        val key = deriveKey(pass, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(data), Charsets.UTF_8)
    }

    private fun deriveKey(pass: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(pass.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val key = factory.generateSecret(spec)
        return SecretKeySpec(key.encoded, "AES")
    }

    private fun b64(b: ByteArray): String = Base64.getEncoder().encodeToString(b)
    private fun deb64(s: String): ByteArray = Base64.getDecoder().decode(s)
}
