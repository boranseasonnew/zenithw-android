package space.zenithw.app

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class EngineHttp(private val connect: (URL)->HttpsURLConnection = { it.openConnection() as HttpsURLConnection }) {
    private val hosts=setOf("api.github.com","github.com","release-assets.githubusercontent.com","objects.githubusercontent.com")
    private fun connection(address: URL, accept: String): HttpsURLConnection {
        if(address.protocol!="https" || address.host !in hosts || address.userInfo!=null || address.port !in listOf(-1,443))
            throw IOException("UPDATE_HOST: untrusted redirect")
        return connect(address).apply {
            instanceFollowRedirects=false
            connectTimeout=15000; readTimeout=30000; useCaches=false
            setRequestProperty("User-Agent","Zenith-Android/${BuildConfig.VERSION_NAME}")
            setRequestProperty("Cache-Control","no-cache")
            setRequestProperty("Accept",accept)
        }
    }
    fun redirect(address: String): URL {
        val url=URL(address)
        val connection=connection(url,"*/*")
        try {
            val code=connection.responseCode
            if(code !in listOf(301,302,303,307,308)) throw IOException("HTTP $code (${url.host})")
            val location=connection.getHeaderField("Location") ?: throw IOException("UPDATE_REDIRECT: missing location")
            return URL(url,location)
        } finally { connection.disconnect() }
    }
    fun read(address: String, limit: Int, accept: String="application/octet-stream"): ByteArray {
        var current=URL(address)
        repeat(6) {
            val connection=connection(current,accept)
            try {
                when(val code=connection.responseCode) {
                    301,302,303,307,308 -> {
                        val location=connection.getHeaderField("Location") ?: throw IOException("UPDATE_REDIRECT: missing location")
                        current=URL(current,location)
                    }
                    200 -> return connection.inputStream.use { input ->
                        val output=ByteArrayOutputStream()
                        val buffer=ByteArray(32768)
                        while(true) {
                            val count=input.read(buffer)
                            if(count<0) break
                            if(output.size()+count>limit) throw IOException("UPDATE_SIZE: response too large")
                            output.write(buffer,0,count)
                        }
                        output.toByteArray()
                    }
                    else -> throw IOException("HTTP $code (${current.host})")
                }
            } finally { connection.disconnect() }
        }
        throw IOException("UPDATE_REDIRECT: too many redirects")
    }
}
