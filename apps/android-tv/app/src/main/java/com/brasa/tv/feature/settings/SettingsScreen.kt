package com.brasa.tv.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.brasa.tv.BuildConfig
import com.brasa.tv.app.BrasaUiState
import com.brasa.tv.data.storage.AppSettings
import com.brasa.tv.data.storage.AppSettingsStore
import com.brasa.tv.designsystem.*
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

private enum class SettingsCategory(val label: String) {
    GENERAL("Geral"), PLAYBACK("Reprodução"), INTERFACE("Interface"), PERSONALIZATION("Personalização"), ACCOUNT("Conta e aplicativo")
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun SettingsScreen(
    state: BrasaUiState,
    settingsStore: AppSettingsStore,
    lastUpdateCheckAt: Long,
    onUpdates: () -> Unit,
    onNetworkDiagnostics: () -> Unit,
    onPlaybackHistory: () -> Unit,
    onScanLibrary: () -> Unit,
    onProfiles: () -> Unit,
    onClearCache: () -> Unit,
    onLoadCache: () -> Unit,
    onResetPersonalization: () -> Unit,
    onAutoplayNext: (Boolean) -> Unit,
    onForget: () -> Unit,
    onBack: () -> Unit,
    progressStatus: String = "",
) {
    var category by rememberSaveable { mutableStateOf(SettingsCategory.GENERAL) }
    var confirmForget by remember { mutableStateOf(false) }
    val settings by settingsStore.values.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    BackHandler { if (confirmForget) confirmForget = false else onBack() }
    LaunchedEffect(Unit) { onLoadCache() }
    AmbientBackground {
        Column(Modifier.fillMaxSize().padding(horizontal = BrasaSpacing.safe)) {
            BrasaTopBar(Modifier.padding(top = BrasaSpacing.x2), active = "Configurações", onHome = onBack, onProfiles = onProfiles, onSettings = {}, profileInitials = state.profile?.initials.orEmpty())
            Spacer(Modifier.height(18.dp))
            Text("Configurações", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(Modifier.width(280.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsCategory.entries.forEach { value ->
                        BrasaButton(value.label, { category = value }, Modifier.fillMaxWidth(), style = if (category == value) BrasaButtonStyle.Primary else BrasaButtonStyle.Ghost)
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(end = 6.dp, bottom = BrasaSpacing.x8), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (category) {
                        SettingsCategory.GENERAL -> {
                            SettingsSection("Conexão") {
                                StatusLine("Computador", state.server?.name ?: "Não conectado")
                                StatusLine("Pareamento", if (state.paired) "Ativado" else "Desativado", if (state.paired) BrasaSuccess else BrasaRed)
                                state.connectionProblem?.let { StatusLine("Conexão atual", it.title, BrasaRed) }
                                BrasaButton("Abrir diagnóstico de rede", onNetworkDiagnostics, Modifier.fillMaxWidth(), style = BrasaButtonStyle.Primary)
                            }
                            SettingsSection("Biblioteca") {
                                Text("Procura novos filmes e episódios nas pastas do computador.", color = BrasaTextMuted, fontSize = BrasaType.metadata)
                                BrasaButton(if (state.libraryScanning) "Buscando novos títulos…" else "Buscar novos títulos", onScanLibrary, Modifier.fillMaxWidth(), enabled = state.paired && !state.previewMode && !state.libraryScanning, style = BrasaButtonStyle.Primary)
                                if (state.libraryScanMessage.isNotBlank()) Text(state.libraryScanMessage, color = BrasaText, fontSize = BrasaType.metadata)
                            }
                        }
                        SettingsCategory.PLAYBACK -> {
                            SettingsSection("Preferências") {
                                ToggleSetting("Priorizar estabilidade", settings.prioritizeStability) { scope.launch { state.profile?.id?.let { settingsStore.saveStability(it, !settings.prioritizeStability) } } }
                                Text("Quando ativado, usa qualidade automática até 720p no próximo vídeo.", color = BrasaTextMuted, fontSize = BrasaType.metadata)
                                Spacer(Modifier.height(8.dp))
                                ToggleSetting("Próximo episódio automático", settings.autoplayNext) {
                                    val next = !settings.autoplayNext
                                    scope.launch { state.profile?.id?.let { settingsStore.saveAutoplayNext(it, next) } }
                                    onAutoplayNext(next)
                                }
                            }
                            SettingsSection("Histórico e armazenamento") {
                                if (progressStatus.isNotBlank()) Text(progressStatus, color = BrasaTextMuted, fontSize = BrasaType.metadata)
                                BrasaButton("Histórico de reprodução", onPlaybackHistory, Modifier.fillMaxWidth())
                                StatusLine("Cache utilizado", formatBytes(state.cacheBytes))
                                BrasaButton(if (state.loading) "Limpando cache…" else "Limpar cache", onClearCache, Modifier.fillMaxWidth(), enabled = !state.loading)
                            }
                        }
                        SettingsCategory.INTERFACE -> {
                            SettingsSection("Escala da interface") {
                                Text("Escolha o tamanho mais confortável para a distância da TV.", color = BrasaTextMuted, fontSize = BrasaType.metadata)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("80%" to .8f, "90%" to .9f, "100%" to 1f, "110%" to 1.1f).forEach { (label, value) -> ScaleOption(label, value, settings.uiScale) { scope.launch { settingsStore.saveUiScale(it) } } }
                                }
                            }
                            SettingsSection("Densidade dos cards") {
                                Text("Muda a quantidade de títulos visíveis sem reduzir a legibilidade.", color = BrasaTextMuted, fontSize = BrasaType.metadata)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ScaleOption("Compacto", .85f, settings.density) { scope.launch { settingsStore.saveDensity(it) } }
                                    ScaleOption("Confortável", 1f, settings.density) { scope.launch { settingsStore.saveDensity(it) } }
                                }
                            }
                        }
                        SettingsCategory.PERSONALIZATION -> SettingsSection("Dados do perfil") {
                            Text("As ações abaixo preservam Minha lista e o progresso assistido.", color = BrasaTextMuted, fontSize = BrasaType.metadata)
                            BrasaButton("Limpar histórico de buscas", { scope.launch { state.profile?.id?.let { settingsStore.clearRecentSearches(it) } } }, Modifier.fillMaxWidth())
                            BrasaButton("Reiniciar avaliações e sugestões", onResetPersonalization, Modifier.fillMaxWidth(), style = BrasaButtonStyle.Ghost)
                        }
                        SettingsCategory.ACCOUNT -> {
                            SettingsSection("Aplicativo") {
                                StatusLine("Versão", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                                StatusLine("Última verificação", if (lastUpdateCheckAt > 0) DateFormat.getDateTimeInstance().format(Date(lastUpdateCheckAt)) else "Nunca")
                                BrasaButton("Verificar atualização", onUpdates, Modifier.fillMaxWidth(), style = BrasaButtonStyle.Primary)
                            }
                            SettingsSection("Conta") {
                                BrasaButton("Trocar perfil", onProfiles, Modifier.fillMaxWidth())
                                if (confirmForget) {
                                    Text("Esquecer este computador remove a autorização desta TV.", color = BrasaTextMuted, fontSize = BrasaType.metadata)
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { BrasaButton("Confirmar", onForget, style = BrasaButtonStyle.Primary); BrasaButton("Cancelar", { confirmForget = false }) }
                                } else BrasaButton("Esquecer computador", { confirmForget = true }, Modifier.fillMaxWidth(), style = BrasaButtonStyle.Ghost)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun ToggleSetting(label: String, enabled: Boolean, onToggle: () -> Unit) =
    BrasaButton("$label — ${if (enabled) "Ativado" else "Desativado"}", onToggle, Modifier.fillMaxWidth(), style = if (enabled) BrasaButtonStyle.Primary else BrasaButtonStyle.Secondary, leadingIcon = if (enabled) BrasaIcon.Check else null)

@Composable private fun ScaleOption(label: String, value: Float, current: Float, onSelect: (Float) -> Unit) =
    BrasaButton("$label${if (kotlin.math.abs(value - current) < .01f) " — Ativado" else ""}", { onSelect(value) }, style = if (kotlin.math.abs(value - current) < .01f) BrasaButtonStyle.Primary else BrasaButtonStyle.Ghost)

@Composable private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(BrasaSurface.copy(alpha = .92f), RoundedCornerShape(16.dp)).border(1.dp, BrasaBorder, RoundedCornerShape(16.dp)).padding(horizontal = BrasaSpacing.x3, vertical = BrasaSpacing.x2)) {
        Text(title, color = BrasaText, fontSize = BrasaType.body, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable private fun StatusLine(label: String, value: String, valueColor: Color = BrasaText) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) { Text(label, modifier = Modifier.weight(1f), color = BrasaTextMuted, fontSize = BrasaType.metadata); Text(value, color = valueColor, fontSize = BrasaType.metadata, fontWeight = FontWeight.SemiBold) }
}

private fun formatBytes(bytes: Long): String = if (bytes < 1024 * 1024) "${bytes / 1024} KB" else "%.1f MB".format(bytes / 1048576.0)
