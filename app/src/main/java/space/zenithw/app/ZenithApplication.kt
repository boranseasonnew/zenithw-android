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
    private var networkWake=0L
    private val networkCallback=object: android.net.ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: android.net.Network,caps: android.net.NetworkCapabilities) {
            val now=android.os.SystemClock.elapsedRealtime()
            if(now-networkWake<1000) return
            networkWake=now
            applicationScope.launch { DownloadWorker.resumeQueue(this@ZenithApplication,true) }
        }
    }
    override fun onCreate() {
        super.onCreate()
        store=LocalStore(this)
        vault=CookieVault(this)
        engine=DownloadEngine(this,store,vault)
        applicationScope.launch { DownloadWorker.resumeQueue(this@ZenithApplication) }
        (getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).registerDefaultNetworkCallback(networkCallback)
        applicationScope.launch { engine.initializeAndUpdate() }
        applicationScope.launch { engine.prepareMetadataRuntime() }
    }
}
