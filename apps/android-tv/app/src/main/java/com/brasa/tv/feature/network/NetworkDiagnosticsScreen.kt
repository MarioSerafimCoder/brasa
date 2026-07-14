package com.brasa.tv.feature.network

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.brasa.tv.designsystem.*
import java.util.Locale

@Composable fun NetworkDiagnosticsScreen(state:NetworkDiagnosticsState,onLoad:()->Unit,onProfile:(String)->Unit,onRun:()->Unit,onCancel:()->Unit,onBack:()->Unit){
    BackHandler{if(state.running)onCancel()else onBack()};androidx.compose.runtime.LaunchedEffect(Unit){onLoad()}
    AmbientBackground{Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=BrasaSpacing.safe)){
        BrasaTopBar(Modifier.padding(top=BrasaSpacing.x2),active="Diagnóstico",onHome=onBack)
        Spacer(Modifier.height(BrasaSpacing.x3));Text("Diagnóstico de rede",color=Color.White,fontSize=BrasaType.page,fontWeight=FontWeight.ExtraBold);Text("Mede a conexão entre esta TV e o computador, sem carregar um filme.",color=BrasaTextMuted,fontSize=BrasaType.body)
        Spacer(Modifier.height(BrasaSpacing.x3));Row(horizontalArrangement=Arrangement.spacedBy(BrasaSpacing.x2)){
            InfoCard("Servidor",state.server?.serverUrl?.ifBlank{state.server?.message}.orEmpty().ifBlank{"Indisponível"});InfoCard("Conexão da TV",connectionLabel(state.local));InfoCard("Interface do computador",state.server?.connectionLabel?:"Não identificada")
        }
        Spacer(Modifier.height(BrasaSpacing.x2));Panel("Teste de convivência"){
            Text("Escolha um perfil e, durante os 60 segundos, use celulares e outros aparelhos normalmente. O servidor limita o tráfego ao bitrate exato do perfil.",color=BrasaTextMuted,fontSize=BrasaType.metadata)
            Spacer(Modifier.height(12.dp));Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){ProfileButton("1080p · 12 Mbps","1080p",state,onProfile);ProfileButton("4K equilibrado · 25 Mbps","4k-balanced",state,onProfile);ProfileButton("4K alto · 40 Mbps","4k-high",state,onProfile)}
            Spacer(Modifier.height(12.dp));if(state.running){Text("Teste em andamento: ${state.elapsedSeconds}s de 60s · ${(state.progress*100).toInt()}%",color=BrasaOrange,fontWeight=FontWeight.Bold);Spacer(Modifier.height(8.dp));BrasaButton("Cancelar teste",onCancel)}else BrasaButton(if(state.loading)"Consultando servidor…" else "Iniciar teste de 60 segundos",onRun,enabled=state.serverAvailable&&!state.loading,style=BrasaButtonStyle.Primary)
        }
        state.result?.let{Spacer(Modifier.height(BrasaSpacing.x2));Panel("Resultado · ${it.level}"){Row(horizontalArrangement=Arrangement.spacedBy(BrasaSpacing.x2)){InfoCard("Velocidade média","${one(it.averageMbps)} Mbps");InfoCard("Latência média","${it.latencyMs} ms");InfoCard("Falhas","${it.failures}");InfoCard("Oscilação","${one(it.oscillationMbps)} Mbps")};Spacer(Modifier.height(10.dp));Text(it.conclusion,color=if(it.level in listOf("Excelente","Boa"))BrasaSuccess else BrasaOrange,fontWeight=FontWeight.Bold);Text("Bitrate máximo recomendado: ${it.recommendedMaxMbps} Mbps",color=BrasaText)}}
        if(state.message.isNotBlank()){Spacer(Modifier.height(BrasaSpacing.x2));Text(state.message,color=if(state.serverAvailable)BrasaText else BrasaRed,fontWeight=FontWeight.SemiBold)};Spacer(Modifier.height(BrasaSpacing.x8))
    }}
}
@Composable private fun Panel(title:String,content:@Composable ColumnScope.()->Unit){Column(Modifier.fillMaxWidth().background(BrasaSurface.copy(alpha=.92f),RoundedCornerShape(16.dp)).border(1.dp,BrasaBorder,RoundedCornerShape(16.dp)).padding(BrasaSpacing.x3)){Text(title,color=BrasaText,fontSize=BrasaType.section,fontWeight=FontWeight.Bold);Spacer(Modifier.height(10.dp));content()}}
@Composable private fun InfoCard(label:String,value:String){Column(Modifier.widthIn(min=210.dp).background(BrasaSurfaceElevated,RoundedCornerShape(12.dp)).padding(14.dp)){Text(label,color=BrasaTextMuted,fontSize=BrasaType.metadata);Text(value,color=BrasaText,fontSize=BrasaType.body,fontWeight=FontWeight.Bold)}}
@Composable private fun ProfileButton(label:String,id:String,state:NetworkDiagnosticsState,onProfile:(String)->Unit){BrasaButton(label,{onProfile(id)},style=if(state.selectedProfile==id)BrasaButtonStyle.Primary else BrasaButtonStyle.Secondary,enabled=!state.running)}
private fun connectionLabel(details:com.brasa.tv.core.network.LocalConnectionDetails)=when(details.type){"ethernet"->"Ethernet";"wifi"->"Wi-Fi · ${details.frequencyBand}";"mobile"->"Rede móvel";else->"Não identificada"}
private fun one(value:Double)=String.format(Locale.US,"%.1f",value)
