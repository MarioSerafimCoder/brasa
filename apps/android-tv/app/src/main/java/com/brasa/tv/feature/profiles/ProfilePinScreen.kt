package com.brasa.tv.feature.profiles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.brasa.tv.app.BrasaUiState
import com.brasa.tv.designsystem.*

@Composable fun ProfilePinScreen(state:BrasaUiState,onVerify:(String)->Unit,onBack:()->Unit){
    var pin by remember{mutableStateOf("")};BackHandler(onBack=onBack)
    Column(Modifier.fillMaxSize().padding(64.dp),verticalArrangement=Arrangement.Center){
        BrasaLogo();Text("PIN de ${state.profile?.name.orEmpty()}",fontSize=40.sp);Spacer(Modifier.height(12.dp));Text("Digite o PIN deste perfil.",fontSize=20.sp,color=Color.LightGray);Spacer(Modifier.height(24.dp))
        BasicTextField(pin,{value->pin=value.filter(Char::isDigit).take(8)},singleLine=true,visualTransformation=PasswordVisualTransformation(),textStyle=androidx.compose.ui.text.TextStyle(Color.White,fontSize=28.sp),modifier=Modifier.width(320.dp).background(BrasaSurface).padding(18.dp))
        Spacer(Modifier.height(16.dp));Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){BrasaButton("Entrar",{onVerify(pin)},enabled=pin.isNotBlank()&&!state.loading);BrasaButton("Voltar",onBack)}
        if(state.message.isNotBlank())Text(state.message,color=BrasaRed)
    }
}
