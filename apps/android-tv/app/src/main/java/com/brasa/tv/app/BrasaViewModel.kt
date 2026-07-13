package com.brasa.tv.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.brasa.tv.core.model.*
import com.brasa.tv.data.repository.BrasaRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BrasaUiState(val loading:Boolean=false,val message:String="",val server:ServerInfo?=null,val pairing:PairingStatus?=null,val profiles:List<Profile> = emptyList(),val profile:Profile?=null,val home:HomeResponse?=null,val searchResults:List<CatalogItem> = emptyList(),val selected:CatalogItem?=null,val playback:PlaybackInfo?=null,val paired:Boolean=false)
class BrasaViewModel(private val repository:BrasaRepository):ViewModel(){private val mutable=MutableStateFlow(BrasaUiState());val state:StateFlow<BrasaUiState> = mutable.asStateFlow();private var pairingJob:Job?=null;private var searchJob:Job?=null
    fun restore(onReady:(Boolean)->Unit)=launch{val info=repository.restore();mutable.value=mutable.value.copy(server=info,paired=info!=null);onReady(info!=null)}
    fun connect(address:String,onReady:(Boolean)->Unit)=launch{val info=repository.connect(address);val paired=repository.isPaired();mutable.value=mutable.value.copy(server=info,paired=paired);onReady(paired)}
    fun startPairing(deviceName:String,onApproved:()->Unit){pairingJob?.cancel();pairingJob=viewModelScope.launch{setLoading(true);runCatching{repository.pair(deviceName){status->mutable.value=mutable.value.copy(pairing=status,loading=false)}}.onSuccess{mutable.value=mutable.value.copy(paired=true,loading=false);onApproved()}.onFailure(::error)}}
    fun stopPairing(){pairingJob?.cancel();pairingJob=null}
    fun loadProfiles(onLoaded:(List<Profile>)->Unit)=launch{val profiles=repository.profiles();mutable.value=mutable.value.copy(profiles=profiles);onLoaded(profiles)}
    fun prepareProfile(profile:Profile){mutable.value=mutable.value.copy(profile=profile,message="")}
    fun chooseProfile(profile:Profile,onReady:()->Unit)=launch{repository.selectProfile(profile.id);var opened=false;repository.cachedHome(profile.id)?.let{mutable.value=mutable.value.copy(profile=profile,home=it);onReady();opened=true};val home=repository.home(profile.id);mutable.value=mutable.value.copy(profile=profile,home=home);if(!opened)onReady()}
    fun refreshHome()=launch{val profile=mutable.value.profile?:return@launch;mutable.value=mutable.value.copy(home=repository.home(profile.id))}
    fun select(item:CatalogItem){mutable.value=mutable.value.copy(selected=item)}
    fun search(query:String){searchJob?.cancel();searchJob=viewModelScope.launch{kotlinx.coroutines.delay(150);val profile=mutable.value.profile?:return@launch;runCatching{repository.search(profile.id,query)}.onSuccess{mutable.value=mutable.value.copy(searchResults=it)}.onFailure(::error)}}
    fun toggleFavorite()=launch{val profile=mutable.value.profile?:return@launch;val item=mutable.value.selected?:return@launch;val next=!item.favorite;mutable.value=mutable.value.copy(selected=item.copy(favorite=next));runCatching{repository.favorite(profile.id,item.mediaKey,next)}.onFailure{mutable.value=mutable.value.copy(selected=item);error(it)};refreshHome()}
    fun loadPlayback(item:CatalogItem,onReady:()->Unit)=launch{val profile=mutable.value.profile?:return@launch;mutable.value=mutable.value.copy(playback=repository.playback(profile.id,item.mediaKey));onReady()}
    fun prefetchPlayback(item:CatalogItem){if(item.type=="series")return;val profile=mutable.value.profile?:return;viewModelScope.launch{repository.prefetchPlayback(profile.id,item.mediaKey)}}
    fun saveProgress(progress:WatchProgress)=viewModelScope.launch{val profile=mutable.value.profile?:return@launch;val item=mutable.value.selected?:return@launch;runCatching{repository.saveProgress(profile.id,item.mediaKey,progress)}}
    fun verifyPin(pin:String,onResult:(Boolean)->Unit)=launch{val profile=mutable.value.profile?:return@launch;onResult(repository.verifyPin(profile.id,pin))}
    fun forget(onDone:()->Unit)=launch{repository.forget();mutable.value=BrasaUiState();onDone()}
    private fun launch(block:suspend()->Unit)=viewModelScope.launch{setLoading(true);runCatching{block()}.onFailure(::error);setLoading(false)}
    private fun setLoading(value:Boolean){mutable.value=mutable.value.copy(loading=value,message=if(value)"" else mutable.value.message)}
    private fun error(value:Throwable){mutable.value=mutable.value.copy(loading=false,message=value.message?:"Não foi possível conectar ao BRasa.")}
    class Factory(private val repository:BrasaRepository):ViewModelProvider.Factory{override fun <T:ViewModel> create(modelClass:Class<T>):T{@Suppress("UNCHECKED_CAST") return BrasaViewModel(repository)as T}}
}
