package com.brasa.tv.core.playback

object SeekPolicy {
    /** Keep progressive Range seeking local. HLS windows may cover only part of a film. */
    fun canSeekLocally(
        playbackMode: String,
        supportsRange: Boolean,
        seekable: Boolean,
        targetMs: Long,
        offsetMs: Long,
        currentMs: Long,
        bufferedMs: Long,
        backBufferMs: Long = 30_000,
    ): Boolean {
        if (!seekable || targetMs < offsetMs) return false
        if (playbackMode != "hls" && supportsRange) return true
        // The server publishes EVENT playlists: earlier segments remain on disk,
        // so rewind does not require another transcode merely for leaving RAM.
        if (playbackMode == "hls") return targetMs <= bufferedMs - 2_000
        return targetMs >= maxOf(offsetMs, currentMs - backBufferMs) && targetMs <= bufferedMs - 2_000
    }
}
