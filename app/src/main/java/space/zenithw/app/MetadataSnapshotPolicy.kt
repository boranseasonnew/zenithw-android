package space.zenithw.app

internal object MetadataSnapshotPolicy {
    const val PREVIEW_TTL_MS = 5 * 60_000L
    private const val UNKNOWN_LINK_TTL_MS = 90_000L
    private const val EXPIRY_MARGIN_SECONDS = 30L

    fun fresh(ageMillis: Long, linkExpirySeconds: Long, nowSeconds: Long): Boolean {
        if (ageMillis !in 0 until PREVIEW_TTL_MS) return false
        if (linkExpirySeconds == 0L) return ageMillis < UNKNOWN_LINK_TTL_MS
        return linkExpirySeconds - nowSeconds > EXPIRY_MARGIN_SECONDS
    }

    // These fields affect extraction. Language/format selection happens later against
    // the complete set of formats and captions already contained in the snapshot.
    data class ExtractionOptions(
        val proxy: String,
        val networkMode: String,
        val userAgent: String,
        val includeTranslatedSubtitles: Boolean,
    )

    fun extractionOptions(options: DownloadOptions) = ExtractionOptions(
        options.proxy, options.networkMode, options.userAgent, options.autoSubtitles,
    )
}
