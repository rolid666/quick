package com.quick.app.config

import com.quick.app.data.db.AppDatabase
import com.quick.app.data.db.DeviceConfig
import com.quick.app.data.db.Line
import com.quick.app.data.db.Model
import org.json.JSONArray
import org.json.JSONObject

/**
 * 配置备份：JSON 文件 ⇄ Room（仅线别/机种/仪器配置，绝不触碰测量历史）。
 * Room 是配置的唯一真源，JSON 只做导入/导出载体 —— 避免双写一致性问题
 * （见 docs/可行性分析与通讯规格-V1.0.md §10）。
 */
data class BackupResult(val importedLines: Int, val importedModels: Int, val deviceUpdated: Boolean)

object ConfigBackup {

    suspend fun export(db: AppDatabase): String {
        val lines = JSONArray()
        db.lineDao().snapshot().forEach { lines.put(it.name) }
        val models = JSONArray()
        db.modelDao().snapshot().forEach { models.put(it.name) }
        val cfg = db.configDao().getOnce() ?: DeviceConfig()
        val root = JSONObject()
            .put("app", "quik-191af-collector")
            .put("version", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("device", JSONObject()
                .put("name", cfg.name)
                .put("ip", cfg.ip)
                .put("port", cfg.port)
                .put("unitId", cfg.unitId)
                .put("pollIntervalMs", cfg.pollIntervalMs)
                .put("timeoutMs", cfg.timeoutMs)
                .put("autoStart", cfg.autoStart))
            .put("lines", lines)
            .put("models", models)
        return root.toString(2)
    }

    /**
     * 导入：线别/机种同名跳过（返回计数），仪器配置直接覆盖。
     * 全程不触碰 measurement_record 表。
     */
    suspend fun import(db: AppDatabase, json: String): BackupResult {
        val root = JSONObject(json)
        if (root.optString("app") != "quik-191af-collector") {
            throw IllegalArgumentException("不是本应用的配置文件")
        }
        val dev = root.getJSONObject("device")
        val cfg = DeviceConfig(
            id = 1,
            name = dev.optString("name", "191AF+"),
            ip = dev.optString("ip", ""),
            port = dev.optInt("port", 502),
            unitId = dev.optInt("unitId", 1),
            pollIntervalMs = dev.optLong("pollIntervalMs", 1000),
            timeoutMs = dev.optLong("timeoutMs", 2000),
            autoStart = dev.optBoolean("autoStart", true)
        )
        db.configDao().put(cfg)

        val now = System.currentTimeMillis()
        var importedLines = 0
        val linesJson = root.optJSONArray("lines")
        val lineCount = linesJson?.length() ?: 0
        for (i in 0 until lineCount) {
            val name = linesJson.getString(i).trim()
            if (name.isEmpty()) continue
            if (db.lineDao().findByName(name) == null) {
                db.lineDao().insert(Line(name = name, createdAt = now, updatedAt = now))
                importedLines++
            }
        }
        val models = root.optJSONArray("models") ?: JSONArray()
        val fresh = ArrayList<Model>(models.length())
        for (i in 0 until models.length()) {
            val name = models.getString(i).trim()
            if (name.isEmpty()) continue
            if (db.modelDao().findByName(name) == null) {
                fresh.add(Model(name = name, createdAt = now, updatedAt = now))
            }
        }
        val inserted = db.modelDao().insertIgnoreAll(fresh).count { it != -1L }
        return BackupResult(importedLines, inserted, deviceUpdated = true)
    }
}
