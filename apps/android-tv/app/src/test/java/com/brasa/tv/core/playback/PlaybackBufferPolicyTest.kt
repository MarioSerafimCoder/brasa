package com.brasa.tv.core.playback

import org.junit.Assert.*
import org.junit.Test

class PlaybackBufferPolicyTest {
    @Test fun lowMemoryTvHasBounded4kBufferWithoutRetainedBackBuffer() {
        val profile = PlaybackBufferPolicy.select("direct", 40_000_000, 2160, 128, true)
        assertEquals(32 * 1024 * 1024, profile.targetBytes)
        assertEquals(0, profile.backBufferMs)
    }
    @Test fun witchHatKeepsNetworkCushionAndFastStart() {
        val profile = PlaybackBufferPolicy.select("direct", 8_389_759, 1080, 256, false)
        assertEquals(30_000, profile.minMs)
        assertEquals(3_000, profile.startMs)
        assertTrue(profile.rebufferMs > profile.startMs)
        assertTrue(profile.targetBytes <= 64 * 1024 * 1024)
    }
    @Test fun profilesRespectMedia3BufferConstraintsAcrossTvSizes() {
        for (memory in listOf(64, 128, 256, 512)) for (mode in listOf("direct", "hls")) {
            val p = PlaybackBufferPolicy.select(mode, 8_000_000, 1080, memory, memory <= 128)
            assertTrue(p.minMs >= p.startMs && p.minMs >= p.rebufferMs && p.maxMs >= p.minMs)
            assertTrue(p.targetBytes.toLong() <= memory * 1024L * 1024L / 4)
        }
    }
}
