package space.zenithw.app

/** Secrets and device-specific grants are never portable defaults. */
internal fun portableOptions(o: DownloadOptions)=o.copy(cookieId=null,proxy="",userAgent="",
    customArguments="",scheduledAt=0,selectedFormat="",filenameOverride="",clipStart="",clipEnd="",clipRanges=emptyList(),exactCuts=false,mergeClips=false)

internal fun duplicateKey(job: DownloadJob): String {
    val o=job.options.copy(cookieId=null,wifiOnly=false,unmeteredOnly=false,chargingOnly=false,scheduledAt=0,
        retries=3,fragmentRetries=3,timeout=20,networkMode="auto",proxy="",userAgent="",
        speedLimitKbps=0,useAria2=false,ariaConnections=4,fragments=4,downloadArchive=false)
    return SourceIdentity.fingerprint(listOf(SourceIdentity.key(job.url),o.json().toString()))
}

internal fun smartSuggestion(store: LocalStore,preview: MediaPreview,manual: DownloadOptions): DownloadOptions {
    if(!store.smartEnabled) return manual
    val saved=store.sourceProfile(preview.url) ?: return manual
    val height=if(preview.metadataReady && saved.height>0)
        preview.heights.filter { it<=saved.height }.maxOrNull() ?: 0 else saved.height
    // Never invent an available format ID or replace authentication/network/clip settings.
    return manual.copy(mode=saved.mode,height=height,container=saved.container,
        audioFormat=saved.audioFormat,audioBitrate=saved.audioBitrate,codec=saved.codec)
}
