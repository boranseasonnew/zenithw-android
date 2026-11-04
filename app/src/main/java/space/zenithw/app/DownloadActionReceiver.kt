package space.zenithw.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch

class DownloadActionReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        val id=intent.getStringExtra("jobId") ?: return
        val pending=goAsync()
        val app=context.applicationContext as ZenithApplication
        app.applicationScope.launch {
            try { DownloadWorker.cancel(app,id) } finally { pending.finish() }
        }
    }
}
