package com.brasa.tv.core.playback

import org.junit.Assert.*
import org.junit.Test

class SeekPolicyTest {
    @Test fun progressiveRangeCanJumpBeyondDownloadedBuffer() {
        assertTrue(SeekPolicy.canSeekLocally("direct", true, true, 3_600_000, 0, 60_000, 90_000))
    }
    @Test fun nonSeekableSourceKeepsServerFallback() {
        assertFalse(SeekPolicy.canSeekLocally("direct", true, false, 70_000, 0, 60_000, 90_000))
    }
    @Test fun hlsOutsidePreparedWindowStillUsesServer() {
        assertFalse(SeekPolicy.canSeekLocally("hls", true, true, 3_600_000, 0, 60_000, 90_000))
        assertFalse(SeekPolicy.canSeekLocally("hls", true, true, 20_000, 0, 60_000, 90_000))
    }
    @Test fun hlsBufferedSeekPreservesAbsoluteOffset() {
        assertTrue(SeekPolicy.canSeekLocally("hls", true, true, 1_750_000, 1_700_000, 1_760_000, 1_800_000))
        assertFalse(SeekPolicy.canSeekLocally("hls", true, true, 1_690_000, 1_700_000, 1_760_000, 1_800_000))
    }
    @Test fun sourceWithoutRangeOnlySeeksInsideBufferedWindow() {
        assertFalse(SeekPolicy.canSeekLocally("direct", false, true, 150_000, 0, 60_000, 90_000))
        assertTrue(SeekPolicy.canSeekLocally("direct", false, true, 70_000, 0, 60_000, 90_000))
    }
}
