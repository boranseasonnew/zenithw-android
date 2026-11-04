package space.zenithw.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A settings-only, explicitly allowlisted portable schema. Cookies/keys/grants are excluded. */
class SettingsBackup(private val context: Context,private val store: LocalStore) {
    data class Preview(val root: JSONObject,val profiles: Int)
    fun export(includeLibrary: Boolean=false): String {
        val p=context.getSharedPreferences("zenithw_v2",Context.MODE_PRIVATE)
        val profiles=JSONArray()
        p.all.filterKeys { it.startsWith("profile:") }.entries.take(100).forEach { (key,value) ->
            val o=DownloadOptions.from(JSONObject(value as String))
            profiles.put(JSONObject().put("host",key.removePrefix("profile:")).put("options",portableOptions(o).json()))
        }
        val library=JSONArray()
        if(includeLibrary) store.jobs.value.filter { it.state==JobState.COMPLETED }.take(1000).forEach {
            library.put(JSONObject().put("identity",duplicateKey(it)).put("favorite",it.favorite).put("collection",it.collection))
        }
        return JSONObject().put("schema",2).put("library",library).put("app","ZenithW").put("version",BuildConfig.VERSION_NAME)
            .put("theme",store.themeMode).put("language",store.language).put("dynamicColors",store.dynamicColors)
            .put("configure",store.configureBeforeDownload).put("remember",store.rememberDownloadChoices)
            .put("preview",store.showMediaPreview).put("history",store.keepHistory)
            .put("concurrency",store.concurrentDownloads).put("smart",store.smartEnabled)
            .put("defaults",portableOptions(store.defaultOptions).json()).put("profiles",profiles).toString(2)
    }
    fun validate(bytes: ByteArray): Preview {
        require(bytes.size<=512*1024) { "backup_size" }
        val root=JSONObject(BackupPolicy.text(bytes))
        require(root.getInt("schema") in 1..2 && root.getString("app")=="ZenithW") { "backup_schema" }
        if(root.getInt("schema")==1) root.put("library",JSONArray()).put("schema",2)
        val library=root.getJSONArray("library");require(library.length()<=1000)
        for(i in 0 until library.length()) {
            val entry=library.getJSONObject(i)
            require(entry.getString("identity").matches(Regex("[a-f0-9]{64}")))
            require(entry.getString("collection").length<=80 && entry.getString("collection").none(Char::isISOControl))
            entry.getBoolean("favorite")
        }
        require(root.getString("theme") in setOf("system","dark","light"))
        require(root.getString("language") in setOf("tr","en","de","fr","ru"))
        require(root.getInt("concurrency") in 1..5)
        validateOptions(root.getJSONObject("defaults"))
        val profiles=root.getJSONArray("profiles");require(profiles.length()<=100)
        val seen=mutableSetOf<String>()
        for(i in 0 until profiles.length()) {
            val item=profiles.getJSONObject(i);val host=item.getString("host")
            require(host.length<=253 && host.matches(Regex("[a-z0-9.-]+")) && seen.add(host))
            validateOptions(item.getJSONObject("options"))
        }
        return Preview(root,profiles.length())
    }
    private fun validateOptions(json: JSONObject): DownloadOptions {
        val o=DownloadOptions.from(json)
        require(o==portableOptions(o)) { "backup_sensitive" }
        require(o.mode in MediaMode.entries && o.container in setOf("mp4","mkv","webm"))
        require(o.audioFormat in setOf("mp3","m4a","opus","wav","flac"))
        FilenamePolicy.validateNative(o.filenameTemplate)
        FilenamePolicy.folder(o.outputFolder)
        return o
    }
    /** Call only after the user's explicit restore confirmation; retain history and sessions. */
    fun restore(preview: Preview) {
        val root=validate(preview.root.toString().toByteArray()).root
        val p=context.getSharedPreferences("zenithw_v2",Context.MODE_PRIVATE)
        val editor=p.edit().putString("themeMode",root.getString("theme")).putString("language",root.getString("language"))
            .putBoolean("dynamicColors",root.getBoolean("dynamicColors")).putBoolean("configureBeforeDownload",root.getBoolean("configure"))
            .putBoolean("rememberDownloadChoices",root.getBoolean("remember")).putBoolean("showMediaPreview",root.getBoolean("preview"))
            .putBoolean("smartEnabled",root.getBoolean("smart"))
            .putInt("concurrentDownloads",root.getInt("concurrency")).putString("defaults",root.getJSONObject("defaults").toString())
        val profiles=root.getJSONArray("profiles")
        for(i in 0 until profiles.length()) {
            val item=profiles.getJSONObject(i)
            editor.putString("profile:"+item.getString("host"),item.getJSONObject("options").toString())
        }
        check(editor.commit())
        val library=root.getJSONArray("library")
        val changes=(0 until library.length()).map { library.getJSONObject(it) }.associateBy { it.getString("identity") }
        store.jobs.value.filter { it.state==JobState.COMPLETED }.forEach { job ->
            changes[duplicateKey(job)]?.let { entry -> store.change(job.id) { it.copy(favorite=entry.getBoolean("favorite"),collection=entry.getString("collection")) } }
        }
    }
}
