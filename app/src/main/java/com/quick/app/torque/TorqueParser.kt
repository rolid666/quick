package com.quick.app.torque

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.Locale

/**
 * 扭力计报文解析 —— **格式已于 2026-09-22 实测确认**：`+5.40kgf*cm`。
 *
 * 实测环境（用户提供）：串口助手 19200 8N2 无流控，收到的是：
 * ```
 * 【RX】  + 5.40 kgf*cm
 * 【RX】  + 6.23 kgf*cm
 * ```
 * 也就是：**带符号的十进制数 + 单位 `kgf*cm`**，2 位小数；串口助手那两处空格是软件排版，
 * 所以本解析器**符号/数字/单位之间的空白可有可无**，两种写法都认。
 *
 * ⚠️ **不要按「一行一笔」解析**：设备**不发换行符**（串口助手是靠「分包间隔 50ms」把每笔切开的），
 * 而且两块数据有可能被一次 `bulkTransfer` 一起读回来（`+5.40kgf*cm+6.23kgf*cm`）。
 * 所以这里**扫描整段字节找 `符号+数字+单位`**：
 * - 一次来 3 笔 → 认 3 笔；
 * - 被切成两块（`+5.4` | `0kgf*cm`）→ 缓冲拼起来后再认，不漏读；
 * - 永远不来换行符也不会漏读最后一行（这正是旧的「按行解析」做不到的）。
 *
 * 认不出来的字节**不丢**：留在缓冲里由 [pendingBytes] / [pendingText] 如实报告（诊断页显示）。
 *
 * ⚠️ 但**送进本解析器的字节已经过白名单过滤**（[keepProtocol]，2026-09-25 用户定）：
 * 现场数据里夹着 `\x18 \x0E \x0F` 这类 FTDI 状态字节/填充字节，它们对读数毫无意义却会污染缓冲。
 * 只保留协议允许的字符（数字、字母、`+ - . *`、空格、CR/LF）之后再喂进来，读数照样认得出，
 * 缓冲里也不会再躺着看不懂的字节。**滤掉多少字节在读循环那边如实计数**（见 [TorqueFrame.noiseBytes]），
 * 所以这不是「静默丢数据」—— 真丢了什么，诊断页看得到。
 *
 * ⚠️ 单位**只认不猜**：必须出现单位记号才算一笔读数（`TORQUE METER V1.2 READY` 这类开机横幅
 * 自然就被挡掉了，不需要额外的「合理性门槛」）。这也意味着**万一设备不发单位，
 * 一笔都不会被认出来** —— 那时诊断页会看到「解析 0 笔、缓冲里堆着 +5.40」，
 * 一眼就知道是格式又变了，而不是静默丢数据。
 */
data class TorqueReading(
    val rawLine: String,      // 匹配到的原文（如 `+ 5.40 kgf*cm`）
    val rawHex: String,       // 这一段原文的原始字节 hex（逐字节可回溯）
    val value: Double,        // 数值（带符号）
    val unit: String          // 单位（归一化到 `kgf*cm` 这类规范写法）
)

/**
 * 这一笔读数能不能进暂存组 —— **只记正数**（用户 2026-09-26 要求）。
 *
 * 为什么负数必然出现：现场是「电锁拧紧一次 → 记录一笔 → 要测下一件就得先把电锁**反扭松**」，
 * 而扭力计的「清除」与「输出」是**同一个按键** —— 反扭松 / 按清除时设备照样吐一个数，
 * 那个数是负的（或 0）。它要是进了暂存组，平均值会被拽下去，一组数据直接废掉。
 *
 * 边界取 `> 0`（**0.00 也跳过**）：真正的拧紧动作测不出 0.00 的扭力，而 0 混进平均
 * 同样能把一组数判成 NG；`-0.00` 因为是按数值比较、`-0.0 > 0.0` 为假，也会被跳过。
 *
 * 判定放在这个纯函数里（而不是写在控制器的 if 里）就是为了能被单测钉住 ——
 * 现场要是希望「0 也记」，改这一行、跑一次单测即可。
 */
