package space.zenithw.app

import android.app.Application
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class ScreenState(val tab: Int=0,val url: String="",val analyzing: Boolean=false,
    val preview: MediaPreview?=null,val message: String?=null,val profiles: List<CookieProfile> = emptyList(),
    val cookieId: String?=null,val autoUpdate: Boolean=true,val channel: String="stable",val language: String="tr")
class ZenithViewModel(application: Application): AndroidViewModel(application) {
    val app=application as ZenithApplication
    private val mutableScreen=MutableStateFlow(ScreenState(profiles=app.vault.profiles(),
        autoUpdate=app.store.autoUpdate,channel=app.store.channel,cookieId=app.store.selectedCookieId,language=app.store.language))
    val screen=mutableScreen.asStateFlow()
    val jobs=app.store.jobs
    val engine=app.engine.status
    private var inspection: Job?=null
    private fun edit(update: (ScreenState)->ScreenState) { mutableScreen.value=update(mutableScreen.value) }
    fun tab(tab: Int)=edit { it.copy(tab=tab,message=null) }
    fun url(url: String) {
        if(url!=mutableScreen.value.url) inspection?.cancel()
        edit { it.copy(url=url,message=null,analyzing=false,preview=null) }
    }
    fun showMessage(value: String)=edit { it.copy(message=value) }
    fun importCookies(uri: Uri) {
        viewModelScope.launch {
            try {
                val profile=withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.use { input ->
                        val bytes=input.readNBytesCompat(1024*1024+1)
                        require(bytes.size<=1024*1024) { "Cookie dosyası çok büyük." }
                        app.vault.importNetscape(bytes.toString(Charsets.UTF_8))
                    } ?: error("Dosya açılamadı.")
                }
                refreshProfiles()
                cookie(profile.id)
                showMessage("Cookie dosyası şifreli olarak kaydedildi.")
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) { showMessage("Cookie dosyası okunamadı. Netscape biçiminde bir dosya seç.") }
        }
    }
    fun dismissMessage()=edit { it.copy(message=null) }
    fun dismissPreview()=edit { it.copy(preview=null) }
    fun cookie(id: String?) {
        inspection?.cancel()
        app.store.selectedCookieId=id
        edit { it.copy(cookieId=id,analyzing=false,preview=null) }
    }
    fun refreshProfiles() {
        val profiles=app.vault.profiles()
        edit { it.copy(profiles=profiles,cookieId=it.cookieId?.takeIf { id -> profiles.any { profile -> profile.id==id } }) }
        app.store.selectedCookieId=mutableScreen.value.cookieId
    }
    fun removeProfile(id: String) { app.vault.remove(id); refreshProfiles() }
    fun shareIntent(text: String) {
        url(runCatching { normalizeUrl(text) }.getOrDefault(text))
        tab(0)
    }
    fun analyze() {
        if(mutableScreen.value.analyzing) return
        val state=mutableScreen.value
        val url=try { normalizeUrl(state.url) } catch(e: Exception) {
            edit { it.copy(message=e.message ?: "Bağlantıyı kontrol et.") }; return
        }
        edit { it.copy(url=url,analyzing=true,message=null) }
        inspection=viewModelScope.launch {
            try {
                val preview=app.engine.inspect(url,state.cookieId)
                edit { it.copy(preview=preview,analyzing=false) }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) {
                coroutineContext.ensureActive()
                edit { it.copy(analyzing=false,message="Medya bilgisi alınamadı. Paylaşım bağlantısını kontrol et; gerekirse bir oturum bağla.") }
            }
        }
    }
    fun enqueue(options: DownloadOptions) {
        app.store.defaultOptions=options
        val state=mutableScreen.value
        val preview=state.preview ?: return
        val job=DownloadJob(url=preview.url,title=preview.title,thumbnail=preview.thumbnail,
            detail=if(options.scheduledAt>System.currentTimeMillis()) "Zamanlandı" else "Sırada",
            options=options.copy(cookieId=state.cookieId))
        DownloadWorker.enqueue(app,job)
        edit { it.copy(preview=null,url="",tab=1,message="İndirme sıraya eklendi.") }
    }
    fun cancel(id: String)=DownloadWorker.cancel(app,id)
    fun retry(job: DownloadJob) {
        val replacement=job.copy(id=UUID.randomUUID().toString(),state=JobState.QUEUED,progress=0f,
            detail="Sırada",createdAt=System.currentTimeMillis(),files=emptyList(),notes=emptyList())
        DownloadWorker.enqueue(app,replacement)
    }
    fun automatic(value: Boolean) { app.store.autoUpdate=value; edit { it.copy(autoUpdate=value) } }
    fun language(value: String) { app.store.language=value; edit { it.copy(language=app.store.language) } }
    fun defaults(value: DownloadOptions) { app.store.defaultOptions=value }
    fun channel(value: String) { app.store.channel=value; edit { it.copy(channel=value) }; updateEngine() }
    fun updateEngine() { app.applicationScope.launch { app.engine.initializeAndUpdate(force=true) } }
}


private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val output=java.io.ByteArrayOutputStream()
    val buffer=ByteArray(8192)
    while(output.size()<limit) {
        val count=read(buffer,0,minOf(buffer.size,limit-output.size()))
        if(count<0) break
        output.write(buffer,0,count)
    }
    return output.toByteArray()
}
