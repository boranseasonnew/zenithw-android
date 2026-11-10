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
    fun ariaArguments(connections: Int, retries: Int, speedLimitKbps: Int, certificate: String,timeout: Int=20): String {
        require(certificate.isNotBlank() && !certificate.any { it.isISOControl() })
        val quotedCertificate = "'" + certificate.replace("'", "'\"'\"'") + "'"
        return buildString {
            append("aria2c:-x${connections.coerceIn(1,16)} -s${connections.coerceIn(1,16)}")
            append(" --summary-interval=1 --connect-timeout=15 --timeout=${timeout.coerceIn(5,120)}")
            append(" --max-tries=${retries.coerceIn(0,10)+1} --retry-wait=2")
            append(" --ca-certificate=$quotedCertificate")
            if(speedLimitKbps > 0) append(" --max-overall-download-limit=${speedLimitKbps}K")
        }
    }
    fun isAriaDownloadFailure(message: String): Boolean =
        Regex("(?i)(?:aria2c|libaria2c\\.so) exited with code [1-9][0-9]*").containsMatchIn(message) ||
        Regex("(?i)(?:aria2c|libaria2c\\.so)[^\\n]*(?:not found|cannot execute|permission denied)").containsMatchIn(message)

    fun archiveSkipped(output: String): Boolean = output.lineSequence().any {
        it.trim().startsWith("[download] ") && it.contains("has already been recorded in the archive")
    }

    private val mediaExtensions = setOf("mp4", "mkv", "webm", "mov", "m4v", "mp3", "m4a", "opus", "wav", "flac", "ogg")
    private val subtitleExtensions = setOf("srt", "vtt", "ass", "lrc")

    fun isExpectedOutput(name: String, mode: String, custom: Boolean): Boolean {
        if (!isPublicOutput(name)) return false
        if (custom) return true
        val extension = name.substringAfterLast('.', "").lowercase()
        return extension in if (mode == "SUBTITLES") subtitleExtensions else mediaExtensions
    }

    fun isPublicOutput(name: String): Boolean {
        if(name.startsWith(".") || name.contains(".temp.") || name.contains(".part.") || name=="source-info.json") return false
        if(name.endsWith(".info.json") || name.endsWith(".description")) return true
        return name.substringAfterLast('.', "").lowercase() in mediaExtensions + subtitleExtensions + setOf("jpg", "jpeg", "png", "webp")
    }
    fun progressDetail(percent: Float, eta: Long, line: String): String = when {
        line.contains("ZenithWSquareCover") -> "Kare kapak hazırlanıyor"
        line.contains("ZenithWProcessing") -> "Son işlemler yapılıyor"
        line.contains("ZenithWRefreshing") -> "İndirme bağlantısı yenileniyor"
        line.contains("ZenithWSubtitleFallback") -> "Altyazı alınamadı, medya indirmesi sürüyor"
        line.contains("[EmbedThumbnail]") -> "Kapak görseli gömülüyor"
        line.contains("[Metadata]") -> "Medya bilgileri gömülüyor"
        line.contains("[Merger]") -> "Ses ve video birleştiriliyor"
        line.contains("[VideoRemuxer]") -> "Dosya biçimi hazırlanıyor"
        line.contains("[ExtractAudio]") -> "Ses dosyası hazırlanıyor"
        line.contains("ZenithWNativeFallback") -> "Aria2c yanıt vermedi; normal indirmeye geçiliyor"
        line.contains("[download] Destination") -> "İndirme başlıyor"
        line.startsWith("ZenithWTransfer") && eta > 0 -> "İndirme için yaklaşık ${eta/60} dk ${eta%60} sn · son işlemler hariç"
        line.startsWith("ZenithWTransfer") -> "İndiriliyor"
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

    fun subtitleWarning(output: String): String? {
        val message = output.lowercase()
        if (message.contains("there are no subtitles for the requested languages")) {
            return "Seçilen dillerde altyazı bulunamadı."
        }
        val failingLine = output.lineSequence().lastOrNull { RecoveryPolicy.isSubtitleFailure(it.lowercase()) } ?: return null
        return subtitleWarning(RecoveryPolicy.failure(failingLine))
    }

    fun subtitleWarning(failure: RecoveryFailure): String {
        val code = failure.httpStatus?.let { " (HTTP $it)" }.orEmpty()
        return "İstenen altyazıların tümü kaydedilemedi$code. Medya indirmesi altyazı hatası nedeniyle durdurulmadı."
    }

    fun failureDetail(failure: RecoveryFailure, original: String = ""): String {
        val detail = when (failure.kind) {
            RecoveryKind.SESSION -> if (original.contains("SESSION_UNAVAILABLE", true) || original.contains("seçili oturum bulunamadı", true))
                "Seçili oturum silinmiş veya okunamıyor. Yeniden bağlanıp oturumu tekrar seç." else "Kaynak geçerli bir oturum istiyor. Oturumu yeniden bağla."
            RecoveryKind.FORBIDDEN -> if (failure.httpStatus == 410) "Medya bağlantısının süresi doldu. Yeni bir indirme başlat." else "Kaynak erişimi reddetti. Bağlantıyı veya seçili oturumu kontrol et."
            RecoveryKind.RATE_LIMIT -> "Kaynak çok fazla istek aldı. Bir süre bekleyip tekrar dene."
            RecoveryKind.FORMAT -> "Seçilen kalite veya biçim bu kaynakta bulunamadı."
            RecoveryKind.SUBTITLE -> "Seçilen altyazı alınamadı. Kaynakta başka bir dil veya altyazı biçimi seç."
            RecoveryKind.NETWORK -> "Ağ bağlantısı kesildi veya kaynak zamanında yanıt vermedi."
            RecoveryKind.STORAGE -> "Cihazda indirmeyi tamamlamak için yeterli boş alan yok."
            RecoveryKind.PERMISSION -> "Dosyaya erişilemedi. İndirme klasörünün izinlerini kontrol et."
            else -> failureDetail(original)
        }
        return failure.httpStatus?.let { "HTTP $it · $detail" } ?: detail
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
