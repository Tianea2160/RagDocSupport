package org.tianea.ragdocsupport.core.port

import org.tianea.ragdocsupport.core.model.DocChunk

interface VectorStore {
    fun ensureCollection()
    fun upsert(chunks: List<DocChunk>)
    fun search(query: FloatArray, library: String? = null, version: String? = null, limit: Int = 5): List<DocChunk>
    fun searchByVersions(query: FloatArray, library: String, versions: List<String>, limit: Int = 10): List<DocChunk>
    fun deleteByLibraryAndVersion(library: String, version: String)
    fun updateLatestFlag(library: String, version: String, latest: Boolean)
    fun listIndexedLibraries(): List<LibraryIndexInfo>
}

data class LibraryIndexInfo(
    val library: String,
    val version: String,
    val chunkCount: Long,
    val latest: Boolean,
)
