package com.brasa.tv.data.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("brasa_tv_settings")
data class AppSettings(val serverBaseUrl:String="",val serverName:String="",val selectedProfileId:String="",val deviceDisplayName:String="BRasa Android TV")
class AppSettingsStore(private val context:Context){
    private object Keys{val server=stringPreferencesKey("server_base_url");val name=stringPreferencesKey("server_name");val profile=stringPreferencesKey("selected_profile_id");val device=stringPreferencesKey("device_display_name")}
    val values:Flow<AppSettings> = context.dataStore.data.map{AppSettings(it[Keys.server].orEmpty(),it[Keys.name].orEmpty(),it[Keys.profile].orEmpty(),it[Keys.device]?:"BRasa Android TV")}
    suspend fun saveServer(url:String,name:String)=context.dataStore.edit{it[Keys.server]=url;it[Keys.name]=name}
    suspend fun saveProfile(id:String)=context.dataStore.edit{it[Keys.profile]=id}
    suspend fun saveDeviceName(name:String)=context.dataStore.edit{it[Keys.device]=name}
    suspend fun forgetServer()=context.dataStore.edit{it.remove(Keys.server);it.remove(Keys.name);it.remove(Keys.profile)}
}
