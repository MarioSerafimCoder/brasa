package com.brasa.tv.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.brasa.tv.BuildConfig
import com.brasa.tv.designsystem.BrasaTheme
import com.brasa.tv.app.navigation.BrasaNavHost
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.brasa.tv.data.storage.AppSettings

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        val previewMode = BuildConfig.DEBUG && intent.getBooleanExtra("preview", false)
        val previewPage = intent.getStringExtra("previewPage").orEmpty()
        val container=(application as BrasaApplication).container
        setContent { val settings by container.settings.values.collectAsState(initial=AppSettings());BrasaTheme(settings.uiScale,settings.density) { BrasaNavHost(container, previewMode, previewPage) } }
    }
}
