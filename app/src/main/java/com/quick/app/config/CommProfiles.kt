package com.quick.app.config

import com.quick.app.data.db.AppDatabase
import com.quick.app.data.db.AppSetting
import com.quick.app.data.db.DeviceConfig
import org.json.JSONArray
import org.json.JSONObject

/**
 * 仪器通信参数（MODBUS TCP/IP）历史 —— 整组保存：IP / 端口 / UnitID / 轮询周期 / 超时。
 *
 * 现场会有多台仪器或同事换班，每次重填容易填错；这里把「保存过的通信配置」记下来，
 * 下次下拉直接选。纯本地（app_setting），不进配置备份 JSON（避免导入时覆盖别人的现场）。
 *
 * 去重规则：**IP + 端口 + UnitID 相同视为同一条**，重复保存只更新时间并置顶；
 * 最多保留 [MAX] 条，超出淘汰最旧的。
 */
data class CommProfile(
    val ip: String,
    val port: Int,
    val unitId: Int,
    val pollIntervalMs: Long,
    val timeoutMs: Long,
    val savedAt: Long
) {
    /** 下拉与列表显示用 */
    val label: String get() = "$ip:$port · Unit $unitId · ${pollIntervalMs}ms"
}

object CommProfiles {

    const val KEY = "comm.profiles"
    const val MAX = 20

    /** 读取历史（新的在前）；坏数据静默忽略，不影响配置页可用 */
    suspend fun load(db: AppDatabase): List<CommProfile> {
        val raw = runCatching { db.settingDao().get(KEY) }.getOrNull() ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val ip = o.optString("ip").trim()
                if (ip.isEmpty()) return@mapNotNull null
                CommProfile(
                    ip = ip,
                    port = o.optInt("port", 502),
                    unitId = o.optInt("unitId", 1),
                    pollIntervalMs = o.optLong("pollIntervalMs", 900),
                    timeoutMs = o.optLong("timeoutMs", 2000),
                    savedAt = o.optLong("savedAt", 0L)
                )
            }.sortedByDescending { it.savedAt }
        }.getOrDefault(emptyList())
    }

    /** 记一条（相同 IP+端口+UnitID 则更新并置顶），返回最新列表 */
    suspend fun remember(db: AppDatabase, cfg: DeviceConfig): List<CommProfile> {
        if (cfg.ip.isBlank()) return load(db)
        val entry = CommProfile(
            ip = cfg.ip.trim(), port = cfg.port, unitId = cfg.unitId,
            pollIntervalMs = cfg.pollIntervalMs, timeoutMs = cfg.timeoutMs,
            savedAt = System.currentTimeMillis()
        )
        val merged = (listOf(entry) + load(db).filterNot { it.sameTargetAs(entry) }).take(MAX)
        write(db, merged)
        return merged
    }

    suspend fun remove(db: AppDatabase, profile: CommProfile): List<CommProfile> {
        val rest = load(db).filterNot { it.sameTargetAs(profile) }
        write(db, rest)
        return rest
    }

    private suspend fun write(db: AppDatabase, list: List<CommProfile>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject()
                    .put("ip", p.ip).put("port", p.port).put("unitId", p.unitId)
                    .put("pollIntervalMs", p.pollIntervalMs).put("timeoutMs", p.timeoutMs)
                    .put("savedAt", p.savedAt)
            )
        }
        db.settingDao().put(AppSetting(KEY, arr.toString()))
    }

    /** 同一台仪器（IP+端口+UnitID）—— 轮询/超时不同也算同一条，只更新参数 */
    private fun CommProfile.sameTargetAs(o: CommProfile): Boolean =
        ip == o.ip && port == o.port && unitId == o.unitId
}
