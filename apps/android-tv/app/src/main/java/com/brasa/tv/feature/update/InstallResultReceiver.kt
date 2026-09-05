package com.brasa.tv.feature.update
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.flow.MutableSharedFlow
object InstallResultBus{val events=MutableSharedFlow<InstallResult>(extraBufferCapacity=8)}
class InstallResultReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        if(intent.action!=ACTION)return
        val status=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE)
        val message=intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        if(status==PackageInstaller.STATUS_PENDING_USER_ACTION){
            @Suppress("DEPRECATION") val confirmation:Intent?=if(Build.VERSION.SDK_INT>=33)intent.getParcelableExtra(Intent.EXTRA_INTENT,Intent::class.java)else intent.getParcelableExtra(Intent.EXTRA_INTENT)
            confirmation?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(context::startActivity)
            return
        }
        UpdatePreferences(context,kotlinx.serialization.json.Json).saveInstallResult(status,message)
        InstallResultBus.events.tryEmit(InstallResult(status,message))
    }
    companion object{const val ACTION="com.brasa.tv.INSTALL_RESULT";const val EXTRA_SESSION_ID="session_id"}
}
