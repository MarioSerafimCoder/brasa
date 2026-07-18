package com.brasa.tv.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager

data class LocalConnectionDetails(val type:String="unknown",val frequencyMhz:Int=0,val frequencyBand:String="Não disponível")

class LocalNetworkAccessController(private val context:Context){
    fun isLocalNetworkAvailable():Boolean { val manager=context.getSystemService(ConnectivityManager::class.java);val network=manager.activeNetwork?:return false;val caps=manager.getNetworkCapabilities(network)?:return false;return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)||caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) }
    @Suppress("DEPRECATION")
    fun connectionDetails():LocalConnectionDetails{
        val manager=context.getSystemService(ConnectivityManager::class.java);val network=manager.activeNetwork?:return LocalConnectionDetails();val caps=manager.getNetworkCapabilities(network)?:return LocalConnectionDetails()
        if(caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))return LocalConnectionDetails("ethernet",0,"Cabo Ethernet")
        if(caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))return LocalConnectionDetails("mobile",0,"Rede móvel")
        if(!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))return LocalConnectionDetails()
        val frequency=runCatching{context.applicationContext.getSystemService(WifiManager::class.java)?.connectionInfo?.frequency?:0}.getOrDefault(0)
        val band=when(frequency){in 2400..2500->"2,4 GHz";in 4900..5900->"5 GHz";in 5925..7125->"6 GHz";else->"Wi-Fi (frequência indisponível)"}
        return LocalConnectionDetails("wifi",frequency,band)
    }
}
