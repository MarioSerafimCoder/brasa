package com.brasa.tv.data.api

import com.brasa.tv.core.model.*
import com.brasa.tv.core.network.BrasaHttpClient
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URLEncoder

class BrasaApi(private val http:BrasaHttpClient,private val json:Json,private val capabilities:()->ClientCapabilities={ClientCapabilities()}){
    suspend fun bootstrap(base:String)=http.get(base,"/api/v1/bootstrap",ServerInfo.serializer(),false)
    suspend fun startPairing(base:String,name:String)=http.post(base,"/api/device-pairing/start",json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(),buildJsonObject{put("name",name);put("type","tv")}),PairingRequest.serializer(),false)
    suspend fun pairingStatus(base:String,id:String)=http.get(base,"/api/device-pairing/status/${enc(id)}",PairingStatus.serializer(),false)
    suspend fun profiles(base:String)=http.get(base,"/api/tv/profiles",ListSerializer(Profile.serializer()))
    suspend fun catalog(base:String,profileId:String)=http.get(base,"/api/tv/catalog?profileId=${enc(profileId)}",CatalogResponse.serializer())
    suspend fun home(base:String,profileId:String)=http.get(base,"/api/v1/tv/home?profileId=${enc(profileId)}",HomeResponse.serializer())
    suspend fun search(base:String,profileId:String,query:String)=http.get(base,"/api/v1/tv/search?profileId=${enc(profileId)}&q=${enc(query)}",ListSerializer(CatalogItem.serializer()))
    suspend fun playback(base:String,profileId:String,mediaKey:String,fallbackMode:String=""):PlaybackInfo{val value=capabilities();val supported=value.playback;val headers=mapOf("X-BRasa-Playback-Containers" to supported.containers.joinToString(","),"X-BRasa-Video-Codecs" to supported.videoCodecs.joinToString(","),"X-BRasa-Audio-Codecs" to supported.audioCodecs.joinToString(","),"X-BRasa-Hdr-Types" to supported.hdrTypes.joinToString(","),"X-BRasa-Video-Capabilities" to json.encodeToString(ListSerializer(com.brasa.tv.core.model.VideoCodecCapability.serializer()),supported.videoCapabilities),"X-BRasa-Max-Video-Width" to (supported.maxWidth.takeIf{it>0}?:value.screen.width).toString(),"X-BRasa-Max-Video-Height" to (supported.maxHeight.takeIf{it>0}?:value.screen.height).toString());val fallback=if(fallbackMode.isBlank())"" else "&fallback=${enc(fallbackMode)}";return http.get(base,"/api/v1/tv/playback/${enc(mediaKey)}?profileId=${enc(profileId)}$fallback",PlaybackInfo.serializer(),headers=headers)}
    suspend fun progress(base:String,profileId:String,mediaKey:String,value:WatchProgress)=http.put(base,"/api/tv/profiles/${enc(profileId)}/progress/${enc(mediaKey)}",json.encodeToString(WatchProgress.serializer(),value),WatchProgress.serializer())
    suspend fun favorite(base:String,profileId:String,mediaKey:String,enabled:Boolean)=if(enabled)http.put(base,"/api/tv/profiles/${enc(profileId)}/favorites/${enc(mediaKey)}","{}",FavoriteResult.serializer())else http.delete(base,"/api/tv/profiles/${enc(profileId)}/favorites/${enc(mediaKey)}")
    suspend fun verifyPin(base:String,profileId:String,pin:String)=http.post(base,"/api/tv/profiles/${enc(profileId)}/verify-pin",json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(),buildJsonObject{put("pin",pin)}),PinResult.serializer())
    suspend fun networkStatus(base:String)=http.get(base,"/api/v1/network/status",NetworkStatusResponse.serializer())
    suspend fun startNetworkTest(base:String,profile:String,durationSeconds:Int=60)=http.post(base,"/api/v1/network/test",json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(),buildJsonObject{put("profile",profile);put("durationSeconds",durationSeconds)}),NetworkTestSession.serializer())
    suspend fun networkTestStatus(base:String,id:String)=http.get(base,"/api/v1/network/test/${enc(id)}",NetworkTestSession.serializer())
    suspend fun cancelNetworkTest(base:String,id:String)=http.post(base,"/api/v1/network/test/${enc(id)}/cancel","{}",NetworkTestSession.serializer())
    suspend fun measureNetworkTest(base:String,path:String,onProgress:(Long,Long)->Unit)=http.measureDownload(base,path,onProgress)
    private fun enc(value:String)=URLEncoder.encode(value,Charsets.UTF_8.name())
}
@kotlinx.serialization.Serializable data class FavoriteResult(val favorite:Boolean=false)
@kotlinx.serialization.Serializable data class PinResult(val valid:Boolean=false)
