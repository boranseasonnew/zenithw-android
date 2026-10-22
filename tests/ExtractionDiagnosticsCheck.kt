package space.zenithw.app

fun main() {
    val output="WARNING: [PornHub] The extractor is attempting impersonation, but no impersonate target is available. "+"Optional dependency guidance. ".repeat(20)+
        "\nERROR: [PornHub] fixture: Unable to download webpage: HTTP Error 410: Gone\n  File stack trace"
    val lines=extractionFailureLines(output)
    check(lines.last().contains("HTTP Error 410"))
    check(lines.first().contains("no impersonate target"))
    check(extractionFailureLines("  connection timed out\n").single()=="connection timed out")
    check(extractionFailureLines("").isEmpty())
    println("Extraction diagnostics checks passed")
}
