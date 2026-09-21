@file:androidx.media3.common.util.UnstableApi
package com.brasa.tv.core.playback

import android.app.Application
import androidx.media3.common.*
import androidx.media3.common.util.Consumer
import androidx.media3.extractor.text.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class OffsetSubtitleParserTest {
    private val text = "WEBVTT\n\n00:00:10.000 --> 00:00:12.000\nPrimeira\n\n00:00:20.000 --> 00:00:22.000\nSegunda\n".toByteArray()
    private fun parser(delay: Long, offset: Long = 0) = OffsetSubtitleParserFactory(delay, offset).create(
        Format.Builder().setSampleMimeType(MimeTypes.TEXT_VTT).setId(OffsetSubtitleParserFactory.EXTERNAL_ID + "0").build())
    private fun parse(parser: SubtitleParser, afterUs: Long? = null): List<CuesWithTiming> {
        val cues = mutableListOf<CuesWithTiming>()
        parser.parse(text, afterUs?.let { SubtitleParser.OutputOptions.onlyCuesAfter(it) } ?: SubtitleParser.OutputOptions.allCues()) { cues.add(it) }
        return cues.filter { it.cues.any { cue -> !cue.text.isNullOrEmpty() } }
    }
    @Test fun directPlaybackCanAdvanceOrDelayWithoutChangingDuration() {
        assertEquals(10500000L, parse(parser(500)).first().startTimeUs)
        assertEquals(9500000L, parse(parser(-500)).first().startTimeUs)
        assertEquals(2000000L, parse(parser(500)).first().durationUs)
    }
    @Test fun externalHlsUsesOriginalTimelineOnceIncludingResumeAndSeek() {
        val parser = parser(750, 8000)
        assertEquals(2750000L, parse(parser).first().startTimeUs)
        parser.reset()
        val later = parse(parser, 12000000)
        assertEquals("Segunda", later.last().cues.first().text.toString())
        assertEquals(12750000L, later.last().startTimeUs)
        parser.reset()
        assertEquals(2750000L, parse(parser).first().startTimeUs)
    }
    @Test fun embeddedTracksDoNotSubtractHlsOffset() {
        val factory = OffsetSubtitleParserFactory(500, 8000)
        val parser = factory.create(Format.Builder().setSampleMimeType(MimeTypes.TEXT_VTT).setId("embedded").build())
        assertEquals(10500000L, parse(parser).first().startTimeUs)
    }
    @Test fun sampleRelativeSubtitlesKeepTheirContainerTimestamp() {
        val source = object : SubtitleParser {
            override fun getCueReplacementBehavior() = Format.CUE_REPLACEMENT_BEHAVIOR_REPLACE
            override fun parse(data: ByteArray, offset: Int, length: Int, options: SubtitleParser.OutputOptions, output: Consumer<CuesWithTiming>) {
                output.accept(CuesWithTiming(emptyList(), C.TIME_UNSET, C.TIME_UNSET))
            }
        }
        var cue: CuesWithTiming? = null
        OffsetSubtitleParser(source, -250000).parse(byteArrayOf(), SubtitleParser.OutputOptions.allCues()) { cue = it }
        assertEquals(-250000L, cue!!.startTimeUs)
        assertEquals(C.TIME_UNSET, cue!!.durationUs)
    }
    @Test fun videoOutputDoesNotUseUiResolutionToCap4k() {
        assertEquals(3840 to 2160, videoOutputSize(1920,1080,3840,2160))
        assertEquals(1920 to 1080, videoOutputSize(1920,1080,0,0))
    }
}
