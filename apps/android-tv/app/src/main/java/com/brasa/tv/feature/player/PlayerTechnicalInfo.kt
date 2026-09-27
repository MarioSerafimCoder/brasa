@file:androidx.media3.common.util.UnstableApi

package com.brasa.tv.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import com.brasa.tv.core.model.PlaybackInfo
import com.brasa.tv.designsystem.BrasaButton
import com.brasa.tv.designsystem.BrasaButtonStyle
import com.brasa.tv.designsystem.BrasaSurface
import java.util.Locale

@Composable
internal fun PlayerTechnicalInfo(info: PlaybackInfo, controls: PlayerControlsState) {
    var position by controls.position
    var buffered by controls.buffered
    var selectedQuality by controls.selectedQuality
    var actualHeight by controls.actualHeight
    var technicalInfoVisible by controls.technicalInfoVisible
    var restoreInfoFocus by controls.restoreInfoFocus
    var subtitleDelayMs by controls.subtitleDelayMs

    if (technicalInfoVisible) {
        val closeFocus = remember { FocusRequester() }
        val closeInfo = {
            technicalInfoVisible = false
            restoreInfoFocus = true
        }
        Dialog(
            onDismissRequest = closeInfo,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Column(
                Modifier.padding(24.dp)
                    .width(460.dp)
                    .heightIn(max = 470.dp)
                    .background(BrasaSurface.copy(alpha = .96f), RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Text(
                    "Informações da reprodução",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(14.dp))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    TechnicalLine("Modo", playbackModeLabel(info))
                    TechnicalLine("Vídeo", info.videoCodec.uppercase().ifBlank { "Não informado" })
                    TechnicalLine("Áudio", info.audioCodec.uppercase().ifBlank { "Não informado" })
                    TechnicalLine(
                        "Resolução",
                        if (info.width > 0 && info.height > 0) "${info.width} × ${info.height}"
                        else "Não informada",
                    )
                    TechnicalLine(
                        "Qualidade",
                        "$selectedQuality${if (actualHeight > 0) " · ${actualHeight}p" else ""}",
                    )
                    TechnicalLine(
                        "Taxa",
                        if (info.bitrate > 0)
                            String.format(
                                Locale.forLanguageTag("pt-BR"),
                                "%.1f Mbps",
                                info.bitrate / 1_000_000.0,
                            )
                        else "Não informada",
                    )
                    TechnicalLine("Buffer", "${((buffered - position).coerceAtLeast(0) / 1000)} s")
                    if (subtitleDelayMs != 0L)
                        TechnicalLine("Legenda", formatSubtitleDelay(subtitleDelayMs))
                }
                Spacer(Modifier.height(14.dp))
                BrasaButton(
                    "Fechar painel",
                    closeInfo,
                    Modifier.fillMaxWidth().focusRequester(closeFocus),
                    style = BrasaButtonStyle.Ghost,
                )
            }
            LaunchedEffect(Unit) {
                withFrameNanos {}
                closeFocus.requestFocus()
            }
        }
    }
}
