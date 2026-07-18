package com.brasa.tv.feature.pairing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import com.brasa.tv.designsystem.AmbientBackground
import com.brasa.tv.designsystem.BrasaBorder
import com.brasa.tv.designsystem.BrasaButton
import com.brasa.tv.designsystem.BrasaButtonStyle
import com.brasa.tv.designsystem.BrasaLogo
import com.brasa.tv.designsystem.BrasaOrange
import com.brasa.tv.designsystem.BrasaRed
import com.brasa.tv.designsystem.BrasaSurface
import com.brasa.tv.designsystem.BrasaTextField
import com.brasa.tv.designsystem.BrasaTextMuted

@Composable
fun PairingScreen(state: BrasaUiState, onStart: (String) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var name by remember { mutableStateOf("BRasa Android TV") }
    val pairing = state.pairing
    AmbientBackground {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BrasaLogo()
            Spacer(Modifier.height(25.dp))
            Text("Autorize esta TV", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            Text("A aprovação é feita com segurança no painel do computador.", color = BrasaTextMuted, fontSize = 18.sp)
            Spacer(Modifier.height(25.dp))
            Column(
                Modifier.width(620.dp).background(BrasaSurface.copy(alpha = .96f), RoundedCornerShape(16.dp)).border(1.dp, BrasaBorder, RoundedCornerShape(16.dp)).padding(27.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (pairing == null) {
                    Text("Nome do aparelho", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    BrasaTextField(name, { name = it }, Modifier.width(520.dp), "Sala de estar")
                    Spacer(Modifier.height(17.dp))
                    BrasaButton("Gerar código", { onStart(name) }, enabled = name.isNotBlank(), style = BrasaButtonStyle.Primary)
                } else {
                    Text("Digite este código no computador", color = BrasaTextMuted, fontSize = 16.sp)
                    Spacer(Modifier.height(7.dp))
                    Text(pairing.code.chunked(3).joinToString(" "), color = BrasaOrange, fontSize = 64.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 5.sp)
                    Text("●  Aguardando aprovação…", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(5.dp))
                    Text(if (pairing.remainingMs > 0) "Expira em ${pairing.remainingMs / 60000}:${((pairing.remainingMs / 1000) % 60).toString().padStart(2, '0')}" else "O código expira em poucos minutos.", color = BrasaTextMuted, fontSize = 15.sp)
                    if (state.message.isNotBlank()) { Spacer(Modifier.height(20.dp)); BrasaButton("Gerar outro código", { onStart(name) }) }
                }
                if (state.message.isNotBlank()) {
                    Spacer(Modifier.height(13.dp))
                    Text(state.message, color = BrasaRed, fontSize = 15.sp)
                }
            }
            Spacer(Modifier.height(15.dp))
            BrasaButton("Voltar", onBack, style = BrasaButtonStyle.Ghost)
        }
    }
}
