package com.brasa.tv.feature.server

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.brasa.tv.app.BrasaUiState
import com.brasa.tv.core.di.AppContainer
import com.brasa.tv.designsystem.AmbientBackground
import com.brasa.tv.designsystem.BrasaBorder
import com.brasa.tv.designsystem.BrasaButton
import com.brasa.tv.designsystem.BrasaButtonStyle
import com.brasa.tv.designsystem.BrasaLogo
import com.brasa.tv.designsystem.BrasaRed
import com.brasa.tv.designsystem.BrasaSurface
import com.brasa.tv.designsystem.BrasaTextField
import com.brasa.tv.designsystem.BrasaTextMuted

@Composable
fun ServerScreen(state: BrasaUiState, container: AppContainer, onConnect: (String) -> Unit) {
    val discovery = remember(container.discovery) { container.discovery.discover() }
    val servers by discovery.collectAsState(initial = emptyList())
    var address by remember { mutableStateOf("") }
    AmbientBackground {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BrasaLogo()
            Spacer(Modifier.height(25.dp))
            Text("Encontrar meu BRasa", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            Text("Conecte esta TV ao computador que guarda sua biblioteca.", color = BrasaTextMuted, fontSize = 18.sp)
            Spacer(Modifier.height(25.dp))
            Column(
                Modifier.width(700.dp).background(BrasaSurface.copy(alpha = .95f), RoundedCornerShape(15.dp)).border(1.dp, BrasaBorder, RoundedCornerShape(15.dp)).padding(24.dp),
            ) {
                if (servers.isNotEmpty()) {
                    Text("Computadores encontrados", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    servers.forEachIndexed { index, server ->
                        BrasaButton(
                            "${server.name}  ·  ${server.address}",
                            { onConnect(server.address) },
                            Modifier.fillMaxWidth(),
                            style = if (index == 0) BrasaButtonStyle.Primary else BrasaButtonStyle.Secondary,
                        )
                        Spacer(Modifier.height(9.dp))
                    }
                }
                Text("Ou informe o endereço do computador", color = BrasaTextMuted, fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    BrasaTextField(address, { address = it }, Modifier.weight(1f), "Ex.: 192.168.1.20:4173")
                    BrasaButton(
                        if (state.loading) "Conectando…" else "Conectar",
                        { onConnect(address) },
                        enabled = !state.loading && address.isNotBlank(),
                        style = BrasaButtonStyle.Primary,
                    )
                }
                if (state.message.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(state.message, color = BrasaRed, fontSize = 15.sp)
                }
            }
        }
    }
}
