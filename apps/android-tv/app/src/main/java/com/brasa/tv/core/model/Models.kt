package com.brasa.tv.core.model

import com.brasa.tv.BuildConfig
import kotlinx.serialization.Serializable

@Serializable data class ApiEnvelope<T>(val ok:Boolean=false,val data:T?=null,val message:String?=null,val code:String?=null)
@Serializable data class ServerInfo(val name:String="BRasa",@Serializable(with=FlexibleIntSerializer::class) val apiVersion:Int=1,val serverVersion:String="",val lanEnabled:Boolean=false,val pairingRequired:Boolean=true,val capabilities:ServerCapabilities=ServerCapabilities())
@Serializable data class ServerCapabilities(val pairing:Boolean=true,val profiles:Boolean=true,val catalog:Boolean=true,val homeRows:Boolean=false,val search:Boolean=false,val progressivePlayback:Boolean=true,val adaptiveHls:Boolean=false,val rangeRequests:Boolean=true,val subtitles:Boolean=true,val audioTracks:Boolean=false)
@Serializable data class DeviceSession(val deviceId:String="",val deviceToken:String="")
@Serializable data class PairingRequest(val requestId:String="",val code:String="",val name:String="",val type:String="tv",val status:String="pending",val expiresAt:String="",@Serializable(with=FlexibleLongSerializer::class) val remainingMs:Long=0)
@Serializable data class PairingStatus(val requestId:String="",val code:String="",val status:String="pending",val expiresAt:String="",@Serializable(with=FlexibleLongSerializer::class) val remainingMs:Long=0,val token:String?=null,val device:AuthorizedDevice?=null)
@Serializable data class AuthorizedDevice(val id:String="",val name:String="",val type:String="tv",val revoked:Boolean=false,val allowedProfileIds:List<String> = emptyList())
@Serializable data class Profile(val id:String="",val name:String="",val initials:String="",val kind:String="adult",@Serializable(with=FlexibleNullableIntSerializer::class) val maxContentRating:Int?=null,val hasPin:Boolean=false,val avatar:Avatar?=null)
@Serializable data class Avatar(val type:String="initials",val value:String="",val color:String="orange")
@Serializable data class CatalogResponse(val profile:Profile=Profile(),val movies:List<CatalogItem> = emptyList(),val series:List<CatalogItem> = emptyList(),val collections:List<Collection> = emptyList(),val favorites:List<String> = emptyList(),val progress:Map<String,WatchProgress> = emptyMap())
@Serializable data class HomeResponse(val profile:Profile=Profile(),val rows:List<HomeRow> = emptyList())
@Serializable data class HomeRow(val id:String="",val title:String="",val type:String="catalog",val items:List<CatalogItem> = emptyList())
@Serializable data class CatalogItem(val id:String="",val mediaKey:String="",val type:String="movie",val title:String="",val originalTitle:String="",@Serializable(with=FlexibleNullableIntSerializer::class) val year:Int?=null,val duration:String="",@Serializable(with=FlexibleNullableIntSerializer::class) val durationMinutes:Int?=null,@Serializable(with=FlexibleNullableDoubleSerializer::class) val rating:Double?=null,val contentRating:String="",val genres:List<String> = emptyList(),val cast:List<String> = emptyList(),val directors:List<String> = emptyList(),val themes:List<String> = emptyList(),val franchise:String="",val overview:String="",val poster:String="",val backdrop:String="",val addedAt:String="",val favorite:Boolean=false,val inMyList:Boolean=false,val completed:Boolean=false,val newEpisode:Boolean=false,val resumeMediaKey:String="",val actionLabel:String="",val recommendationReason:String="",val searchHint:String="",val reaction:String="",val hiddenSuggestion:Boolean=false,@Serializable(with=FlexibleNullableIntSerializer::class) val remainingMinutes:Int?=null,val progress:WatchProgress?=null,val streamUrl:String="",val seriesId:String="",@Serializable(with=FlexibleNullableIntSerializer::class) val seasonNumber:Int?=null,@Serializable(with=FlexibleNullableIntSerializer::class) val episodeNumber:Int?=null,val seasons:List<Season> = emptyList(),val subtitles:List<SubtitleTrack> = emptyList())
typealias Movie = CatalogItem
typealias Series = CatalogItem
@Serializable data class Season(@Serializable(with=FlexibleIntSerializer::class) val seasonNumber:Int=0,val episodes:List<CatalogItem> = emptyList())
typealias Episode = CatalogItem
@Serializable data class Collection(val id:String="",val title:String="",val subtitle:String="",val banner:String="",val items:List<CatalogItem> = emptyList())
@Serializable data class PlaybackInfo(
    val mediaId:String="",val mediaKey:String="",val playbackUrl:String="",val mimeType:String="video/*",val container:String="",val videoCodec:String="",val audioCodec:String="",
    val supportsRange:Boolean=true,@Serializable(with=FlexibleNullableLongSerializer::class) val duration:Long?=null,@Serializable(with=FlexibleLongSerializer::class) val resumePosition:Long=0,@Serializable(with=FlexibleLongSerializer::class) val playbackOffset:Long=0,@Serializable(with=FlexibleLongSerializer::class) val bitrate:Long=0,@Serializable(with=FlexibleIntSerializer::class) val width:Int=0,@Serializable(with=FlexibleIntSerializer::class) val height:Int=0,val playbackRevision:String="",
    val subtitles:List<SubtitleTrack> = emptyList(),val audioTracks:List<AudioTrack> = emptyList(),val nextEpisode:CatalogItem?=null,val preparationStatus:String="ready",
    @Serializable(with=FlexibleDoubleSerializer::class) val preparationProgress:Double=100.0,val playbackMode:String="direct",val qualities:List<String> = emptyList(),val quality:String="Automática",val errorType:String="",
    val errorMessage:String="",val adaptiveReasons:List<String> = emptyList(),
)
@Serializable data class SubtitleTrack(val label:String="",val srclang:String="",val src:String="",val mimeType:String="text/vtt",val default:Boolean=false)
@Serializable data class AudioTrack(val id:String="",val label:String="",val language:String="",val codec:String="")
@Serializable data class WatchProgress(val mediaType:String="movie",val mediaId:String="",val seriesId:String="",@Serializable(with=FlexibleDoubleSerializer::class) val currentTime:Double=0.0,@Serializable(with=FlexibleDoubleSerializer::class) val duration:Double=0.0,@Serializable(with=FlexibleDoubleSerializer::class) val percentage:Double=0.0,val completed:Boolean=false,val updatedAt:String="")
@Serializable data class ActionResult(val action:String="",val enabled:Boolean=false)
@Serializable data class ProfilePreferences(val autoplayNext:Boolean=false)
@Serializable data class ResetPersonalizationResult(val reset:Boolean=false)
@Serializable data class ApiError(val code:String="API_ERROR",val message:String="Não foi possível concluir a operação.",@Serializable(with=FlexibleIntSerializer::class) val status:Int=0)
@Serializable data class ClientCapabilities(val client:String="brasa-android-tv",val clientVersion:String=BuildConfig.VERSION_NAME,val platform:String="android-tv",val manufacturer:String="",val model:String="",val screen:ScreenCapabilities=ScreenCapabilities(),val playback:PlaybackCapabilities=PlaybackCapabilities())
@Serializable data class ScreenCapabilities(val width:Int=0,val height:Int=0)
@Serializable data class VideoCodecCapability(
    val codec:String="",val maxWidth:Int=0,val maxHeight:Int=0,val maxBitrate:Long=0,val hardware:Boolean=false,val profiles:List<String> = emptyList(),
)
@Serializable data class PlaybackCapabilities(
    val containers:List<String> = emptyList(),val videoCodecs:List<String> = emptyList(),val audioCodecs:List<String> = emptyList(),val hdrTypes:List<String> = emptyList(),
    val videoCapabilities:List<VideoCodecCapability> = emptyList(),val maxWidth:Int=0,val maxHeight:Int=0,val subtitleFormats:List<String> = listOf("vtt"),
)
@Serializable data class NetworkServerStatus(val available:Boolean=false,val type:String="unknown",val connectionLabel:String="Conexão não identificada",val name:String="",val ip:String="",val mac:String="",val gateway:String="",val subnetMask:String="",@Serializable(with=FlexibleLongSerializer::class) val speed:Long=0,val category:String="",val serverUrl:String="",@Serializable(with=FlexibleIntSerializer::class) val port:Int=4173,val dynamicIpWarning:String="",val message:String="")
@Serializable data class NetworkFirewallStatus(val configured:Boolean=false,val supported:Boolean=false,val publicNetwork:Boolean=false,val message:String="")
@Serializable data class NetworkStatusResponse(val server:NetworkServerStatus=NetworkServerStatus(),val firewall:NetworkFirewallStatus=NetworkFirewallStatus())
@Serializable data class NetworkTestSession(val id:String="",val profile:String="1080p",@Serializable(with=FlexibleIntSerializer::class) val bitrateMbps:Int=12,@Serializable(with=FlexibleIntSerializer::class) val durationSeconds:Int=60,val state:String="ready",@Serializable(with=FlexibleLongSerializer::class) val bytesSent:Long=0,@Serializable(with=FlexibleIntSerializer::class) val interruptions:Int=0,@Serializable(with=FlexibleLongSerializer::class) val elapsedMs:Long=0,val streamPath:String="")
data class NetworkTransferMeasurement(val bytesRead:Long,val elapsedMs:Long,val samplesMbps:List<Double>,val failures:Int)
