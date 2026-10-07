package space.zenithw.app

/** Pure request rules, shared by the engine and the focused JVM checks. */
object DownloadPolicy {
    data class ThumbnailPlan(val embed: Boolean, val write: Boolean, val convert: String?, val note: String?)
    fun thumbnailPlan(mode: String, container: String, audioFormat: String,
                      embed: Boolean, download: Boolean, imageFormat: String): ThumbnailPlan {
        require(imageFormat in listOf("jpg", "png", "webp"))
        val media = mode != "SUBTITLES"
        val supported = when(mode) {
            "VIDEO" -> container in listOf("mp4", "mkv")
            "AUDIO" -> audioFormat in listOf("m4a", "mp3", "opus", "flac")
            else -> false
        }
        val sidecar = media && embed && !supported
        return ThumbnailPlan(
            embed = media && embed && supported,
            write = download || sidecar,
            convert = if(download || sidecar) imageFormat else if(media && embed && supported) "jpg" else null,
            note = if(sidecar) "Bu format kapağı gömmeyi desteklemiyor; kaynakta görsel varsa ayrı resim olarak kaydedilir." else null
        )
    }
    fun ariaArguments(connections: Int, retries: Int, speedLimitKbps: Int, certificate: String): String {
        require(certificate.isNotBlank() && !certificate.any { it.isISOControl() })
        val quotedCertificate = "'" + certificate.replace("'", "'\"'\"'") + "'"
        return buildString {
            append("aria2c:-x${connections.coerceIn(1,16)} -s${connections.coerceIn(1,16)}")
            append(" --summary-interval=1 --connect-timeout=15 --timeout=20")
            append(" --max-tries=${retries.coerceIn(0,10)+1} --retry-wait=2")
            append(" --ca-certificate=$quotedCertificate")
            if(speedLimitKbps > 0) append(" --max-overall-download-limit=${speedLimitKbps}K")
        }
    }
    fun isAriaDownloadFailure(message: String): Boolean =
        Regex("(?i)(?:aria2c|libaria2c\\.so) exited with code [1-9][0-9]*").containsMatchIn(message) ||
        Regex("(?i)(?:aria2c|libaria2c\\.so)[^\\n]*(?:not found|cannot execute|permission denied)").containsMatchIn(message)

    fun isPublicOutput(name: String): Boolean {
        if(name.startsWith(".") || name.contains(".temp.") || name.contains(".part.")) return false
        return name.substringAfterLast('.', "").lowercase() in setOf(
            "mp4","mkv","webm","mov","m4v","mp3","m4a","opus","wav","flac","ogg",
            "jpg","jpeg","png","webp","srt","vtt","ass","lrc"
        )
    }
    fun progressDetail(percent: Float, eta: Long, line: String): String = when {
        line.contains("[EmbedThumbnail]") -> "Kapak görseli gömülüyor"
        line.contains("[Metadata]") -> "Medya bilgileri gömülüyor"
        line.contains("[Merger]") -> "Ses ve video birleştiriliyor"
        line.contains("[VideoRemuxer]") -> "Dosya biçimi hazırlanıyor"
        line.contains("[ExtractAudio]") -> "Ses dosyası hazırlanıyor"
        line.contains("ZenithWNativeFallback") -> "Aria2c yanıt vermedi; normal indirmeye geçiliyor"
        line.contains("[download] Destination") -> "İndirme başlıyor"
        eta > 0 && percent >= 0 -> "Yaklaşık ${eta/60} dk ${eta%60} sn"
        percent >= 0 -> "İndiriliyor"
        else -> "Bağlantı ve medya hazırlanıyor"
    }
    fun thumbnailWarning(output: String): String? = when {
        output.contains("there aren't any thumbnails to embed", true) ||
        output.contains("No thumbnails on disk", true) ||
        output.contains("no thumbnails to download", true) ||
        output.contains("Skipping embedding the thumbnail", true) ->
            "Kaynakta kullanılabilir kapak bulunamadı; medya dosyası kaydedildi."
        output.contains("doesn't support embedding a thumbnail", true) ->
            "Dosya kaydedildi ancak bu formatta kapak gömülemedi."
        else -> null
    }
    fun failureDetail(message: String): String = when {
        message.contains("EmbedThumbnail",true) || message.contains("Unable to embed",true) ->
            "Kapak gömülemedi. Başka bir format seç veya kapağı ayrı kaydet."
        message.contains("Sign in",true) || message.contains("HTTP Error 401",true) ||
        message.contains("HTTP Error 403",true) -> "Kaynak erişimi reddetti. Bağlantıyı veya seçili oturumu kontrol et."
        message.contains("Requested format is not available",true) -> "Seçilen kalite veya biçim bu kaynakta bulunamadı."
        message.contains("ffmpeg",true) -> "Medya dönüştürülemedi. Başka bir dosya biçimiyle tekrar dene."
        else -> "İndirme tamamlanamadı. Bağlantıyı veya seçili oturumu kontrol et."
    }
}
