package com.brasa.tv.feature.update
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.brasa.tv.core.network.BrasaHttpClient
import com.brasa.tv.data.storage.AppSettingsStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
class UpdateViewModel(private val repository:UpdateRepository,private val settings:AppSettingsStore,private val preferences:UpdatePreferences,private val http:BrasaHttpClient,private val context:Context,private val validator:ApkValidator):ViewModel(){private val mutable=MutableStateFlow<UpdateUiState>(UpdateUiState.Idle);val state:StateFlow<UpdateUiState> = mutable.asStateFlow();private var job:Job?=null;private var downloader:ApkDownloader?=null
fun check(force:Boolean){if(job?.isActive==true)return;job=viewModelScope.launch{mutable.value=UpdateUiState.Checking;runCatching{repository.check(force)}.onSuccess{result->mutable.value=if(result.available&&result.update!=null)UpdateUiState.Available(result.update)else UpdateUiState.UpToDate}.onFailure{mutable.value=UpdateUiState.Error(it.message?:"Servidor indisponível.")}}}
fun download(update:UpdateManifest){if(job?.isActive==true)return;job=viewModelScope.launch{runCatching{val base=settings.values.first().serverBaseUrl;val active=ApkDownloader(http.authenticatedClient(),context.cacheDir,base);downloader=active;val file=active.download(update){percent->mutable.value=UpdateUiState.Downloading(update,percent)};mutable.value=UpdateUiState.Validating(update);val result=validator.validate(file,update);if(!result.valid){file.delete();error(result.message)};mutable.value=UpdateUiState.Ready(update,file.absolutePath)}.onFailure{mutable.value=UpdateUiState.Error(it.message?:"Não foi possível baixar a atualização.",update)};downloader=null}}
fun cancel(){downloader?.cancel();job?.cancel();val update=when(val current=mutable.value){is UpdateUiState.Downloading->current.update;is UpdateUiState.Validating->current.update;else->null};mutable.value=update?.let(UpdateUiState::Available)?:UpdateUiState.Idle}
fun defer(){val update=when(val current=mutable.value){is UpdateUiState.Available->current.update;is UpdateUiState.Ready->current.update;is UpdateUiState.Error->current.update;else->null};update?.let{repository.defer(it.versionCode)};mutable.value=UpdateUiState.Idle}
fun lastCheckAt()=repository.lastCheckAt()
class Factory(private val repository:UpdateRepository,private val settings:AppSettingsStore,private val preferences:UpdatePreferences,private val http:BrasaHttpClient,private val context:Context,private val validator:ApkValidator):ViewModelProvider.Factory{override fun<T:ViewModel>create(modelClass:Class<T>):T{@Suppress("UNCHECKED_CAST")return UpdateViewModel(repository,settings,preferences,http,context,validator)as T}}
}
