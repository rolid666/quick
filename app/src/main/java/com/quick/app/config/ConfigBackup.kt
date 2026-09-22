package com.quick.app.config

import com.quick.app.data.db.AppDatabase
import com.quick.app.data.db.DeviceConfig
import com.quick.app.data.db.DeviceSn
import com.quick.app.data.db.LIMIT_RMAX
import com.quick.app.data.db.LIMIT_VMAX
import com.quick.app.data.db.Line
import com.quick.app.data.db.LimitPreset
import com.quick.app.data.db.Model
import com.quick.app.data.db.SN_MANUAL
import com.quick.app.data.db.Station
import com.quick.app.data.db.TempPreset
import com.quick.app.data.db.TORQUE_DICT_KINDS
import com.quick.app.data.db.TQ_RANGE
import com.quick.app.data.db.TorqueDict
import com.quick.app.data.db.limitPresetName
import com.quick.app.data.db.tempPresetName
import com.quick.app.data.db.torqueRangeName
import org.json.JSONArray
import org.json.JSONObject

/**
 * 配置备份：JSON 文件 ⇄ Room
 * （仅线别/机种/站别/温度设置/漏电压上限/接地电阻上限/设备编号/仪器配置/扭力计四项字典，
 * 绝不触碰任何测量历史 —— 烙铁的 measurement_record 与扭力计的 torque_record 都不在备份范围内）。
 * Room 是配置的唯一真源，JSON 只做导入/导出载体 —— 避免双写一致性问题
 * （见 docs/可行性分析与通讯规格-V1.0.md §10）。
 *
 * 不含删除密码与通信配置历史（前者是安全凭据、后者是本机现场记录，
 * 导入别人的备份不应改变本机的口令与历史列表）。
 *
 * ⚠️ 格式标识 `app = "quik-191af-collector"` **保持不变**（2026-09-21 用户拍板）：
 * 它只是一个格式标识符，改了会让所有既有备份文件**导入失败** —— 现场手里的备份比名字重要。
 * 版本 4→5 多了 `torqueDicts`；5→6 是**扭矩范围带上限/下限两个数**（2026-09-22 用户要求 5）。
 * 两处都是**向前兼容**：旧版备份缺的字段按「没有」处理，照样能导入。
 */
data class BackupResult(
    val importedLines: Int,
    val importedModels: Int,
    val importedStations: Int,
    val importedPresets: Int,
    val importedLimits: Int,
    val importedDeviceSns: Int,
    val deviceUpdated: Boolean = true,
    /** 扭力计四项字典（线别/机种/扭矩范围/设备信息）本次新增的条数 */
    val importedTorqueDicts: Int = 0
)

object ConfigBackup {

