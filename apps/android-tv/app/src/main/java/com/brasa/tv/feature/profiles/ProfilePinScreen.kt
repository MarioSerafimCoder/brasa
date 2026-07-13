package com.brasa.tv.feature.profiles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.brasa.tv.app.BrasaUiState
import com.brasa.tv.designsystem.AmbientBackground
import com.brasa.tv.designsystem.BrasaButton
import com.brasa.tv.designsystem.BrasaButtonStyle
import com.brasa.tv.designsystem.BrasaLogo
import com.brasa.tv.designsystem.BrasaRed
import com.brasa.tv.designsystem.BrasaTextField
import com.brasa.tv.designsystem.BrasaTextMuted

@Composable
fun ProfilePinScreen(state: BrasaUiState, onVerify: (String) -> Unit, onBack: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    BackHandler(onBack = onBack)
    AmbientBackground {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BrasaLogo()
            Spacer(Modifier.height(27.dp))
            Text("PIN de ${state.profile?.name.orEmpty()}", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            Text("Digite o PIN deste perfil para continuar.", color = BrasaTextMuted, fontSize = 18.sp)
            Spacer(Modifier.height(23.dp))
            BrasaTextField(
                value = pin,
                onValueChange = { value -> pin = value.filter(Char::isDigit).take(8) },
                modifier = Modifier.width(340.dp),
                placeholder = "••••",
                visualTransformation = PasswordVisualTransformation(),
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BrasaButton("Entrar", { onVerify(pin) }, enabled = pin.isNotBlank() && !state.loading, style = BrasaButtonStyle.Primary)
                BrasaButton("Voltar", onBack)
            }
            if (state.message.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(state.message, color = BrasaRed, fontSize = 15.sp)
            }
        }
    }
}
