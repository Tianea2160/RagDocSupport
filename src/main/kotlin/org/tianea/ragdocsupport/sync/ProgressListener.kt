package org.tianea.ragdocsupport.sync

fun interface ProgressListener {
    fun onEvent(event: ProgressEvent)

    companion object {
        val NOOP = ProgressListener { }
    }
}

data class ProgressEvent(
    val type: ProgressEventType,
    val message: String,
)

enum class ProgressEventType {
    INFO,
    CRAWL,
    CHUNK,
    EMBED,
    UPSERT,
    WARN,
    COMPLETE,
    ERROR,
}
