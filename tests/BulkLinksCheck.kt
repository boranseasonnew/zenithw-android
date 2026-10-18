package space.zenithw.app

fun main() {
    val first=BulkLinkInput(url="https://example.org/first")
    val second=BulkLinkInput(url="https://example.org/second")
    val rows=listOf(first,second)
    val inserted=replaceBulkLink(rows,first.id," https://example.org/a\r\n\nhttps://example.org/b ")
    check(inserted.map { it.url }==listOf("https://example.org/a","https://example.org/b",second.url))
    check(inserted.first().id==first.id)
    check(inserted.last()==second)
    check(inserted.map { it.id }.toSet().size==3)
    check(rows.first()==first) // The caller's saved draft stays intact.
    check(replaceBulkLink(rows,first.id,"").first()==first.copy(url=""))
    check(replaceBulkLink(rows,"removed-row","https://example.org")==rows)
    val full=List(50) { BulkLinkInput(url="https://example.org/$it") }
    check(runCatching { replaceBulkLink(full,full[0].id,"https://example.org/a\nhttps://example.org/b") }.isFailure)
    check(replaceBulkLink(full,full[0].id,"https://example.org/new").size==50)
    check(runCatching { replaceBulkLink(rows,first.id,"x".repeat(8193)) }.isFailure)
    println("10 bulk-link regression checks passed")
}
