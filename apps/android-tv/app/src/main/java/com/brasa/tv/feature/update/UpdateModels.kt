package com.brasa.tv.feature.update
import kotlinx.serialization.Serializable
@Serializable data class UpdateManifest(val schemaVersion:Int=1,val packageName:String="",val versionCode:Long=0,val versionName:String="",val minimumServerApi:Int=1,val minimumAndroidApi:Int=23,val sizeBytes:Long=0,val sha256:String="",val signingCertificateSha256:String="",val mandatory:Boolean=false,val publishedAt:String="",val releaseNotes:List<String> = emptyList(),val downloadPath:String="")
@Serializable data class UpdateCheckResponse(val available:Boolean=false,val update:UpdateManifest?=null)
data class UpdateClientInfo(val versionCode:Long,val versionName:String)
sealed interface UpdateUiState{data object Idle:UpdateUiState;data object Checking:UpdateUiState;data object UpToDate:UpdateUiState;data class Available(val update:UpdateManifest):UpdateUiState;data class Downloading(val update:UpdateManifest,val percent:Int):UpdateUiState;data class Validating(val update:UpdateManifest):UpdateUiState;data class Ready(val update:UpdateManifest,val apkPath:String):UpdateUiState;data class PermissionRequired(val update:UpdateManifest,val apkPath:String):UpdateUiState;data class Installing(val update:UpdateManifest):UpdateUiState;data class Installed(val versionName:String):UpdateUiState;data class Error(val message:String,val update:UpdateManifest?=null):UpdateUiState}
data class InstallResult(val status:Int,val message:String="")
enum class ApkValidationError{HASH,PACKAGE,VERSION,CERTIFICATE,SIZE,RELEASE_NOT_CONFIGURED,INVALID_APK}
data class ApkValidationResult(val valid:Boolean,val error:ApkValidationError?=null,val message:String="")
