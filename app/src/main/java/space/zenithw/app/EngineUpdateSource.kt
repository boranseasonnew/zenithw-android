package space.zenithw.app

import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Resolve the official release without the unauthenticated GitHub API quota. */
object EngineUpdateSource {
    fun repository(channel: String)=when(channel) {
        "stable" -> "yt-dlp/yt-dlp"
        "nightly" -> "yt-dlp/yt-dlp-nightly-builds"
        else -> error("Unknown engine channel")
    }

    fun latestTag(channel: String): String {
        val repository=repository(channel)
        var current=URL("https://github.com/$repository/releases/latest")
        repeat(6) {
            require(current.protocol=="https" && current.host=="github.com")
            val prefix="/$repository/releases/tag/"
            if(current.path.startsWith(prefix)) {
                val tag=current.path.removePrefix(prefix)
                require(tag.matches(Regex("[0-9]{4}\\.[0-9]{2}\\.[0-9]{2}(?:\\.[0-9]+)?")))
                return tag
            }
            require(current.path=="/$repository/releases/latest")
            val connection=current.openConnection() as HttpsURLConnection
            connection.instanceFollowRedirects=false
            connection.connectTimeout=15000
            connection.readTimeout=30000
            connection.setRequestProperty("User-Agent","Zenith-Android")
            try {
                require(connection.responseCode in 300..399) { "Release lookup failed: HTTP ${connection.responseCode}" }
                current=URL(current,connection.getHeaderField("Location") ?: error("Missing release redirect"))
            } finally { connection.disconnect() }
        }
        error("Too many release redirects")
    }
}
