@file:androidx.media3.common.util.UnstableApi

package com.brasa.tv.feature.player

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.brasa.tv.core.model.PlaybackInfo
import com.brasa.tv.core.playback.PlaybackCoordinator
import com.brasa.tv.core.playback.PlaybackLifecycle
import kotlinx.coroutines.CancellationException

internal class PlayerSessionController {
    val player = mutableStateOf<ExoPlayer?>(null)
    val error = mutableStateOf("")
    val attempt = mutableIntStateOf(0)
}

@Composable
internal fun rememberPlayerSession(
    identity: String,
    baseUrl: String,
    info: PlaybackInfo,
    coordinator: PlaybackCoordinator,
): PlayerSessionController {
    val state = remember(identity, baseUrl) { PlayerSessionController() }
    LaunchedEffect(identity, baseUrl, state.attempt.intValue) {
        state.player.value = null
        state.error.value = ""
        try {
            state.player.value = coordinator.acquire(baseUrl, info)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            state.error.value = failure.message ?: "Não foi possível preparar o vídeo."
        }
    }
    return state
}

@Composable
internal fun PlayerLifecycleEffect(
    player: ExoPlayer,
    save: () -> Unit,
    cancelRecovery: () -> Unit,
    foregroundChanged: (Boolean) -> Unit,
) {
    val owner = LocalLifecycleOwner.current
    val latestSave by androidx.compose.runtime.rememberUpdatedState(save)
    val latestCancel by androidx.compose.runtime.rememberUpdatedState(cancelRecovery)
    val latestForeground by androidx.compose.runtime.rememberUpdatedState(foregroundChanged)
    DisposableEffect(player, owner) {
        val lifecycle =
            PlaybackLifecycle(
                player,
                { latestSave() },
                { latestCancel() },
                { latestForeground(it) },
            )
        owner.lifecycle.addObserver(lifecycle)
        if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) lifecycle.background()
        onDispose {
            owner.lifecycle.removeObserver(lifecycle)
            lifecycle.detach()
        }
    }
}

/** The listener is detached before releasing the session and its owned player. */
@Composable
internal fun PlayerSessionEffect(
    player: ExoPlayer,
    coordinator: PlaybackCoordinator,
    listener: Player.Listener,
    onDisposePlayer: () -> Boolean,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val latestDispose by androidx.compose.runtime.rememberUpdatedState(onDisposePlayer)
    DisposableEffect(player) {
        val session = MediaSession.Builder(context, player).build()
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        player.addListener(listener)
        onDispose {
            val completed = latestDispose()
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            player.removeListener(listener)
            session.release()
            coordinator.release(player, completed = completed)
        }
    }
}
