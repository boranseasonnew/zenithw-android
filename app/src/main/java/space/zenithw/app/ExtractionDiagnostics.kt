package space.zenithw.app

/** Keep the actual failure instead of truncating the first optional-dependency warning. */
internal fun extractionFailureLines(message: String): List<String> {
    val lines=message.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
    val errors=lines.filter { it.startsWith("ERROR:",ignoreCase=true) }
    val warnings=lines.filter { it.startsWith("WARNING:",ignoreCase=true) }
    return (warnings.takeLast(2)+errors.takeLast(4)).ifEmpty { lines.takeLast(4) }
}
