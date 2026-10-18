package space.zenithw.app

import org.json.JSONObject

/** A single media URL is still selectable when opened from the playlist picker. */
internal fun playlistEntries(metadata: JSONObject,sourceUrl: String): List<PlaylistEntry> {
    val entries=metadata.optJSONArray("entries")
    if(entries==null) {
        if(metadata.optString("_type") in listOf("playlist","multi_video")) return emptyList()
        return listOf(PlaylistEntry(1,metadata.optString("title").ifBlank { "Medya" }.take(300),normalizeUrl(sourceUrl)))
    }
    return (0 until minOf(entries.length(),200)).mapNotNull { index ->
        val entry=entries.optJSONObject(index) ?: return@mapNotNull null
        val raw=entry.optString("webpage_url").takeIf { it.startsWith("http") }
            ?: entry.optString("url").takeIf { it.startsWith("http") }
            ?: entry.optString("id").takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }?.let { "https://www.youtube.com/watch?v=$it" }
        raw?.let { runCatching {
            // Use the position as the selection key: extractors can repeat playlist_index.
            PlaylistEntry(index+1,entry.optString("title").ifBlank { "Medya" }.take(300),normalizeUrl(it))
        }.getOrNull() }
    }
}
