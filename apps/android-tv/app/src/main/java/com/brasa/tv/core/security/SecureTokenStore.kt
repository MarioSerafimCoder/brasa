package com.brasa.tv.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.brasa.tv.core.model.DeviceSession
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureTokenStore(private val context:Context,private val json:Json=Json){
    private val alias="brasa_tv_device_key_v1";private val file by lazy{context.noBackupFilesDir.resolve("secure/device_session.bin")}
    @Synchronized fun save(session:DeviceSession){require(session.deviceToken.isNotBlank());val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());val encrypted=cipher.doFinal(json.encodeToString(DeviceSession.serializer(),session).encodeToByteArray());file.parentFile?.mkdirs();file.writeBytes(byteArrayOf(cipher.iv.size.toByte())+cipher.iv+encrypted)}
    @Synchronized fun load():DeviceSession?=runCatching{if(!file.exists())return null;val bytes=file.readBytes();val ivSize=bytes.first().toInt()and 0xff;require(ivSize in 12..16&&bytes.size>ivSize+1);val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(1,1+ivSize)));json.decodeFromString(DeviceSession.serializer(),cipher.doFinal(bytes.copyOfRange(1+ivSize,bytes.size)).decodeToString())}.getOrElse{clear();null}
    fun hasToken()=load()?.deviceToken?.isNotBlank()==true
    @Synchronized fun clear(){file.delete();runCatching{val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)};store.deleteEntry(alias)}}
    private fun key():SecretKey{val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)};(store.getKey(alias,null)as?SecretKey)?.let{return it};return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").run{init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());generateKey()}}
}
