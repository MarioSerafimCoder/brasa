package com.brasa.tv.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class LocalNetworkAccessController(private val context:Context){
    fun isLocalNetworkAvailable():Boolean { val manager=context.getSystemService(ConnectivityManager::class.java);val network=manager.activeNetwork?:return false;val caps=manager.getNetworkCapabilities(network)?:return false;return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)||caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) }
}
