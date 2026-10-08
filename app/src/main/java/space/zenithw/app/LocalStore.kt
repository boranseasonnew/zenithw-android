package space.zenithw.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class LocalStore(context: Context) {
    private val preferences=context.getSharedPreferences("zenithw_v2",Context.MODE_PRIVATE)
    private val mutableJobs=MutableStateFlow(loadJobs())
    val jobs=mutableJobs.asStateFlow()
    var language: String
        get()=AppLanguage.valid(preferences.getString("language","tr") ?: "tr")
        set(value) { preferences.edit().putString("language",AppLanguage.valid(value)).apply() }
    var autoUpdate: Boolean
        get()=preferences.getBoolean("autoUpdate",true)
        set(value) { preferences.edit().putBoolean("autoUpdate",value).apply() }
    var channel: String
        get()=preferences.getString("channel","stable") ?: "stable"
        set(value) { preferences.edit().putString("channel",value).apply() }
    var lastEngineVersion: String
        get()=preferences.getString("engineVersion","Birlikte gelen sürüm") ?: ""
        set(value) { preferences.edit().putString("engineVersion",value).apply() }
    var lastEngineCheck: Long
        get()=preferences.getLong("engineChecked",0)
        set(value) { preferences.edit().putLong("engineChecked",value).apply() }
    var selectedCookieId: String?
        get()=preferences.getString("selectedCookieId",null)
        set(value) { preferences.edit().putString("selectedCookieId",value).apply() }
    var defaultOptions: DownloadOptions
        get()=runCatching {
            preferences.getString("defaults",null)?.let { DownloadOptions.from(JSONObject(it)) }
                ?: DownloadOptions(embedThumbnail=true)
        }.getOrDefault(DownloadOptions(embedThumbnail=true))
        set(value) {
            preferences.edit().putString("defaults",value.copy(cookieId=null,scheduledAt=0,selectedFormat="").json().toString()).apply()
        }
    private fun loadJobs(): List<DownloadJob> = runCatching {
        val array=JSONArray(preferences.getString("jobs","[]"))
        (0 until array.length()).mapNotNull { runCatching { DownloadJob.from(array.getJSONObject(it)) }.getOrNull() }
    }.getOrDefault(emptyList())
    @Synchronized fun put(job: DownloadJob) {
        val list=(mutableJobs.value.filterNot { it.id==job.id } + job).sortedByDescending { it.createdAt }
        preferences.edit().putString("jobs",JSONArray(list.map { it.json() }).toString()).commit()
        mutableJobs.value=list
    }
    @Synchronized fun change(id: String, edit: (DownloadJob)->DownloadJob) {
        mutableJobs.value.find { it.id==id }?.let { put(edit(it)) }
    }
    fun get(id: String)=mutableJobs.value.find { it.id==id }
}

class CookieVault(private val context: Context) {
    private val preferences=context.getSharedPreferences("zenithw_cookie_vault",Context.MODE_PRIVATE)
    private val alias="zenithw_v2_cookie_key"
    private fun key(): SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias,null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    fun profiles(): List<CookieProfile> = preferences.all.values.mapNotNull { raw ->
        runCatching {
            val data=JSONObject(raw as String)
            CookieProfile(data.getString("id"),data.getString("host"),data.getInt("count"),data.getLong("updated"))
        }.getOrNull()
    }.sortedByDescending { it.updatedAt }
    @Synchronized fun capture(host: String, header: String): CookieProfile {
        require(host.isNotBlank() && !host.any { it.isISOControl() })
        val cookies=header.split(';').mapNotNull { value ->
            val pair=value.trim().split('=',limit=2)
            if(pair.size==2 && pair[0].isNotBlank() && pair.none { it.contains('\t') || it.contains('\n') || it.contains('\r') })
                pair[0] to pair[1] else null
        }
        require(cookies.isNotEmpty()) { "Bu siteden henüz oturum bilgisi alınamadı." }
        val domain=host.lowercase()
        val existing=profiles().firstOrNull { it.host==domain }
        val id=existing?.id ?: UUID.randomUUID().toString()
        val cookieText="# Netscape HTTP Cookie File\n"+cookies.joinToString("\n") {
            "$domain\tFALSE\t/\tTRUE\t0\t${it.first}\t${it.second}"
        }+"\n"
        return save(id,domain,cookies.size,cookieText)
    }
    @Synchronized fun importNetscape(text: String): CookieProfile {
        require(text.startsWith("# Netscape HTTP Cookie File") || text.startsWith("# HTTP Cookie File"))
        require(!text.contains('\u0000'))
        val lines=text.lineSequence().filter { it.isNotBlank() && (!it.startsWith("#") || it.startsWith("#HttpOnly_")) }.toList()
        require(lines.isNotEmpty() && lines.size<=2000)
        val rows=lines.map { line ->
            val fields=line.removePrefix("#HttpOnly_").split('\t')
            require(fields.size==7)
            require(fields[0].matches(Regex("\\.?[a-zA-Z0-9.-]+")))
            require(fields[1] in listOf("TRUE","FALSE") && fields[3] in listOf("TRUE","FALSE"))
            require(fields[2].startsWith("/") && fields[4].toLongOrNull()?.let { it>=0 }==true)
            require(fields[5].isNotBlank() && fields.none { value -> value.any { it.isISOControl() } })
            fields
        }
        val hosts=rows.map { it[0].removePrefix(".") }.distinct()
        val host=if(hosts.size==1) hosts.first() else "${hosts.first()} +${hosts.size-1} site"
        return save(UUID.randomUUID().toString(),host,rows.size,"# Netscape HTTP Cookie File\n"+lines.joinToString("\n")+"\n")
    }
    private fun save(id: String,host: String,count: Int,text: String): CookieProfile {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,key()) }
        val encrypted=cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        val updated=System.currentTimeMillis()
        val data=JSONObject().put("id",id).put("host",host).put("count",count)
            .put("updated",updated).put("iv",Base64.encodeToString(cipher.iv,Base64.NO_WRAP))
            .put("payload",Base64.encodeToString(encrypted,Base64.NO_WRAP))
        require(preferences.edit().putString(id,data.toString()).commit())
        return CookieProfile(id,host,count,updated)
    }
    fun temporaryFile(id: String?, directory: File): File? {
        if(id==null) return null
        val raw=preferences.getString(id,null) ?: error("Seçili oturum bulunamadı; yeniden bağlan.")
        val data=JSONObject(raw)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(data.getString("iv"),Base64.NO_WRAP)))
        }
        val bytes=cipher.doFinal(Base64.decode(data.getString("payload"),Base64.NO_WRAP))
        return File(directory,"session.txt").apply { writeBytes(bytes) }
    }
    fun remove(id: String) { preferences.edit().remove(id).apply() }
}
