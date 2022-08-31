package be.mygod.pogoplusplus.util

import android.annotation.SuppressLint
import android.os.Build
import android.os.Parcelable
import androidx.annotation.RequiresApi
import be.mygod.librootkotlinx.RootCommandNoResult
import be.mygod.librootkotlinx.RootServer
import be.mygod.librootkotlinx.RootSession
import be.mygod.pogoplusplus.App.Companion.app
import kotlinx.parcelize.Parcelize
import java.util.concurrent.TimeUnit

object RootManager : RootSession() {
    @Parcelize
    @RequiresApi(33)
    class RootInit : RootCommandNoResult {
        @SuppressLint("BlockedPrivateApi", "PrivateApi")
        override suspend fun execute(): Parcelable? {
            // this is needed for later using BluetoothAdapter
            Class.forName("android.app.ActivityThread").getDeclaredMethod("initializeMainlineModules").invoke(null)
            return null
        }
    }

    override val timeout get() = TimeUnit.MINUTES.toMillis(1)
    override suspend fun initServer(server: RootServer) {
        server.init(app)
        if (Build.VERSION.SDK_INT >= 33) server.execute(RootInit())
    }
}
