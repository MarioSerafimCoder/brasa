package com.brasa.tv.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.brasa.tv.BuildConfig
import com.brasa.tv.app.BrasaUiState
import com.brasa.tv.designsystem.BrasaBorder
import com.brasa.tv.designsystem.BrasaButton
import com.brasa.tv.designsystem.BrasaButtonStyle
import com.brasa.tv.designsystem.BrasaSurface
import com.brasa.tv.designsystem.BrasaText
import com.brasa.tv.designsystem.BrasaTextMuted
import com.brasa.tv.designsystem.BrasaTopBar
import com.brasa.tv.designsystem.AmbientBackground
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(
    state: BrasaUiState,
    lastUpdateCheckAt: Long,
    onUpdates: () -> Unit,
    onProfiles: () -> Unit,
    onForget: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    AmbientBackground {
        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
            BrasaTopBar(
                modifier = Modifier.padding(top = 16.dp),
                active = "Configurações",
                onHome = onBack,
                onProfiles = onProfiles,
                onSettings = {},
            )
            Spacer(Modifier.height(30.dp))
            Text("Configurações", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
            Text("Gerencie a conexão, o perfil e as atualizações do BRasa TV.", color = BrasaTextMuted, fontSize = 17.sp)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                Column(
                    Modifier.width(360.dp).background(BrasaSurface.copy(alpha = .94f), RoundedCornerShape(13.dp)).border(1.dp, BrasaBorder, RoundedCornerShape(13.dp)).padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Ações", color = BrasaText, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(3.dp))
                    BrasaButton("Verificar atualização", onUpdates, Modifier.fillMaxWidth(), style = BrasaButtonStyle.Primary)
                    BrasaButton("Trocar perfil", onProfiles, Modifier.fillMaxWidth())
                    BrasaButton("Esquecer computador", onForget, Modifier.fillMaxWidth())
                    BrasaButton("Voltar ao início", onBack, Modifier.fillMaxWidth(), style = BrasaButtonStyle.Ghost)
                }
                Column(
                    Modifier.weight(1f).background(BrasaSurface.copy(alpha = .94f), RoundedCornerShape(13.dp)).border(1.dp, BrasaBorder, RoundedCornerShape(13.dp)).padding(25.dp),
                ) {
                    Text("Status do aplicativo", color = BrasaText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(18.dp))
                    StatusLine("Computador", state.server?.name ?: "BRasa")
                    StatusLine("API", "Versão ${state.server?.apiVersion ?: 1}")
                    StatusLine("Perfil ativo", state.profile?.name ?: "Nenhum")
                    StatusLine("Dispositivo", if (state.paired) "Autorizado" else "Não autorizado")
                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(BrasaBorder))
                    Spacer(Modifier.height(14.dp))
                    StatusLine("BRasa TV", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    StatusLine(
                        "Última verificação",
                        if (lastUpdateCheckAt > 0) DateFormat.getDateTimeInstance().format(Date(lastUpdateCheckAt)) else "Nunca",
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Text(label, modifier = Modifier.weight(1f), color = BrasaTextMuted, fontSize = 16.sp)
        Text(value, color = BrasaText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
