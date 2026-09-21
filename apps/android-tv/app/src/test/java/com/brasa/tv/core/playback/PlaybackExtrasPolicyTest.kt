package com.brasa.tv.core.playback

import com.brasa.tv.core.model.PlaybackMarker
import org.junit.Assert.*
import org.junit.Test

class PlaybackExtrasPolicyTest {
    @Test fun stabilityCapsResolutionWithoutExceedingDeviceLimits() {
        val original = com.brasa.tv.core.model.PlaybackCapabilities(maxWidth = 3840, maxHeight = 2160)
        assertEquals(720, PlaybackExtrasPolicy.stabilityCapabilities(original, true).maxHeight)
        assertEquals(1280, PlaybackExtrasPolicy.stabilityCapabilities(original, true).maxWidth)
        assertEquals(original, PlaybackExtrasPolicy.stabilityCapabilities(original, false))
        assertEquals(480, PlaybackExtrasPolicy.stabilityCapabilities(original.copy(maxHeight = 480), true).maxHeight)
    }
    @Test fun nextEpisodeOnlyUsesIdleNetworkWithEntireRemainderBuffered() {
        fun allowed(buffer: Long = 60_000, remaining: Long = 50_000, stable: Long = 30_000, loading: Boolean = false, foreground: Boolean = true, seeking: Boolean = false) =
            PlaybackExtrasPolicy.canPrepareNext(foreground, true, loading, false, buffer, remaining, stable, seeking)
        assertTrue(allowed())
        assertFalse(allowed(buffer = 35_000)); assertFalse(allowed(remaining = 150_000))
        assertFalse(allowed(stable = 29_999)); assertFalse(allowed(loading = true))
        assertFalse(allowed(foreground = false)); assertFalse(allowed(seeking = true))
    }
    @Test fun previewUsesTenSecondBuckets() {
        assertEquals(20_000L, PlaybackExtrasPolicy.previewBucket(29_999))
        assertEquals(0L, PlaybackExtrasPolicy.previewBucket(-1))
    }
    @Test fun markersRespectAbsoluteTimelineAndKeepPostCreditsScene() {
        val marker = PlaybackMarker("credits", 1_100_000, 1_180_000, "embedded-chapter")
        val manual=marker.copy(source="manual")
        assertEquals(manual, PlaybackExtrasPolicy.activeMarker(listOf(manual), 1_150_000, 1_200_000))
        assertEquals(marker, PlaybackExtrasPolicy.activeMarker(listOf(marker), 1_150_000, 1_200_000))
        assertNull(PlaybackExtrasPolicy.activeMarker(listOf(marker), 1_190_000, 1_200_000))
        assertNull(PlaybackExtrasPolicy.activeMarker(listOf(marker.copy(source = "guess")), 1_150_000, 1_200_000))
        assertNull(PlaybackExtrasPolicy.activeMarker(listOf(marker), 1_150_000, 1_170_000))
        assertNull(PlaybackExtrasPolicy.activeMarker(emptyList(), 100_000, 1_200_000))
    }
}
