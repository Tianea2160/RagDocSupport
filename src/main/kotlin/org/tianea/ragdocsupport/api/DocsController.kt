package org.tianea.ragdocsupport.api

import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.tianea.ragdocsupport.core.port.VectorStore
import org.tianea.ragdocsupport.sync.BulkRegisterRequest
import org.tianea.ragdocsupport.sync.DocSyncService

@RestController
@RequestMapping("/api/docs")
class DocsController(
    private val syncService: DocSyncService,
    private val vectorStore: VectorStore,
    private val embeddingModel: EmbeddingModel,
) {
    @PostMapping("/register")
    fun register(
        @RequestBody request: RegisterRequest,
    ): ResponseEntity<RegisterResponse> {
        val result = syncService.register(request.library, request.version, request.docUrl)
        return ResponseEntity.ok(
            RegisterResponse(
                success = result.success,
                chunksIndexed = result.chunksIndexed,
                failedDocTypes = result.failedDocTypes.map { "${it.docType}: ${it.triedUrls}" },
            ),
        )
    }

    @PostMapping("/register/bulk")
    fun registerBulk(
        @RequestBody requests: List<BulkRegisterRequest>,
    ): ResponseEntity<BulkRegisterResponse> {
        val result = syncService.registerBulk(requests)
        return ResponseEntity.ok(
            BulkRegisterResponse(
                totalChunks = result.totalChunks,
                successCount = result.successCount,
                failureCount = result.failureCount,
                entries = result.entries.map {
                    BulkRegisterEntryResponse(
                        library = it.library,
                        version = it.version,
                        success = it.result.success,
                        chunksIndexed = it.result.chunksIndexed,
                    )
                },
            ),
        )
    }

    @GetMapping("/search")
    fun search(
        @RequestParam query: String,
        @RequestParam(required = false) library: String?,
        @RequestParam(required = false) version: String?,
        @RequestParam(required = false, defaultValue = "5") limit: Int,
    ): ResponseEntity<List<SearchResultResponse>> {
        val queryEmbedding = embeddingModel.embed(query)
        val results = vectorStore.search(queryEmbedding, library, version, limit)
        return ResponseEntity.ok(
            results.map {
                SearchResultResponse(
                    library = it.metadata.library,
                    version = it.metadata.version,
                    section = it.metadata.section,
                    sectionPath = it.metadata.sectionPath,
                    sourceUrl = it.metadata.sourceUrl,
                    text = it.text,
                )
            },
        )
    }

    @GetMapping("/list")
    fun list(): ResponseEntity<List<LibraryInfoResponse>> {
        val libraries = vectorStore.listIndexedLibraries()
        return ResponseEntity.ok(
            libraries.map {
                LibraryInfoResponse(
                    library = it.library,
                    version = it.version,
                    chunkCount = it.chunkCount,
                    latest = it.latest,
                )
            },
        )
    }

    @GetMapping("/compare")
    fun compare(
        @RequestParam library: String,
        @RequestParam fromVersion: String,
        @RequestParam toVersion: String,
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false, defaultValue = "10") limit: Int,
    ): ResponseEntity<CompareResponse> {
        val searchQuery = query ?: "migration changes from $fromVersion to $toVersion"
        val queryEmbedding = embeddingModel.embed(searchQuery)
        val results = vectorStore.searchByVersions(queryEmbedding, library, listOf(fromVersion, toVersion), limit)
        return ResponseEntity.ok(
            CompareResponse(
                library = library,
                fromVersion = fromVersion,
                toVersion = toVersion,
                results = results.map {
                    SearchResultResponse(
                        library = it.metadata.library,
                        version = it.metadata.version,
                        section = it.metadata.section,
                        sectionPath = it.metadata.sectionPath,
                        sourceUrl = it.metadata.sourceUrl,
                        text = it.text,
                    )
                },
            ),
        )
    }
}

data class RegisterRequest(
    val library: String,
    val version: String,
    val docUrl: String? = null,
)

data class RegisterResponse(
    val success: Boolean,
    val chunksIndexed: Int,
    val failedDocTypes: List<String>,
)

data class BulkRegisterResponse(
    val totalChunks: Int,
    val successCount: Int,
    val failureCount: Int,
    val entries: List<BulkRegisterEntryResponse>,
)

data class BulkRegisterEntryResponse(
    val library: String,
    val version: String,
    val success: Boolean,
    val chunksIndexed: Int,
)

data class SearchResultResponse(
    val library: String,
    val version: String,
    val section: String,
    val sectionPath: String,
    val sourceUrl: String,
    val text: String,
)

data class LibraryInfoResponse(
    val library: String,
    val version: String,
    val chunkCount: Long,
    val latest: Boolean,
)

data class CompareResponse(
    val library: String,
    val fromVersion: String,
    val toVersion: String,
    val results: List<SearchResultResponse>,
)
