package space.zenithw.app

fun main() {
    check(DownloadPolicy.archiveSkipped("[download] LAqBFGH1r_c: has already been recorded in the archive"))
    check(!DownloadPolicy.archiveSkipped("[download] Destination: example.mp4"))
    check(!DownloadPolicy.archiveSkipped("title: has already been recorded in the archive"))

    var count=0
    fun expect(condition: Boolean) { check(condition); count++ }
    val p=DownloadPolicy
    expect(p.thumbnailPlan("AUDIO","webm","opus",true,false,"jpg").embed)
    expect(p.thumbnailPlan("VIDEO","mp4","wav",true,false,"jpg").embed)
    expect(p.thumbnailPlan("VIDEO","mkv","opus",true,false,"jpg").embed)
    val wav=p.thumbnailPlan("AUDIO","mp4","wav",true,false,"jpg")
    expect(!wav.embed && wav.write && wav.note!=null)
    val webm=p.thumbnailPlan("VIDEO","webm","m4a",true,false,"jpg")
    expect(!webm.embed && webm.write && webm.note!=null)
    expect(!p.thumbnailPlan("SUBTITLES","mp4","m4a",true,false,"jpg").embed)
    expect(p.thumbnailPlan("AUDIO","mp4","opus",true,false,"webp").convert=="jpg")
    expect(p.thumbnailPlan("AUDIO","mp4","opus",true,true,"png").convert=="png")
    expect(p.thumbnailPlan("AUDIO","mp4","m4a",false,false,"jpg").convert==null)
    val arguments=p.ariaArguments(4,0,100,"/private/with space/cert.pem")
    expect(arguments.contains("-x4 -s4"))
    expect(arguments.contains("--max-tries=1"))
    expect(arguments.contains("--timeout=20"))
    expect(arguments.contains("--max-overall-download-limit=100K"))
    expect(arguments.contains("--ca-certificate='/private/with space/cert.pem'"))
    expect(p.isAriaDownloadFailure("ERROR: aria2c exited with code 19"))
    expect(p.isAriaDownloadFailure("ERROR: aria2c not found"))
    expect(!p.isAriaDownloadFailure("ERROR: Sign in to confirm you're not a bot"))
    expect(!p.isAriaDownloadFailure("ERROR: Unable to embed using ffprobe & ffmpeg"))
    expect(!p.isAriaDownloadFailure("ERROR: HTTP Error 403: Forbidden"))
    for(name in listOf("session.txt","video.mp4.aria2","video.mp4.part","video.temp.mp4","video.part.mp4","video.ytdl",".hidden.mp4"))
        expect(!p.isPublicOutput(name))
    for(name in listOf("video.mp4","audio.opus","cover.jpg","subtitles.tr.srt"))
        expect(p.isPublicOutput(name))
    expect(p.progressDetail(-1f,-1,"[youtube] extracting")=="Bağlantı ve medya hazırlanıyor")
    expect(p.progressDetail(99f,10,"[EmbedThumbnail] Embedding")== "Kapak görseli gömülüyor")
    expect(p.thumbnailWarning("[EmbedThumbnail] There aren't any thumbnails to embed")!=null)
    expect(p.thumbnailWarning("[EmbedThumbnail] Embedding thumbnail in file")==null)
    expect(p.failureDetail("ERROR: Unable to embed using ffprobe & ffmpeg").startsWith("Kapak gömülemedi"))
    println("$count focused policy checks passed")
}
