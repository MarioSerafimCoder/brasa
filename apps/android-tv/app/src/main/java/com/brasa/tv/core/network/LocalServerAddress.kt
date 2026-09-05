package com.brasa.tv.core.network

import java.net.IDN
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URI

object LocalServerAddress {
    fun normalize(raw:String, defaultPort:Int=4173):String {
        val value=raw.trim().trimEnd('/'); require(value.isNotBlank()){"Informe o endereço do BRasa."}
        val withScheme=if("://" in value)value else "http://$value"
        val uri=URI(withScheme); require(uri.scheme.equals("http",true)){"Use HTTP na rede doméstica."}; require(uri.userInfo==null&&uri.query==null&&uri.fragment==null){"Endereço inválido."}
        val host=IDN.toASCII(uri.host?:throw IllegalArgumentException("Host inválido.")); require(isAllowedHost(host)){"Use um IP privado ou endereço .local."}
        val port=if(uri.port==-1)defaultPort else uri.port; require(port in 1..65535){"Porta inválida."}
        return "http://$host:$port"
    }
    fun isAllowedHost(host:String):Boolean { val value=host.lowercase().trimEnd('.'); if(value.endsWith(".local")||value=="localhost")return true; return runCatching { val address=InetAddress.getByName(value); address is Inet4Address&&(address.isSiteLocalAddress||address.isLoopbackAddress) }.getOrDefault(false) }
    fun resolve(baseUrl:String,path:String):String { val base=URI(normalize(baseUrl)+"/"); val resolved=base.resolve(path.removePrefix("/")); require(resolved.host.equals(base.host,true)&&resolved.port==base.port){"A URL saiu do servidor pareado."}; return resolved.toString() }
    fun sameOrigin(first:String,second:String):Boolean { val a=URI(first);val b=URI(second);return a.scheme.equals(b.scheme,true)&&a.host.equals(b.host,true)&&effectivePort(a)==effectivePort(b) }
    private fun effectivePort(uri:URI)=if(uri.port!=-1)uri.port else if(uri.scheme=="https")443 else 80
}
