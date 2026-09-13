@file:androidx.media3.common.util.UnstableApi
package com.brasa.tv.core.playback

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.media3.common.Player
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class PlaybackLifecycleTest {
    @Test fun homePausesAndSavesOnceWithoutAutomaticResume() {
        var pauses = 0; var saves = 0; var cancelled = 0; var foreground = true
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, _ ->
            when (method.name) { "pause" -> { pauses++; null }; "addListener", "removeListener" -> null; else -> null }
        } as Player
        val policy = PlaybackLifecycle(player, { saves++ }, { cancelled++ }, { foreground = it })
        val owner = object : LifecycleOwner { override val lifecycle: Lifecycle get() = error("not needed") }
        policy.onStateChanged(owner, Lifecycle.Event.ON_PAUSE)
        policy.onStateChanged(owner, Lifecycle.Event.ON_STOP)
        assertEquals(1, pauses); assertEquals(1, saves); assertEquals(1, cancelled); assertFalse(foreground)
        policy.onStateChanged(owner, Lifecycle.Event.ON_RESUME)
        assertTrue(foreground); assertEquals(1, pauses)
        policy.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        policy.onStateChanged(owner, Lifecycle.Event.ON_PAUSE)
        policy.onStateChanged(owner, Lifecycle.Event.ON_RESUME)
        assertEquals(2, pauses)
        policy.detach()
    }
    @Test fun audioFocusLossPausesAndLatePlayInBackgroundIsBlocked() {
        var pauses = 0; var saves = 0
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, _ -> if (method.name == "pause") pauses++; null } as Player
        val policy = PlaybackLifecycle(player, { saves++ }, {}, {})
        policy.onPlaybackSuppressionReasonChanged(Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS)
        assertEquals(1, pauses); assertEquals(1, saves)
        policy.background()
        policy.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        assertEquals(3, pauses)
    }
}