fun TorqueReading.isRecordableTorque(): Boolean = value > 0.0

/** 串口收到的一块原始字节（诊断页展示用：原始 vs 剥离 FTDI 状态字节后） */
data class TorqueFrame(
    val atMs: Long,
    val rawHex: String,       // 从 endpoint 读到的全部字节（含 FTDI 状态字节）
    val dataHex: String,      // 剥离 FTDI 状态字节后的有效数据（**滤噪声之前**的，诊断只看这个）
    val text: String,         // 白名单过滤后的干净文本 —— 也就是真正送进解析器的那串字符
    /** 这一段里被白名单滤掉多少字节（0 = 这包是干净的；诊断页据此一眼看出链路是否干净） */
    val noiseBytes: Int = 0,
    /** 滤掉的那些字节严格按 UTF-8 能解成含非 ASCII 的文字时的原文（否则 null，见 [TorqueStreamParser.droppedTextOrNull]） */
    val noiseUtf8: String? = null
)

/**
 * 流式解析器：串口是**分块**到达的，所以必须带缓冲累积 —— 直接对每块跑正则会在块边界上漏读数。
 *
 * 只在读循环那一个协程里使用（无并发）。
 */
class TorqueStreamParser(private val maxBufferBytes: Int = 512) {

    private val acc = ByteArrayOutputStream()

    /** 因为超过缓冲上限而被丢掉的字节数（诊断页如实显示，绝不装作没发生） */
    var droppedBytes: Long = 0L
        private set

    /**
     * 喂一块数据，返回这次能凑出的所有读数（可能为空）。
     *
     * 一次调用里能认出几笔就返回几笔 —— 不许「一块只当一笔」。
     *
     * ⚠️ 传进来的应当是**已过白名单**（[keepProtocol]）的字节，见类注释。
     */
    fun feed(chunk: ByteArray): List<TorqueReading> {
        if (chunk.isNotEmpty()) acc.write(chunk, 0, chunk.size)
        val all = acc.toByteArray()
        // ISO-8859-1：1 字节 = 1 字符、没有替换字符，**偏移量与字节一一对应**，
        // 于是匹配出来的下标可以直接拿去切字节（U+FFFD 那种解码会破坏这个对应关系）
        val text = String(all, Charsets.ISO_8859_1)

        val out = ArrayList<TorqueReading>(2)
        var consumedTo = -1
        var from = 0
        while (true) {
            val m = READING.find(text, from) ?: break
            val num = m.groupValues[2].toDoubleOrNull()
            from = m.range.last + 1
            if (num == null) continue
            val value = if (m.groupValues[1] == "-") -num else num
            out += TorqueReading(
                rawLine = m.value.trim(),
                rawHex = hex(all.copyOfRange(m.range.first, m.range.last + 1)),
                value = value,
                unit = normalizeUnit(m.groupValues[3])
            )
            consumedTo = m.range.last + 1
        }

        if (consumedTo > 0) {
            // 已认出来的部分连同紧跟其后的空白/控制字节一起丢掉 ——
            // CR/LF/空格不能卡在缓冲里，否则「缓冲还剩几个字节」这个指示永远归不了零
            var start = consumedTo
            while (start < all.size && isSkippable(all[start])) start++
            val rest = all.copyOfRange(start, all.size)
            acc.reset(); acc.write(rest, 0, rest.size)
        }
        // 一直认不出来时缓冲不能无限涨（二进制噪声 / 格式又变了）
        if (acc.size() > maxBufferBytes) {
            val b = acc.toByteArray()
            val keep = maxBufferBytes / 2
            droppedBytes += (b.size - keep).toLong()
            acc.reset(); acc.write(b, b.size - keep, keep)
        }
        return out
    }

    fun reset() {
        acc.reset()
    }

    /** 当前缓冲里挂了多少字节（诊断页显示「没认出来的尾巴」，避免误以为丢数据） */
    val pendingBytes: Int get() = acc.size()

