package io.github.cuimiles.studydesk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LanReleaseTest {
    private val valid = """{"packageName":"io.github.cuimiles.studydesk","versionCode":7,"versionName":"1.2.0-lan","size":1234,"sha256":"${"a".repeat(64)}","url":"/api/android/apk/7"}"""
    private val server = "http://192.168.1.2:8765/"

    @Test fun validManifest() {
        val release = LanRelease.parse(valid, server, "io.github.cuimiles.studydesk")
        assertEquals(7, release.versionCode)
        assertEquals(server, release.server)
    }

    @Test fun rejectsOffServerOrWrongPackage() {
        for (bad in listOf(
            valid.replace("/api/android/apk/7", "http://evil.example/app.apk"),
            valid.replace("/api/android/apk/7", "/api/android/apk/6"),
            valid.replace("io.github.cuimiles.studydesk", "io.fake.app"),
            valid.replace("${"a".repeat(64)}", "invalid"),
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                LanRelease.parse(bad, server, "io.github.cuimiles.studydesk")
            }
        }
    }
}
