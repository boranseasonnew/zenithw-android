package space.zenithw.app

import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** Local HLS source exercises the real generic extractor, without external credentials. */
internal class MetadataFixture(
    private val delayMillis: Long = 0,
    private val media: ByteArray? = null,
    private val mediaStatus: () -> String = { "200 OK" },
): java.io.Closeable {
    private val server=ServerSocket(0,20,InetAddress.getByName("127.0.0.1"))
    val requests=AtomicInteger()
    private val paths=java.util.concurrent.ConcurrentHashMap<String,AtomicInteger>()
    fun requestsFor(path: String): Int = paths[path]?.get() ?: 0
    val origin="http://127.0.0.1:${server.localPort}"
    private val sockets=java.util.Collections.synchronizedList(mutableListOf<java.net.Socket>())
    private val accept=thread(isDaemon=true,name="MetadataFixture") {
        runCatching {
            while(!server.isClosed) {
                val socket=server.accept();sockets.add(socket)
                thread(isDaemon=true) {
                    socket.use { connection -> runCatching {
                        val input=connection.getInputStream().bufferedReader()
                        val request=input.readLine().orEmpty();var header=input.readLine()
                        while(!header.isNullOrEmpty()) header=input.readLine()
                        requests.incrementAndGet()
                        val path=request.split(' ').getOrElse(1) { "/" }
                        paths.computeIfAbsent(path) { AtomicInteger() }.incrementAndGet()
                        if(delayMillis>0) Thread.sleep(delayMillis)
                        val type: String
                        val body=when {
                            path.startsWith("/captioned") -> {
                                type="text/html; charset=utf-8"
                                val subtitle=if(path.endsWith("broken")) "broken.vtt" else "captions.vtt"
                                """<html><head><title>Caption fixture</title></head><body><video controls><source src="$origin/sample.mp4" type="video/mp4"><track kind="subtitles" srclang="en" src="$origin/$subtitle"></video></body></html>"""
                            }
                            path=="/sample.mp4" && media!=null -> { type="video/mp4"; "" }
                            path.endsWith(".vtt") -> {
                                type="text/vtt; charset=utf-8"
                                "WEBVTT\n\n00:00:00.000 --> 00:00:00.800\nFixture caption\n"
                            }
                            path.startsWith("/declared-duration") -> {
                                type="text/html; charset=utf-8"
                                """<html><head><title>Declared duration</title><script type="application/ld+json">{"@context":"https://schema.org","@type":"VideoObject","name":"Declared duration","description":"Controlled source metadata","duration":"PT2M5S","contentUrl":"$origin/video.mp4"}</script></head><body></body></html>"""
                            }
                            path.startsWith("/duration.mpd") -> {
                                type="application/dash+xml"
                                """<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" mediaPresentationDuration="PT125S" type="static" minBufferTime="PT1S"><Period duration="PT125S"><AdaptationSet mimeType="video/mp4" contentType="video" codecs="avc1.42e01e"><Representation id="video" bandwidth="500000" width="640" height="360"><BaseURL>$origin/video.mp4</BaseURL></Representation></AdaptationSet></Period></MPD>"""
                            }
                            path.startsWith("/master") -> {
                                type="application/vnd.apple.mpegurl"
                                "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=500000,RESOLUTION=640x360,CODECS=\"avc1.42e01e,mp4a.40.2\"\n$origin/media.m3u8\n"
                            }
                            path.startsWith("/media") -> {
                                type="application/vnd.apple.mpegurl"
                                "#EXTM3U\n#EXT-X-TARGETDURATION:125\n#EXTINF:125,\n$origin/segment.ts\n#EXT-X-ENDLIST\n"
                            }
                            else -> {
                                type="text/html; charset=utf-8"
                                val schema=if(path.startsWith("/structured")) """<script type="application/ld+json">{"@context":"https://schema.org","@type":"VideoObject","name":"Structured fixture","description":"Controlled HLS fixture","duration":"PT2M5S","contentUrl":"$origin/master.m3u8","thumbnailUrl":"https://i.ytimg.com/vi/aqz-KE-bpKQ/hqdefault.jpg"}</script>""" else ""
                                """<html><head><title>Fixture $path</title><meta property="og:title" content="Fixture $path"><meta property="og:image" content="https://i.ytimg.com/vi/aqz-KE-bpKQ/hqdefault.jpg">$schema</head><body><video controls><source src="$origin/master.m3u8" type="application/x-mpegURL"></video></body></html>"""
                            }
                        }
                        val bytes=if(path=="/sample.mp4" && media!=null) media else body.toByteArray()
                        connection.getOutputStream().apply {
                            val status=when {
                                path=="/broken.vtt" -> "429 Too Many Requests"
                                path=="/sample.mp4" && request.startsWith("GET ") -> mediaStatus()
                                path.startsWith("/fail") -> "503 Service Unavailable"
                                else -> "200 OK"
                            }
                            write("HTTP/1.1 $status\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            if(!request.startsWith("HEAD ")) write(bytes)
                            flush()
                        }
                    } }
                    sockets.remove(socket)
                }
            }
        }
    }
    override fun close() { server.close();synchronized(sockets) { sockets.toList() }.forEach { runCatching { it.close() } };accept.join(1000) }
}