    suspend fun export(db: AppDatabase): String {
        val lines = JSONArray()
        db.lineDao().snapshot().forEach { lines.put(it.name) }
        val models = JSONArray()
        db.modelDao().snapshot().forEach { models.put(it.name) }
        val stations = JSONArray()
        db.stationDao().snapshot().forEach { stations.put(it.name) }
        // 温度设置带两个数字（名称可由它们重算，不依赖导出时的显示格式）
        val presets = JSONArray()
        db.tempPresetDao().snapshot().forEach {
            presets.put(JSONObject()
                .put("name", it.name)
                .put("setTemp", it.setTemp)
                .put("tolerance", it.tolerance))
        }
        // 上限设置（漏电压/接地电阻）：只存类别 + 寄存器原值，名称由两者重算
        val limits = JSONArray()
        db.limitPresetDao().snapshot().forEach {
            limits.put(JSONObject().put("kind", it.kind).put("valueRaw", it.valueRaw))
        }
        val deviceSns = JSONArray()
        db.deviceSnDao().snapshot().forEach { deviceSns.put(it.name) }
        // 扭力计四项字典（v5 起）：**带 kind**，四类共用一个数组（表就是一张，见 TorqueDict）
        val torqueDicts = torqueDictsToJson(db.torqueDictDao().snapshotAll())
        val cfg = db.configDao().getOnce() ?: DeviceConfig()
        val root = JSONObject()
            .put("app", "quik-191af-collector")
            .put("version", 6)
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
            .put("stations", stations)
            .put("tempPresets", presets)
            .put("limitPresets", limits)
            .put("deviceSns", deviceSns)
            .put("torqueDicts", torqueDicts)
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
            name = dev.optString("name", "192AF+"),
            ip = dev.optString("ip", ""),
            port = dev.optInt("port", 502),
            unitId = dev.optInt("unitId", 1),
            pollIntervalMs = dev.optLong("pollIntervalMs", 900),
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

        // 站别：v2 备份起才有该数组；旧备份缺这项时按 0 处理（向前兼容）
        val stations = root.optJSONArray("stations") ?: JSONArray()
        val freshStations = ArrayList<Station>(stations.length())
        for (i in 0 until stations.length()) {
            val name = stations.getString(i).trim()
            if (name.isEmpty()) continue
            if (db.stationDao().findByName(name) == null) {
                freshStations.add(Station(name = name, createdAt = now, updatedAt = now))
            }
        }
        val insertedStations = db.stationDao().insertIgnoreAll(freshStations).count { it != -1L }

        // 温度设置：v3 备份起才有该数组；旧备份缺这项时按 0 处理（向前兼容）。
        // 名称以两个数字重算，避免备份文件里手改过的 name 与参数不一致。
        val presets = root.optJSONArray("tempPresets") ?: JSONArray()
        val freshPresets = ArrayList<TempPreset>(presets.length())
        for (i in 0 until presets.length()) {
            val o = presets.optJSONObject(i) ?: continue
            val setTemp = o.optInt("setTemp", Int.MIN_VALUE)
            val tol = o.optInt("tolerance", Int.MIN_VALUE)
            if (setTemp == Int.MIN_VALUE || tol == Int.MIN_VALUE) continue
            val name = tempPresetName(setTemp, tol)
            if (db.tempPresetDao().findByName(name) == null) {
                freshPresets.add(TempPreset(name = name, setTemp = setTemp, tolerance = tol,
                    createdAt = now, updatedAt = now))
            }
        }
        val insertedPresets = db.tempPresetDao().insertIgnoreAll(freshPresets).count { it != -1L }

        // 上限设置：v4 备份起才有该数组；旧备份缺这项时按 0 处理（向前兼容）。
        // 名称同样重算，并且只认两个已知类别 —— 备份文件里写别的 kind 一律丢弃。
        val limits = root.optJSONArray("limitPresets") ?: JSONArray()
        val freshLimits = ArrayList<LimitPreset>(limits.length())
        for (i in 0 until limits.length()) {
            val o = limits.optJSONObject(i) ?: continue
            val kind = o.optString("kind", "")
            val valueRaw = o.optInt("valueRaw", Int.MIN_VALUE)
            if (kind != LIMIT_VMAX && kind != LIMIT_RMAX) continue
            if (valueRaw == Int.MIN_VALUE || valueRaw <= 0) continue
            val name = limitPresetName(kind, valueRaw)
            if (db.limitPresetDao().find(kind, name) == null) {
                freshLimits.add(LimitPreset(kind = kind, name = name, valueRaw = valueRaw,
                    createdAt = now, updatedAt = now))
            }
        }
        val insertedLimits = db.limitPresetDao().insertIgnoreAll(freshLimits).count { it != -1L }

        // 设备编号：同理，旧备份缺这项时按 0 处理；导入的统一记为手输来源
        // （「仪器」标记只表示本机真的从仪器读到过，导入的编号不具备这个含义）
        val deviceSns = root.optJSONArray("deviceSns") ?: JSONArray()
        val freshSns = ArrayList<DeviceSn>(deviceSns.length())
        for (i in 0 until deviceSns.length()) {
            val name = deviceSns.getString(i).trim()
            if (name.isEmpty()) continue
            if (db.deviceSnDao().findByName(name) == null) {
                freshSns.add(DeviceSn(name = name, source = SN_MANUAL, createdAt = now, updatedAt = now))
            }
        }
        val insertedSns = db.deviceSnDao().insertIgnoreAll(freshSns).count { it != -1L }

        // 扭力计字典：v5 备份起才有该数组；旧备份缺这项时按 0 处理（向前兼容）。
        // 解析规则（已知 kind / 名称重算 / 上下限缺一不可）全在 [torqueDictsFromJson] 里，
        // 那里是纯函数，被单测钉死；这里只做「库里已有同名就跳过」。
        val parsedTorque = torqueDictsFromJson(root.optJSONArray("torqueDicts") ?: JSONArray(), now)
        val freshTorque = parsedTorque.filter { db.torqueDictDao().find(it.kind, it.name) == null }
        val insertedTorque = db.torqueDictDao().insertIgnoreAll(freshTorque).count { it != -1L }

        return BackupResult(
            importedLines, inserted, insertedStations, insertedPresets, insertedLimits, insertedSns,
            deviceUpdated = true, importedTorqueDicts = insertedTorque
        )
    }
}

