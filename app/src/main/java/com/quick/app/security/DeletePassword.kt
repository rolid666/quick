package com.quick.app.security

import com.quick.app.data.db.AppDatabase
import com.quick.app.data.db.AppSetting
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * 记录删除密码 —— **不写死在代码里**：只把「盐 + PBKDF2 哈希」存在本机 app_setting，
 * 明文永不落库、也不进配置备份 JSON（备份只含线别/机种/站别/通信参数）。
 *
 * - 从未改过密码时，用出厂默认 [DEFAULT] 校验（此时库里没有记录，等于「默认口令」）；
 * - 改过之后一律以库里的盐+哈希为准，默认口令立即失效。
 *
 * 用途仅限「删除测量记录」（用户 2026-09 定：配置页/管理页不设卡，不打断现场操作）。
 */
object DeletePassword {

    /** 出厂默认口令；首次修改密码后即失效 */
    const val DEFAULT = "1234"

    private const val KEY = "pwd.delete"
    private const val ITERATIONS = 12_000
    private const val KEY_LENGTH = 256
    private const val SALT_BYTES = 16

    /** 是否仍是出厂默认口令 */
    suspend fun isDefault(db: AppDatabase): Boolean = db.settingDao().get(KEY).isNullOrBlank()

    /**
     * 校验口令。输入为空一律不通过。
     * 比较用 [MessageDigest.isEqual]（定长比较，避免逐字节提前返回的时序差异）。
     */
    suspend fun verify(db: AppDatabase, input: String): Boolean {
        if (input.isEmpty()) return false
        val stored = db.settingDao().get(KEY)
        if (stored.isNullOrBlank()) return input == DEFAULT
        val parts = stored.split(":")
        if (parts.size != 2) return false
        val salt = parts[0].hexToBytes() ?: return false
        val expect = parts[1].hexToBytes() ?: return false
        val got = hash(input, salt)
        return MessageDigest.isEqual(expect, got)
    }

    /** 设置/修改口令。库中只落盐与哈希，长度下限 4 位（现场易用性优先，不强制复杂度） */
    suspend fun set(db: AppDatabase, newPwd: String) {
        require(newPwd.length >= 4) { "密码至少 4 位" }
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = hash(newPwd, salt)
        db.settingDao().put(AppSetting(KEY, salt.toHex() + ":" + hash.toHex()))
    }

    private fun hash(pwd: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pwd.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

private fun String.hexToBytes(): ByteArray? {
    if (length % 2 != 0) return null
    return try {
        ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    } catch (_: NumberFormatException) {
        null
    }
}
