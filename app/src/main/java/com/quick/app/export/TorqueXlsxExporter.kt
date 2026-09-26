package com.quick.app.export

import com.quick.app.data.db.TORQUE_NG
import com.quick.app.data.db.TORQUE_OK
import com.quick.app.data.db.TorqueRecord
import com.quick.app.data.db.torqueValueText
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 扭力计记录的 XLSX 导出 —— 与烙铁的 [XlsxExporter] **各自独立**（用户要求：导出也要相互独立）。
 *
 * 为什么另写一份而不是把 [XlsxExporter] 抽成通用工具：
 * 烙铁那张表的列已经跟现场对过、被 Excel/WPS 验过，动它一分都是风险；
 * 扭力计这张表的列**还没定型**（数据格式、单位都没确认），后面必然还要改。
 * 两者共用一个「通用导出器」的结果只会是：改扭力计的列时把烙铁的导出改坏。
 * zip + XML 这套样板重复一遍的代价，远小于现场导出坏了查不出来的代价。
 *
 * 列已于 2026-09-22 定稿（用户要求 4）：
 * **时间 / 线别 / 机种 / 扭矩范围 / 设备信息 / 三次测量结果 / 平均扭矩 / 判断结果** 是必有的，
 * 另外带上**判定当时的上下限**（QC 能自证凭什么判 OK）与**原始报文**列（可读的溯源依据）。
 *
 * 2026-09-26 用户要求：**导出里不再要「原始字节(hex)」这一列**（现场看的是数据，不是字节流），
 * 于是删掉了这一列。**注意库里的 `TorqueRecord.rawHex` 照旧保留**（历史详情页仍可查看），
 * 只是不往这张表里写 —— 删的是「导出的一列」，不是「留的证据」。
 *
 * 数值列固定 **2 位小数**（[torqueValueText]，与界面/入库同一口径）——
 * 设备实测就是 2 位，导出多写位数是假精度；单位写在列名里，不混进单元格。
 */
object TorqueXlsxExporter {

    fun export(records: List<TorqueRecord>, file: File) {
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
            entry("xl/worksheets/sheet1.xml", sheetXml(records))
        }
    }

    private fun sheetXml(records: List<TorqueRecord>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        sb.append("<sheetData>")
        sb.append("<row r=\"1\">")
        for ((i, h) in HEADERS.withIndex()) sb.append(cellXml(1, i + 1, h, style = 1))
        sb.append("</row>")
        records.forEachIndexed { idx, r ->
            val row = idx + 2
            val cells = listOf(
                r.id.toString(),
                XlsxExporter.formatTime(r.timestampMs),   // 时间 = 第三笔的时刻（用户拍板）
                r.lineName,
                r.modelName,
                r.rangeName,
                r.deviceName,
                torqueValueText(r.rangeMin),              // 判定下限快照
                torqueValueText(r.rangeMax),              // 判定上限快照
                r.v1Text,
                r.v2Text,
                r.v3Text,
                r.averageText,
                judgeText(r),                             // OK / NG / 未判定
                r.sampleCount.toString(),
                r.rawText
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

    /** 判定列：`OK` / `NG`；旧数据（v8 迁移过来）没有判据，明写「未判定」而不是留空 */
    private fun judgeText(r: TorqueRecord): String = when (r.judge) {
        TORQUE_OK, TORQUE_NG -> r.judge
        else -> "未判定"
    }

    /** 表头顺序 = 单元格顺序，两者必须一一对应（列名里直接标单位，单元格不混单位） */
    private val HEADERS = listOf(
        "ID", "时间", "线别", "机种", "扭矩范围", "设备信息",
        "扭矩下限(kgf*cm)", "扭矩上限(kgf*cm)",
        "第1次(kgf*cm)", "第2次(kgf*cm)", "第3次(kgf*cm)", "平均扭矩(kgf*cm)",
        "判断结果", "参与平均笔数", "原始报文"
    )

    // ---------- 固定部件（与 XlsxExporter 同源；不改那边，各自一份） ----------

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
<sheets><sheet name="扭力记录" sheetId="1" r:id="rId1"/></sheets>
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
