package com.brasa.tv.feature.update
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
class ApkDownloaderTest{
@Test fun downloadsFixedEndpointWithProgress()=runBlocking{val server=MockWebServer();val directory=Files.createTempDirectory("brasa-download").toFile();server.enqueue(MockResponse().setBody("APK!"));server.start();try{val progress=mutableListOf<Int>();val update=UpdateManifest(sizeBytes=4,downloadPath="/api/v1/android-tv/update/apk");val file=ApkDownloader(OkHttpClient.Builder().followRedirects(false).build(),directory,server.url("/").toString(),UpdateClientInfo(1,"1.0.0")).download(update,progress::add);assertEquals("APK!",file.readText());assertEquals(100,progress.last());assertEquals("/api/v1/android-tv/update/apk",server.takeRequest().path)}finally{server.shutdown();directory.deleteRecursively()}}
@Test fun rejectsWrongSizeExternalPathAndRedirect()=runBlocking{val server=MockWebServer();val directory=Files.createTempDirectory("brasa-download").toFile();server.enqueue(MockResponse().setBody("bad"));server.enqueue(MockResponse().setResponseCode(302).addHeader("Location","http://192.168.1.2/file.apk"));server.start();try{val downloader=ApkDownloader(OkHttpClient.Builder().followRedirects(false).build(),directory,server.url("/").toString(),UpdateClientInfo(1,"1.0.0"),10);assertTrue(runCatching{downloader.download(UpdateManifest(sizeBytes=4,downloadPath="/api/v1/android-tv/update/apk")){} }.isFailure);assertFalse(directory.resolve("updates/brasa-tv-update.apk.part").exists());assertTrue(runCatching{downloader.download(UpdateManifest(sizeBytes=1,downloadPath="https://externo/apk")){} }.isFailure);assertTrue(runCatching{downloader.download(UpdateManifest(sizeBytes=1,downloadPath="/api/v1/android-tv/update/apk")){} }.isFailure)}finally{server.shutdown();directory.deleteRecursively()}}}
