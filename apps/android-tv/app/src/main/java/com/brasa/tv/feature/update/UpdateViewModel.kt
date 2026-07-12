package com.brasa.tv.feature.update
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.lifecycle.*
import com.brasa.tv.BuildConfig
import com.brasa.tv.core.network.BrasaHttpClient
import com.brasa.tv.data.storage.AppSettingsStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
class UpdateViewModel(private val repository:UpdateRepository,private val settings:AppSettingsStore,private val preferences:UpdatePreferences,private val http:BrasaHttpClient,private val context:Context,private val validator:ApkValidator,private val installer:InstallerGateway):ViewModel(){private val mutable=MutableStateFlow<UpdateUiState>(initialState());val state:StateFlow<UpdateUiState> = mutable.asStateFlow();private var job:Job?=null;private var downloader:ApkDownloader?=null
init{viewModelScope.launch{InstallResultBus.events.collect(::handleInstallResult)}}
fun check(force:Boolean){if(job?.isActive==true)return;job=viewModelScope.launch{mutable.value=UpdateUiState.Checking;runCatching{repository.check(force)}.onSuccess{result->mutable.value=if(result.available&&result.update!=null)UpdateUiState.Available(result.update)else UpdateUiState.UpToDate}.onFailure{mutable.value=UpdateUiState.Error(it.message?:"Servidor indisponível.")}}}
fun download(update:UpdateManifest){if(job?.isActive==true)return;job=viewModelScope.launch{runCatching{val base=settings.values.first().serverBaseUrl;val active=ApkDownloader(http.authenticatedClient(),context.cacheDir,base);downloader=active;val file=active.download(update){percent->mutable.value=UpdateUiState.Downloading(update,percent)};mutable.value=UpdateUiState.Validating(update);val result=validator.validate(file,update);if(!result.valid){file.delete();error(result.message)};mutable.value=UpdateUiState.Ready(update,file.absolutePath)}.onFailure{mutable.value=UpdateUiState.Error(it.message?:"Não foi possível baixar a atualização.",update)};downloader=null}}
fun cancel(){downloader?.cancel();job?.cancel();val update=when(val current=mutable.value){is UpdateUiState.Downloading->current.update;is UpdateUiState.Validating->current.update;else->null};mutable.value=update?.let(UpdateUiState::Available)?:UpdateUiState.Idle}
fun prepareInstall(){val ready=mutable.value as?UpdateUiState.Ready?:return;if(!installer.canRequestPackageInstalls()){mutable.value=UpdateUiState.PermissionRequired(ready.update,ready.apkPath);return};commit(ready)}
fun permissionIntent():Intent=installer.permissionIntent()
fun permissionReturned(){val required=mutable.value as?UpdateUiState.PermissionRequired?:return;mutable.value=if(installer.canRequestPackageInstalls())UpdateUiState.Ready(required.update,required.apkPath)else UpdateUiState.Error("Permissão de instalação não concedida.",required.update)}
private fun commit(ready:UpdateUiState.Ready){job=viewModelScope.launch{preferences.savePending(ready.update);mutable.value=UpdateUiState.Installing(ready.update);runCatching{installer.install(File(ready.apkPath),ready.update)}.onFailure{mutable.value=UpdateUiState.Error(it.message?:"O instalador não pôde ser iniciado.",ready.update)}}}
private fun handleInstallResult(result:InstallResult){if(result.status==PackageInstaller.STATUS_SUCCESS){val version=preferences.pending()?.second.orEmpty();preferences.clearPending();mutable.value=UpdateUiState.Installed(version)}else mutable.value=UpdateUiState.Error(InstallStatusMapper.message(result),currentUpdate())}
private fun currentUpdate()=when(val current=mutable.value){is UpdateUiState.Installing->current.update;is UpdateUiState.PermissionRequired->current.update;is UpdateUiState.Ready->current.update;else->null}
fun defer(){val update=when(val current=mutable.value){is UpdateUiState.Available->current.update;is UpdateUiState.Ready->current.update;is UpdateUiState.Error->current.update;else->null};update?.let{repository.defer(it.versionCode)};mutable.value=UpdateUiState.Idle}
fun lastCheckAt()=repository.lastCheckAt()
private fun initialState():UpdateUiState{val pending=preferences.pending();if(pending!=null&&BuildConfig.VERSION_CODE.toLong()>=pending.first){preferences.clearPending();return UpdateUiState.Installed(pending.second)};preferences.consumeInstallResult()?.let{return UpdateUiState.Error(InstallStatusMapper.message(it))};return UpdateUiState.Idle}
class Factory(private val repository:UpdateRepository,private val settings:AppSettingsStore,private val preferences:UpdatePreferences,private val http:BrasaHttpClient,private val context:Context,private val validator:ApkValidator,private val installer:InstallerGateway):ViewModelProvider.Factory{override fun<T:ViewModel>create(modelClass:Class<T>):T{@Suppress("UNCHECKED_CAST")return UpdateViewModel(repository,settings,preferences,http,context,validator,installer)as T}}
}
