package space.zenithw.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity: ComponentActivity() {
    private val model: ZenithViewModel by viewModels()
    private val notifications=registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        if(savedInstanceState==null) receive(intent)
        setContent {
            val screen by model.screen.collectAsStateWithLifecycle()
            ZenithTheme(mode=screen.themeMode,dynamicColors=screen.dynamicColors) {
                ZenithApp(model,onDownload={ options ->
                    requestDownloadNotifications()
                    model.enqueue(options)
                },onQueuedDownload=::requestDownloadNotifications)
            }
        }
    }
    private fun requestDownloadNotifications() {
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); receive(intent) }
    override fun onResume() { super.onResume(); model.refreshProfiles() }
    private fun receive(intent: Intent?) {
        try {
        if(intent?.action in setOf(Intent.ACTION_SEND,Intent.ACTION_SEND_MULTIPLE) && intent?.type=="text/plain") {
            val values=mutableListOf<String>()
            intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.length<=IncomingLinks.MAX_BYTES }?.let(values::add)
            if(intent.action==Intent.ACTION_SEND_MULTIPLE) {
                intent.getCharSequenceArrayListExtra(Intent.EXTRA_TEXT)?.take(51)?.forEach {
                    if(it.length<=8192) values.add(it.toString())
                }
            }
            if(values.sumOf { it.length }<=IncomingLinks.MAX_BYTES) model.shareIntent(values.joinToString("\n"))
        }
        if(intent?.getBooleanExtra("showQueue",false)==true) model.library(false)
        } catch(e: Exception) { model.showMessage(AppLanguage.context(this).getString(R.string.check_link)) }
    }
}
