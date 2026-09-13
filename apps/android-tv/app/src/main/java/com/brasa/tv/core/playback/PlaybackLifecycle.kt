@file:androidx.media3.common.util.UnstableApi
package com.brasa.tv.core.playback

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.Player

/** Background always pauses; returning requires an explicit Play, preserving manual pauses. */
class PlaybackLifecycle(
    private val player: Player,
    private val save: () -> Unit,
    private val cancelRecovery: () -> Unit,
    private val foregroundChanged: (Boolean) -> Unit,
) : LifecycleEventObserver, Player.Listener {
    private var foreground = true
    init { player.addListener(this) }
    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_RESUME -> { foreground = true; foregroundChanged(true) }
            Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> background()
            else -> Unit
        }
    }
    fun background() {
        if (!foreground) return
        foreground = false
        foregroundChanged(false)
        cancelRecovery()
        save()
        player.pause()
    }
    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (playWhenReady && !foreground) player.pause()
        if (!playWhenReady) save()
    }
    override fun onPlaybackSuppressionReasonChanged(reason: Int) {
        if (reason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) { cancelRecovery(); player.pause(); save() }
    }
    fun detach() { player.removeListener(this) }
}
