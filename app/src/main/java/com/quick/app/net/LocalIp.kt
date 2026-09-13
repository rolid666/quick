package com.quick.app.net

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * 本机（手机/平板）IPv4 地址枚举 —— 配网时用来确定仪器应处的网段。
 *
 * 背景：手机开热点时无法给自己的热点接口手动设 IP（系统固定，如 192.168.43.1），
 * 因此仪器扫码配置里的 ServerIP（仪器自身静态 IP）必须由外部给一个「同网段」地址。
 * 本工具列出本机各接口 IPv4 并给出同网段建议，避免用户盲猜。
 *
 * 只使用 NetworkInterface 本地枚举（无需额外权限；INTERNET 已声明）。
 */
object LocalIp {

    data class Iface(val name: String, val ip: String) {
        /** 同网段标签（现场按 /24 使用）：192.168.43.1 → 192.168.43.x */
        val subnet: String get() = ip.substringBeforeLast('.', ip) + ".x"

        val label: String
            get() = when {
                name.startsWith("ap") || name.contains("softap") -> "热点"
                name.startsWith("wlan") -> "Wi-Fi"
                name.startsWith("eth") -> "有线"
                name.startsWith("rmnet") || name.startsWith("ccmni") -> "移动数据"
                else -> name
            }
    }

    /** 全部 up、非回环、非链路本地的 IPv4；热点/Wi-Fi 接口排在前面 */
    fun list(): List<Iface> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { nif ->
                nif.inetAddresses.toList()
                    .filterIsInstance<Inet4Address>()
                    .filterNot { it.isLoopbackAddress || it.isLinkLocalAddress }
                    .mapNotNull { addr -> addr.hostAddress?.let { Iface(nif.name, it) } }
            }
            .distinctBy { it.ip }
            .sortedWith(compareBy({ rank(it.name) }, { it.name }))
    }.getOrElse { emptyList() }

    private fun rank(n: String): Int = when {
        n.startsWith("ap") || n.contains("softap") -> 0
        n.startsWith("wlan") -> 1
        n.startsWith("eth") -> 2
        else -> 3
    }

    /** 给仪器的建议静态 IP：同网段末位 100（被本机占用则顺延到 101） */
    fun suggestInstrumentIp(iface: Iface): String {
        val prefix = iface.ip.substringBeforeLast('.', "")
        val self = iface.ip.substringAfterLast('.', "")
        val host = if (self == "100") "101" else "100"
        return if (prefix.isEmpty()) iface.ip else "$prefix.$host"
    }
}
