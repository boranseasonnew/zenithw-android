package space.zenithw.app

import java.net.URI
import java.security.MessageDigest
import java.io.InputStream

/** Bounded and platform-independent input policies, shared by intents and TXT import. */
object IncomingLinks {
    const val MAX_LINKS=50
    const val MAX_BYTES=512*1024
    private val pattern=Regex("https?://[^\\s<>\"\\u0000-\\u001f]+",RegexOption.IGNORE_CASE)
    fun valid(raw: String): String? = runCatching {
        require(raw.length in 1..8192 && raw.none(Char::isISOControl))
        val uri=URI(raw)
        require(uri.scheme.lowercase() in setOf("https","http") && uri.host!=null &&
            uri.host.contains('.') && uri.rawUserInfo==null && uri.port in -1..65535)
        // Fragments are client navigation; query strings (including signatures) are preserved.
        raw.substringBefore('#')
    }.getOrNull()
    fun extract(text: String): List<String> {
        require(text.length<=MAX_BYTES)
        val found=pattern.findAll(text).mapNotNull { match ->
            val raw=match.value.trimEnd('.',',',';',')',']','}')
            valid(raw)
        }.distinctBy(SourceIdentity::key).take(MAX_LINKS+1).toList()
        require(found.size<=MAX_LINKS)
        return found
    }
    data class Import(val links: List<String>,val invalid: Int,val duplicates: Int)
    fun read(input: InputStream,cancelled: ()->Boolean={false}): Import {
        val source=input.buffered(8192)
        val links=linkedMapOf<String,String>();var invalid=0;var duplicates=0;var bytes=0;var lines=0
        // Read bounded bytes per line, never BufferedReader.readLine on an untrusted giant line.
        val line=java.io.ByteArrayOutputStream()
        fun accept() {
            val raw=line.toString("UTF-8").trim().removePrefix("\uFEFF");line.reset()
            if(raw.isEmpty()) return
            val url=valid(raw)
            if(url==null) invalid++ else if(links.putIfAbsent(SourceIdentity.key(url),url)!=null) duplicates++
            require(links.size<=MAX_LINKS) { "import_limit" }
        }
        while(true) {
            check(!cancelled()) { "cancelled" }
            val byte=source.read();if(byte<0) break
            require(++bytes<=MAX_BYTES) { "import_limit" }
            if(byte==10) { require(++lines<=10000);accept() }
            else { require(line.size()<8192) { "import_limit" };line.write(byte) }
        }
        if(line.size()>0) accept()
        return Import(links.values.toList(),invalid,duplicates)
    }
}

object SourceIdentity {
    private val youtubeId=Regex("[a-zA-Z0-9_-]{11}")
    fun platform(url: String): String=runCatching { URI(url).host.lowercase().removePrefix("www.") }.getOrDefault("")
    fun key(url: String): String {
        val uri=runCatching { URI(url) }.getOrNull() ?: return url
        val host=platform(url)
        val query=uri.rawQuery.orEmpty().split('&').map { it.split('=',limit=2) }
        val video=when {
            host=="youtu.be" -> uri.path.trim('/')
            host in setOf("youtube.com","m.youtube.com","music.youtube.com") ->
                if(uri.path=="/watch") query.firstOrNull { it[0]=="v" }?.getOrNull(1)
                else uri.path.split('/').let { if(it.getOrNull(1) in setOf("shorts","embed","live")) it.getOrNull(2) else null }
            else -> null
        }
        // Do not collapse playlist URLs, arbitrary signatures or platform-specific parameters.
        return if(video!=null && youtubeId.matches(video) && query.none { it[0]=="list" }) "youtube:$video"
            else url.substringBefore('#')
    }
    fun fingerprint(parts: List<String>): String=MessageDigest.getInstance("SHA-256")
        .digest(parts.joinToString("\u0000").toByteArray()).joinToString("") { "%02x".format(it) }
}

object FilenamePolicy {
    val variables=linkedMapOf("title" to "%(title).150B","uploader" to "%(uploader).80B",
        "channel" to "%(channel).80B","date" to "%(upload_date)s","id" to "%(id)s",
        "resolution" to "%(resolution)s","ext" to "%(ext)s","platform" to "%(extractor_key)s")
    const val DEFAULT="{title} [{id}].{ext}"
    fun compile(template: String): String {
        require(template.length in 1..250 && template.none { it.isISOControl() || it in "/\\:*?\"<>|%" } && !template.contains(".."))
        val compiled=Regex("\\{([a-z]+)}").replace(template) { variables[it.groupValues[1]] ?: error("template_variable") }
        require(!compiled.contains('{') && !compiled.contains('}') && template.endsWith(".{ext}"))
        return compiled
    }
    fun preview(template: String): String {
        compile(template)
        val sample=mapOf("title" to "Video","uploader" to "Creator","channel" to "Channel",
            "date" to "20261104","id" to "abc123","resolution" to "1920x1080","ext" to "mp4","platform" to "YouTube")
        return Regex("\\{([a-z]+)}").replace(template) { sample.getValue(it.groupValues[1]) }
    }
    /** Imported backups may interpolate only public naming fields, never headers/cookies. */
    fun validateNative(template: String) {
        require(template.length in 1..250 && template.none { it.isISOControl() || it in "/\\:*?\"<>|" } && !template.contains(".."))
        val fields=Regex("%\\((title|uploader|channel|upload_date|id|resolution|ext|extractor_key)\\)(?:\\.[0-9]{1,3})?[sB]")
        val remaining=fields.replace(template,"").replace("%%","")
        require('%' !in remaining && template.endsWith(".%(ext)s"))
    }
    fun folder(value: String): String {
        require(value.length<=80 && value !in setOf(".","..") && value.none { it.isISOControl() || it in "/\\:*?\"<>|%" })
        return value.trim().trimEnd('.')
    }
}

