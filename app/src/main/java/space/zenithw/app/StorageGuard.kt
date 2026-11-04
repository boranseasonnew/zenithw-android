package space.zenithw.app

import android.content.Context
import java.io.File

class StorageGuard(private val context: Context,private val store: LocalStore) {
    data class Candidate(val jobId: String,val bytes: Long,val kind: String="download")
    fun abandoned(): List<Candidate> {
        val root=File(context.noBackupFilesDir,"downloads").canonicalFile
        val protected=setOf(JobState.QUEUED,JobState.RUNNING,JobState.SAVING,JobState.PAUSED)
        val downloads=store.jobs.value.filter { it.state !in protected }.mapNotNull { job ->
            if(!job.id.matches(Regex("[a-zA-Z0-9-]{1,80}"))) return@mapNotNull null
            val folder=File(root,job.id)
            if(!folder.isDirectory || folder.canonicalFile.parentFile!=root || folder.lastModified()>System.currentTimeMillis()-86400000) null
            else Candidate(job.id,folder.listFiles().orEmpty().filter { it.isFile && it.canonicalFile.parentFile==folder.canonicalFile }.sumOf { it.length() })
        }
        val toolsRoot=File(context.cacheDir,"media-tools").canonicalFile
        val activeTool=androidx.work.WorkManager.getInstance(context).getWorkInfosForUniqueWork(LocalToolWorker.NAME).get().any { !it.state.isFinished }
        val tools=if(activeTool) emptyList() else toolsRoot.listFiles().orEmpty().mapNotNull { folder ->
            if(folder.canonicalFile.parentFile!=toolsRoot || !folder.name.matches(Regex("[a-f0-9-]{36}")) ||
                folder.lastModified()>System.currentTimeMillis()-86400000) return@mapNotNull null
            val marker=File(folder,".zenith-owned")
            if(!marker.isFile || marker.canonicalFile.parentFile!=folder.canonicalFile || marker.length()>100 || marker.readText()!="ZenithW.LocalMediaTools/1") null
            else Candidate(folder.name,folder.listFiles().orEmpty().filter { it.isFile && it.canonicalFile.parentFile==folder.canonicalFile }.sumOf { it.length() },"tool")
        }
        return downloads+tools
    }
    /** Only the reviewed IDs; recheck liveness, age and containment at deletion time. */
    fun cleanup(reviewed: List<Candidate>): Long=synchronized(store) {
        val allowed=abandoned().associateBy { it.kind+":"+it.jobId };var freed=0L
        val root=File(context.noBackupFilesDir,"downloads").canonicalFile
        reviewed.forEach { old ->
            val current=allowed[old.kind+":"+old.jobId] ?: return@forEach
            if(current.bytes!=old.bytes) return@forEach
            val parent=if(old.kind=="tool") File(context.cacheDir,"media-tools").canonicalFile else root
            val folder=File(parent,old.jobId)
            if(folder.canonicalFile.parentFile==parent) folder.listFiles().orEmpty().forEach { file ->
                if(file.isFile && file.canonicalFile.parentFile==folder.canonicalFile) {
                    val size=file.length();if(file.delete()) freed+=size
                }
            }
        }
        freed
    }
}
