package space.zenithw.app

import java.net.URI
import java.net.URLDecoder

/** Conservative identity rules: preserve the query and fragment in cache keys.
 * Only the explicit public YouTube oEmbed hint uses the verified video ID. */
internal object PreviewPolicy {
    private fun youtubeHost(host: String)=host=="youtube.com" || host.endsWith(".youtube.com") || host=="youtube-nocookie.com" || host.endsWith(".youtube-nocookie.com")
    private fun query(uri: URI,name: String): String?=uri.rawQuery?.split('&')?.firstNotNullOfOrNull {
        val parts=it.split('=',limit=2)
        if(parts[0]==name) URLDecoder.decode(parts.getOrElse(1) { "" },"UTF-8") else null
    }
    fun youtubeVideoId(url: String): String?=runCatching {
        val uri=URI(url);val host=uri.host?.lowercase().orEmpty()
        val id=when {
            host=="youtu.be" -> uri.path.removePrefix("/").substringBefore('/')
            youtubeHost(host) && uri.path=="/watch" -> query(uri,"v")
            youtubeHost(host) && (uri.path.startsWith("/shorts/") || uri.path.startsWith("/embed/") || uri.path.startsWith("/live/")) -> uri.path.split('/').getOrNull(2)
            else -> null
        }
        id?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
    }.getOrNull()
    fun isCollection(url: String): Boolean=runCatching {
        val uri=URI(url)
        uri.path.orEmpty().trimEnd('/').endsWith("/playlist") || youtubeHost(uri.host?.lowercase().orEmpty()) && query(uri,"list")!=null
    }.getOrDefault(false)
    fun isPlaylistOnly(url: String): Boolean=runCatching {
        val uri=URI(url)
        uri.path.orEmpty().trimEnd('/').endsWith("/playlist") && youtubeVideoId(url)==null
    }.getOrDefault(false)
}
