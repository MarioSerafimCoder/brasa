@file:androidx.media3.common.util.UnstableApi
package com.brasa.tv.core.playback

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import java.io.IOException
import java.net.SocketTimeoutException
import com.brasa.tv.core.model.PlaybackInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable data class PlaybackEvent(
    val sequence: Int = 0, val kind: String, val elapsedMs: Long = 0, val durationMs: Long = 0,
    val positionMs: Long = 0, val bufferMs: Long = 0, val bandwidthBps: Long = 0,
    val bitrate: Long = 0, val height: Int = 0, val droppedFrames: Int = 0, val errorCode: Int = 0,
    val mode: String = "", val hlsSessionId: String = "", val reason: String = "",
    val server: EncodingObservation? = null,
)
@Serializable data class EncodingObservation(val state: String = "", val encoder: String = "", val encodingSpeed: Double = 0.0, val outputSeconds: Double = 0.0)
@Serializable data class PlaybackBatch(val id: String, val mediaKey: String, val events: List<PlaybackEvent>)
@Serializable data class PlaybackHistoryEntry(
    val id: String, val title: String, val startedAt: Long, val elapsedMs: Long = 0,
    val bufferCount: Int = 0, val bufferDurationMs: Long = 0, val pauseDurationMs: Long = 0,
    val bufferOpen: Boolean = false,
    val errors: Int = 0, val conversions: Int = 0, val ended: Boolean = false,
    val indications: List<String> = emptyList(), val events: List<PlaybackEvent> = emptyList(),
)

/** Two substantial interruptions in two minutes justify preparing an adaptive stream.
 * Startup, seeking and deliberate pauses never enter this policy. */
class AdaptiveFallbackPolicy {
    private val interruptions = ArrayDeque<Long>()
    private var requested = false
    fun onInterruption(nowMs: Long, durationMs: Long, direct: Boolean): Boolean {
        if (!direct || requested || durationMs < 1_500) return false
        while (interruptions.isNotEmpty() && nowMs - interruptions.first() > 120_000) interruptions.removeFirst()
        interruptions.addLast(nowMs)
        if (interruptions.size < 2) return false
        requested = true
        return true
    }
}

/** Bounded, best-effort telemetry. Upload failures must never affect the player. */
class PlaybackDiagnosticsRecorder(
    private val mediaKey: String,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val send: suspend (PlaybackBatch) -> Unit,
) {
    private val id = UUID.randomUUID().toString()
    private val startedAt = clock()
    private val queue = ArrayDeque<PlaybackEvent>()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var sequence = 0
    @Volatile private var finished = false
    var beforeFinish: (() -> Unit)? = null
    private var lastSource = ""
    var mode = ""; private set
    var hlsSessionId = ""; private set
    var transitioning = false; private set
    val fallback = AdaptiveFallbackPolicy()
    init {
        record(PlaybackEvent(kind = "start"))
        scope.launch {
            var failures = 0
            while (isActive) {
                withTimeoutOrNull(if (finished) 100L else 5_000L) { wake.receive() }
                val batch = synchronized(queue) { queue.take(40) }
                if (batch.isEmpty()) { if (finished) break else continue }
                try {
                    withTimeout(8_000) { send(PlaybackBatch(id, mediaKey, batch)) }
                    synchronized(queue) { while (queue.isNotEmpty() && queue.first().sequence <= batch.last().sequence) queue.removeFirst() }
                    failures = 0
                } catch (error: CancellationException) { if (!isActive) throw error; failures++ }
                catch (_: Exception) { failures++ }
                if (finished && failures >= 3) break
                if (failures > 0) delay(5_000)
            }
            scope.cancel()
        }
    }
    fun source(info: PlaybackInfo) {
        mode = info.playbackMode
        hlsSessionId = info.hlsSessionId.ifBlank { Regex("/hls/([a-f0-9]{24})/").find(info.playbackUrl)?.groupValues?.get(1).orEmpty() }
        val identity = "$mode|${info.preparationStatus}|${info.playbackRevision}|$hlsSessionId"
        if (identity == lastSource) return
        lastSource = identity
        transitioning = info.preparationStatus != "ready"
        record(PlaybackEvent(kind = when(info.preparationStatus) { "ready" -> "source"; "failed" -> "preparation_error"; else -> "preparing" }, positionMs = PlaybackTimeline.absolutePosition(info, info.resumePosition)))
    }
    fun conversion(reason: String) { transitioning = true; record(PlaybackEvent(kind = "conversion", reason = reason)) }
    fun record(event: PlaybackEvent) {
        synchronized(queue) {
            if (finished) return
            queue.addLast(event.copy(sequence = ++sequence, elapsedMs = (clock() - startedAt).coerceAtLeast(0), mode = mode, hlsSessionId = hlsSessionId))
            // Preserve recent errors and interruptions when the server is unreachable.
            while (queue.size > 240) {
                val sample = queue.firstOrNull { it.kind == "sample" }
                if (sample != null) queue.remove(sample) else queue.removeFirst()
            }
        }
    }
    fun finish() { beforeFinish?.invoke(); record(PlaybackEvent(kind = "end")); synchronized(queue) { finished = true }; wake.trySend(Unit) }
}

