package com.brasa.tv.core.di

import android.content.Context
import com.brasa.tv.core.network.BrasaHttpClient
import com.brasa.tv.core.network.LocalNetworkAccessController
import com.brasa.tv.core.security.SecureTokenStore
import com.brasa.tv.data.api.BrasaApi
import com.brasa.tv.data.repository.BrasaRepository
import com.brasa.tv.data.storage.AppSettingsStore
import com.brasa.tv.feature.server.BrasaNsdDiscovery
import kotlinx.serialization.json.Json

class AppContainer(context:Context){
    val json=Json{ignoreUnknownKeys=true;explicitNulls=false;encodeDefaults=true}
    val settings=AppSettingsStore(context);val tokenStore=SecureTokenStore(context,json);val networkAccess=LocalNetworkAccessController(context);val discovery=BrasaNsdDiscovery(context)
    val http=BrasaHttpClient(tokenStore,json);val api=BrasaApi(http,json);val repository=BrasaRepository(api,settings,tokenStore,http)
}
