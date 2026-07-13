package com.brasa.tv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.brasa.tv.BuildConfig
import com.brasa.tv.designsystem.BrasaTheme
import com.brasa.tv.app.navigation.BrasaNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        val previewMode = BuildConfig.DEBUG && intent.getBooleanExtra("preview", false)
        val previewPage = intent.getStringExtra("previewPage").orEmpty()
        setContent { BrasaTheme { BrasaNavHost((application as BrasaApplication).container, previewMode, previewPage) } }
    }
}
