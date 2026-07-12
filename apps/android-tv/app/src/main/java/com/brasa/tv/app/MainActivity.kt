package com.brasa.tv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.brasa.tv.designsystem.BrasaTheme
import com.brasa.tv.app.navigation.BrasaNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent { BrasaTheme { BrasaNavHost((application as BrasaApplication).container) } }
    }
}
