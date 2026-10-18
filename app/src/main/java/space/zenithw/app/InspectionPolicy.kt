package space.zenithw.app

/** Bounded discovery, with an explicit patient retry for slow sites. Not a throughput claim. */
object InspectionPolicy {
    fun socketTimeout(slow: Boolean)=if(slow) 20 else 8
    fun totalTimeoutMillis(slow: Boolean)=if(slow) 90000L else 35000L
    fun extractorRetries(slow: Boolean)=if(slow) 3 else 1
    fun shouldCheckUpdate(force: Boolean,automatic: Boolean,now: Long,last: Long,hours: Int): Boolean =
        force || automatic && (last<=0 || now<last || now-last>=hours.coerceIn(1,168)*3600000L)

    fun seconds(raw: String): Double? {
        if(raw.isBlank()) return null
        require(raw.matches(Regex("\\d+(?::[0-5]\\d){0,2}(?:\\.\\d{1,3})?"))) { "Use seconds or HH:MM:SS for a clip." }
        return raw.split(':').fold(0.0) { total,part -> total*60+part.toDouble() }.also {
            require(it.isFinite()) { "Clip time is too large." }
        }
    }
    fun section(start: String,end: String,duration: Int=0): String? {
        if(start.isBlank() && end.isBlank()) return null
        val from=seconds(start) ?: 0.0
        val to=seconds(end)
        require(to==null || to>from) { "Clip end must be after its start." }
        require(duration<=0 || from<duration && (to==null || to<=duration)) { "Clip is outside the video duration." }
        return "*$from-${to ?: "inf"}"
    }
    fun literalFilename(value: String): String {
        val clean=value.trim()
        require(clean.length in 1..150 && clean.toByteArray(Charsets.UTF_8).size<=200 && clean !in listOf(".","..") &&
            clean.none { it.isISOControl() || it in "/\\:*?\"<>|" }) { "Invalid filename." }
        // Escape yt-dlp's interpolation marker in a literal user supplied title.
        return clean.replace("%","%%")+".%(ext)s"
    }
}
