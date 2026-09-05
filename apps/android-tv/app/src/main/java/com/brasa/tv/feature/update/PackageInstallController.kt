package com.brasa.tv.feature.update
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
interface InstallerGateway{fun canRequestPackageInstalls():Boolean;fun permissionIntent():Intent;suspend fun install(file:File,update:UpdateManifest):Int}
class PackageInstallController(private val context:Context):InstallerGateway{override fun canRequestPackageInstalls()=Build.VERSION.SDK_INT<26||context.packageManager.canRequestPackageInstalls();override fun permissionIntent()=Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}"));override suspend fun install(file:File,update:UpdateManifest)=withContext(Dispatchers.IO){require(canRequestPackageInstalls()){"Permissão para instalar atualizações não concedida."};val installer=context.packageManager.packageInstaller;val params=PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply{setAppPackageName("com.brasa.tv");setSize(update.sizeBytes);if(Build.VERSION.SDK_INT>=26)setInstallReason(PackageManager.INSTALL_REASON_USER);if(Build.VERSION.SDK_INT>=31)setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)};val sessionId=installer.createSession(params);try{installer.openSession(sessionId).use{session->file.inputStream().use{input->session.openWrite("brasa-tv-release.apk",0,file.length()).use{output->input.copyTo(output);session.fsync(output)}};val intent=Intent(context,InstallResultReceiver::class.java).setAction(InstallResultReceiver.ACTION).putExtra(InstallResultReceiver.EXTRA_SESSION_ID,sessionId);val flags=PendingIntent.FLAG_UPDATE_CURRENT or if(Build.VERSION.SDK_INT>=31)PendingIntent.FLAG_MUTABLE else 0;val pending=PendingIntent.getBroadcast(context,sessionId,intent,flags);session.commit(pending.intentSender)};sessionId}catch(error:Throwable){runCatching{installer.abandonSession(sessionId)};throw error}}}
