package be.mygod.pogoplusplus

import android.app.Application
import be.mygod.pogoplusplus.util.DeviceStorageApp

class App : Application() {
    companion object {
        lateinit var app: App
    }

    lateinit var deviceStorage: DeviceStorageApp

    override fun onCreate() {
        super.onCreate()
        app = this
        deviceStorage = DeviceStorageApp(this)
        MainService.updateNotificationChannels()
    }
}
