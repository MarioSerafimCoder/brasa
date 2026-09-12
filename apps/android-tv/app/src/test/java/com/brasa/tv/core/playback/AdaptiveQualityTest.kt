@file:androidx.media3.common.util.UnstableApi
package com.brasa.tv.core.playback

import android.app.Application
import android.os.Handler
import androidx.media3.common.*
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.chunk.MediaChunkIterator
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.BandwidthMeter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AdaptiveQualityTest {
    @Test fun dropsDuringSlowNetworkAndRecoversOnlyWithStableBuffer() {
        val meter = object : BandwidthMeter {
            var estimate = 12_000_000L
            override fun getBitrateEstimate() = estimate
            override fun getTransferListener(): TransferListener? = null
            override fun addEventListener(handler: Handler, eventListener: BandwidthMeter.EventListener) {}
            override fun removeEventListener(eventListener: BandwidthMeter.EventListener) {}
        }
        val group = TrackGroup(*listOf(480 to 1_200_000, 720 to 3_200_000, 1080 to 6_500_000).map { (height, bitrate) ->
            Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).setHeight(height).setWidth(height * 16 / 9).setAverageBitrate(bitrate).build()
        }.toTypedArray())
        val selection = adaptiveTrackSelectionFactory().createTrackSelections(
            arrayOf(ExoTrackSelection.Definition(group, 0, 1, 2)), meter, MediaSource.MediaPeriodId(Any()), Timeline.EMPTY,
        )[0]!!
        fun update(bufferMs: Long) = selection.updateSelectedTrack(0, bufferMs * 1000, C.TIME_UNSET, emptyList(), Array(3) { MediaChunkIterator.EMPTY })
        selection.enable()
        update(20_000); assertEquals(1080, selection.selectedFormat.height)
        meter.estimate = 2_000_000
        update(4_000); assertEquals(480, selection.selectedFormat.height)
        meter.estimate = 12_000_000
        update(4_000); assertEquals(480, selection.selectedFormat.height)
        update(20_000); assertEquals(1080, selection.selectedFormat.height)
        selection.disable()
    }
}
