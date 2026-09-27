@file:androidx.media3.common.util.UnstableApi

package com.brasa.tv.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.brasa.tv.core.model.CatalogItem
import com.brasa.tv.core.model.PlaybackInfo
import com.brasa.tv.core.model.playableItem
import com.brasa.tv.data.storage.AppSettings
import com.brasa.tv.designsystem.BrasaButton
import com.brasa.tv.designsystem.BrasaButtonStyle
import com.brasa.tv.designsystem.BrasaIcon
import com.brasa.tv.designsystem.BrasaOrange
import com.brasa.tv.designsystem.BrasaSurface
import com.brasa.tv.designsystem.BrasaText
import com.brasa.tv.designsystem.BrasaTextMuted

@Composable
internal fun BoxScope.PlayerEndPanel(
    info: PlaybackInfo,
    selected: CatalogItem?,
    related: List<CatalogItem>,
    settings: AppSettings,
    controls: PlayerControlsState,
    actions: PlayerOverlayActions,
) {
    var ended by controls.ended
    var autoNextSeconds by controls.autoNextSeconds
    var autoNextCancelled by controls.autoNextCancelled
    val endFocus = controls.endFocus

    val onNext = actions.onNext
    val onSignal = actions.onSignal
    fun exit() = actions.exit()
    if (ended) {
        Column(
            Modifier.align(Alignment.Center)
                .width(620.dp)
                .background(BrasaSurface.copy(alpha = .97f), RoundedCornerShape(16.dp))
                .padding(30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (info.nextEpisode != null) {
                Text(
                    "Próximo episódio",
                    color = Color.White,
                    fontSize = 29.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(info.nextEpisode.title, color = BrasaTextMuted, fontSize = 18.sp)
                if (settings.autoplayNext && !autoNextCancelled)
                    Text(
                        "Reprodução automática em $autoNextSeconds s",
                        color = BrasaOrange,
                        fontSize = 16.sp,
                    )
                Spacer(Modifier.height(21.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BrasaButton(
                        "Reproduzir agora",
                        { onNext(info.nextEpisode) },
                        style = BrasaButtonStyle.Primary,
                        leadingIcon = BrasaIcon.Play,
                    )
                    if (settings.autoplayNext && !autoNextCancelled)
                        BrasaButton(
                            "Cancelar contagem",
                            { autoNextCancelled = true },
                            Modifier.focusRequester(endFocus),
                        )
                    BrasaButton("Voltar à série", ::exit)
                }
            } else {
                Text(
                    "Você terminou ${selected?.title.orEmpty()}",
                    color = Color.White,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BrasaButton(
                        "Gostei",
                        { onSignal("like", true) },
                        style = BrasaButtonStyle.Primary,
                    )
                    BrasaButton("Não é para mim", { onSignal("not-for-me", true) })
                    BrasaButton("Voltar", ::exit)
                }
                if (related.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "Talvez você também goste",
                        color = BrasaText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        related.forEach { candidate ->
                            BrasaButton(
                                candidate.title.take(24),
                                { onNext(candidate.playableItem()) },
                                style = BrasaButtonStyle.Ghost,
                            )
                        }
                    }
                }
            }
        }
    }
}
