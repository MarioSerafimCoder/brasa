package com.brasa.tv.core.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class HostBoundAuthInterceptorTest {
    @Test fun attachesTokenOnlyToBoundOrigin(){
        val server=MockWebServer();server.enqueue(MockResponse().setBody("{}"));server.start()
        try{val origin=server.url("/").toString().trimEnd('/');val client=OkHttpClient.Builder().addInterceptor(HostBoundAuthInterceptor{origin to "secret-token"}).build();val request=Request.Builder().url(server.url("/api/tv/profiles")).tag(AuthRequired::class.java,AuthRequired(true)).build();client.newCall(request).execute().close();assertEquals("secret-token",server.takeRequest().getHeader("X-BRasa-Device-Token"))}finally{server.shutdown()}
    }
    @Test fun blocksTokenForDifferentOrigin(){val client=OkHttpClient.Builder().addInterceptor(HostBoundAuthInterceptor{"http://192.168.1.2:4173" to "secret-token"}).build();val request=Request.Builder().url("http://127.0.0.1:4173/api/tv/profiles").tag(AuthRequired::class.java,AuthRequired(true)).build();assertThrows(IOException::class.java){client.newCall(request).execute()}}
}
