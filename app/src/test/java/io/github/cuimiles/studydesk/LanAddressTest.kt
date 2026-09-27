package io.github.cuimiles.studydesk

import org.junit.Assert.*
import org.junit.Test

class LanAddressTest {
    @Test fun acceptsLocalServersAndEmulator() {
        assertEquals("http://10.184.17.163:8765/", LanAddress.normalize("10.184.17.163:8765"))
        assertEquals("http://10.184.17.163:8765/", LanAddress.normalize("10.184.17.163"))
        assertEquals("http://10.0.2.2:8765/", LanAddress.normalize("http://10.0.2.2:8765/"))
        assertEquals("https://192.168.0.12:8765/", LanAddress.normalize("https://192.168.0.12"))
    }
    @Test fun rejectsUntrustedDestinations() {
        listOf("https://example.com", "file:///etc/passwd", "http://10.0.0.1@evil.com", "http://192.168.1.1:0", "http://192.168.1.1/path", "http://256.1.1.1", "javascript:alert(1)").forEach {
            assertTrue(it, runCatching { LanAddress.normalize(it) }.isFailure)
        }
    }
    @Test fun bridgeStaysOnExactServerOrigin() {
        assertTrue(LanAddress.sameOrigin("http://10.0.2.2:8765", "http://10.0.2.2:8765/#/write/resnet-01"))
        assertFalse(LanAddress.sameOrigin("http://10.0.2.2:8765", "http://10.0.2.2:8766/"))
        assertFalse(LanAddress.sameOrigin("http://10.0.2.2:8765", "http://10.0.2.2.evil.com:8765/"))
        assertFalse(LanAddress.sameOrigin("http://10.0.2.2:8765", "https://example.com"))
    }
}
