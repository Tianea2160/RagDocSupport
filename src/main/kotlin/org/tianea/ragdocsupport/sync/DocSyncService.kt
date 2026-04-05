package org.tianea.ragdocsupport.sync

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.tianea.ragdocsupport.core.model.Dependency
import org.tianea.ragdocsupport.core.model.DocChunk
import org.tianea.ragdocsupport.core.model.DocType
import org.tianea.ragdocsupport.core.model.DocUrlPattern
import org.tianea.ragdocsupport.core.port.DocSourceRepository
import org.tianea.ragdocsupport.core.port.VectorStore
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.StructuredTaskScope
import java.util.concurrent.StructuredTaskScope.Joiner

@Service
class DocSyncService(
    private val docProcessor: DocProcessor,
    private val vectorStore: VectorStore,
    private val docSourceRepository: DocSourceRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun register(
        library: String,
        version: String,
        docUrl: String? = null,
        listener: ProgressListener = ProgressListener.NOOP,
    ): RegisterResult {
        listener.onEvent(ProgressEvent(ProgressEventType.INFO, "Starting registration for $library:$version"))

        val urlCandidates = resolveUrlCandidates(library, Dependency("", "", version), docUrl)
        if (urlCandidates.isEmpty()) {
            listener.onEvent(ProgressEvent(ProgressEventType.ERROR, "No URL candidates found for $library:$version"))
            return RegisterResult(success = false)
        }

        vectorStore.ensureCollection()

        val existingVersions =
            vectorStore
                .listIndexedLibraries()
                .filter { it.library == library && it.latest }
        for (info in existingVersions) {
            vectorStore.updateLatestFlag(library, info.version, false)
        }

        vectorStore.deleteByLibraryAndVersion(library, version)

        val docTypeResults = ConcurrentLinkedQueue<DocTypeProcessResult>()

        StructuredTaskScope.open(Joiner.awaitAll<Unit>()).use { scope ->
            for ((docType, pattern, candidateUrls) in urlCandidates) {
                scope.fork<Unit>(
                    Runnable {
                        val procMsg =
                            "Processing $library:$version ($docType) " +
                                "with ${candidateUrls.size} candidate URL(s)"
                        log.info(procMsg)
                        listener.onEvent(ProgressEvent(ProgressEventType.INFO, procMsg))
                        val chunks =
                            if (pattern.recursive) {
                                docProcessor.processRecursive(
                                    library,
                                    version,
                                    docType,
                                    candidateUrls,
                                    pattern.maxDepth,
                                    listener,
                                )
                            } else {
                                docProcessor.processSingle(library, version, docType, candidateUrls, listener)
                            }
                        docTypeResults.add(DocTypeProcessResult(docType, candidateUrls, chunks))
                    },
                )
            }
            scope.join()
        }

        var totalChunks = 0
        val failedDocTypes = mutableListOf<FailedDocType>()

        for (result in docTypeResults) {
            if (result.chunks == null) {
                failedDocTypes.add(FailedDocType(result.docType, result.urls))
                continue
            }
            vectorStore.upsert(result.chunks)
            totalChunks += result.chunks.size
            listener.onEvent(
                ProgressEvent(
                    ProgressEventType.UPSERT,
                    "Upserted ${result.chunks.size} chunks for $library:$version (${result.docType})",
                ),
            )
        }

        val completionMsg =
            if (totalChunks > 0) {
                "Registration complete: $totalChunks chunks indexed for $library:$version"
            } else {
                "Registration failed: no chunks indexed for $library:$version"
            }
        listener.onEvent(
            ProgressEvent(
                if (totalChunks > 0) ProgressEventType.COMPLETE else ProgressEventType.ERROR,
                completionMsg,
            ),
        )

        return RegisterResult(
            success = totalChunks > 0,
            chunksIndexed = totalChunks,
            failedDocTypes = failedDocTypes,
        )
    }

    fun registerBulk(
        libraries: List<BulkRegisterRequest>,
        listener: ProgressListener = ProgressListener.NOOP,
    ): BulkRegisterResult {
        val results = ConcurrentLinkedQueue<BulkRegisterEntry>()

        StructuredTaskScope.open(Joiner.awaitAll<Unit>()).use { scope ->
            for (request in libraries) {
                scope.fork<Unit>(
                    Runnable {
                        log.info("Bulk registering ${request.library}:${request.version}")
                        val result = register(request.library, request.version, request.docUrl, listener)
                        results.add(BulkRegisterEntry(request.library, request.version, result))
                    },
                )
            }
            scope.join()
        }

        return BulkRegisterResult(results.toList())
    }

    private fun resolveUrlCandidates(
        library: String,
        dependency: Dependency,
        explicitUrl: String?,
    ): List<DocTypeCandidates> {
        if (explicitUrl != null) {
            return listOf(
                DocTypeCandidates(DocType.REFERENCE, DocUrlPattern(explicitUrl), listOf(explicitUrl)),
            )
        }

        val source = docSourceRepository.findByLibrary(library) ?: return emptyList()
        return source.docs.map { (docType, pattern) ->
            DocTypeCandidates(docType, pattern, pattern.resolveAll(dependency))
        }
    }
}

private data class DocTypeProcessResult(
    val docType: DocType,
    val urls: List<String>,
    val chunks: List<DocChunk>?,
)

data class DocTypeCandidates(
    val docType: DocType,
    val pattern: DocUrlPattern,
    val urls: List<String>,
)

data class RegisterResult(
    val success: Boolean,
    val chunksIndexed: Int = 0,
    val failedDocTypes: List<FailedDocType> = emptyList(),
)

data class FailedDocType(
    val docType: DocType,
    val triedUrls: List<String>,
)

data class BulkRegisterRequest(
    val library: String,
    val version: String,
    val docUrl: String? = null,
)

data class BulkRegisterResult(
    val entries: List<BulkRegisterEntry>,
) {
    val totalChunks: Int get() = entries.sumOf { it.result.chunksIndexed }
    val successCount: Int get() = entries.count { it.result.success }
    val failureCount: Int get() = entries.count { !it.result.success }
}

data class BulkRegisterEntry(
    val library: String,
    val version: String,
    val result: RegisterResult,
)
