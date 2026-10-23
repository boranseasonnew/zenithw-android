package space.zenithw.app

fun main() {
    val id="aqz-KE-bpKQ"
    listOf("https://www.youtube.com/watch?v=$id&list=PLx&t=12", "https://youtu.be/$id?si=abc", "https://www.youtube.com/shorts/$id", "https://m.youtube.com/embed/$id").forEach {
        check(PreviewPolicy.youtubeVideoId(it)==id)
    }
    listOf("https://youtube.com.evil.example/watch?v=$id","https://youtu.be/short","https://example.org/watch?v=$id","not a url").forEach {
        check(PreviewPolicy.youtubeVideoId(it)==null)
    }
    check(PreviewPolicy.isCollection("https://www.youtube.com/watch?v=$id&list=PLx"))
    check(!PreviewPolicy.isPlaylistOnly("https://www.youtube.com/watch?v=$id&list=PLx"))
    check(PreviewPolicy.isPlaylistOnly("https://www.youtube.com/playlist?list=PLx"))
    check(!PreviewPolicy.isCollection("https://example.org/video?list=private"))
    println("PASS: 12 conservative video/playlist identity checks")
}
