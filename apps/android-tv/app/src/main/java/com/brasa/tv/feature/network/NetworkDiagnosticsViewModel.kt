package com.brasa.tv.feature.network

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.brasa.tv.core.model.NetworkServerStatus
import com.brasa.tv.core.network.LocalConnectionDetails
import com.brasa.tv.core.network.LocalNetworkAccessController
import com.brasa.tv.data.repository.BrasaRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.floor

data class NetworkTestResult(val averageMbps:Double=0.0,val latencyMs:Long=0,val failures:Int=0,val oscillationMbps:Double=0.0,val recommendedMaxMbps:Int=0,val level:String="",val conclusion:String="")
data class NetworkDiagnosticsState(val loading:Boolean=false,val serverAvailable:Boolean=false,val server:NetworkServerStatus?=null,val local:LocalConnectionDetails=LocalConnectionDetails(),val selectedProfile:String="1080p",val running:Boolean=false,val progress:Float=0f,val elapsedSeconds:Int=0,val result:NetworkTestResult?=null,val message:String="")

class NetworkDiagnosticsViewModel(private val repository:BrasaRepository,private val localNetwork:LocalNetworkAccessController):ViewModel(){
    private val mutable=MutableStateFlow(NetworkDiagnosticsState());val state:StateFlow<NetworkDiagnosticsState> = mutable.asStateFlow();private var testJob:Job?=null;private var activeTestId:String=""
    fun load(){viewModelScope.launch{mutable.value=mutable.value.copy(loading=true,message="",local=localNetwork.connectionDetails());runCatching{repository.networkStatus()}.onSuccess{mutable.value=mutable.value.copy(loading=false,serverAvailable=it.server.available,server=it.server,message=if(it.server.available)"" else "Servidor indisponível para diagnóstico.")}.onFailure{mutable.value=mutable.value.copy(loading=false,serverAvailable=false,message="Servidor indisponível: ${it.message?:"verifique a conexão"}")}}}
    fun selectProfile(profile:String){if(!mutable.value.running)mutable.value=mutable.value.copy(selectedProfile=profile,result=null)}
    fun runTest(){if(testJob?.isActive==true)return;testJob=viewModelScope.launch{val profile=mutable.value.selectedProfile;mutable.value=mutable.value.copy(running=true,progress=0f,elapsedSeconds=0,result=null,message="Use outros aparelhos normalmente durante o teste.");runCatching{
        val latencies=mutableListOf<Long>();var pingFailures=0;repeat(4){val started=SystemClock.elapsedRealtime();runCatching{repository.networkStatus()}.onSuccess{latencies+=SystemClock.elapsedRealtime()-started}.onFailure{pingFailures++}}
        val session=repository.startNetworkTest(profile,60);activeTestId=session.id
        val transfer=repository.measureNetworkTest(session.streamPath){_,elapsed->mutable.value=mutable.value.copy(progress=(elapsed/60_000f).coerceIn(0f,1f),elapsedSeconds=(elapsed/1000).toInt())}
        val final=repository.networkTestStatus(session.id);activeTestId=""
        val average=if(transfer.elapsedMs>0)transfer.bytesRead*8.0/(transfer.elapsedMs*1000.0) else 0.0;val latency=if(latencies.isEmpty())999 else latencies.average().toLong();val failures=pingFailures+transfer.failures+final.interruptions;val oscillation=(transfer.samplesMbps.maxOrNull()?:average)-(transfer.samplesMbps.minOrNull()?:average)
        classify(profile,average,latency,failures,oscillation)
    }.onSuccess{mutable.value=mutable.value.copy(running=false,progress=1f,result=it,message=it.conclusion)}.onFailure{activeTestId="";mutable.value=mutable.value.copy(running=false,message=if(it is kotlinx.coroutines.CancellationException)"Teste cancelado." else "Teste interrompido: ${it.message?:"servidor indisponível"}")}}}
    fun cancel(){val id=activeTestId;testJob?.cancel();testJob=null;activeTestId="";mutable.value=mutable.value.copy(running=false,message="Teste cancelado.");if(id.isNotBlank())viewModelScope.launch{runCatching{repository.cancelNetworkTest(id)}}}
    override fun onCleared(){cancel();super.onCleared()}
    private fun classify(profile:String,average:Double,latency:Long,failures:Int,oscillation:Double):NetworkTestResult{val required=when(profile){"4k-balanced"->25;"4k-high"->40;else->12};val ratio=average/required;val level=when{failures>1||latency>120||ratio<.75->"Instável";profile!="1080p"&&ratio<1.15->"Limitada";latency<=20&&ratio>=1.5&&failures==0->"Excelente";else->"Boa"};val conclusion=when(level){"Instável"->"Rede congestionada";"Limitada"->"Rede adequada somente para 1080p";else->"Rede adequada"};return NetworkTestResult(average,latency,failures,oscillation,floor((average*.75).coerceAtLeast(8.0)).toInt(),level,conclusion)}
    class Factory(private val repository:BrasaRepository,private val localNetwork:LocalNetworkAccessController):ViewModelProvider.Factory{override fun <T:ViewModel> create(modelClass:Class<T>):T{@Suppress("UNCHECKED_CAST") return NetworkDiagnosticsViewModel(repository,localNetwork) as T}}
}
