package calebxzhou.rdi.client.android

import android.app.Application
import android.content.Context
import calebxzhou.rdi.client.service.ClientDirs
import calebxzhou.rdi.client.ui.AndroidPlatform
import calebxzhou.rdi.common.DL_MOD_DIR
import calebxzhou.rdi.common.net.httpCacheDir

object AndroidBootstrap {
    fun initialize(context: Context) {
        val appContext = context.applicationContext
        AndroidPlatform.initialize(appContext)
        ClientDirs.ensureInit(appContext)
        httpCacheDir = appContext.cacheDir.resolve("http")
        DL_MOD_DIR = ClientDirs.dlModsDir
    }
}

class RdiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidBootstrap.initialize(this)
    }
}
