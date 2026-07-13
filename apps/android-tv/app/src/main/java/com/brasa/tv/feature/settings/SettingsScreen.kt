package com.brasa.tv.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.brasa.tv.BuildConfig
import com.brasa.tv.app.BrasaUiState
import com.brasa.tv.designsystem.AmbientBackground
import com.brasa.tv.designsystem.BrasaBorder
import com.brasa.tv.designsystem.BrasaButton
import com.brasa.tv.designsystem.BrasaButtonStyle
import com.brasa.tv.designsystem.BrasaRed
import com.brasa.tv.designsystem.BrasaSpacing
import com.brasa.tv.designsystem.BrasaSurface
import com.brasa.tv.designsystem.BrasaSuccess
import com.brasa.tv.designsystem.BrasaText
import com.brasa.tv.designsystem.BrasaTextMuted
import com.brasa.tv.designsystem.BrasaTopBar
import com.brasa.tv.designsystem.BrasaType
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(
    state: BrasaUiState,
    lastUpdateCheckAt: Long,
    onUpdates: () -> Unit,
    onProfiles: () -> Unit,
    onClearCache: () -> Unit,
    onLoadCache: () -> Unit,
    onForget: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmForget by remember { mutableStateOf(false) }
    BackHandler { if (confirmForget) confirmForget = false else onBack() }
    LaunchedEffect(Unit) { onLoadCache() }
    AmbientBackground {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = BrasaSpacing.safe)) {
            BrasaTopBar(Modifier.padding(top = BrasaSpacing.x2), active = "Configurações", onHome = onBack, onProfiles = onProfiles, onSettings = {}, profileInitials = state.profile?.initials.orEmpty())
            Spacer(Modifier.height(BrasaSpacing.x4))
            Text("Configurações", color = Color.White, fontSize = BrasaType.page, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(BrasaSpacing.x3))
            SettingsSection("Conexão") {
                StatusLine("Computador conectado", state.server?.name ?: "Não conectado")
                StatusLine("Status", if (state.paired) "● Conectado" else "● Offline", if (state.paired) BrasaSuccess else BrasaRed)
            }
            Spacer(Modifier.height(BrasaSpacing.x2))
            SettingsSection("Reprodução") {
                StatusLine("Cache utilizado", formatBytes(state.cacheBytes))
                BrasaButton(if (state.loading) "Limpando cache…" else "Limpar cache", onClearCache, Modifier.fillMaxWidth(), enabled = !state.loading)
            }
            Spacer(Modifier.height(BrasaSpacing.x2))
            SettingsSection("Aplicativo") {
                StatusLine("Versão", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                StatusLine("Última verificação", if (lastUpdateCheckAt > 0) DateFormat.getDateTimeInstance().format(Date(lastUpdateCheckAt)) else "Nunca")
                BrasaButton("Verificar atualização", onUpdates, Modifier.fillMaxWidth(), style = BrasaButtonStyle.Primary)
            }
            Spacer(Modifier.height(BrasaSpacing.x2))
            SettingsSection("Conta") {
                BrasaButton("Trocar perfil", onProfiles, Modifier.fillMaxWidth())
                if (confirmForget) {
                    Text("Esquecer este computador remove a autorização desta TV.", color = BrasaTextMuted, fontSize = BrasaType.metadata)
                    Row(horizontalArrangement = Arrangement.spacedBy(BrasaSpacing.x2)) { BrasaButton("Confirmar", onForget, style = BrasaButtonStyle.Primary); BrasaButton("Cancelar", { confirmForget = false }) }
                } else BrasaButton("Esquecer computador", { confirmForget = true }, Modifier.fillMaxWidth(), style = BrasaButtonStyle.Ghost)
            }
            Spacer(Modifier.height(BrasaSpacing.x8))
        }
    }
}

@Composable private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().background(BrasaSurface.copy(alpha = .9f), RoundedCornerShape(16.dp)).border(1.dp, BrasaBorder, RoundedCornerShape(16.dp)).padding(horizontal = BrasaSpacing.x3, vertical = BrasaSpacing.x2)) {
        Text(title, color = BrasaText, fontSize = BrasaType.body, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp)); content()
    }
}

@Composable private fun StatusLine(label: String, value: String, valueColor: Color = BrasaText) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) { Text(label, modifier = Modifier.weight(1f), color = BrasaTextMuted, fontSize = BrasaType.metadata); Text(value, color = valueColor, fontSize = BrasaType.metadata, fontWeight = FontWeight.SemiBold) }
}

private fun formatBytes(bytes: Long): String = if (bytes < 1024 * 1024) "${bytes / 1024} KB" else "%.1f MB".format(bytes / 1048576.0)
