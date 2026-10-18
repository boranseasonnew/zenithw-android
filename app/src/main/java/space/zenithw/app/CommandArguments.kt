package space.zenithw.app

/** Download arguments only. These tokens are passed to yt-dlp directly, never to a shell. */
fun parseCustomArguments(raw: String): List<String> {
    require(raw.length <= 8192) { "Komut en fazla 8192 karakter olabilir." }
    val tokens = tokenizeDownloadArguments(raw)
    require(tokens.isNotEmpty()) { "En az bir yt-dlp seçeneği gir." }
    require(tokens.size <= 160) { "Çok fazla komut seçeneği." }
    var index = 0
    while (index < tokens.size) {
        val token = tokens[index]
        val equalAt = token.indexOf('=')
        val option = if (equalAt > 0) token.substring(0, equalAt) else token
        when {
            option in commandValueOptions -> {
                if (equalAt > 0) require(token.substring(equalAt + 1).isNotEmpty()) { "$option için değer gir." }
                else {
                    index++
                    require(index < tokens.size && !tokens[index].startsWith("-")) { "$option için değer gir." }
                }
            }
            option in commandSwitchOptions -> require(equalAt < 0) { "$option değer almaz." }
            token.length > 2 && token.take(2) in commandShortValueOptions && token[2] != '-' -> {
                require(token.substring(2).isNotEmpty()) { "Seçenek değeri eksik." }
            }
            else -> throw IllegalArgumentException("Desteklenmeyen seçenek: ${token.take(70)}")
        }
        index++
    }
    return tokens
}

fun validateCustomArguments(raw: String) { parseCustomArguments(raw) }

private val commandShortValueOptions = setOf("-f", "-S", "-r", "-R", "-N", "-I")
private val commandValueOptions = commandShortValueOptions + setOf(
    "--format", "--format-sort", "--merge-output-format", "--remux-video", "--recode-video",
    "--audio-format", "--audio-quality", "--fixup",
    "--sub-langs", "--sub-format", "--convert-subs", "--convert-thumbnails",
    "--playlist-items", "--playlist-start", "--playlist-end", "--max-downloads",
    "--min-filesize", "--max-filesize", "--match-filter", "--break-match-filters",
    "--date", "--datebefore", "--dateafter", "--min-views", "--max-views",
    "--limit-rate", "--throttled-rate", "--retries", "--fragment-retries", "--extractor-retries",
    "--file-access-retries", "--concurrent-fragments", "--socket-timeout", "--buffer-size",
    "--http-chunk-size", "--sleep-interval", "--max-sleep-interval", "--sleep-requests",
    "--sleep-subtitles", "--retry-sleep", "--proxy", "--geo-verification-proxy", "--user-agent",
    "--referer", "--add-headers", "--impersonate", "--source-address", "--download-sections",
    "--sponsorblock-remove", "--sponsorblock-mark", "--sponsorblock-chapter-title",
    "--extractor-args", "--compat-options"
)
private val commandSwitchOptions = setOf(
    "-x", "-v", "-q", "-w", "-c", "-k", "-4", "-6",
    "--extract-audio", "--verbose", "--quiet", "--no-warnings", "--force-ipv4", "--force-ipv6",
    "--no-playlist", "--yes-playlist", "--playlist-reverse", "--playlist-random",
    "--ignore-errors", "--no-abort-on-error", "--abort-on-error", "--abort-on-unavailable-fragments",
    "--skip-unavailable-fragments", "--continue", "--no-continue", "--no-overwrites", "--force-overwrites",
    "--keep-video", "--no-keep-video", "--restrict-filenames", "--no-restrict-filenames", "--windows-filenames",
    "--no-windows-filenames", "--no-part", "--part", "--mtime", "--no-mtime",
    "--write-subs", "--no-write-subs", "--write-auto-subs", "--no-write-auto-subs", "--embed-subs", "--no-embed-subs",
    "--embed-thumbnail", "--no-embed-thumbnail", "--write-thumbnail", "--no-write-thumbnail",
    "--write-all-thumbnails", "--embed-metadata", "--no-embed-metadata", "--embed-chapters", "--no-embed-chapters",
    "--write-info-json", "--no-write-info-json", "--write-description", "--no-write-description",
    "--no-check-certificates", "--prefer-insecure", "--geo-bypass", "--no-geo-bypass",
    "--hls-use-mpegts", "--no-hls-use-mpegts", "--hls-prefer-native",
    "--video-multistreams", "--no-video-multistreams", "--audio-multistreams", "--no-audio-multistreams",
    "--format-sort-force", "--no-format-sort-force", "--prefer-free-formats", "--no-prefer-free-formats",
    "--force-keyframes-at-cuts", "--no-force-keyframes-at-cuts", "--no-sponsorblock"
)

private fun tokenizeDownloadArguments(raw: String): List<String> {
    val result = mutableListOf<String>()
    val value = StringBuilder()
    var quote: Char? = null
    var escaped = false
    var started = false
    raw.forEach { character ->
        require(character == '\n' || character == '\r' || character == '\t' || !character.isISOControl()) { "Geçersiz komut karakteri." }
        when {
            escaped -> { value.append(character); escaped = false; started = true }
            character == '\\' && quote != '\'' -> { escaped = true; started = true }
            quote != null -> if (character == quote) quote = null else value.append(character)
            character == '\'' || character == '"' -> { quote = character; started = true }
            character.isWhitespace() -> if (started) { result += value.toString(); value.clear(); started = false }
            else -> {
                require(character !in ";|&<>`") { "Kabuk komutları desteklenmiyor." }
                value.append(character); started = true
            }
        }
    }
    require(quote == null && !escaped) { "Tırnak veya kaçış karakteri tamamlanmamış." }
    if (started) result += value.toString()
    return result
}
