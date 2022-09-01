package be.mygod.pogoplusplus.util

import android.annotation.SuppressLint
import android.os.Build
import android.os.Parcelable
import android.util.Log
import be.mygod.librootkotlinx.Logger
import be.mygod.librootkotlinx.RootCommandNoResult
import be.mygod.librootkotlinx.RootServer
import be.mygod.librootkotlinx.RootSession
import be.mygod.pogoplusplus.App.Companion.app
import kotlinx.parcelize.Parcelize
import timber.log.Timber
import java.util.concurrent.TimeUnit

object RootManager : RootSession(), Logger {
    @Parcelize
    class RootInit : RootCommandNoResult {
        override suspend fun execute(): Parcelable? {
            Timber.plant(object : Timber.DebugTree() {
                @SuppressLint("LogNotTimber")
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                    if (priority >= Log.WARN) {
                        System.err.println("$priority/$tag: $message")
                        t?.printStackTrace()
                    }
                    if (t == null) {
                        Log.println(priority, tag, message)
                    } else {
                        Log.println(priority, tag, message)
                        Log.d(tag, message, t)
                        if (priority >= Log.WARN) t.printStackTrace(System.err)
                    }
                }
            })
            Logger.me = RootManager
            // this is needed for later using BluetoothAdapter
            @SuppressLint("BlockedPrivateApi", "PrivateApi")
            if (Build.VERSION.SDK_INT >= 33) try {
                Class.forName("android.app.ActivityThread").getDeclaredMethod("initializeMainlineModules").invoke(null)
            } catch (e: ReflectiveOperationException) {
                Timber.w(e)
            }
            return null
        }
    }

    override fun d(m: String?, t: Throwable?) = Timber.d(t, m)
    override fun e(m: String?, t: Throwable?) = Timber.e(t, m)
    override fun i(m: String?, t: Throwable?) = Timber.i(t, m)
    override fun w(m: String?, t: Throwable?) = Timber.w(t, m)

    override val timeout get() = TimeUnit.MINUTES.toMillis(1)
    override suspend fun initServer(server: RootServer) {
        server.init(app)
        server.execute(RootInit())
    }
}
