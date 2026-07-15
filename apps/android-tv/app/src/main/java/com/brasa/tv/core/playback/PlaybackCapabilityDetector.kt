package com.brasa.tv.core.playback

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.view.Display
import android.view.WindowManager
import com.brasa.tv.core.model.ClientCapabilities
import com.brasa.tv.core.model.PlaybackCapabilities
import com.brasa.tv.core.model.ScreenCapabilities

class PlaybackCapabilityDetector(private val context: Context) {
    private val detected by lazy(::detect)

    fun snapshot(): ClientCapabilities = detected

    private fun detect(): ClientCapabilities {
        val video = linkedSetOf<String>()
        val audio = linkedSetOf<String>()
        val metrics = context.resources.displayMetrics
        var maxWidth = metrics.widthPixels
        var maxHeight = metrics.heightPixels
        runCatching { MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.toList() }.getOrDefault(emptyList())
            .filterNot(MediaCodecInfo::isEncoder)
            .forEach { codec ->
                codec.supportedTypes.forEach { rawType ->
                    val type = rawType.lowercase()
                    val codecCapabilities = runCatching { codec.getCapabilitiesForType(rawType) }.getOrNull()
                    if (type.startsWith("video/")) codecCapabilities?.videoCapabilities?.let { supported ->
                        maxWidth = maxOf(maxWidth, supported.supportedWidths.upper.coerceAtMost(7680))
                        maxHeight = maxOf(maxHeight, supported.supportedHeights.upper.coerceAtMost(4320))
                    }
                    when (type) {
                        "video/avc" -> video += "h264"
                        "video/hevc" -> {
                            video += "hevc"
                            val profiles = codecCapabilities?.profileLevels?.map { it.profile }.orEmpty()
                            if (profiles.any { it == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 || it == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10 }) video += "hevc-main10"
                        }
                        "video/dolby-vision" -> video += "dolby-vision"
                        "video/x-vnd.on2.vp8" -> video += "vp8"
                        "video/x-vnd.on2.vp9" -> video += "vp9"
                        "video/av01" -> video += "av1"
                        "video/mpeg2" -> video += "mpeg2video"
                        "audio/mp4a-latm" -> audio += "aac"
                        "audio/mpeg" -> audio += "mp3"
                        "audio/ac3" -> audio += "ac3"
                        "audio/eac3", "audio/eac3-joc" -> audio += "eac3"
                        "audio/ac4" -> audio += "ac4"
                        "audio/vnd.dts" -> audio += "dts"
                        "audio/vnd.dts.hd" -> audio += "dts-hd"
                        "audio/true-hd" -> audio += "truehd"
                        "audio/opus" -> audio += "opus"
                        "audio/vorbis" -> audio += "vorbis"
                    }
                }
            }
        val hdrTypes = detectHdrTypes().toMutableSet()
        if ("dolby-vision" in video) hdrTypes += "dolby-vision"
        return ClientCapabilities(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            screen = ScreenCapabilities(metrics.widthPixels, metrics.heightPixels),
            playback = PlaybackCapabilities(
                containers = listOf("mp4", "matroska", "webm", "mpegts", "hls"),
                videoCodecs = video.sorted(),
                audioCodecs = audio.sorted(),
                hdrTypes = hdrTypes.sorted(),
                maxWidth = maxWidth,
                maxHeight = maxHeight,
            ),
        )
    }

    @Suppress("DEPRECATION")
    private fun detectHdrTypes(): Set<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return emptySet()
        val display = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        val result = linkedSetOf<String>()
        for (type in display?.hdrCapabilities?.supportedHdrTypes ?: intArrayOf()) when (type) {
            Display.HdrCapabilities.HDR_TYPE_HDR10 -> result += "hdr10"
            Display.HdrCapabilities.HDR_TYPE_HLG -> result += "hlg"
            Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> result += "dolby-vision"
            Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> result += "hdr10-plus"
        }
        return result
    }
}
