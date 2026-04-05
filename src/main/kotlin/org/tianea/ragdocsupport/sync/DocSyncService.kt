package org.tianea.ragdocsupport.sync

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.tianea.ragdocsupport.core.model.Dependency
import org.tianea.ragdocsupport.core.model.DocType
import org.tianea.ragdocsupport.core.model.DocUrlPattern
import org.tianea.ragdocsupport.core.port.DocSourceRepository
import org.tianea.ragdocsupport.core.port.VectorStore

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
    ): RegisterResult {
        val urlCandidates = resolveUrlCandidates(library, Dependency("", "", version), docUrl)
        if (urlCandidates.isEmpty()) {
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

        var totalChunks = 0
        val failedDocTypes = mutableListOf<FailedDocType>()

        for ((docType, pattern, candidateUrls) in urlCandidates) {
            log.info("Processing $library:$version ($docType) with ${candidateUrls.size} candidate URL(s)")

            val chunks =
                if (pattern.recursive) {
                    docProcessor.processRecursive(library, version, docType, candidateUrls, pattern.maxDepth)
                } else {
                    docProcessor.processSingle(library, version, docType, candidateUrls)
                }

            if (chunks == null) {
                failedDocTypes.add(FailedDocType(docType, candidateUrls))
                continue
            }

            vectorStore.upsert(chunks)
            totalChunks += chunks.size
        }

        return RegisterResult(
            success = totalChunks > 0,
            chunksIndexed = totalChunks,
            failedDocTypes = failedDocTypes,
        )
    }

    fun registerBulk(libraries: List<BulkRegisterRequest>): BulkRegisterResult {
        val results = mutableListOf<BulkRegisterEntry>()

        for (request in libraries) {
            log.info("Bulk registering ${request.library}:${request.version}")
            val result = register(request.library, request.version, request.docUrl)
            results.add(BulkRegisterEntry(request.library, request.version, result))
        }

        return BulkRegisterResult(results)
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
