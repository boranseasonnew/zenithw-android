package space.zenithw.app

import java.io.IOException
import java.net.URL
import org.json.JSONObject

/** Official release routes only; binaries are checked against the same tagged checksum. */
object EngineUpdateSource {
    fun repository(channel: String)=when(channel) {
        "stable" -> "yt-dlp/yt-dlp"
        "nightly" -> "yt-dlp/yt-dlp-nightly-builds"
        else -> throw IOException("UPDATE_CHANNEL: unknown channel")
    }
    fun validatedTag(tag: String): String {
        if(!tag.matches(Regex("[0-9]{4}\\.[0-9]{2}\\.[0-9]{2}(?:\\.[0-9]+)?")))
            throw IOException("UPDATE_TAG: invalid release version")
        return tag
    }
    fun latestTag(channel: String, http: EngineHttp=EngineHttp()): String {
        val repository=repository(channel)
        val failures=mutableListOf<Exception>()
        // Direct asset redirects work independently of the HTML page and API quota.
        for(asset in listOf(false,true)) {
            try {
                val entry="/$repository/releases/latest" + if(asset) "/download/SHA2-256SUMS" else ""
                var current=URL("https://github.com$entry")
                repeat(6) {
                    if(current.protocol!="https" || current.host!="github.com" || current.userInfo!=null || current.port !in listOf(-1,443))
                        throw IOException("UPDATE_HOST: untrusted release redirect")
                    val prefix="/$repository/releases/" + if(asset) "download/" else "tag/"
                    if(current.path.startsWith(prefix)) {
                        val suffix=current.path.removePrefix(prefix)
                        return validatedTag(if(asset) {
                            if(!suffix.endsWith("/SHA2-256SUMS")) throw IOException("UPDATE_ASSET: unexpected release file")
                            suffix.removeSuffix("/SHA2-256SUMS")
                        } else suffix)
                    }
                    if(current.path!=entry) throw IOException("UPDATE_REDIRECT: unexpected release path")
                    current=http.redirect(current.toString())
                }
                throw IOException("UPDATE_REDIRECT: too many release redirects")
            } catch(error: Exception) { failures.add(error) }
        }
        try {
            val json=JSONObject(String(http.read("https://api.github.com/repos/$repository/releases/latest",2*1024*1024,"application/vnd.github+json"),Charsets.UTF_8))
            return validatedTag(json.getString("tag_name"))
        } catch(error: Exception) {
            failures.forEach(error::addSuppressed)
            throw IOException("UPDATE_LOOKUP: ${diagnostic(error)}",error)
        }
    }
    fun asset(channel: String, tag: String, name: String, limit: Int, http: EngineHttp=EngineHttp()): ByteArray {
        val repository=repository(channel)
        validatedTag(tag)
        if(name !in setOf("yt-dlp","SHA2-256SUMS")) throw IOException("UPDATE_ASSET: unsupported file")
        try { return http.read("https://github.com/$repository/releases/download/$tag/$name",limit) }
        catch(direct: Exception) {
            try {
                val release=JSONObject(String(http.read("https://api.github.com/repos/$repository/releases/tags/$tag",2*1024*1024,"application/vnd.github+json"),Charsets.UTF_8))
                if(release.getString("tag_name")!=tag) throw IOException("UPDATE_TAG: release mismatch")
                val assets=release.getJSONArray("assets")
                val asset=(0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.optString("name")==name }
                    ?: throw IOException("UPDATE_ASSET: missing file")
                val url=asset.getString("url")
                if(!url.matches(Regex("https://api\\.github\\.com/repos/${Regex.escape(repository)}/releases/assets/[0-9]+")))
                    throw IOException("UPDATE_HOST: unexpected asset API")
                return http.read(url,limit)
            } catch(error: Exception) {
                error.addSuppressed(direct)
                throw IOException("UPDATE_DOWNLOAD ($name): ${diagnostic(error)}",error)
            }
        }
    }
    fun diagnostic(error: Throwable): String {
        val detail=error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
        val previous=error.suppressed.firstOrNull()?.let { "; fallback: ${it.message ?: it.javaClass.simpleName}" }.orEmpty()
        return (detail+previous).replace(Regex("https?://[^\\s]+"),"[link]")
            .replace(Regex("[\\r\\n\\u0000]")," ").take(175)
    }
}
