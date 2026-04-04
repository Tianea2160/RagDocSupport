package org.tianea.ragdocsupport.sync

import org.slf4j.LoggerFactory
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.stereotype.Service
import org.tianea.ragdocsupport.core.model.Dependency
import org.tianea.ragdocsupport.core.model.DocChunk
import org.tianea.ragdocsupport.core.model.DocMetadata
import org.tianea.ragdocsupport.core.model.DocType
import org.tianea.ragdocsupport.core.port.DocSourceRepository
import org.tianea.ragdocsupport.core.port.VectorStore
import org.tianea.ragdocsupport.crawler.DocChunker
import org.tianea.ragdocsupport.crawler.DocCrawler
import org.tianea.ragdocsupport.crawler.HtmlToMarkdownConverter

@Service
class DocSyncService(
    private val crawler: DocCrawler,
    private val converter: HtmlToMarkdownConverter,
    private val chunker: DocChunker,
    private val embeddingModel: EmbeddingModel,
    private val vectorStore: VectorStore,
    private val docSourceRepository: DocSourceRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun register(
        library: String,
        version: String,
        docUrl: String? = null,
    ): RegisterResult {
        val dependency = Dependency(group = "", artifact = "", version = version)

        // Resolve URL candidates per docType
        val urlCandidates = resolveUrlCandidates(library, dependency, docUrl)
        if (urlCandidates.isEmpty()) {
            return RegisterResult(success = false)
        }

        // Ensure collection exists
        vectorStore.ensureCollection()

        // Mark all existing versions of this library as not latest
        val existingVersions =
            vectorStore
                .listIndexedLibraries()
                .filter { it.library == library && it.latest }
        for (info in existingVersions) {
            vectorStore.updateLatestFlag(library, info.version, false)
        }

        // Delete existing docs for this version (re-index)
        vectorStore.deleteByLibraryAndVersion(library, version)

        var totalChunks = 0
        val failedDocTypes = mutableListOf<FailedDocType>()

        for ((docType, candidateUrls) in urlCandidates) {
            log.info("Processing $library:$version ($docType) with ${candidateUrls.size} candidate URL(s)")

            // Crawl with fallback
            val crawlResult = crawler.crawlWithFallback(candidateUrls)

            val document = crawlResult.document
            val resolvedUrl = crawlResult.resolvedUrl
            if (document == null || resolvedUrl == null) {
                log.warn("All candidate URLs failed for $library:$version ($docType)")
                failedDocTypes.add(FailedDocType(docType, crawlResult.failedUrls))
                continue
            }

            log.info("Successfully crawled $library:$version ($docType) from $resolvedUrl")

            // Convert to markdown
            val markdown = converter.convert(document)
            if (markdown.isBlank()) {
                log.warn("Empty content from: $resolvedUrl")
                failedDocTypes.add(FailedDocType(docType, listOf(resolvedUrl)))
                continue
            }

            // Chunk
            val chunkResults = chunker.chunk(markdown)
            log.info("Created ${chunkResults.size} chunks from $resolvedUrl")

            // Embed
            val texts = chunkResults.map { it.text }
            val embeddings = embeddingModel.embedAll(texts)

            // Create DocChunks with embeddings
            val docChunks =
                chunkResults.mapIndexed { idx, chunkResult ->
                    DocChunk(
                        text = chunkResult.text,
                        metadata =
                        DocMetadata(
                            library = library,
                            version = version,
                            docType = docType,
                            section = chunkResult.section,
                            sectionPath = chunkResult.sectionPath,
                            sourceUrl = resolvedUrl,
                        ),
                        embedding = embeddings[idx],
                    )
                }

            // Store
            vectorStore.upsert(docChunks)
            totalChunks += docChunks.size
        }

        return RegisterResult(
            success = totalChunks > 0,
            chunksIndexed = totalChunks,
            failedDocTypes = failedDocTypes,
        )
    }

    private fun resolveUrlCandidates(
        library: String,
        dependency: Dependency,
        explicitUrl: String?,
    ): List<Pair<DocType, List<String>>> {
        if (explicitUrl != null) {
            return listOf(DocType.REFERENCE to listOf(explicitUrl))
        }

        val source = docSourceRepository.findByLibrary(library) ?: return emptyList()
        return source.docs.map { (docType, pattern) ->
            docType to pattern.resolveAll(dependency)
        }
    }

    private fun EmbeddingModel.embedAll(texts: List<String>): List<FloatArray> = texts.map { embed(it) }
}

data class RegisterResult(
    val success: Boolean,
    val chunksIndexed: Int = 0,
    val failedDocTypes: List<FailedDocType> = emptyList(),
)

data class FailedDocType(
    val docType: DocType,
    val triedUrls: List<String>,
)
