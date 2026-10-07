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

class MainActivity: ComponentActivity() {
    private val model: ZenithViewModel by viewModels()
    private val notifications=registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        receive(intent)
        setContent {
            ZenithTheme {
                ZenithApp(model,onDownload={ options ->
                    if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
                        notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    model.enqueue(options)
                })
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); receive(intent) }
    override fun onResume() { super.onResume(); model.refreshProfiles() }
    private fun receive(intent: Intent?) {
        if(intent?.action==Intent.ACTION_SEND && intent.type=="text/plain") intent.getStringExtra(Intent.EXTRA_TEXT)?.let(model::shareIntent)
        if(intent?.getBooleanExtra("showQueue",false)==true) model.tab(1)
    }
}
