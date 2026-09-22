package com.quick.app.config

import com.quick.app.data.db.TQ_DEVICE
import com.quick.app.data.db.TQ_LINE
import com.quick.app.data.db.TQ_MODEL
import com.quick.app.data.db.TQ_RANGE
import com.quick.app.data.db.TorqueDict
import com.quick.app.data.db.torqueRangeName
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 配置（扭力计四项字典）导出 ⇄ 导入的**往返测试** —— 用户 2026-09-22 要求 7
 * 「最后检查一下配置数据的导出和导入是否正常」。
 *
 * 这里测的是**纯 JSON 层**（不碰 Room）：导出怎么生成、导入怎么净化。
 * 好处是能把现场真会遇到的坏输入（旧备份缺字段、外部文件乱写 kind、
 * 有人在备份里手改名称）全部走一遍 —— 这些恰恰是「导入后数据不对」的常见来源。
 */
class ConfigBackupTorqueTest {

    private val now = 1_700_000_000_000L

    private fun dict(kind: String, name: String, min: Double? = null, max: Double? = null) =
        TorqueDict(kind = kind, name = name, minValue = min, maxValue = max,
            createdAt = now, updatedAt = now)

    // ---------- 导出 ----------

    @Test
    fun `扭矩范围导出时带上限与下限`() {
        val arr = torqueDictsToJson(
            listOf(dict(TQ_RANGE, "0.5~6 kgf*cm", min = 0.5, max = 6.0))
        )
        assertEquals(1, arr.length())
        val o = arr.getJSONObject(0)
        assertEquals(TQ_RANGE, o.getString("kind"))
        assertEquals(0.5, o.getDouble("minValue"), 1e-9)
        assertEquals(6.0, o.getDouble("maxValue"), 1e-9)
    }

    @Test
    fun `其它三类不带数值字段`() {
        val arr = torqueDictsToJson(listOf(dict(TQ_LINE, "A线"), dict(TQ_DEVICE, "QK-HT-001")))
        assertEquals(2, arr.length())
        assertTrue(!arr.getJSONObject(0).has("minValue"))
        assertTrue(!arr.getJSONObject(1).has("maxValue"))
    }

    // ---------- 往返（这是要求 7 的核心）----------

    @Test
    fun `导出再导入 四项字典一条不少 数值也一样`() {
        val src = listOf(
            dict(TQ_LINE, "A线"),
            dict(TQ_MODEL, "M1"),
            dict(TQ_RANGE, torqueRangeName(0.5, 6.0), min = 0.5, max = 6.0),
            dict(TQ_RANGE, torqueRangeName(2.0, 8.0), min = 2.0, max = 8.0),
            dict(TQ_DEVICE, "QK-HT-001")
        )
        val back = torqueDictsFromJson(torqueDictsToJson(src), now)

        assertEquals(5, back.size)
        assertEquals(src.map { it.kind }, back.map { it.kind })
        assertEquals(src.map { it.name }, back.map { it.name })
        val r = back.first { it.kind == TQ_RANGE && it.name == torqueRangeName(0.5, 6.0) }
        assertEquals(0.5, r.minValue!!, 1e-9)
        assertEquals(6.0, r.maxValue!!, 1e-9)
        // 非数值类别不许被塞进数值
        assertNull(back.first { it.kind == TQ_LINE }.minValue)
    }

    @Test
    fun `导出的名称与数值一定自洽`() {
        // 万一有人在备份文件里把 name 改成别的，导入后也必须以两个数重算 —— 判据不能与名字对不上
        val hand = JSONArray().put(
            JSONObject().put("kind", TQ_RANGE).put("name", "随便写的名字")
                .put("minValue", 1.0).put("maxValue", 5.0)
        )
        val back = torqueDictsFromJson(hand, now)
        assertEquals(1, back.size)
        assertEquals(torqueRangeName(1.0, 5.0), back[0].name)
        assertEquals(1.0, back[0].minValue!!, 1e-9)
        assertEquals(5.0, back[0].maxValue!!, 1e-9)
    }

    // ---------- 旧备份 / 坏输入 ----------

    @Test
    fun `v5 旧备份没有上下限 保留名称但不编造数值`() {
        // v5（2026-09-21）导出的就是这种：只有 kind + name
        val old = JSONArray().put(JSONObject().put("kind", TQ_RANGE).put("name", "1~5 kgf*cm"))
        val back = torqueDictsFromJson(old, now)
        assertEquals(1, back.size)
        assertEquals("1~5 kgf*cm", back[0].name)
        assertNull(back[0].minValue)
        assertNull(back[0].maxValue)   // 管理页会标「⚠ 缺上下限」，等操作员补填
    }

    @Test
    fun `数值不可用的范围只保留名称 绝不编造数值`() {
        val bad = JSONArray()
            .put(JSONObject().put("kind", TQ_RANGE).put("name", "半个范围").put("minValue", 1.0))
            .put(JSONObject().put("kind", TQ_RANGE).put("name", "上限小于下限")
                .put("minValue", 5.0).put("maxValue", 1.0))
        val back = torqueDictsFromJson(bad, now)
        // 第一个：只有下限 → 保留名称（等于旧备份的处理）
        // 第二个：上限 < 下限 → 同样只保留名称，宁可不判定也不给一条永远 NG 的范围
        assertEquals(2, back.size)
        assertTrue(back.all { it.minValue == null && it.maxValue == null })
        assertEquals("半个范围", back[0].name)
        assertEquals("上限小于下限", back[1].name)
    }

    @Test
    fun `未知 kind 与空名称一律丢弃`() {
        val bad = JSONArray()
            .put(JSONObject().put("kind", "hacked").put("name", "垃圾类别"))
            .put(JSONObject().put("kind", TQ_LINE).put("name", "   "))
        assertTrue(torqueDictsFromJson(bad, now).isEmpty())
    }

    @Test
    fun `备份文件里的数字是字符串时按数字读`() {
        // 手改备份文件时常见：数字被加上了引号 —— org.json 的 optDouble 能读出来，不该整条丢掉
        val hand = JSONArray().put(
            JSONObject().put("kind", TQ_RANGE).put("name", "x")
                .put("minValue", "1.5").put("maxValue", "4.5")
        )
        val back = torqueDictsFromJson(hand, now)
        assertEquals(1, back.size)
        assertEquals(1.5, back[0].minValue!!, 1e-9)
        assertEquals(4.5, back[0].maxValue!!, 1e-9)
    }

    @Test
    fun `空数组不炸`() {
        assertTrue(torqueDictsFromJson(JSONArray(), now).isEmpty())
        assertEquals(0, torqueDictsToJson(emptyList()).length())
    }

    @Test
    fun `格式标识不能被改动`() {
        // 改了它，现场手里的所有备份文件都会导入失败（用户 2026-09-21 拍板保持不变）
        val candidates = listOf(
            java.io.File("src/main/java/com/quick/app/config/ConfigBackup.kt"),
            java.io.File("app/src/main/java/com/quick/app/config/ConfigBackup.kt")
        )
        val src = candidates.firstOrNull { it.exists() }?.readText()
        assertTrue("找不到 ConfigBackup.kt（单测工作目录变了）", src != null)
        assertTrue(src!!.contains("\"quik-191af-collector\""))
    }
}
