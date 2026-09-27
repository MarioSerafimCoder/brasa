package com.brasa.tv.feature.player

import org.junit.Assert.*
import org.junit.Test

class PlayerSeekControllerTest {
    @Test fun acceleratedPreviewStaysInsideMediaAndIsIndependentOfHlsOffset() {
        val seek = PlayerSeekController()
        seek.movePreview(180_000, 600_000, 1, 0)
        assertEquals(190_000, seek.preview.longValue)
        seek.movePreview(180_000, 600_000, 1, 8)
        assertEquals(310_000, seek.preview.longValue)
        seek.movePreview(180_000, 320_000, 1, 8)
        assertEquals(319_000, seek.preview.longValue)
        assertEquals(319_000L, seek.beginRemoteSeek(seek.preview.longValue, 320_000))
        assertNull(seek.beginRemoteSeek(20_000, 320_000))
        assertEquals(319_000, seek.remoteTarget.longValue)
    }

    @Test fun seekCannotGoBeforeZeroOrPastUnknownDuration() {
        val seek = PlayerSeekController()
        seek.movePreview(2_000, 30_000, -1, 8)
        assertEquals(0, seek.preview.longValue)
        assertEquals(0L, seek.beginRemoteSeek(9_000, 0))
    }
}
