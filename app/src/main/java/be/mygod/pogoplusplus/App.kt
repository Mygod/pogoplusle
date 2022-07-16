package be.mygod.pogoplusplus

import android.annotation.SuppressLint
import android.app.Application
import android.os.Build
import android.util.Log
import be.mygod.pogoplusplus.util.DeviceStorageApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.initialize
import timber.log.Timber

class App : Application() {
    companion object {
        lateinit var app: App
    }

    lateinit var deviceStorage: DeviceStorageApp

    override fun onCreate() {
        super.onCreate()
        app = this
        deviceStorage = DeviceStorageApp(this)
        Firebase.initialize(deviceStorage)
        when (val codename = Build.VERSION.CODENAME) {
            "REL" -> { }
            else -> FirebaseCrashlytics.getInstance().apply {
                setCustomKey("codename", codename)
                setCustomKey("preview_sdk", Build.VERSION.PREVIEW_SDK_INT)
            }
        }
        Timber.plant(object : Timber.DebugTree() {
            @SuppressLint("LogNotTimber")
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                if (t == null) {
                    if (priority != Log.DEBUG || BuildConfig.DEBUG) Log.println(priority, tag, message)
                    FirebaseCrashlytics.getInstance().log("${"XXVDIWEF".getOrElse(priority) { 'X' }}/$tag: $message")
                } else {
                    if (priority >= Log.WARN || priority == Log.DEBUG) {
                        Log.println(priority, tag, message)
                        Log.w(tag, message, t)
                    }
                    if (priority >= Log.INFO) FirebaseCrashlytics.getInstance().recordException(t)
                }
            }
        })
        MainService.updateNotificationChannels()
    }
}