class PlaybackDiagnosticsAttachment(
    private val player: ExoPlayer,
    private val info: PlaybackInfo,
    private val recorder: PlaybackDiagnosticsRecorder,
    private val onAdaptiveFallback: () -> Unit,
    private val onQuality: (Int) -> Unit,
) : Player.Listener, AnalyticsListener {
    private var ready = player.playbackState == Player.STATE_READY
    private var ignoreBufferUntil = 0L
    private var bufferingAt: Long? = null
    private var pausedAt: Long? = null
    private var bandwidth = 0L
    private var bitrate = info.bitrate
    private var height = info.height
    private var detached = false
    init { player.addListener(this); player.addAnalyticsListener(this); recorder.beforeFinish = { detach() } }
    private fun event(kind: String, duration: Long = 0, error: Int = 0, dropped: Int = 0, reason: String = "") = PlaybackEvent(
        kind = kind, durationMs = duration.coerceAtLeast(0), positionMs = PlaybackTimeline.absolutePosition(info, player.currentPosition).coerceAtLeast(0),
        bufferMs = player.totalBufferedDuration.coerceAtLeast(0), bandwidthBps = bandwidth,
        bitrate = bitrate.coerceAtLeast(0), height = height.coerceAtLeast(0), errorCode = error, droppedFrames = dropped, reason = reason,
    )
    fun sample() { recorder.record(event("sample")) }
    private fun closeBuffer(allowFallback: Boolean = false) {
        val start = bufferingAt ?: return
        bufferingAt = null
        val now = SystemClock.elapsedRealtime()
        recorder.record(event("buffer_end", now - start))
        if (allowFallback && recorder.fallback.onInterruption(now, now - start, info.playbackMode == "direct")) onAdaptiveFallback()
    }
    override fun onPlaybackStateChanged(state: Int) {
        if (state == Player.STATE_BUFFERING && ready && SystemClock.elapsedRealtime() >= ignoreBufferUntil && player.playWhenReady && bufferingAt == null) {
            bufferingAt = SystemClock.elapsedRealtime(); recorder.record(event("buffer_start"))
        }
        if (state == Player.STATE_READY) { ready = true; ignoreBufferUntil = 0L; closeBuffer(allowFallback = true) }
        if (state == Player.STATE_ENDED) { closeBuffer(); recorder.record(event("ended")) }
    }
    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
            closeBuffer(); ignoreBufferUntil = SystemClock.elapsedRealtime() + 2_000
            recorder.record(event("seek", reason = "seek"))
        }
    }
    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (!playWhenReady) {
            closeBuffer()
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST && !recorder.transitioning) {
                pausedAt = SystemClock.elapsedRealtime(); recorder.record(event("pause", reason = "user"))
            }
        } else {
            pausedAt?.let { recorder.record(event("resume", SystemClock.elapsedRealtime() - it, reason = "user")) }
            pausedAt = null
            if (player.playbackState == Player.STATE_BUFFERING) onPlaybackStateChanged(Player.STATE_BUFFERING)
        }
    }
    override fun onRenderedFirstFrame() { recorder.record(event("first_frame")) }
    override fun onPlayerError(error: PlaybackException) { closeBuffer(); recorder.record(event("error", error = error.errorCode)) }
    override fun onLoadError(eventTime: AnalyticsListener.EventTime, loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData, error: IOException, wasCanceled: Boolean) {
        if (!wasCanceled) recorder.record(event("error", error = when (error) {
            is SocketTimeoutException -> PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
            is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException -> PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
            else -> PlaybackException.ERROR_CODE_IO_UNSPECIFIED
        }))
    }
    override fun onBandwidthEstimate(eventTime: AnalyticsListener.EventTime, totalLoadTimeMs: Int, totalBytesLoaded: Long, bitrateEstimate: Long) { bandwidth = bitrateEstimate.coerceAtLeast(0) }
    override fun onVideoInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?) {
        height = format.height; bitrate = format.bitrate.toLong()
        onQuality(height); recorder.record(event("quality"))
    }
    override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) { recorder.record(event("dropped_frames", dropped = droppedFrames)) }
    fun detach() {
        if (detached) return
        detached = true
        recorder.beforeFinish = null
        closeBuffer()
        pausedAt?.let { recorder.record(event("resume", SystemClock.elapsedRealtime() - it, reason = "user")) }
        player.removeListener(this); player.removeAnalyticsListener(this)
    }
}