// ---------- 扭力计字典的 JSON ⇄ 实体（纯函数，单测覆盖）----------
// 单独拎出来是为了能对「导出→导入」这一步做真单测（不依赖 Room/Android）。
// 用户 2026-09-22 要求 7：「最后检查一下配置数据的导出和导入是否正常」——
// 这就是那项检查的落点：规则写在纯函数里，被测试直接钉住。

/** 导出：扭力计四项字典。**扭矩范围带上限/下限两个数**（v6），其余三类只有名字 */
fun torqueDictsToJson(items: List<TorqueDict>): JSONArray {
    val arr = JSONArray()
    items.forEach { d ->
        val o = JSONObject().put("kind", d.kind).put("name", d.name)
        // 只有扭矩范围有这两个数；其余类别不写进去（不塞一堆 null 让备份文件变难读）
        if (d.kind == TQ_RANGE) {
            d.minValue?.let { o.put("minValue", it) }
            d.maxValue?.let { o.put("maxValue", it) }
        }
        arr.put(o)
    }
    return arr
}

/**
 * 导入：解析 + 净化。规则（每一条都被单测钉着）：
 * 1. **只认四个已知 kind** —— 备份文件里写别的 kind 一律丢弃（不让外部文件往库里塞垃圾类别）；
 * 2. **名称为空的一律丢弃**；
 * 3. 扭矩范围**两个数齐全且上限 ≥ 下限**时，名称**由两个数重算**（[torqueRangeName]）——
 *    这样即使有人在备份文件里手改了 name，也不会出现「名字写 0.5~6、判据却是 1~5」这种对不上的情况；
 * 4. 扭矩范围缺数值（v5 及更早的备份里就有这种）→ **保留名称、数值留空**，
 *    管理页会标「⚠ 缺上下限」，操作员补填后即可用于判定。**不编造数值**。
 */
fun torqueDictsFromJson(arr: JSONArray, now: Long): List<TorqueDict> {
    val out = ArrayList<TorqueDict>(arr.length())
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val kind = o.optString("kind", "")
        if (kind !in TORQUE_DICT_KINDS) continue
        val rawName = o.optString("name", "").trim()
        if (kind == TQ_RANGE) {
            val min = if (o.has("minValue") && !o.isNull("minValue")) o.optDouble("minValue") else null
            val max = if (o.has("maxValue") && !o.isNull("maxValue")) o.optDouble("maxValue") else null
            val usable = min != null && max != null && !min.isNaN() && !max.isNaN() && max >= min
            if (usable) {
                out.add(
                    TorqueDict(
                        kind = kind, name = torqueRangeName(min!!, max!!),
                        minValue = min, maxValue = max, createdAt = now, updatedAt = now
                    )
                )
            } else if (rawName.isNotEmpty()) {
                out.add(TorqueDict(kind = kind, name = rawName, createdAt = now, updatedAt = now))
            }
            continue
        }
        if (rawName.isEmpty()) continue
        out.add(TorqueDict(kind = kind, name = rawName, createdAt = now, updatedAt = now))
    }
    return out
}
