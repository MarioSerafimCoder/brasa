package com.brasa.tv.core.playback

import com.brasa.tv.core.model.PlaybackInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PlaybackTimelineTest {
    private val resumed = PlaybackInfo(
        mediaId = "6",
        mediaKey = "movie:6",
        duration = 7_762_879,
        playbackOffset = 1_736_000,
        resumePosition = 578,
        playbackMode = "hls",
    )

    @Test fun mapsLocalHlsTimeToAbsoluteMovieTime() {
        assertEquals(1_736_578, PlaybackTimeline.absolutePosition(resumed, 578))
        assertEquals(7_762_879, PlaybackTimeline.absoluteDuration(resumed, 6_026_879))
    }

    @Test fun savesProgressAgainstOriginalDuration() {
        val progress = PlaybackTimeline.progress(resumed, 12_578, 6_026_879, false)
        assertNotNull(progress)
        assertEquals(1_748.578, progress!!.currentTime, 0.001)
        assertEquals(7_762.879, progress.duration, 0.001)
        assertEquals(22.525, progress.percentage, 0.001)
    }
}
