package com.brasa.tv.feature.server

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.brasa.tv.app.BrasaUiState
import com.brasa.tv.core.di.AppContainer
import com.brasa.tv.designsystem.*

@Composable fun ServerScreen(state:BrasaUiState,container:AppContainer,onConnect:(String)->Unit){
    val discovery=remember(container.discovery){container.discovery.discover()};val servers by discovery.collectAsState(initial=emptyList());var address by remember{mutableStateOf("")}
    Column(Modifier.fillMaxSize().padding(64.dp),verticalArrangement=Arrangement.Center){
        BrasaLogo();Spacer(Modifier.height(20.dp));Text("Encontrar meu BRasa",fontSize=42.sp)
        Text("Selecione um servidor encontrado ou informe o endereço do computador.",fontSize=20.sp,color=Color.LightGray);Spacer(Modifier.height(28.dp))
        servers.forEach{BrasaButton("${it.name} — ${it.address}",{onConnect(it.address)},Modifier.width(520.dp));Spacer(Modifier.height(10.dp))}
        BasicTextField(value=address,onValueChange={address=it},singleLine=true,textStyle=androidx.compose.ui.text.TextStyle(Color.White,fontSize=20.sp),modifier=Modifier.width(520.dp).background(BrasaSurface).padding(18.dp));Spacer(Modifier.height(14.dp))
        BrasaButton(if(state.loading)"Conectando…" else "Conectar",{onConnect(address)},enabled=!state.loading&&address.isNotBlank())
        if(state.message.isNotBlank()){Spacer(Modifier.height(14.dp));Text(state.message,color=BrasaRed)}
    }
}
