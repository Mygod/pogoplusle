package be.mygod.pogoplusplus.util

import be.mygod.librootkotlinx.RootServer
import be.mygod.librootkotlinx.RootSession
import be.mygod.pogoplusplus.App.Companion.app
import java.util.concurrent.TimeUnit

object RootManager : RootSession() {
    override val timeout get() = TimeUnit.MINUTES.toMillis(1)
    override suspend fun initServer(server: RootServer) {
        server.init(app.deviceStorage)
    }
}
