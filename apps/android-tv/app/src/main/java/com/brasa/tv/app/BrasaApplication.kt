package com.brasa.tv.app

import android.app.Application
import android.content.ComponentCallbacks2
import com.brasa.tv.core.di.AppContainer

class BrasaApplication : Application() {
    val container by lazy { AppContainer(this) }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) container.playback.trimInactiveCache()
    }
}
