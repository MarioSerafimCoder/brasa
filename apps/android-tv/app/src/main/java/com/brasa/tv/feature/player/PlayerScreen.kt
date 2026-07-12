@file:androidx.media3.common.util.UnstableApi

package com.brasa.tv.feature.player

import android.app.Activity
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import com.brasa.tv.app.BrasaUiState
import com.brasa.tv.core.di.AppContainer
import com.brasa.tv.core.model.CatalogItem
import com.brasa.tv.core.model.PlaybackInfo
import com.brasa.tv.core.model.WatchProgress
import com.brasa.tv.core.playback.PlaybackFactory
import com.brasa.tv.data.storage.AppSettings
import com.brasa.tv.designsystem.BrasaButton
import kotlinx.coroutines.delay

@Composable fun PlayerScreen(state:BrasaUiState,container:AppContainer,onProgress:(WatchProgress)->Unit,onNext:(CatalogItem)->Unit,onBack:()->Unit){
    val info=state.playback
    val settings by container.settings.values.collectAsState(initial=AppSettings())
    if(info==null||settings.serverBaseUrl.isBlank()){BackHandler(onBack=onBack)}else{key(info.mediaKey){PlayerContent(info,settings.serverBaseUrl,container,onProgress,onNext,onBack)}}
}

@Composable private fun PlayerContent(info:PlaybackInfo,serverBaseUrl:String,container:AppContainer,onProgress:(WatchProgress)->Unit,onNext:(CatalogItem)->Unit,onBack:()->Unit){
    val context=LocalContext.current
    val player=remember(info.mediaKey,serverBaseUrl){PlaybackFactory(context,container.http,container.tokenStore).create(serverBaseUrl,info)}
    val session=remember(player){MediaSession.Builder(context,player).build()}
    var ended by remember{mutableStateOf(false)}
    fun save(completed:Boolean=false){val duration=player.duration;if(duration<=0||duration==C.TIME_UNSET)return;onProgress(WatchProgress(mediaType=if(info.mediaKey.startsWith("episode:"))"episode" else "movie",mediaId=info.mediaId,currentTime=player.currentPosition/1000.0,duration=duration/1000.0,percentage=player.currentPosition.toDouble()/duration*100,completed=completed))}
    fun exit(){save();player.pause();onBack()}
    BackHandler{exit()}
    DisposableEffect(player){
        val activity=context as? Activity;activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val listener=object:Player.Listener{override fun onPlaybackStateChanged(playbackState:Int){if(playbackState==Player.STATE_ENDED){save(true);ended=true}}}
        player.addListener(listener)
        onDispose{save();activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);player.removeListener(listener);session.release();player.release()}
    }
    LaunchedEffect(player){while(true){delay(12_000);if(player.isPlaying)save()}}
    Box(Modifier.fillMaxSize().background(Color.Black).onPreviewKeyEvent{event->
        if(event.nativeKeyEvent.action!=KeyEvent.ACTION_DOWN)false else when(event.nativeKeyEvent.keyCode){
            KeyEvent.KEYCODE_DPAD_LEFT->{player.seekTo((player.currentPosition-10_000).coerceAtLeast(0));true}
            KeyEvent.KEYCODE_DPAD_RIGHT->{val end=player.duration.takeIf{it>0&&it!=C.TIME_UNSET}?:Long.MAX_VALUE;player.seekTo((player.currentPosition+10_000).coerceAtMost(end));true}
            KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE->{if(player.isPlaying)player.pause()else player.play();true}
            else->false
        }
    }.focusable()){
        AndroidView(factory={PlayerView(it).apply{useController=true;controllerAutoShow=false;this.player=player}},modifier=Modifier.fillMaxSize(),update={it.player=player})
        Row(Modifier.padding(24.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){BrasaButton("Voltar",::exit);BrasaButton("Play/Pause",{if(player.isPlaying)player.pause()else player.play()});BrasaButton("Áudio",{cycleAudio(player)});BrasaButton("Legenda",{cycleSubtitle(player)})}
        if(ended&&info.nextEpisode!=null){Column(Modifier.align(Alignment.Center).background(Color(0xE6151922)).padding(28.dp)){androidx.tv.material3.Text("Próximo episódio disponível");Spacer(Modifier.height(12.dp));BrasaButton("Reproduzir agora",{onNext(info.nextEpisode)});BrasaButton("Voltar à série",::exit)}}
    }
}

private fun cycleAudio(player:ExoPlayer){val languages=player.currentTracks.groups.filter{it.type==C.TRACK_TYPE_AUDIO}.flatMap{group->(0 until group.length).mapNotNull{group.getTrackFormat(it).language}}.distinct();if(languages.isEmpty())return;val current=player.trackSelectionParameters.preferredAudioLanguages.firstOrNull();val next=languages[(languages.indexOf(current)+1).coerceAtLeast(0)%languages.size];player.trackSelectionParameters=player.trackSelectionParameters.buildUpon().setPreferredAudioLanguage(next).build()}
private fun cycleSubtitle(player:ExoPlayer){val languages=player.currentTracks.groups.filter{it.type==C.TRACK_TYPE_TEXT}.flatMap{group->(0 until group.length).mapNotNull{group.getTrackFormat(it).language}}.distinct();val current=player.trackSelectionParameters.preferredTextLanguages.firstOrNull();if(languages.isEmpty()||current==languages.lastOrNull()){player.trackSelectionParameters=player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,true).build();return};val next=languages[(languages.indexOf(current)+1).coerceAtLeast(0)%languages.size];player.trackSelectionParameters=player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,false).setPreferredTextLanguage(next).build()}
