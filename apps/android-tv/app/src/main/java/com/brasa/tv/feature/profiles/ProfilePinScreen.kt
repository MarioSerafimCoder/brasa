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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
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
    val firstKey = remember { FocusRequester() }
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { runCatching { firstKey.requestFocus() } }
    LaunchedEffect(pin) { if (pin.length == 4 && !state.loading) onVerify(pin) }
    LaunchedEffect(state.message) { if (state.message.isNotBlank()) { delay(450); pin = "";runCatching { firstKey.requestFocus() } } }
    fun addDigit(digit:String){if(!state.loading&&pin.length<4)pin+=digit}
    AmbientBackground {
        Column(Modifier.fillMaxSize().padding(horizontal = BrasaSpacing.safe), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            BrasaLogo()
            Spacer(Modifier.height(BrasaSpacing.x3))
            Text("PIN de ${state.profile?.name.orEmpty()}", color = Color.White, fontSize = BrasaType.page, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(BrasaSpacing.x1))
            Text("Use o controle remoto para informar os quatro números.", color = BrasaTextMuted, fontSize = BrasaType.body)
            Spacer(Modifier.height(BrasaSpacing.x3))
            Row(horizontalArrangement = Arrangement.spacedBy(BrasaSpacing.x2)) {
                repeat(4) { index -> Box(Modifier.size(62.dp).background(BrasaSurfaceElevated, RoundedCornerShape(14.dp)).border(2.dp, when { state.message.isNotBlank() -> BrasaRed; index == pin.length -> BrasaFocus; else -> BrasaBorder }, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Text(if (index < pin.length) "•" else "", color = BrasaOrange, fontSize = BrasaType.page, fontWeight = FontWeight.Black) } }
            }
            Spacer(Modifier.height(BrasaSpacing.x2))
            Column(verticalArrangement=Arrangement.spacedBy(BrasaSpacing.x1),horizontalAlignment=Alignment.CenterHorizontally){
                listOf(listOf("1","2","3"),listOf("4","5","6"),listOf("7","8","9")).forEachIndexed{rowIndex,row->Row(horizontalArrangement=Arrangement.spacedBy(BrasaSpacing.x1)){row.forEachIndexed{index,digit->BrasaButton(digit,{addDigit(digit)},Modifier.width(74.dp).then(if(rowIndex==0&&index==0)Modifier.focusRequester(firstKey) else Modifier),enabled=!state.loading)}}}
                Row(horizontalArrangement=Arrangement.spacedBy(BrasaSpacing.x1)){BrasaButton("Apagar",{pin=pin.dropLast(1)},Modifier.width(148.dp),enabled=pin.isNotEmpty()&&!state.loading);BrasaButton("0",{addDigit("0")},Modifier.width(74.dp),enabled=!state.loading)}
            }
            Spacer(Modifier.height(BrasaSpacing.x2))
            if (state.message.isNotBlank()) Text(state.message, color = BrasaRed, fontSize = BrasaType.metadata, fontWeight = FontWeight.Bold) else Text(if (state.loading) "Verificando…" else "A entrada será confirmada automaticamente.", color = BrasaTextMuted, fontSize = BrasaType.metadata)
            Spacer(Modifier.height(BrasaSpacing.x2))
            BrasaButton("Voltar", onBack, style = BrasaButtonStyle.Ghost)
        }
    }
}
