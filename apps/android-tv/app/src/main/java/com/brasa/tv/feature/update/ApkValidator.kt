package com.brasa.tv.feature.update
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.brasa.tv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
data class ApkIdentity(val packageName:String,val versionCode:Long,val certificateSha256:String)
interface ApkInspector{fun inspect(file:File):ApkIdentity?;fun installedCertificate(packageName:String):String?}
class AndroidApkInspector(private val context:Context):ApkInspector{
    override fun inspect(file:File):ApkIdentity?{val info=context.packageManager.getPackageArchiveInfo(file.absolutePath,flags())?:return null;return ApkIdentity(info.packageName,versionCode(info),certificate(info))}
    override fun installedCertificate(packageName:String):String?=runCatching{certificate(context.packageManager.getPackageInfo(packageName,flags()))}.getOrNull()
    private fun flags()=if(Build.VERSION.SDK_INT>=28)PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
    @Suppress("DEPRECATION") private fun versionCode(info:PackageInfo)=if(Build.VERSION.SDK_INT>=28)info.longVersionCode else info.versionCode.toLong()
    @Suppress("DEPRECATION") private fun certificate(info:PackageInfo):String{val bytes=if(Build.VERSION.SDK_INT>=28)info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()else info.signatures?.firstOrNull()?.toByteArray();return bytes?.sha256().orEmpty()}
}
class ApkValidator(private val inspector:ApkInspector,private val installedVersionCode:Long=BuildConfig.VERSION_CODE.toLong(),private val expectedCertificate:String=BuildConfig.RELEASE_CERTIFICATE_SHA256){suspend fun validate(file:File,update:UpdateManifest)=withContext(Dispatchers.IO){
    fun failure(error:ApkValidationError,message:String)=ApkValidationResult(false,error,message)
    if(expectedCertificate.isBlank())return@withContext failure(ApkValidationError.RELEASE_NOT_CONFIGURED,"APK debug não pode ser atualizado para release. Instale o primeiro release manualmente.")
    if(file.length()!=update.sizeBytes)return@withContext failure(ApkValidationError.SIZE,"O tamanho do APK não corresponde ao manifesto.")
    val hash=file.readBytes().sha256();if(!hash.equals(update.sha256,true))return@withContext failure(ApkValidationError.HASH,"O hash da atualização é inválido.")
    val identity=inspector.inspect(file)?:return@withContext failure(ApkValidationError.INVALID_APK,"O arquivo baixado não é um APK válido.")
    if(identity.packageName!="com.brasa.tv"||update.packageName!="com.brasa.tv")return@withContext failure(ApkValidationError.PACKAGE,"O pacote da atualização é inválido.")
    if(identity.versionCode<=installedVersionCode||identity.versionCode!=update.versionCode)return@withContext failure(ApkValidationError.VERSION,"A atualização não possui versionCode superior.")
    val expected=expectedCertificate.replace(":","").uppercase()
    val installed=inspector.installedCertificate("com.brasa.tv").orEmpty().replace(":","").uppercase()
    if(installed!=expected||!identity.certificateSha256.equals(expected,true)||!update.signingCertificateSha256.replace(":","").equals(expected,true))return@withContext failure(ApkValidationError.CERTIFICATE,"A assinatura da atualização não corresponde ao BRasa instalado.")
    ApkValidationResult(true)
}}
private fun ByteArray.sha256()=MessageDigest.getInstance("SHA-256").digest(this).joinToString(""){"%02X".format(it)}
