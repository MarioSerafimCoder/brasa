package com.brasa.tv.feature.server

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class DiscoveredServer(val name:String,val host:String,val port:Int){val address:String get()="http://$host:$port"}
class BrasaNsdDiscovery(context:Context){private val manager=context.getSystemService(NsdManager::class.java)
    fun discover():Flow<List<DiscoveredServer>> = callbackFlow { val found=linkedMapOf<String,DiscoveredServer>();val listener=object:NsdManager.DiscoveryListener{override fun onDiscoveryStarted(type:String){};override fun onDiscoveryStopped(type:String){};override fun onStartDiscoveryFailed(type:String,errorCode:Int){close()};override fun onStopDiscoveryFailed(type:String,errorCode:Int){};override fun onServiceLost(serviceInfo:NsdServiceInfo){found.remove(serviceInfo.serviceName);trySend(found.values.toList())};override fun onServiceFound(serviceInfo:NsdServiceInfo){manager.resolveService(serviceInfo,object:NsdManager.ResolveListener{override fun onResolveFailed(info:NsdServiceInfo,errorCode:Int){};override fun onServiceResolved(info:NsdServiceInfo){val host=info.host?.hostAddress?:return;found[info.serviceName]=DiscoveredServer(info.serviceName,host,info.port);trySend(found.values.toList())}})}};manager.discoverServices("_brasa._tcp.",NsdManager.PROTOCOL_DNS_SD,listener);awaitClose{runCatching{manager.stopServiceDiscovery(listener)}} }
}
