package com.brasa.tv.data.repository

import com.brasa.tv.core.model.*
import com.brasa.tv.core.network.BrasaHttpClient
import com.brasa.tv.core.network.LocalServerAddress
import com.brasa.tv.core.security.SecureTokenStore
import com.brasa.tv.data.api.BrasaApi
import com.brasa.tv.data.storage.AppSettings
import com.brasa.tv.data.storage.AppSettingsStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

class BrasaRepository(private val api:BrasaApi,private val settings:AppSettingsStore,private val tokens:SecureTokenStore,private val http:BrasaHttpClient){
    suspend fun settings():AppSettings=settings.values.first()
    suspend fun connect(rawAddress:String):ServerInfo{val base=LocalServerAddress.normalize(rawAddress);val info=api.bootstrap(base);require(info.apiVersion==1){"Servidor incompatível."};settings.saveServer(base,info.name);http.bindServer(base);return info}
    suspend fun restore():ServerInfo?{val current=settings();if(current.serverBaseUrl.isBlank()||!tokens.hasToken())return null;http.bindServer(current.serverBaseUrl);return runCatching{val info=api.bootstrap(current.serverBaseUrl);api.profiles(current.serverBaseUrl);info}.getOrNull()}
    suspend fun pair(deviceName:String,onStatus:suspend(PairingStatus)->Unit):DeviceSession{val base=requireServer();val request=api.startPairing(base,deviceName);onStatus(PairingStatus(requestId=request.requestId,code=request.code,status="pending",expiresAt=request.expiresAt,remainingMs=request.remainingMs));while(true){delay(2_000);val status=api.pairingStatus(base,request.requestId);onStatus(status.copy(code=request.code));when(status.status){"approved"->{val token=status.token?:error("O token de pareamento não foi entregue.");val session=DeviceSession(status.device?.id.orEmpty(),token);tokens.save(session);http.bindServer(base);return session};"rejected"->error("Pareamento recusado no computador.");"expired"->error("O código expirou. Gere outro.")}}}
    fun isPaired()=tokens.hasToken()
    suspend fun profiles()=api.profiles(requireServer())
    suspend fun selectProfile(id:String){settings.saveProfile(id)}
    suspend fun home(profileId:String):HomeResponse{val base=requireServer();val response=runCatching{api.home(base,profileId)}.getOrElse{catalogAsHome(api.catalog(base,profileId))};return response.copy(rows=response.rows.map{row->row.copy(items=row.items.map{it.withArtwork(base)})})}
    suspend fun catalog(profileId:String):CatalogResponse{val base=requireServer();val value=api.catalog(base,profileId);return value.copy(movies=value.movies.map{it.withArtwork(base)},series=value.series.map{it.withArtwork(base)})}
    suspend fun playback(profileId:String,key:String)=api.playback(requireServer(),profileId,key)
    suspend fun saveProgress(profileId:String,key:String,value:WatchProgress)=api.progress(requireServer(),profileId,key,value)
    suspend fun favorite(profileId:String,key:String,enabled:Boolean)=api.favorite(requireServer(),profileId,key,enabled)
    suspend fun verifyPin(profileId:String,pin:String)=api.verifyPin(requireServer(),profileId,pin).valid
    suspend fun forget(){tokens.clear();settings.forgetServer()}
    suspend fun search(profileId:String,query:String):List<CatalogItem>{val value=query.trim().lowercase();if(value.isBlank())return emptyList();val catalog=catalog(profileId);return(catalog.movies+catalog.series+catalog.series.flatMap{it.seasons.flatMap{season->season.episodes}}).filter{listOf(it.title,it.originalTitle,it.overview,it.genres.joinToString()).any{text->text.lowercase().contains(value)}}}
    private suspend fun requireServer()=settings().serverBaseUrl.ifBlank{error("Nenhum servidor configurado.")}
    private fun CatalogItem.withArtwork(base:String):CatalogItem=copy(poster=poster.toLocalUrl(base),backdrop=backdrop.toLocalUrl(base),seasons=seasons.map{season->season.copy(episodes=season.episodes.map{it.withArtwork(base)})})
    private fun String.toLocalUrl(base:String)=if(isBlank())"" else LocalServerAddress.resolve(base,this)
    private fun catalogAsHome(catalog:CatalogResponse):HomeResponse{val allEpisodes=catalog.series.flatMap{it.seasons.flatMap(Season::episodes)};val continuing=(catalog.movies+allEpisodes).filter{(it.progress?.percentage?:0.0)in 0.1..94.9};val favorites=catalog.movies.filter(CatalogItem::favorite);return HomeResponse(catalog.profile,listOf(HomeRow("continue","Continuar assistindo",items=continuing),HomeRow("movies","Filmes",items=catalog.movies),HomeRow("series","Séries",items=catalog.series),HomeRow("favorites","Minha lista",items=favorites)).filter{it.items.isNotEmpty()})}
}
