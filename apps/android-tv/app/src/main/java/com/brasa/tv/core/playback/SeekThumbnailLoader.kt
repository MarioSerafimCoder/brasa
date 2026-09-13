package com.brasa.tv.core.playback

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.brasa.tv.core.network.AuthRequired
import com.brasa.tv.core.network.BrasaHttpClient
import com.brasa.tv.core.network.LocalServerAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class SeekThumbnailLoader(private val http: BrasaHttpClient) {
    private val client = http.authenticatedClient().newBuilder().callTimeout(4, TimeUnit.SECONDS).build()
    suspend fun load(base: String, path: String, positionMs: Long, allowed: Boolean): Bitmap? {
        if (path.isBlank()) return null
        val request = Request.Builder().url(LocalServerAddress.resolve(base, "$path&positionMs=${PlaybackExtrasPolicy.previewBucket(positionMs)}&allowGenerate=${if (allowed) 1 else 0}"))
            .tag(AuthRequired::class.java, AuthRequired(true)).build()
        val bytes: ByteArray? = suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resume(null) }
                override fun onResponse(call: Call, response: Response) {
                    val value = runCatching { response.use {
                        if (it.code != 200 || it.body.contentLength() !in 1..131072) null else it.body.bytes()
                    } }.getOrNull()
                    if (continuation.isActive) continuation.resume(value)
                }
            })
        }
        return withContext(Dispatchers.IO) { bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
    }
}
