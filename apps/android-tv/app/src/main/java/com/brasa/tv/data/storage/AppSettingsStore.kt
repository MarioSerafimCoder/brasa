package com.brasa.tv.data.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("brasa_tv_settings")
data class AppSettings(val serverBaseUrl:String="",val serverName:String="",val selectedProfileId:String="",val deviceDisplayName:String="BRasa Android TV",val uiScale:Float=.9f,val density:Float=1f)
class AppSettingsStore(private val context:Context){
    private object Keys{val server=stringPreferencesKey("server_base_url");val name=stringPreferencesKey("server_name");val profile=stringPreferencesKey("selected_profile_id");val device=stringPreferencesKey("device_display_name");val uiScale=floatPreferencesKey("ui_scale");val density=floatPreferencesKey("card_density")}
    val values:Flow<AppSettings> = context.dataStore.data.map{AppSettings(it[Keys.server].orEmpty(),it[Keys.name].orEmpty(),it[Keys.profile].orEmpty(),it[Keys.device]?:"BRasa Android TV",it[Keys.uiScale]?.coerceIn(.8f,1.1f)?:.9f,it[Keys.density]?.coerceIn(.8f,1.15f)?:1f)}
    suspend fun saveServer(url:String,name:String)=context.dataStore.edit{it[Keys.server]=url;it[Keys.name]=name}
    suspend fun saveProfile(id:String)=context.dataStore.edit{it[Keys.profile]=id}
    suspend fun saveDeviceName(name:String)=context.dataStore.edit{it[Keys.device]=name}
    suspend fun saveUiScale(value:Float)=context.dataStore.edit{it[Keys.uiScale]=value.coerceIn(.8f,1.1f)}
    suspend fun saveDensity(value:Float)=context.dataStore.edit{it[Keys.density]=value.coerceIn(.8f,1.15f)}
    suspend fun forgetServer()=context.dataStore.edit{it.remove(Keys.server);it.remove(Keys.name);it.remove(Keys.profile)}
}
