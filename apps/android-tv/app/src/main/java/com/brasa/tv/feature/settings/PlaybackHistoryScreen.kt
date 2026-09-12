package com.brasa.tv.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.brasa.tv.core.di.AppContainer
import com.brasa.tv.core.playback.PlaybackEvent
import com.brasa.tv.core.playback.PlaybackHistoryEntry
import com.brasa.tv.designsystem.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PlaybackHistoryScreen(container: AppContainer, profileId: String?, onBack: () -> Unit) {
    var history by remember { mutableStateOf<List<PlaybackHistoryEntry>>(emptyList()) }
    var detail by remember { mutableStateOf<PlaybackHistoryEntry?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    val backFocus = remember { FocusRequester() }
    val goBack = { if (selectedId != null) { selectedId = null; detail = null } else onBack() }
    BackHandler(onBack = goBack)
    LaunchedEffect(selectedId, revision, profileId) {
        loading = true; error = ""
        try {
            check(!profileId.isNullOrBlank())
            val base = container.settings.values.first().serverBaseUrl
            val id = selectedId
            if (id == null) history = container.api.playbackHistory(base, profileId)
            else detail = container.api.playbackHistoryDetail(base, profileId, id)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "Não foi possível consultar o histórico. Confira a conexão e se o servidor está atualizado." }
        finally { loading = false }
    }
    LaunchedEffect(selectedId) { withFrameNanos { }; backFocus.requestFocus() }
    AmbientBackground {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = BrasaSpacing.safe, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    BrasaButton("Voltar", goBack, Modifier.focusRequester(backFocus))
                    BrasaButton("Atualizar", { revision++ }, enabled = !loading)
                }
            }
            item { Text("Histórico de reprodução", color = Color.White, fontSize = 28.sp) }
            item { Text("Últimos 30 dias desta TV e deste perfil · até 100 sessões no servidor. Os indícios ajudam a investigar, mas não comprovam a causa.", color = BrasaTextMuted, fontSize = 16.sp) }
            if (loading) item { Text("Carregando…", color = BrasaText) }
            if (error.isNotBlank()) item { Text(error, color = BrasaRed) }
            if (!loading && error.isBlank() && selectedId == null && history.isEmpty()) item { Text("As próximas reproduções aparecerão aqui.", color = BrasaText) }
            if (selectedId == null) items(history, key = { it.id }) { entry ->
                Column { BrasaButton(entry.title, { selectedId = entry.id }, Modifier.fillMaxWidth()); Text("${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.startedAt))} · ${entry.bufferCount} interrupções · ${seconds(entry.bufferDurationMs)} s parados · ${entry.errors} erros", color = BrasaTextMuted, modifier = Modifier.padding(top = 6.dp)) }
            }
            if (selectedId != null && !loading && error.isBlank()) detail?.let { entry ->
                item { Text(entry.title, color = Color.White, fontSize = 23.sp) }
                item { Text("${entry.bufferCount} interrupções (${seconds(entry.bufferDurationMs)} s) · pausas pelo controle: ${seconds(entry.pauseDurationMs)} s\n${entry.errors} erros · ${entry.conversions} recuperações com conversão · ${if (entry.ended) "sessão encerrada" else "sessão aberta ou interrompida"}", color = BrasaText) }
                item { Text(entry.indications.joinToString("\n").ifBlank { "Sem indícios suficientes para atribuir uma causa." }, color = BrasaTextMuted) }
                if (entry.bufferOpen) item { Text("Há uma interrupção sem registro de encerramento. Sua duração final ainda é desconhecida.", color = BrasaOrange) }
                item { Text("Linha do tempo · últimos 600 eventos (o resumo mantém os totais)", color = BrasaTextMuted) }
                items(entry.events, key = { it.sequence }) { event ->
                    var focused by remember { mutableStateOf(false) }
                    Column(Modifier.fillMaxWidth().border(2.dp, if (focused) BrasaOrange else Color.Transparent).onFocusChanged { focused = it.isFocused }.focusable().padding(12.dp)) {
                        Text(eventDescription(event), color = BrasaText, fontSize = 17.sp)
                    }
                }
            }
        }
    }
}

private fun seconds(value: Long) = String.format(Locale.getDefault(), "%.1f", value / 1000.0)
internal fun eventDescription(event: PlaybackEvent): String {
    val label = when(event.kind) {
        "start" -> "Início da sessão"; "source" -> "Vídeo disponível"; "preparing" -> "Preparando vídeo"
        "preparation_error" -> "Erro na preparação"; "first_frame" -> "Primeiro quadro"; "sample" -> "Medição"
        "buffer_start" -> "Interrupção iniciada"; "buffer_end" -> "Interrupção: ${seconds(event.durationMs)} s"
        "pause" -> "Pausa pelo controle"; "resume" -> "Pausa encerrada: ${seconds(event.durationMs)} s"
        "seek" -> "Mudança de posição"; "error" -> "Erro ${event.errorCode}"; "quality" -> "Qualidade: ${event.height}p"
        "dropped_frames" -> "${event.droppedFrames} quadros perdidos"; "retry" -> "Reconectando"
        "conversion" -> "Preparando recuperação ${if (event.reason == "network") "adaptativa por interrupções" else "do vídeo"}"
        "ended" -> "Fim do vídeo"; "end" -> "Sessão encerrada"; else -> event.kind
    }
    val minute = event.elapsedMs / 60_000; val second = event.elapsedMs / 1000 % 60
    val mode = if (event.mode == "direct") "Original" else event.mode.uppercase()
    val network = if (event.bandwidthBps > 0) " · rede estimada ${String.format(Locale.getDefault(), "%.1f", event.bandwidthBps / 1_000_000.0)} Mb/s" else ""
    val encoding = event.server?.let { "\nServidor: ${it.encoder.ifBlank { "iniciando" }} · ${String.format(Locale.getDefault(), "%.2f", it.encodingSpeed)}× · ${it.state}" }.orEmpty()
    return "%d:%02d · %s".format(minute, second, label) + "\n$mode · posição ${event.positionMs / 1000}s · buffer ${seconds(event.bufferMs)} s$network$encoding"
}