    /** 缓冲里没认出来的那段（可读化；诊断页显示「到底卡在哪几个字节上」） */
    fun pendingText(): String = if (acc.size() == 0) "" else printable(acc.toByteArray())

    companion object {

        /**
         * 读数正则：`(符号)(数字)(单位)`，中间允许任意空白。
         *
         * 单位**长的写在前面**（`mN` 必须在 `N` 前面，否则 `12.5 mN*m` 会被从 `N` 处切成 `N*m`），
         * 分隔符允许 `*` `.` `-` `·` 四种写法（设备实测是 `kgf*cm`，其余是常见变体）。
         */
        private const val UNIT_EXPR =
            "kgf\\s*[-*.·]\\s*cm" + "|" +
                "mN\\s*[-*.·]\\s*m" + "|" +
                "cN\\s*[-*.·]\\s*m" + "|" +
                "N\\s*[-*.·]\\s*m" + "|" +
                "lbf\\s*[-*.·]\\s*in" + "|" +
                "ozf\\s*[-*.·]\\s*in"

        private val READING = Regex(
            "([+-])?\\s*(\\d+(?:\\.\\d+)?)\\s*($UNIT_EXPR)",
            RegexOption.IGNORE_CASE
        )

        private fun isSkippable(b: Byte): Boolean {
            val v = b.toInt() and 0xFF
            return v == 0x00 || v == 0x09 || v == 0x0A || v == 0x0D || v == 0x20
        }

        /**
         * 单位归一化：`kgf.cm` / `kgf-cm` / `KGF*CM` 统一成设备实测的 `kgf*cm` 写法，
         * 免得同一种单位在库里存出四种字符串（导出与检索都会变得不可靠）。
         */
        fun normalizeUnit(raw: String): String {
            val k = raw.lowercase(Locale.US).replace(Regex("[\\s*.·\\-]"), "")
            return when (k) {
                "kgfcm" -> "kgf*cm"
                "nm" -> "N*m"
                "cnm" -> "cN*m"
                "mnm" -> "mN*m"
                "lbfin" -> "lbf*in"
                "ozfin" -> "ozf*in"
                else -> raw.trim()
            }
        }

        /**
         * **协议字符白名单**（用户 2026-09-25 定）：这个字节是否可能出现在 `+ 5.40 kgf*cm` 里。
         *
         * 放行：`0-9` `A-Z` `a-z` `+` `-` `.` `*` 空格 `\r` `\n`，外加 `·`（0xB7）。
         * 别的（`\x18 \x0E \x0F` 这类 FTDI 状态字节残留、填充字节、`0x00`…）一律当噪声。
         *
         * `·` 是**故意**放行的：单位和写变体里有 `kgf·cm`（见 [UNIT_EXPR]），滤掉它会让一种
         * 本来认得的写法突然失配。顺带一个好处：设备要是发 UTF-8 的 `·`（C2 B7），C2 被滤掉、
         * B7 留下，`kgf·cm` 反而能被认出来。
         */
        fun isProtocolByte(b: Byte): Boolean {
            val v = b.toInt() and 0xFF
            return v in 0x30..0x39 ||   // 0-9
                v in 0x41..0x5A ||      // A-Z
                v in 0x61..0x7A ||      // a-z
                v == 0x2B ||            // +
                v == 0x2D ||            // -
                v == 0x2E ||            // .
                v == 0x2A ||            // *
                v == 0x20 ||            // 空格
                v == 0x0D ||            // \r
                v == 0x0A ||            // \n
                v == 0xB7               // ·（kgf·cm 这种写法）
        }

        /** 只留下协议允许的字符，其余全部丢掉（读数只由这些字符构成，别的都是噪声） */
        fun keepProtocol(bytes: ByteArray): ByteArray {
            val out = ByteArray(bytes.size)
            var n = 0
            for (b in bytes) if (isProtocolByte(b)) out[n++] = b
            return if (n == bytes.size) bytes else out.copyOf(n)
        }

        /** [keepProtocol] 丢掉的那些字节 —— **只给诊断看**（「滤掉的到底是什么」） */
        fun notProtocol(bytes: ByteArray): ByteArray {
            val out = ByteArray(bytes.size)
            var n = 0
            for (b in bytes) if (!isProtocolByte(b)) out[n++] = b
            return out.copyOf(n)
        }

        /**
         * 被白名单滤掉的那部分字节，**严格按 UTF-8** 能解成含非 ASCII 的文字时返回它，否则 null。
         *
         * 这一问必须留着：滤掉的是 FTDI 状态字节残留 / 链路坏字节 → 纯噪声，丢掉正好；
         * 但如果滤掉的是一段**合法 UTF-8 的文字**（比如设备突然改用中文提示），
         * 那就不是「链路噪声」而是「设备换了说法」——诊断页要把这句话显出来，
         * 免得把「设备在说话」当成「线路坏了」去查错方向（见 [utf8OrNull]）。
         */
        fun droppedTextOrNull(raw: ByteArray): String? {
            val noise = notProtocol(raw)
            if (noise.isEmpty()) return null
            val s = utf8OrNull(noise) ?: return null
            return if (hasNonAscii(s)) s else null
        }

        /** 字节 → 可读文本：可打印 ASCII 原样，其余按转义写（诊断日志要能看出 CR/LF） */
        fun printable(bytes: ByteArray): String {
            val sb = StringBuilder(bytes.size)
            for (b in bytes) {
                val v = b.toInt() and 0xFF
                when {
                    v == 0x0D -> sb.append("\\r")
                    v == 0x0A -> sb.append("\\n")
                    v == 0x09 -> sb.append("\\t")
                    v in 0x20..0x7E -> sb.append(v.toChar())
                    else -> sb.append(String.format(Locale.US, "\\x%02X", v))
                }
            }
            return if (sb.length > MAX_TEXT) sb.substring(0, MAX_TEXT) + "…" else sb.toString()
        }

        fun hex(bytes: ByteArray): String {
            val sb = StringBuilder(bytes.size * 3)
            for ((i, b) in bytes.withIndex()) {
                if (i > 0) sb.append(' ')
                sb.append(String.format(Locale.US, "%02X", b.toInt() and 0xFF))
            }
            return if (sb.length > MAX_TEXT * 3) sb.substring(0, MAX_TEXT * 3) + " …" else sb.toString()
        }

        /**
         * 把一段字节**严格按 UTF-8** 试解一遍；解不通（有非法/残缺序列）返回 null。
         *
         * 用途是诊断页那一问：**「这段看不懂的字节，是设备在发文字，还是链路在坏字节？」**
         * - 能严格解出中文/符号 → 设备确实发了文字（手册只说了读数用 ASCII，没说它不发别的），
         *   那就不是链路问题，别再去改驱动；
         * - 解不通、且高位字节散乱 → 才该怀疑采样/线路。
         *
         * 手工解码不够：Java 的 `String(bytes, UTF_8)` 会把非法字节替换成 U+FFFD 而不报错，
         * 那样「解出来有字」就没有证据价值了 —— 所以这里用严格模式。
         */
        fun utf8OrNull(bytes: ByteArray): String? = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            null
        }

        /**
         * 严格解码结果里有没有**真正超出 ASCII 的**字符（有才值得显示；纯 ASCII 用 [printable] 看更清楚）。
         *
         * 界取 `> 0x7F`（ASCII 是 0x00–0x7F）：`0x7F`(DEL)、`0x01` 这些控制字符是**链路噪声**的常见样子，
         * 不能算成「设备在发文字」——那会把排查方向带偏。
         */
        fun hasNonAscii(s: String): Boolean = s.any { it.code > 0x7F }

        /** 一段文本最多显示这么长（超过的都是噪声，也没人看得完） */
        private const val MAX_TEXT = 400
    }
}
