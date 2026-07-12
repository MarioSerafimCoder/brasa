package com.brasa.tv.feature.pairing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.brasa.tv.app.BrasaUiState
import com.brasa.tv.designsystem.*

@Composable fun PairingScreen(state:BrasaUiState,onStart:(String)->Unit,onBack:()->Unit){BackHandler(onBack=onBack);var name by remember{mutableStateOf("BRasa Android TV")};val pairing=state.pairing;Column(Modifier.fillMaxSize().padding(64.dp),verticalArrangement=Arrangement.Center){BrasaLogo();Spacer(Modifier.height(20.dp));Text("Autorize esta TV no computador",fontSize=40.sp);if(pairing==null){Text("Dê um nome para identificar o aparelho no painel.",color=Color.LightGray,fontSize=20.sp);Spacer(Modifier.height(20.dp));BasicTextField(name,{name=it},singleLine=true,textStyle=androidx.compose.ui.text.TextStyle(Color.White,fontSize=20.sp),modifier=Modifier.width(520.dp).padding(18.dp));Spacer(Modifier.height(16.dp));BrasaButton("Gerar código",{onStart(name)},enabled=name.isNotBlank())}else{Text(pairing.code,fontSize=68.sp,color=BrasaOrange);Text("Aguardando aprovação…",fontSize=22.sp);Text("O código expira em poucos minutos.",color=Color.LightGray);Spacer(Modifier.height(20.dp));BrasaButton("Gerar outro código",{onStart(name)})};if(state.message.isNotBlank())Text(state.message,color=BrasaRed);Spacer(Modifier.height(18.dp));BrasaButton("Voltar",onBack)}}
