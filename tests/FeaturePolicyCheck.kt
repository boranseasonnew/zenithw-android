package space.zenithw.app

fun main() {
    var checks=0
    fun verify(value: Boolean) { check(value);checks++ }
    verify(IncomingLinks.extract("Watch https://youtu.be/aqz-KE-bpKQ and https://www.youtube.com/watch?v=aqz-KE-bpKQ").size==1)
    verify(IncomingLinks.valid("javascript:alert(1)")==null)
    verify(IncomingLinks.valid("https://user:password@example.org/video")==null)
    verify(IncomingLinks.valid("https://example.org/\nvideo")==null)
    verify(IncomingLinks.valid("https://example.org/"+"x".repeat(8192))==null)
    verify(IncomingLinks.valid("https://example.org/watch?v=abc&sig=secret#fragment")=="https://example.org/watch?v=abc&sig=secret")
    verify(SourceIdentity.key("https://youtu.be/aqz-KE-bpKQ")==SourceIdentity.key("https://www.youtube.com/shorts/aqz-KE-bpKQ"))
    verify(SourceIdentity.key("https://youtube.com/watch?v=aqz-KE-bpKQ&list=abc")!="youtube:aqz-KE-bpKQ")
    verify(SourceIdentity.key("https://example.org/watch?v=abc&variant=1")!=SourceIdentity.key("https://example.org/watch?v=abc&variant=2"))
    val imported=IncomingLinks.read("\uFEFFhttps://example.org/one\r\n\ninvalid\nhttps://example.org/one\nhttps://example.org/two\n".byteInputStream())
    verify(imported.links.size==2 && imported.invalid==1 && imported.duplicates==1)
    verify(runCatching { IncomingLinks.read("x".repeat(8193).byteInputStream()) }.isFailure)
    verify(runCatching { IncomingLinks.read("https://example.org/one".byteInputStream()) { true } }.isFailure)
    verify(runCatching { IncomingLinks.read((1..51).joinToString("\n") { "https://example.org/$it" }.byteInputStream()) }.isFailure)
    verify(runCatching { IncomingLinks.extract("x".repeat(IncomingLinks.MAX_BYTES+1)) }.isFailure)
    verify(FilenamePolicy.compile(FilenamePolicy.DEFAULT)=="%(title).150B [%(id)s].%(ext)s")
    verify(FilenamePolicy.preview("{platform} - {title}.{ext}")=="YouTube - Video.mp4")
    verify(runCatching { FilenamePolicy.validateNative(FilenamePolicy.compile(FilenamePolicy.DEFAULT)) }.isSuccess)
    verify(runCatching { FilenamePolicy.validateNative("%(title)s.%(ext)s") }.isSuccess)
    verify(runCatching { FilenamePolicy.validateNative("%(http_headers.Cookie)s.%(ext)s") }.isFailure)
    verify(runCatching { FilenamePolicy.validateNative("%(http_headers.Authorization)s.%(ext)s") }.isFailure)
    listOf("../{title}.{ext}","{title}\\{id}.{ext}","{token}.{ext}","%(title)s.mp4","{title}","{title}:x.{ext}").forEach {
        verify(runCatching { FilenamePolicy.compile(it) }.isFailure)
    }
    listOf("..","../private","x\\y","a:b","\u0000").forEach { verify(runCatching { FilenamePolicy.folder(it) }.isFailure) }
    verify(FilenamePolicy.folder(" Videos. ")=="Videos")
    verify(RecoveryPolicy.classify("ERROR HTTP Error 403")==RecoveryKind.FORBIDDEN)
    verify(RecoveryPolicy.classify("HTTP 429 too many requests")==RecoveryKind.RATE_LIMIT)
    verify(RecoveryPolicy.retryable(RecoveryPolicy.classify("connection reset")))
    verify(!RecoveryPolicy.retryable(RecoveryKind.FORBIDDEN))
    verify(!RecoveryPolicy.retryable(RecoveryKind.STORAGE))
    verify(runCatching { ClipRange(10.0,5.0).validate() }.isFailure)
    verify(runCatching { ClipRange(Double.NaN,5.0).validate() }.isFailure)
    verify(runCatching { ClipRange(0.0,30.0).validate(20.0) }.isFailure)
    verify(ClipRange(1.5,3.0).validate(10.0).end==3.0)
    verify(BackupPolicy.text("{\"schema\":2}".toByteArray()).isNotEmpty())
    verify(runCatching { BackupPolicy.text(("[".repeat(17)+"0"+"]".repeat(17)).toByteArray()) }.isFailure)
    verify(runCatching { BackupPolicy.text(byteArrayOf(0xc3.toByte(),0x28)) }.isFailure)
    verify(runCatching { BackupPolicy.text(ByteArray(512*1024+1)) }.isFailure)
    println("$checks feature-policy regression checks passed")
}
