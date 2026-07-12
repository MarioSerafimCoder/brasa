package com.brasa.tv.data.api

import com.brasa.tv.core.model.*
import com.brasa.tv.core.network.BrasaHttpClient
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URLEncoder

class BrasaApi(private val http:BrasaHttpClient,private val json:Json){
    suspend fun bootstrap(base:String)=http.get(base,"/api/v1/bootstrap",ServerInfo.serializer(),false)
    suspend fun startPairing(base:String,name:String)=http.post(base,"/api/device-pairing/start",json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(),buildJsonObject{put("name",name);put("type","tv")}),PairingRequest.serializer(),false)
    suspend fun pairingStatus(base:String,id:String)=http.get(base,"/api/device-pairing/status/${enc(id)}",PairingStatus.serializer(),false)
    suspend fun profiles(base:String)=http.get(base,"/api/tv/profiles",ListSerializer(Profile.serializer()))
    suspend fun catalog(base:String,profileId:String)=http.get(base,"/api/tv/catalog?profileId=${enc(profileId)}",CatalogResponse.serializer())
    suspend fun home(base:String,profileId:String)=http.get(base,"/api/v1/tv/home?profileId=${enc(profileId)}",HomeResponse.serializer())
    suspend fun playback(base:String,profileId:String,mediaKey:String)=http.get(base,"/api/v1/tv/playback/${enc(mediaKey)}?profileId=${enc(profileId)}",PlaybackInfo.serializer())
    suspend fun progress(base:String,profileId:String,mediaKey:String,value:WatchProgress)=http.put(base,"/api/tv/profiles/${enc(profileId)}/progress/${enc(mediaKey)}",json.encodeToString(WatchProgress.serializer(),value),WatchProgress.serializer())
    suspend fun favorite(base:String,profileId:String,mediaKey:String,enabled:Boolean)=if(enabled)http.put(base,"/api/tv/profiles/${enc(profileId)}/favorites/${enc(mediaKey)}","{}",FavoriteResult.serializer())else http.delete(base,"/api/tv/profiles/${enc(profileId)}/favorites/${enc(mediaKey)}")
    suspend fun verifyPin(base:String,profileId:String,pin:String)=http.post(base,"/api/tv/profiles/${enc(profileId)}/verify-pin",json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(),buildJsonObject{put("pin",pin)}),PinResult.serializer())
    private fun enc(value:String)=URLEncoder.encode(value,Charsets.UTF_8.name())
}
@kotlinx.serialization.Serializable data class FavoriteResult(val favorite:Boolean=false)
@kotlinx.serialization.Serializable data class PinResult(val valid:Boolean=false)
