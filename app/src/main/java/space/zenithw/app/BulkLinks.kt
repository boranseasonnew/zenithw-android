package space.zenithw.app

import java.util.UUID

data class BulkLinkInput(val id: String=UUID.randomUUID().toString(),val url: String="")

internal fun replaceBulkLink(rows: List<BulkLinkInput>,id: String,value: String): List<BulkLinkInput> {
    val index=rows.indexOfFirst { it.id==id }
    if(index<0) return rows
    val parts=value.lineSequence().map(String::trim).filter(String::isNotBlank).toList().ifEmpty { listOf("") }
    require(rows.size+parts.size-1<=50)
    require(parts.all { it.length<=8192 })
    return rows.take(index)+parts.mapIndexed { part,url ->
        BulkLinkInput(if(part==0) id else UUID.randomUUID().toString(),url)
    }+rows.drop(index+1)
}
