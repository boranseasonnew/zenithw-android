package space.zenithw.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ZenithApplication : Application() {
    lateinit var store: LocalStore
        private set
    lateinit var vault: CookieVault
        private set
    lateinit var engine: DownloadEngine
        private set
    val applicationScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    override fun onCreate() {
        super.onCreate()
        store=LocalStore(this)
        vault=CookieVault(this)
        engine=DownloadEngine(this,store,vault)
        applicationScope.launch { engine.initializeAndUpdate() }
    }
}
