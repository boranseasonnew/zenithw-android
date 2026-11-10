package space.zenithw.app

data class DownloadOutcome(val notes: List<String> = emptyList(), val archiveSkipped: Boolean=false)

internal class DownloadFailureException(val failure: RecoveryFailure, message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)
