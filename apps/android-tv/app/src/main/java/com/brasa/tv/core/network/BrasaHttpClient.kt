@file:androidx.media3.common.util.UnstableApi

package com.brasa.tv.core.network

import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.brasa.tv.core.model.ApiEnvelope
import com.brasa.tv.core.model.ApiError
import com.brasa.tv.core.security.SecureTokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.math.max

class BrasaHttpClient(private val tokenStore:SecureTokenStore,val json:Json=Json{ignoreUnknownKeys=true;explicitNulls=false}){
    @Volatile private var pairedOrigin:String=""
    private val client=OkHttpClient.Builder().connectTimeout(5,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS).followRedirects(false).addInterceptor(HostBoundAuthInterceptor{pairedOrigin to tokenStore.load()?.deviceToken.orEmpty()}).build()
    fun bindServer(baseUrl:String){pairedOrigin=LocalServerAddress.normalize(baseUrl)}
    suspend fun <T> get(base:String,path:String,serializer:KSerializer<T>,authenticated:Boolean=true)=execute(base,path,"GET",null,serializer,authenticated)
    suspend fun <T> post(base:String,path:String,body:String,serializer:KSerializer<T>,authenticated:Boolean=true)=execute(base,path,"POST",body,serializer,authenticated)
    suspend fun <T> put(base:String,path:String,body:String,serializer:KSerializer<T>)=execute(base,path,"PUT",body,serializer,true)
    suspend fun delete(base:String,path:String):Boolean=withContext(Dispatchers.IO){client.newCall(request(base,path,"DELETE",null,true)).execute().use{if(it.code==401)throw DeviceRevokedException();it.isSuccessful}}
    fun authenticatedClient():OkHttpClient=client
    suspend fun measureDownload(base:String,path:String,onProgress:(Long,Long)->Unit):com.brasa.tv.core.model.NetworkTransferMeasurement=withContext(Dispatchers.IO){
        val started=android.os.SystemClock.elapsedRealtime();var bytes=0L;var failures=0;var sampleStarted=started;var sampleBytes=0L;val samples=mutableListOf<Double>()
        try{
            client.newCall(request(base,path,"GET",null,true)).execute().use{response->
                if(response.code==401)throw DeviceRevokedException()
                if(!response.isSuccessful)throw BrasaApiException(response.code,"Não foi possível executar o teste de rede.")
                val source=response.body?.byteStream()?:throw IOException("Resposta vazia no teste de rede.");val buffer=ByteArray(32*1024)
                while(true){coroutineContext.ensureActive();val read=source.read(buffer);if(read<0)break;if(read==0){failures++;continue};bytes+=read;sampleBytes+=read;val now=android.os.SystemClock.elapsedRealtime();if(now-sampleStarted>=1000){samples+=sampleBytes*8.0/(max(1,now-sampleStarted)*1000.0);sampleBytes=0;sampleStarted=now};onProgress(bytes,now-started)}
            }
        }catch(error:Throwable){if(error is kotlinx.coroutines.CancellationException)throw error;failures++;throw error}
        val elapsed=android.os.SystemClock.elapsedRealtime()-started
        com.brasa.tv.core.model.NetworkTransferMeasurement(bytes,elapsed,samples,failures)
    }
    fun authenticatedMediaDataSource(baseUrl:String):OkHttpDataSource.Factory{
        val normalized=LocalServerAddress.normalize(baseUrl)
        val token=tokenStore.load()?.deviceToken.orEmpty()
        if(pairedOrigin.isBlank()||pairedOrigin!=normalized||token.isBlank())throw IOException("Credencial de mídia indisponível para este servidor.")
        return OkHttpDataSource.Factory(client).setDefaultRequestProperties(mapOf("X-BRasa-Device-Token" to token))
    }
    private suspend fun <T> execute(base:String,path:String,method:String,body:String?,serializer:KSerializer<T>,authenticated:Boolean):T=withContext(Dispatchers.IO){client.newCall(request(base,path,method,body,authenticated)).execute().use{response->val text=response.body?.string().orEmpty();if(response.code==401)throw DeviceRevokedException();if(!response.isSuccessful){val error=runCatching{json.decodeFromString(ApiEnvelope.serializer(ApiError.serializer()),text).data}.getOrNull();throw BrasaApiException(response.code,error?.message?:"Falha ao conectar ao BRasa.")};json.decodeFromString(ApiEnvelope.serializer(serializer),text).data?:throw BrasaApiException(response.code,"Resposta vazia do BRasa.")}}
    private fun request(base:String,path:String,method:String,body:String?,authenticated:Boolean):Request{val url=LocalServerAddress.resolve(base,path);val builder=Request.Builder().url(url).header("Accept","application/json").tag(AuthRequired::class.java,AuthRequired(authenticated));return builder.method(method,body?.toRequestBody("application/json; charset=utf-8".toMediaType())).build()}
}
data class AuthRequired(val enabled:Boolean)
class HostBoundAuthInterceptor(private val credentials:()->Pair<String,String>):Interceptor{override fun intercept(chain:Interceptor.Chain):Response{val request=chain.request();if(request.tag(AuthRequired::class.java)?.enabled!=true)return chain.proceed(request);val(origin,token)=credentials();if(origin.isBlank()||token.isBlank()||!LocalServerAddress.sameOrigin(origin,request.url.toString()))throw IOException("Credencial bloqueada para host não pareado.");return chain.proceed(request.newBuilder().header("X-BRasa-Device-Token",token).build())}}
class BrasaApiException(val status:Int,override val message:String):IOException(message)
class DeviceRevokedException:IOException("Este dispositivo foi revogado.")
