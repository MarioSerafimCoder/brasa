package com.brasa.tv.feature.update
import com.brasa.tv.BuildConfig
import com.brasa.tv.core.network.*
import kotlinx.coroutines.*
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
class ApkDownloader(private val client:OkHttpClient,private val cacheDir:File,private val baseUrl:String,private val clientInfo:UpdateClientInfo=UpdateClientInfo(BuildConfig.VERSION_CODE.toLong(),BuildConfig.VERSION_NAME),private val maxBytes:Long=512L*1024*1024){private val running=AtomicBoolean(false);@Volatile private var call:Call?=null
suspend fun download(update:UpdateManifest,onProgress:(Int)->Unit):File=withContext(Dispatchers.IO){
    check(running.compareAndSet(false,true)){"Já existe um download em andamento."}
    val directory=cacheDir.resolve("updates").apply{mkdirs()};val partial=directory.resolve("brasa-tv-update.apk.part");val complete=directory.resolve("brasa-tv-update.apk");partial.delete()
    try{
        require(update.downloadPath=="/api/v1/android-tv/update/apk"){"Caminho de download inválido."}
        val request=Request.Builder().url(LocalServerAddress.resolve(baseUrl,"/api/v1/android-tv/update/apk")).header("X-BRasa-Client","brasa-android-tv").header("X-BRasa-Package-Name",BuildConfig.APPLICATION_ID).header("X-BRasa-Version-Code",clientInfo.versionCode.toString()).header("X-BRasa-Version-Name",clientInfo.versionName).header("X-BRasa-Client-Api","1").tag(AuthRequired::class.java,AuthRequired(true)).build()
        call=client.newCall(request);call!!.execute().use{response->
            if(response.isRedirect)throw IOException("Redirecionamento de atualização bloqueado.");if(response.code==401)throw DeviceRevokedException();if(!response.isSuccessful)throw IOException("Download recusado pelo servidor (${response.code}).")
            val body=response.body;val declared=body.contentLength();if(declared<1||declared>maxBytes||declared!=update.sizeBytes)throw IOException("Tamanho da atualização inválido.")
            body.byteStream().use{input->partial.outputStream().use{output->val buffer=ByteArray(64*1024);var total=0L;while(true){coroutineContext.ensureActive();val count=input.read(buffer);if(count<0)break;total+=count;if(total>maxBytes||total>update.sizeBytes)throw IOException("Atualização excedeu o tamanho permitido.");output.write(buffer,0,count);onProgress((total*100/update.sizeBytes).toInt().coerceIn(0,100))};output.flush()}}
            if(partial.length()!=update.sizeBytes)throw IOException("Download incompleto.")
        }
        complete.delete();if(!partial.renameTo(complete))throw IOException("Não foi possível concluir o download.");complete
    }catch(error:Throwable){partial.delete();throw error}finally{call=null;running.set(false)}
}
fun cancel(){call?.cancel()}
}
