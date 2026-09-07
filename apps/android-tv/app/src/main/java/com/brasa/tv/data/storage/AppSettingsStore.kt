package com.brasa.tv.data.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("brasa_tv_settings")
data class AppSettings(val serverBaseUrl:String="",val serverName:String="",val selectedProfileId:String="",val deviceDisplayName:String="BRasa Android TV",val uiScale:Float=.9f,val density:Float=1f,
    val audioLanguage:String="",val subtitleLanguage:String="",val subtitleMode:String="auto",val subtitleSize:Float=1f,val subtitleStyle:String="outline",val autoplayNext:Boolean=false,val recentSearches:List<String> = emptyList())
class AppSettingsStore(private val context:Context){
    private object Keys{val server=stringPreferencesKey("server_base_url");val name=stringPreferencesKey("server_name");val profile=stringPreferencesKey("selected_profile_id");val device=stringPreferencesKey("device_display_name");val uiScale=floatPreferencesKey("ui_scale");val density=floatPreferencesKey("card_density")}
    private fun preference(profile:String, name:String)=stringPreferencesKey("playback_${profile}_$name")
    val values:Flow<AppSettings> = context.dataStore.data.map{
        val profile=it[Keys.profile].orEmpty()
        AppSettings(it[Keys.server].orEmpty(),it[Keys.name].orEmpty(),profile,it[Keys.device]?:"BRasa Android TV",it[Keys.uiScale]?.coerceIn(.8f,1.1f)?:.9f,it[Keys.density]?.coerceIn(.8f,1.15f)?:1f,
            it[preference(profile,"audio")].orEmpty(),it[preference(profile,"subtitle")].orEmpty(),it[preference(profile,"subtitle_mode")]?:"auto",
            it[preference(profile,"subtitle_size")]?.toFloatOrNull()?.coerceIn(.8f,1.4f)?:1f,it[preference(profile,"subtitle_style")]?:"outline",it[booleanPreferencesKey("playback_${profile}_autoplay_next")]?:false,it[preference(profile,"recent_searches")]?.split("\u001f")?.filter(String::isNotBlank).orEmpty())
    }
    suspend fun saveAudioLanguage(profileId:String,language:String)=context.dataStore.edit{it[preference(profileId,"audio")]=language}
    suspend fun saveSubtitleChoice(profileId:String,mode:String,language:String="")=context.dataStore.edit{it[preference(profileId,"subtitle_mode")]=mode;it[preference(profileId,"subtitle")]=language}
    suspend fun saveSubtitleSize(profileId:String,size:Float)=context.dataStore.edit{it[preference(profileId,"subtitle_size")]=size.coerceIn(.8f,1.4f).toString()}
    suspend fun saveSubtitleStyle(profileId:String,style:String)=context.dataStore.edit{it[preference(profileId,"subtitle_style")]=style}
    suspend fun saveAutoplayNext(profileId:String,enabled:Boolean)=context.dataStore.edit{it[booleanPreferencesKey("playback_${profileId}_autoplay_next")]=enabled}
    suspend fun addRecentSearch(profileId:String,query:String)=context.dataStore.edit{values->val key=preference(profileId,"recent_searches");val next=listOf(query.trim())+values[key].orEmpty().split("\u001f");values[key]=next.filter(String::isNotBlank).distinctBy(String::lowercase).take(8).joinToString("\u001f")}
    suspend fun clearRecentSearches(profileId:String)=context.dataStore.edit{it.remove(preference(profileId,"recent_searches"))}
    suspend fun saveServer(url:String,name:String)=context.dataStore.edit{it[Keys.server]=url;it[Keys.name]=name}
    suspend fun saveProfile(id:String)=context.dataStore.edit{it[Keys.profile]=id}
    suspend fun saveDeviceName(name:String)=context.dataStore.edit{it[Keys.device]=name}
    suspend fun saveUiScale(value:Float)=context.dataStore.edit{it[Keys.uiScale]=value.coerceIn(.8f,1.1f)}
    suspend fun saveDensity(value:Float)=context.dataStore.edit{it[Keys.density]=value.coerceIn(.8f,1.15f)}
    suspend fun forgetServer()=context.dataStore.edit{it.remove(Keys.server);it.remove(Keys.name);it.remove(Keys.profile)}
}
