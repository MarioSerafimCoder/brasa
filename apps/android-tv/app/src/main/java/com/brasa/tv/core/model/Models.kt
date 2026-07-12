package com.brasa.tv.core.model

import kotlinx.serialization.Serializable

@Serializable data class ApiEnvelope<T>(val ok:Boolean=false,val data:T?=null,val message:String?=null,val code:String?=null)
@Serializable data class ServerInfo(val name:String="BRasa",val apiVersion:Int=1,val serverVersion:String="",val lanEnabled:Boolean=false,val pairingRequired:Boolean=true,val capabilities:ServerCapabilities=ServerCapabilities())
@Serializable data class ServerCapabilities(val pairing:Boolean=true,val profiles:Boolean=true,val catalog:Boolean=true,val homeRows:Boolean=false,val search:Boolean=false,val progressivePlayback:Boolean=true,val rangeRequests:Boolean=true,val subtitles:Boolean=true,val audioTracks:Boolean=false)
@Serializable data class DeviceSession(val deviceId:String="",val deviceToken:String="")
@Serializable data class PairingRequest(val requestId:String="",val code:String="",val name:String="",val type:String="tv",val status:String="pending",val expiresAt:String="",val remainingMs:Long=0)
@Serializable data class PairingStatus(val requestId:String="",val code:String="",val status:String="pending",val expiresAt:String="",val remainingMs:Long=0,val token:String?=null,val device:AuthorizedDevice?=null)
@Serializable data class AuthorizedDevice(val id:String="",val name:String="",val type:String="tv",val revoked:Boolean=false,val allowedProfileIds:List<String> = emptyList())
@Serializable data class Profile(val id:String="",val name:String="",val initials:String="",val kind:String="adult",val maxContentRating:Int?=null,val hasPin:Boolean=false,val avatar:Avatar?=null)
@Serializable data class Avatar(val type:String="initials",val value:String="",val color:String="orange")
@Serializable data class CatalogResponse(val profile:Profile=Profile(),val movies:List<CatalogItem> = emptyList(),val series:List<CatalogItem> = emptyList(),val collections:List<Collection> = emptyList(),val favorites:List<String> = emptyList(),val progress:Map<String,WatchProgress> = emptyMap())
@Serializable data class HomeResponse(val profile:Profile=Profile(),val rows:List<HomeRow> = emptyList())
@Serializable data class HomeRow(val id:String="",val title:String="",val type:String="catalog",val items:List<CatalogItem> = emptyList())
@Serializable data class CatalogItem(val id:String="",val mediaKey:String="",val type:String="movie",val title:String="",val originalTitle:String="",val year:Int?=null,val duration:String="",val rating:Double?=null,val contentRating:String="",val genres:List<String> = emptyList(),val overview:String="",val poster:String="",val backdrop:String="",val favorite:Boolean=false,val progress:WatchProgress?=null,val streamUrl:String="",val seriesId:String="",val seasonNumber:Int?=null,val episodeNumber:Int?=null,val seasons:List<Season> = emptyList(),val subtitles:List<SubtitleTrack> = emptyList())
typealias Movie = CatalogItem
typealias Series = CatalogItem
@Serializable data class Season(val seasonNumber:Int=0,val episodes:List<CatalogItem> = emptyList())
typealias Episode = CatalogItem
@Serializable data class Collection(val id:String="",val title:String="",val subtitle:String="",val banner:String="",val items:List<CatalogItem> = emptyList())
@Serializable data class PlaybackInfo(val mediaId:String="",val mediaKey:String="",val playbackUrl:String="",val mimeType:String="video/*",val container:String="",val videoCodec:String="",val audioCodec:String="",val supportsRange:Boolean=true,val duration:Long?=null,val resumePosition:Long=0,val subtitles:List<SubtitleTrack> = emptyList(),val audioTracks:List<AudioTrack> = emptyList(),val nextEpisode:CatalogItem?=null,val preparationStatus:String="ready")
@Serializable data class SubtitleTrack(val label:String="",val srclang:String="",val src:String="",val mimeType:String="text/vtt",val default:Boolean=false)
@Serializable data class AudioTrack(val id:String="",val label:String="",val language:String="",val codec:String="")
@Serializable data class WatchProgress(val mediaType:String="movie",val mediaId:String="",val seriesId:String="",val currentTime:Double=0.0,val duration:Double=0.0,val percentage:Double=0.0,val completed:Boolean=false,val updatedAt:String="")
@Serializable data class ApiError(val code:String="API_ERROR",val message:String="Não foi possível concluir a operação.",val status:Int=0)
@Serializable data class ClientCapabilities(val client:String="brasa-android-tv",val clientVersion:String="1.0.0",val platform:String="android-tv",val manufacturer:String="",val model:String="",val screen:ScreenCapabilities=ScreenCapabilities(),val playback:PlaybackCapabilities=PlaybackCapabilities())
@Serializable data class ScreenCapabilities(val width:Int=0,val height:Int=0)
@Serializable data class PlaybackCapabilities(val containers:List<String> = emptyList(),val videoCodecs:List<String> = emptyList(),val audioCodecs:List<String> = emptyList(),val subtitleFormats:List<String> = listOf("vtt"))
