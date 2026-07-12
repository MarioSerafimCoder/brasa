package com.brasa.tv.app

import android.app.Application
import com.brasa.tv.core.di.AppContainer

class BrasaApplication : Application() {
    val container by lazy { AppContainer(this) }
}
