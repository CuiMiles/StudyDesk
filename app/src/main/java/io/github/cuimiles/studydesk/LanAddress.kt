package io.github.cuimiles.studydesk

import java.net.URI

/** No DNS lookup: only loopback or explicit private IPv4 addresses are accepted. */
object LanAddress {
    fun normalize(raw: String): String {
        val value = raw.trim().let { if (it.contains("://")) it else "http://$it" }
        val uri = runCatching { URI(value) }.getOrNull()
            ?: throw IllegalArgumentException("地址格式错误，例如 http://192.168.1.100:8765")
        require(uri.scheme in listOf("http", "https") && uri.userInfo == null && uri.query == null && uri.fragment == null) {
            "请输入 http 或 https 局域网地址，不要附带账号或参数"
        }
        val host = uri.host?.lowercase().orEmpty()
        val parts = host.split('.').map { it.toIntOrNull() }
        val validIpv4 = parts.size == 4 && parts.all { it != null && it in 0..255 } &&
            (parts[0] == 10 || parts[0] == 127 || parts[0] == 192 && parts[1] == 168 || parts[0] == 172 && parts[1] in 16..31)
        require(host == "localhost" || validIpv4) { "请输入局域网 IPv4 地址，例如 192.168.1.100" }
        require(uri.port == -1 || uri.port in 1..65535) { "端口必须在 1–65535 之间" }
        require(uri.path.isNullOrEmpty() || uri.path == "/") { "只填写服务器地址和端口，不要附带页面路径" }
        return "${uri.scheme}://$host:${if (uri.port != -1) uri.port else 8765}/"
    }
    fun sameOrigin(server: String, destination: String): Boolean = runCatching {
        val a = URI(normalize(server)); val b = URI(destination)
        fun port(uri: URI) = if (uri.port >= 0) uri.port else if (uri.scheme == "https") 443 else 80
        b.userInfo == null && a.scheme == b.scheme && a.host.equals(b.host, ignoreCase = true) && port(a) == port(b)
    }.getOrDefault(false)
}
