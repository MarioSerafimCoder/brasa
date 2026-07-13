package com.brasa.tv.feature.profiles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.brasa.tv.app.BrasaUiState
import com.brasa.tv.designsystem.AmbientBackground
import com.brasa.tv.designsystem.BrasaBorder
import com.brasa.tv.designsystem.BrasaButton
import com.brasa.tv.designsystem.BrasaButtonStyle
import com.brasa.tv.designsystem.BrasaFocus
import com.brasa.tv.designsystem.BrasaLogo
import com.brasa.tv.designsystem.BrasaOrange
import com.brasa.tv.designsystem.BrasaRed
import com.brasa.tv.designsystem.BrasaSpacing
import com.brasa.tv.designsystem.BrasaSurfaceElevated
import com.brasa.tv.designsystem.BrasaTextMuted
import com.brasa.tv.designsystem.BrasaType
import kotlinx.coroutines.delay

@Composable
fun ProfilePinScreen(state: BrasaUiState, onVerify: (String) -> Unit, onBack: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    val inputFocus = remember { FocusRequester() }
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { inputFocus.requestFocus() }
    LaunchedEffect(pin) { if (pin.length == 4 && !state.loading) onVerify(pin) }
    LaunchedEffect(state.message) { if (state.message.isNotBlank()) { delay(450); pin = ""; inputFocus.requestFocus() } }
    AmbientBackground {
        Column(Modifier.fillMaxSize().padding(horizontal = BrasaSpacing.safe), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            BrasaLogo()
            Spacer(Modifier.height(BrasaSpacing.x4))
            Text("PIN de ${state.profile?.name.orEmpty()}", color = Color.White, fontSize = BrasaType.page, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(BrasaSpacing.x1))
            Text("Digite os quatro números do perfil.", color = BrasaTextMuted, fontSize = BrasaType.body)
            Spacer(Modifier.height(BrasaSpacing.x4))
            Box(contentAlignment = Alignment.Center) {
                BasicTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(4) },
                    modifier = Modifier.size(1.dp).focusRequester(inputFocus),
                    textStyle = TextStyle(color = Color.Transparent),
                    cursorBrush = SolidColor(Color.Transparent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(BrasaSpacing.x2)) {
                    repeat(4) { index ->
                        Box(
                            Modifier.size(76.dp).background(BrasaSurfaceElevated, RoundedCornerShape(16.dp)).border(3.dp, when { state.message.isNotBlank() -> BrasaRed; index == pin.length -> BrasaFocus; else -> BrasaBorder }, RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (index < pin.length) "•" else "", color = BrasaOrange, fontSize = BrasaType.page, fontWeight = FontWeight.Black) }
                    }
                }
            }
            Spacer(Modifier.height(BrasaSpacing.x3))
            if (state.message.isNotBlank()) Text(state.message, color = BrasaRed, fontSize = BrasaType.metadata, fontWeight = FontWeight.Bold)
            else Text(if (state.loading) "Verificando…" else "A entrada será confirmada automaticamente.", color = BrasaTextMuted, fontSize = BrasaType.metadata)
            Spacer(Modifier.height(BrasaSpacing.x3))
            BrasaButton("Voltar", onBack, style = BrasaButtonStyle.Ghost)
        }
    }
}
