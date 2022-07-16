package be.mygod.pogoplusplus

import android.app.Application
import be.mygod.pogoplusplus.util.DeviceStorageApp
import be.mygod.pogoplusplus.util.RootManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class App : Application() {
    companion object {
        lateinit var app: App
    }

    lateinit var deviceStorage: DeviceStorageApp

    override fun onCreate() {
        super.onCreate()
        app = this
        deviceStorage = DeviceStorageApp(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level == TRIM_MEMORY_RUNNING_CRITICAL || level >= TRIM_MEMORY_BACKGROUND) GlobalScope.launch {
            RootManager.closeExisting()
        }
    }
}
