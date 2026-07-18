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
import com.brasa.tv.core.model.VideoCodecCapability

class PlaybackCapabilityDetector(private val context: Context) {
    private val detected by lazy(::detect)

    fun snapshot(): ClientCapabilities = detected

    private fun detect(): ClientCapabilities {
        val video = linkedSetOf<String>()
        val audio = linkedSetOf<String>()
        val videoCapabilities = mutableListOf<VideoCodecCapability>()
        val metrics = context.resources.displayMetrics
        var maxWidth = metrics.widthPixels
        var maxHeight = metrics.heightPixels
        runCatching { MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.toList() }.getOrDefault(emptyList())
            .filterNot(MediaCodecInfo::isEncoder)
            .forEach { codec ->
                codec.supportedTypes.forEach { rawType ->
                    val type = rawType.lowercase()
                    val codecCapabilities = runCatching { codec.getCapabilitiesForType(rawType) }.getOrNull()
                    val videoToken = videoToken(type)
                    if (videoToken != null && isHardwareDecoder(codec)) codecCapabilities?.videoCapabilities?.let { supported ->
                        val width = supported.supportedWidths.upper.coerceAtMost(7680)
                        val height = supported.supportedHeights.upper.coerceAtMost(4320)
                        val bitrate = supported.bitrateRange.upper.toLong().coerceAtMost(500_000_000L)
                        val profiles = codecCapabilities.profileLevels.mapNotNull { profileName(videoToken, it.profile) }.distinct()
                        video += videoToken
                        if ("main10" in profiles) video += "hevc-main10"
                        maxWidth = maxOf(maxWidth, width)
                        maxHeight = maxOf(maxHeight, height)
                        videoCapabilities += VideoCodecCapability(videoToken, width, height, bitrate, hardware = true, profiles = profiles)
                    }
                    when (type) {
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
        val mergedVideoCapabilities = videoCapabilities
            .groupBy(VideoCodecCapability::codec)
            .map { (codec, values) -> VideoCodecCapability(codec, values.maxOf { it.maxWidth }, values.maxOf { it.maxHeight }, values.maxOf { it.maxBitrate }, true, values.flatMap { it.profiles }.distinct().sorted()) }
            .sortedBy(VideoCodecCapability::codec)
        val hdrTypes = detectHdrTypes()
        return ClientCapabilities(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            screen = ScreenCapabilities(metrics.widthPixels, metrics.heightPixels),
            playback = PlaybackCapabilities(
                containers = listOf("mp4", "matroska", "webm", "mpegts", "hls"),
                videoCodecs = video.sorted(),
                audioCodecs = audio.sorted(),
                hdrTypes = hdrTypes.sorted(),
                videoCapabilities = mergedVideoCapabilities,
                maxWidth = maxWidth,
                maxHeight = maxHeight,
            ),
        )
    }

    private fun videoToken(type: String): String? = when (type) {
        "video/avc" -> "h264"
        "video/hevc" -> "hevc"
        "video/dolby-vision" -> "dolby-vision"
        "video/x-vnd.on2.vp8" -> "vp8"
        "video/x-vnd.on2.vp9" -> "vp9"
        "video/av01" -> "av1"
        "video/mpeg2" -> "mpeg2video"
        else -> null
    }

    private fun isHardwareDecoder(codec: MediaCodecInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return codec.isHardwareAccelerated && !codec.isSoftwareOnly
        val name = codec.name.lowercase()
        return !name.startsWith("omx.google.") && !name.startsWith("c2.android.") && !name.contains(".sw.")
    }

    private fun profileName(codec: String, profile: Int): String? = when {
        codec == "hevc" && profile in setOf(MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10, MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10) -> "main10"
        else -> null
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
