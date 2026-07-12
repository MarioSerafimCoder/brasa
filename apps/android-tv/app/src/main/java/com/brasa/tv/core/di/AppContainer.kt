package com.brasa.tv.core.di

import android.content.Context
import com.brasa.tv.core.network.BrasaHttpClient
import com.brasa.tv.core.network.LocalNetworkAccessController
import com.brasa.tv.core.security.SecureTokenStore
import com.brasa.tv.data.api.BrasaApi
import com.brasa.tv.data.repository.BrasaRepository
import com.brasa.tv.data.storage.AppSettingsStore
import com.brasa.tv.feature.server.BrasaNsdDiscovery
import com.brasa.tv.feature.update.*
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.first

class AppContainer(val context:Context){
    val json=Json{ignoreUnknownKeys=true;explicitNulls=false;encodeDefaults=true}
    val settings=AppSettingsStore(context);val tokenStore=SecureTokenStore(context,json);val networkAccess=LocalNetworkAccessController(context);val discovery=BrasaNsdDiscovery(context)
    val http=BrasaHttpClient(tokenStore,json);val api=BrasaApi(http,json);val repository=BrasaRepository(api,settings,tokenStore,http)
    val updatePreferences=UpdatePreferences(context,json);val updateRepository=UpdateRepository(HttpUpdateApi(http,json),updatePreferences,{settings.values.first().serverBaseUrl});val apkValidator=ApkValidator(AndroidApkInspector(context))
}
