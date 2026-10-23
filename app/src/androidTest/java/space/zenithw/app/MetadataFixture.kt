package space.zenithw.app

import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** Local HLS source exercises the real generic extractor, without external credentials. */
internal class MetadataFixture(private val delayMillis: Long=0): java.io.Closeable {
    private val server=ServerSocket(0,20,InetAddress.getByName("127.0.0.1"))
    val requests=AtomicInteger()
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
                        if(delayMillis>0) Thread.sleep(delayMillis)
                        val type: String
                        val body=when {
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
                                """<html><head><title>Fixture $path</title><meta property="og:title" content="Fixture $path"><meta property="og:image" content="https://i.ytimg.com/vi/aqz-KE-bpKQ/hqdefault.jpg"></head><body><video controls><source src="$origin/master.m3u8" type="application/x-mpegURL"></video></body></html>"""
                            }
                        }
                        val bytes=body.toByteArray()
                        connection.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            if(!request.startsWith("HEAD ")) write(bytes)
                            flush()
                        }
                    } }
                    sockets.remove(socket)
                }
            }
        }
    }
    override fun close() { server.close();sockets.toList().forEach { runCatching { it.close() } };accept.join(1000) }
}