enum class RecoveryKind { FORBIDDEN, RATE_LIMIT, SESSION, FORMAT, SUBTITLE, NETWORK, STORAGE, PERMISSION, UNSUPPORTED, CONVERSION, ENGINE, UNKNOWN }

data class RecoveryFailure(val kind: RecoveryKind, val httpStatus: Int? = null) {
    val code: String get() = httpStatus?.let { "HTTP_$it" } ?: kind.name
}

object RecoveryPolicy {
    private val statusPattern = Regex("(?i)\\b(?:HTTP(?:\\s+(?:Error|status(?: code)?))?\\s*[:=_]?\\s*|status(?:code)?\\s*=\\s*)([1-5][0-9]{2})\\b")

    fun failure(message: String): RecoveryFailure {
        // yt-dlp may include earlier warnings or retry attempts. The terminal
        // error line owns the final classification and its HTTP status.
        val lines = message.lines().map(String::trim)
        val lastError = lines.indexOfLast {
            it.startsWith("ERROR:", ignoreCase = true) || it.startsWith("ERROR ", ignoreCase = true)
        }
        // The native downloader can put the HTTP status on the continuation
        // after an empty ERROR header. Earlier retry warnings are unrelated.
        val terminal = if (lastError < 0) message else lines.drop(lastError)
            .takeWhile { !it.startsWith("WARNING:", ignoreCase = true) }.joinToString("\n")
        val m = terminal.lowercase()
        val status = statusPattern.findAll(m).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()
        val kind = when {
            listOf("session_unavailable", "auth_required", "seçili oturum bulunamadı", "sign in", "login required", "expired session", "cookies are no longer valid").any { it in m } -> RecoveryKind.SESSION
            "no space" in m || "enospc" in m || "storage_limit" in m -> RecoveryKind.STORAGE
            "permission denied" in m || "securityexception" in m || "input_permission" in m || "output_permission" in m -> RecoveryKind.PERMISSION
            isSubtitleFailure(m) -> RecoveryKind.SUBTITLE
            status == 401 -> RecoveryKind.SESSION
            status == 429 || "too many requests" in m -> RecoveryKind.RATE_LIMIT
            status == 403 || status == 410 -> RecoveryKind.FORBIDDEN
            "requested format" in m || "no video formats" in m || "no_formats" in m -> RecoveryKind.FORMAT
            status in setOf(408, 500, 502, 503, 504) || listOf("timed out", "timeout", "connection reset", "network is unreachable", "temporary failure", "connection aborted", "unable to resolve host", "remote end closed connection", "incomplete read").any { it in m } -> RecoveryKind.NETWORK
            "unsupported url" in m || "unsupported source" in m || "unsupported_url" in m -> RecoveryKind.UNSUPPORTED
            "ffmpeg" in m || "conversion failed" in m || "conversion_failed" in m || "encoder_unavailable" in m || "invalid_output" in m -> RecoveryKind.CONVERSION
            "python" in m || "engine" in m -> RecoveryKind.ENGINE
            else -> RecoveryKind.UNKNOWN
        }
        return RecoveryFailure(kind, status)
    }

    internal fun isSubtitleFailure(lowercaseMessage: String): Boolean =
        listOf("unable to download video subtitles", "cannot write video subtitles", "subtitle_conversion_failed", "no_subtitle_output", "unable to embed subtitles", "unable to convert subtitles").any { it in lowercaseMessage }

    fun classify(message: String): RecoveryKind = failure(message).kind
    fun retryable(kind: RecoveryKind)=kind in setOf(RecoveryKind.NETWORK,RecoveryKind.RATE_LIMIT)

    fun downloadRetryLimit(kind: RecoveryKind): Int = when (kind) {
        RecoveryKind.NETWORK -> 2
        RecoveryKind.RATE_LIMIT -> 1
        else -> 0
    }
}

data class ClipRange(val start: Double,val end: Double) {
    fun validate(duration: Double=0.0): ClipRange {
        require(start.isFinite() && end.isFinite() && start>=0 && end>start && (duration<=0 || end<=duration))
        return this
    }
}

object BackupPolicy {
    /** Reject deep JSON before handing it to Android's recursive JSONObject parser. */
    fun text(bytes: ByteArray): String {
        require(bytes.size<=512*1024)
        val text=Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        var quoted=false;var escaped=false;var depth=0;var nodes=0
        for(char in text) {
            require(char!='\u0000')
            if(quoted) {
                if(escaped) escaped=false else if(char=='\\') escaped=true else if(char=='"') quoted=false
            } else when(char) {
                '"' -> quoted=true
                '{','[' -> { require(++depth<=16 && ++nodes<=20000) }
                '}',']' -> require(--depth>=0)
            }
        }
        require(!quoted && depth==0)
        return text
    }
}
