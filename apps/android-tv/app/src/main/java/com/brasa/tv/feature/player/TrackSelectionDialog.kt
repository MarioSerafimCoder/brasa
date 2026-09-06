@file:androidx.media3.common.util.UnstableApi

package com.brasa.tv.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.C
import com.brasa.tv.data.storage.AppSettings
import com.brasa.tv.designsystem.BrasaButton
import com.brasa.tv.designsystem.BrasaButtonStyle
import com.brasa.tv.designsystem.BrasaSurface
import androidx.tv.material3.Text

@Composable
fun TrackSelectionDialog(
    type: Int,
    tracks: List<PlaybackTrack>,
    subtitlesDisabled: Boolean,
    settings: AppSettings,
    onSelect: (PlaybackTrack?) -> Unit,
    onAutomaticAudio: () -> Unit,
    onSize: (Float) -> Unit,
    onStyle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    val subtitles = type == C.TRACK_TYPE_TEXT
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.width(620.dp).heightIn(max = 510.dp).background(BrasaSurface, RoundedCornerShape(18.dp)).padding(24.dp)) {
            Text(if (subtitles) "Legendas" else "Áudio", color = Color.White, fontSize = 26.sp)
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    BrasaButton(
                        if (subtitles) (if (subtitlesDisabled) "✓ " else "") + "Sem legenda" else "Áudio automático",
                        { if (subtitles) onSelect(null) else onAutomaticAudio() },
                        Modifier.fillMaxWidth().focusRequester(firstFocus),
                    )
                }
                itemsIndexed(tracks) { index, track ->
                    BrasaButton(
                        "${if (track.selected && (!subtitles || !subtitlesDisabled)) "✓ " else ""}${index + 1}. ${track.label}",
                        { onSelect(track) }, Modifier.fillMaxWidth(), enabled = track.supported,
                        style = if (track.selected && (!subtitles || !subtitlesDisabled)) BrasaButtonStyle.Primary else BrasaButtonStyle.Secondary,
                    )
                }
                if (tracks.isEmpty()) item { Text("Nenhuma faixa disponível neste vídeo.", color = Color.LightGray) }
            }
            if (subtitles) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BrasaButton("Tamanho: ${(settings.subtitleSize * 100).toInt()}%", {
                        val sizes = listOf(.8f, 1f, 1.2f, 1.4f)
                        onSize(sizes[(sizes.indexOf(settings.subtitleSize) + 1) % sizes.size])
                    })
                    BrasaButton(if (settings.subtitleStyle == "background") "Fundo escuro" else "Contorno", {
                        onStyle(if (settings.subtitleStyle == "background") "outline" else "background")
                    })
                }
            }
            Spacer(Modifier.height(12.dp))
            BrasaButton("Concluir", onDismiss, Modifier.fillMaxWidth())
        }
        LaunchedEffect(Unit) { firstFocus.requestFocus() }
    }
}
