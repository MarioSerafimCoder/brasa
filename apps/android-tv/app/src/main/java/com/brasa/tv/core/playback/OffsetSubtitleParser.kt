@file:androidx.media3.common.util.UnstableApi
package com.brasa.tv.core.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.Consumer
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser

/** Positive delay displays text later. External files use the original movie's
 * clock; subtitles extracted from HLS already use the segment's clock. */
class OffsetSubtitleParserFactory(private val delayMs: Long, private val playbackOffsetMs: Long) : SubtitleParser.Factory {
    private val delegate = DefaultSubtitleParserFactory()
    override fun supportsFormat(format: Format) = delegate.supportsFormat(format)
    override fun getCueReplacementBehavior(format: Format) = delegate.getCueReplacementBehavior(format)
    override fun create(format: Format): SubtitleParser {
        val external = format.id?.startsWith(EXTERNAL_ID) == true
        val shiftUs = (delayMs.coerceIn(-60_000, 60_000) - if (external) playbackOffsetMs else 0) * 1000
        return OffsetSubtitleParser(delegate.create(format), shiftUs)
    }
    companion object { const val EXTERNAL_ID = "brasa-external:" }
}

internal class OffsetSubtitleParser(private val delegate: SubtitleParser, private val shiftUs: Long) : SubtitleParser {
    override fun getCueReplacementBehavior() = delegate.cueReplacementBehavior
    override fun reset() = delegate.reset()
    override fun parse(data: ByteArray, offset: Int, length: Int, outputOptions: SubtitleParser.OutputOptions, output: Consumer<CuesWithTiming>) {
        val options = when {
            outputOptions.startTimeUs == C.TIME_UNSET -> SubtitleParser.OutputOptions.allCues()
            outputOptions.outputAllCues -> SubtitleParser.OutputOptions.cuesAfterThenRemainingCuesBefore(outputOptions.startTimeUs - shiftUs)
            else -> SubtitleParser.OutputOptions.onlyCuesAfter(outputOptions.startTimeUs - shiftUs)
        }
        delegate.parse(data, offset, length, options) { cue ->
            // TIME_UNSET means relative to the container sample, not the movie.
            val start = if (cue.startTimeUs == C.TIME_UNSET) 0 else cue.startTimeUs
            output.accept(CuesWithTiming(cue.cues, start + shiftUs, cue.durationUs))
        }
    }
}
