package com.quick.app.export

import com.quick.app.data.db.MeasurementRecord
import com.quick.app.net.channelText
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 最小 XLSX 导出 —— 零第三方依赖（xlsx 本质 = zip + XML）。
 * 方案评估见 docs/可行性分析与通讯规格-V1.0.md §11；
 * 同族项目 Quick192AF-App 已用本方案现场验证可被 Excel/WPS 正常打开。
 */
object XlsxExporter {

    private const val COLUMNS = 17

    /** @param headerDates true=时间列输出为日期单元格，false=按文本输出（保持导出所见即所得） */
    fun export(records: List<MeasurementRecord>, file: File, headerDates: Boolean = false) {
        file.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("[Content_Types].xml", CT_XML)
            entry("_rels/.rels", RELS_XML)
            entry("xl/workbook.xml", WB_XML)
            entry("xl/_rels/workbook.xml.rels", WB_RELS_XML)
            entry("xl/styles.xml", STYLES_XML)
            entry("xl/worksheets/sheet1.xml", sheetXml(records, headerDates))
        }
    }

    fun formatTime(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(ms))

    private fun sheetXml(records: List<MeasurementRecord>, headerDates: Boolean): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        sb.append("<sheetData>")
        // 表头
        sb.append("<row r=\"1\">")
        for ((i, h) in HEADERS.withIndex()) {
            sb.append(cellXml(1, i + 1, h, style = 1))
        }
        sb.append("</row>")
        // 数据
        records.forEachIndexed { idx, r ->
            val row = idx + 2
            val cells = listOf(
                r.id.toString(),
                formatTime(r.timestampMs),
                r.lineName,
                r.modelName,
                r.stationName,
                r.deviceInfo ?: "",                      // 0x0A~0x19 设备信息(扫码)
                r.deviceIp,
                channelText(r.channel),                  // 0x04 测量通道
                r.targetTemp?.toString() ?: "",          // 0x1A 设定温度 ℃
                r.tempLow?.toString() ?: "",             // 0x1B 温度判断下限 ℃
                r.tempHigh?.toString() ?: "",            // 0x1C 温度判断上限 ℃
                num(r.voltageLimitMv),                   // 0x1D 电压判断上限 mV
                num(r.resistanceLimitOhm),               // 0x1E 电阻判断上限 Ω
                num(r.measuredTemp),                     // 0x20 测试保存温度 ℃
                num(r.measuredVoltageMv),                // 0x21 测试保存电压 mV
                num(r.measuredResistanceOhm),            // 0x22 测试保存电阻 Ω
                r.result                                 // 0x23 判定
            )
            sb.append("<row r=\"$row\">")
            for ((i, v) in cells.withIndex()) sb.append(cellXml(row, i + 1, v))
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    /** 所有内容用 inline string，避免 sharedStrings 部件（合法且更简单） */
    private fun cellXml(row: Int, col: Int, v: String, style: Int? = null): String {
        val ref = "${colName(col)}$row"
        return "<c r=\"$ref\"${style?.let { " s=\"$it\"" } ?: ""} t=\"inlineStr\"><is><t xml:space=\"preserve\">" +
            xmlEscape(v) +
            "</t></is></c>"
    }

    private fun colName(i: Int): String {
        var n = i
        val sb = StringBuilder()
        while (n > 0) {
            n -= 1
            sb.insert(0, ('A'.code + n % 26).toChar())
            n /= 26
        }
        return sb.toString()
    }

    private fun xmlEscape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("\n", "&#10;")

    /** 小数字段统一 1 位（温度 ×0.1 ℃、电压 ×0.1 mV、电阻 ×0.1 Ω），空值留空 */
    private fun num(v: Double?): String =
        if (v == null) "" else String.format(Locale.US, "%.1f", v)

    private val HEADERS = listOf(
        "ID", "时间", "线别", "机种", "站别", "设备信息", "设备IP", "测量通道",
        "设定温度(℃)", "温度下限(℃)", "温度上限(℃)", "电压上限(mV)", "电阻上限(Ω)",
        "测量温度(℃)", "保存电压(mV)", "保存电阻(Ω)", "结果"
    )

    // ---------- 固定部件 ----------
    private val CT_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

    private val RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private val WB_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="测量记录" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""

    private val WB_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    /** 0=默认, 1=表头加粗 */
    private val STYLES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="2"><font><sz val="11"/><name val="宋体"/></font><font><b/><sz val="11"/><name val="宋体"/></font></fonts>
<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs>
<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>"""
}
