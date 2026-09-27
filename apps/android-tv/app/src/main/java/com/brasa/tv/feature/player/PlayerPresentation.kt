@file:androidx.media3.common.util.UnstableApi

package com.brasa.tv.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.Text
import com.brasa.tv.core.model.PlaybackInfo
import com.brasa.tv.designsystem.BrasaButton
import com.brasa.tv.designsystem.BrasaButtonStyle
import com.brasa.tv.designsystem.BrasaOrange
import com.brasa.tv.designsystem.BrasaRed
import com.brasa.tv.designsystem.BrasaTextMuted
import java.util.Locale

@Composable
internal fun PreparationScreen(info: PlaybackInfo, onRetry: () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val failed = info.preparationStatus == "failed"
    Box(
        Modifier.fillMaxSize().background(Color.Black).focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (failed) "Não foi possível preparar o vídeo"
                else if (info.preparationStatus == "analyzing") "Analisando mídia"
                else "Preparando reprodução",
                color = if (failed) BrasaRed else Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            if (!failed) {
                Text("${info.preparationProgress.toInt()}%", color = BrasaOrange, fontSize = 24.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (info.playbackMode == "hls")
                        "Criando streaming adaptativo. A reprodução começa com os primeiros segmentos."
                    else "Criando uma versão compatível com esta TV.",
                    color = BrasaTextMuted,
                    fontSize = 17.sp,
                )
            } else
                Text(
                    info.errorMessage.ifBlank {
                        when (info.errorType) {
                            "network" -> "Não foi possível receber os dados do servidor."
                            "decode" -> "O dispositivo não conseguiu decodificar este vídeo."
                            "codec" -> "O formato original não é compatível com este dispositivo."
                            else -> "O servidor não conseguiu processar esta mídia."
                        }
                    },
                    color = BrasaTextMuted,
                    fontSize = 17.sp,
                )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (failed)
                    BrasaButton("Tentar novamente", onRetry, style = BrasaButtonStyle.Primary)
                BrasaButton("Voltar", onBack)
            }
        }
    }
}

@Composable
internal fun TechnicalLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = BrasaTextMuted, fontSize = 14.sp)
        Spacer(Modifier.width(16.dp))
        Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

internal fun playbackModeLabel(info: PlaybackInfo): String =
    when {
        info.playbackMode == "direct" -> "Original"
        info.videoCopied -> "Vídeo original adaptado"
        info.playbackMode == "hls" -> "Conversão adaptativa"
        else -> info.playbackMode.replaceFirstChar { it.uppercase() }
    }

internal fun formatSubtitleDelay(delayMs: Long): String =
    String.format(Locale.forLanguageTag("pt-BR"), "%+.2f s", delayMs / 1000.0)

internal fun formatTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = totalSeconds % 3600 / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
}

internal fun cycleQuality(player: ExoPlayer, info: PlaybackInfo, current: String): String {
    val options = listOf("Automática") + info.qualities
    val next = options[(options.indexOf(current).coerceAtLeast(0) + 1) % options.size]
    val builder = player.trackSelectionParameters.buildUpon()
    val cap = if (info.prioritizeStability) 720 else 2160
    player.trackSelectionParameters =
        if (next == "Automática" && !info.prioritizeStability)
            builder.clearVideoSizeConstraints().build()
        else
            builder
                .setMaxVideoSize(
                    Int.MAX_VALUE,
                    minOf(cap, next.filter(Char::isDigit).toIntOrNull() ?: cap),
                )
                .build()
    return next
}
