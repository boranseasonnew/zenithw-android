package space.zenithw.app

fun main() {
    var checks = 0
    fun accepted(input: String, expected: List<String>) { check(parseCustomArguments(input) == expected) { input }; checks++ }
    fun rejected(input: String) { check(runCatching { parseCustomArguments(input) }.isFailure) { input }; checks++ }
    accepted("-f bestaudio -x --audio-format mp3", listOf("-f", "bestaudio", "-x", "--audio-format", "mp3"))
    accepted("--format 'bv*[height<=1080]+ba/b'", listOf("--format", "bv*[height<=1080]+ba/b"))
    accepted("--user-agent \"Zenith test agent\"\n--force-ipv4", listOf("--user-agent", "Zenith test agent", "--force-ipv4"))
    accepted("--match-filter 'duration < 600 & !is_live'", listOf("--match-filter", "duration < 600 & !is_live"))
    accepted("--sub-langs=tr.*,en.* --write-subs", listOf("--sub-langs=tr.*,en.*", "--write-subs"))
    accepted("-f137+140 -N4 -S+size", listOf("-f137+140", "-N4", "-S+size"))
    accepted("--download-sections '*00:30-01:00' --fixup never", listOf("--download-sections", "*00:30-01:00", "--fixup", "never"))
    accepted("--user-agent 'It\"s literal'", listOf("--user-agent", "It\"s literal"))
    accepted("--user-agent \"a\\\"b\"", listOf("--user-agent", "a\"b"))
    listOf("", "yt-dlp -f best", "https://example.com/a", "-- -f best", "--output /sdcard/file", "--output=/sdcard/file",
        "-o/sdcard/file", "-P/tmp", "--paths=/tmp", "--config-locations /tmp/a", "--config /tmp/a", "-a/tmp/urls",
        "--exec 'echo x'", "--exec-before-download x", "--load-info-json x", "--cookies /tmp/c", "--cookies-from-browser chrome",
        "--ffmpeg-location /tmp/a", "--downloader /tmp/a", "--postprocessor-args 'x'", "--use-postprocessor x", "--alias x y",
        "--print filename", "--simulate", "--skip-download", "--flat-playlist", "--dump-json", "--progress-template x",
        "--format", "--format=", "--format --verbose", "--verbose=true", "-xv", "-f 'unclosed", "--format best\\",
        "-f best; echo x", "-f best | x", "-f best && x", "-f best > /tmp/out", "--parse-metadata 'x:y'", "--unknown-option x",
        "-f best\u0000", "-f " + "a".repeat(8192)).forEach(::rejected)
    println("$checks command argument regression checks passed")
}
