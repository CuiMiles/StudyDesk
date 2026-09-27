package io.github.cuimiles.studydesk

import org.json.JSONObject

/** Public update manifest. A versioned, same-origin URL prevents redirects to another host. */
data class LanRelease(
    val versionCode: Int,
    val versionName: String,
    val size: Long,
    val sha256: String,
    val url: String,
    val server: String,
) {
    companion object {
        fun parse(json: String, server: String, expectedPackage: String): LanRelease {
            val value = JSONObject(json)
            val code = value.getInt("versionCode")
            val name = value.getString("versionName")
            val size = value.getLong("size")
            val digest = value.getString("sha256")
            val url = value.getString("url")
            require(value.getString("packageName") == expectedPackage)
            require(code > 0 && name.length in 1..64 && size in 1..200_000_000)
            require(digest.matches(Regex("[0-9a-f]{64}")))
            require(url == "/api/android/apk/$code")
            return LanRelease(code, name, size, digest, url, server)
        }
    }
}
